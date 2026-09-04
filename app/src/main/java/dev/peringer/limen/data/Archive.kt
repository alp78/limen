package dev.peringer.limen.data

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveImportResult(
  val imported: Int,
  val skippedExisting: Int,
  val rejected: Int,
)

/** Versioned, local-only backup of protocol JSON and its original evidence files. */
internal object ArchiveIO {
  private const val MANIFEST = "limen-archive.txt"
  private const val MARKER = "LIMEN_ARCHIVE\nversion=1\n"
  private const val MAX_ENTRIES = 20_000
  private const val STAGE_PREFIX = ".archive-import-"
  private const val MAX_UNPACKED_BYTES = 2L * 1024L * 1024L * 1024L

  private val json = Json { ignoreUnknownKeys = true }

  fun export(root: File, output: OutputStream) {
    ZipOutputStream(output.buffered()).use { zip ->
      put(zip, MANIFEST, MARKER.toByteArray(Charsets.UTF_8))
      listOf("protocols", "photos", "signatures").forEach { folder ->
        val base = File(root, folder)
        base.walkTopDown()
          .filter { it.isFile && it.canonicalPath.startsWith(base.canonicalPath + File.separator) }
          .sortedBy { it.relativeTo(base).invariantSeparatorsPath }
          .forEach { file ->
            val relative = file.relativeTo(base).invariantSeparatorsPath
            val entryName = "$folder/$relative"
            if (isAllowed(entryName)) put(zip, entryName, file)
          }
      }
    }
  }

  fun import(root: File, input: InputStream): ArchiveImportResult {
    root.mkdirs()
    // A restore killed part way through leaves its stage behind, and nothing else ever swept
    // them: on a phone that has lost the app mid-import twice, two unpacked copies of the
    // whole archive sat in filesDir for good.
    root.listFiles()
      ?.filter { it.isDirectory && it.name.startsWith(STAGE_PREFIX) }
      ?.forEach { it.deleteRecursively() }
    val stage = File(root, "$STAGE_PREFIX${System.nanoTime()}").apply { mkdirs() }
    try {
      unpack(stage, input)
      require(File(stage, MANIFEST).readText() == MARKER) { "Not a Limen archive" }
      return installValidated(root, stage)
    } finally {
      stage.deleteRecursively()
    }
  }

  private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
    zip.write(bytes)
    zip.closeEntry()
  }

  private fun put(zip: ZipOutputStream, name: String, file: File) {
    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
    file.inputStream().buffered().use { it.copyTo(zip) }
    zip.closeEntry()
  }

  private fun unpack(stage: File, input: InputStream) {
    val rootPath = stage.canonicalPath + File.separator
    val seen = mutableSetOf<String>()
    var entries = 0
    var bytes = 0L
    var hasManifest = false
    ZipInputStream(input.buffered()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries++
        require(entries <= MAX_ENTRIES) { "Archive contains too many entries" }
        val name = entry.name
        require(!entry.isDirectory && isAllowed(name) && seen.add(name)) { "Invalid archive entry" }
        val target = File(stage, name).canonicalFile
        require(target.path.startsWith(rootPath)) { "Archive entry escapes its root" }
        target.parentFile?.mkdirs()
        target.outputStream().buffered().use { out ->
          val buffer = ByteArray(32 * 1024)
          while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            bytes += read
            require(bytes <= MAX_UNPACKED_BYTES) { "Archive is too large" }
            out.write(buffer, 0, read)
          }
        }
        if (name == MANIFEST) hasManifest = true
        zip.closeEntry()
      }
    }
    require(hasManifest) { "Archive manifest is missing" }
  }

  private fun isAllowed(name: String): Boolean {
    if (name == MANIFEST) return true
    if (name.contains('\\') || name.startsWith('/') || name.contains("../")) return false
    val parts = name.split('/')
    if (parts.size !in 2..3) return false
    return when (parts.first()) {
      "protocols" -> parts.size == 2 && parts[1].endsWith(".json") && safe(parts[1])
      "photos", "signatures" -> parts.size == 3 && safe(parts[1]) && safe(parts[2])
      else -> false
    }
  }

  private fun safe(segment: String): Boolean =
    segment.length in 1..200 && segment != "." && segment != ".." &&
      segment.matches(Regex("[A-Za-z0-9._-]+"))

  private fun installValidated(root: File, stage: File): ArchiveImportResult {
    val stagedProtocols = File(stage, "protocols").listFiles { file -> file.extension == "json" }
      ?.sortedBy { it.name }
      .orEmpty()
    var imported = 0
    var skipped = 0
    var rejected = 0

    stagedProtocols.forEach { sourceJson ->
      val protocol = runCatching {
        json.decodeFromString(Protocol.serializer(), sourceJson.readText())
      }.getOrNull()
      if (protocol == null || !safe(protocol.id) || sourceJson.nameWithoutExtension != protocol.id) {
        rejected++
        return@forEach
      }

      val targetJson = File(File(root, "protocols"), sourceJson.name)
      if (targetJson.exists()) {
        skipped++
        return@forEach
      }

      val stagedPhotos = File(File(stage, "photos"), protocol.id)
      val stagedInk = File(File(stage, "signatures"), protocol.id)
      if (!validEvidence(protocol, stagedPhotos, stagedInk)) {
        rejected++
        return@forEach
      }

      val targetPhotos = File(File(root, "photos"), protocol.id)
      val targetInk = File(File(root, "signatures"), protocol.id)
      if (targetPhotos.exists() || targetInk.exists()) {
        rejected++
        return@forEach
      }

      val installed = runCatching {
        installDirectory(stagedPhotos, targetPhotos)
        installDirectory(stagedInk, targetInk)
        atomicCopy(sourceJson, targetJson)
      }
      if (installed.isSuccess) {
        imported++
      } else {
        targetJson.delete()
        targetPhotos.deleteRecursively()
        targetInk.deleteRecursively()
        rejected++
      }
    }
    return ArchiveImportResult(imported, skipped, rejected)
  }

  private fun validEvidence(protocol: Protocol, photos: File, ink: File): Boolean {
    val referencedNames = protocol.defects.flatMap { it.photoFiles() } +
      protocol.objects.flatMap { it.photoFiles() } +
      protocol.signatures.map { it.file }.filter { it.isNotBlank() }
    if (referencedNames.any { !safe(it) }) return false
    if (ProtocolIntegrity.missingEvidence(protocol, photos, ink).isNotEmpty()) return false
    if (protocol.isFinalized() && !ProtocolIntegrity.matches(protocol, photos, ink)) return false
    val describedPhotos = protocol.defects.flatMap { it.photoEvidence } +
      protocol.objects.flatMap { it.photoEvidence }
    return describedPhotos.all { evidence ->
      evidence.sha256.isBlank() || runCatching {
        ProtocolIntegrity.fileHash(File(photos, evidence.file)).equals(evidence.sha256, ignoreCase = true)
      }.getOrDefault(false)
    }
  }

  private fun installDirectory(source: File, target: File) {
    if (!source.exists()) return
    require(!target.exists()) { "Evidence destination already exists" }
    source.copyRecursively(target, overwrite = false)
  }

  private fun atomicCopy(source: File, target: File) {
    target.parentFile?.mkdirs()
    val tmp = File.createTempFile("${target.nameWithoutExtension}-", ".tmp", target.parentFile)
    try {
      source.copyTo(tmp, overwrite = true)
      try {
        Files.move(tmp.toPath(), target.toPath(), ATOMIC_MOVE)
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), target.toPath())
      }
    } finally {
      tmp.delete()
    }
  }
}
