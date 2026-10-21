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
  val repairable: Int = 0,
  val repaired: Int = 0,
)

/**
 * The one bound a restore keeps: the disk it writes to. Asked once per megabyte rather than
 * per read, because the zip reader hands back about 512 bytes at a time and a 66 MB restore
 * asked the file system 135,000 times. Each check leaves room for the next megabyte and one
 * more, so the disk never fills.
 */
internal class FreeSpace(private val dir: File) {
  private var unchecked = Long.MAX_VALUE

  fun before(bytes: Int) {
    if (unchecked >= MEGABYTE) {
      require(dir.usableSpace > 2 * MEGABYTE) { "Insufficient free storage" }
      unchecked = 0
    }
    unchecked += bytes
  }

  private companion object { const val MEGABYTE = 1024L * 1024L }
}

/** Versioned, local-only backup of protocol JSON and its original evidence files. */
internal object ArchiveIO {
  private const val MANIFEST = "limen-archive.txt"
  private const val MARKER = "LIMEN_ARCHIVE\nversion=1\n"
  private const val STAGE_PREFIX = ".archive-import-"

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * No size or count limit, on this side or on [import]. A cap of 64 MB refused a single
   * record of thirty camera originals, which weigh 69 MB; the only bound a backup meets is
   * the storage it is written to and restored onto.
   */
  fun export(root: File, output: OutputStream, protocolIds: Set<String>? = null) {
    // Closing the zip finishes the archive without closing the caller's stream.
    val open = object : OutputStream() {
      override fun write(value: Int) = output.write(value)
      override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(buffer, offset, length)
      override fun flush() = output.flush()
    }
    ZipOutputStream(open.buffered()).use { zip ->
      put(zip, MANIFEST, MARKER.toByteArray(Charsets.UTF_8))
      fun putChecked(name: String, file: File) = put(zip, name, file)
      val catalogue = Store(root).catalogue()
      require(catalogue.unreadable.none { protocolIds == null || it.id in protocolIds }) { "Recover unreadable records before exporting" }
      catalogue.records.filter { protocolIds == null || it.id in protocolIds }.forEach { p ->
        require(safe(p.id))
        require(ProtocolIntegrity.missingEvidence(p, File(root, "photos/${p.id}"), File(root, "signatures/${p.id}")).isEmpty())
        putChecked("protocols/${p.id}.json", File(root, "protocols/${p.id}.json"))
        val photos = (p.allPhotoFiles() + p.details.attachments.map { it.file }).filter(String::isNotBlank).distinct()
        photos.forEach { name -> require(safe(name)); putChecked("photos/${p.id}/$name", File(root, "photos/${p.id}/$name")) }
        p.signatures.map { it.file }.filter(String::isNotBlank).distinct().forEach { name ->
          require(safe(name)); putChecked("signatures/${p.id}/$name", File(root, "signatures/${p.id}/$name"))
        }
        File(root, "exports/${p.id}").listFiles()?.filter { it.isFile && safe(it.name) }?.forEach { file -> putChecked("exports/${p.id}/${file.name}", file) }
      }
    }
  }

  fun import(root: File, input: InputStream, repairCorrupt: Boolean = false): ArchiveImportResult {
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
      return installValidated(root, stage, repairCorrupt)
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
    val space = FreeSpace(stage)
    var hasManifest = false
    ZipInputStream(input.buffered()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
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
            space.before(read)
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
      "photos", "signatures", "exports" -> parts.size == 3 && safe(parts[1]) && safe(parts[2])
      else -> false
    }
  }

  private fun safe(segment: String): Boolean =
    segment.length in 1..200 && segment != "." && segment != ".." &&
      segment.matches(Regex("[A-Za-z0-9._-]+"))

  private fun installValidated(root: File, stage: File, repairCorrupt: Boolean): ArchiveImportResult {
    val stagedProtocols = File(stage, "protocols").listFiles { file -> file.extension == "json" }
      ?.sortedBy { it.name }
      .orEmpty()
    var imported = 0
    var skipped = 0
    var rejected = 0
    var repairable = 0
    var repaired = 0

    stagedProtocols.forEach { sourceJson ->
      val protocol = runCatching {
        json.decodeFromString(Protocol.serializer(), sourceJson.readText())
      }.getOrNull()
      if (protocol == null || !safe(protocol.id) || sourceJson.nameWithoutExtension != protocol.id) {
        rejected++
        return@forEach
      }

      val targetJson = File(File(root, "protocols"), sourceJson.name)
      val stagedPhotos = File(File(stage, "photos"), protocol.id)
      val stagedInk = File(File(stage, "signatures"), protocol.id)
      if (!validEvidence(protocol, stagedPhotos, stagedInk)) {
        rejected++
        return@forEach
      }

      val targetPhotos = File(File(root, "photos"), protocol.id)
      val targetInk = File(File(root, "signatures"), protocol.id)
      var quarantine: File? = null
      if (targetJson.exists()) {
        val old = runCatching { json.decodeFromString<Protocol>(targetJson.readText()) }.getOrNull()
        val healthy = old?.let { validEvidence(it, targetPhotos, targetInk) } == true
        if (healthy || !repairCorrupt) {
          if (!healthy) repairable++
          skipped++
          return@forEach
        }
        quarantine = File(root, "recovery/${protocol.id}-${System.nanoTime()}").apply { mkdirs() }
        val moved = mutableListOf<Pair<File, File>>()
        val preserved = runCatching {
          listOf(targetJson, targetPhotos, targetInk, File(root, "exports/${protocol.id}")).filter { it.exists() }.forEach { oldFile ->
            val name = if (oldFile == targetJson) oldFile.name else oldFile.parentFile!!.name
            val retained = File(quarantine, name)
            Files.move(oldFile.toPath(), retained.toPath())
            moved += oldFile to retained
          }
        }
        if (preserved.isFailure) {
          moved.asReversed().forEach { (original, retained) -> Files.move(retained.toPath(), original.toPath()) }
          rejected++
          return@forEach
        }
      } else if (targetPhotos.exists() || targetInk.exists() || File(root, "exports/${protocol.id}").exists()) {
        rejected++
        return@forEach
      }

      val installed = runCatching {
        installDirectory(stagedPhotos, targetPhotos)
        installDirectory(stagedInk, targetInk)
        installDirectory(File(stage, "exports/${protocol.id}"), File(root, "exports/${protocol.id}"))
        atomicCopy(sourceJson, targetJson)
      }
      if (installed.isSuccess) {
        imported++
        if (quarantine != null) repaired++
      } else {
        targetJson.delete()
        targetPhotos.deleteRecursively()
        targetInk.deleteRecursively()
        File(root, "exports/${protocol.id}").deleteRecursively()
        quarantine?.let { q ->
          mapOf(targetJson to File(q, targetJson.name), targetPhotos to File(q, "photos"), targetInk to File(q, "signatures"),
            File(root, "exports/${protocol.id}") to File(q, "exports")).forEach { (target, saved) ->
            if (saved.exists()) Files.move(saved.toPath(), target.toPath())
          }
        }
        rejected++
      }
    }
    return ArchiveImportResult(imported, skipped, rejected, repairable, repaired)
  }

  private fun validEvidence(protocol: Protocol, photos: File, ink: File): Boolean {
    val referencedNames = protocol.allPhotoFiles() + protocol.details.attachments.map { it.file }.filter(String::isNotBlank) +
      protocol.signatures.map { it.file }.filter { it.isNotBlank() }
    if (referencedNames.any { !safe(it) }) return false
    if (ProtocolIntegrity.missingEvidence(protocol, photos, ink).isNotEmpty()) return false
    if (protocol.isFinalized() && !ProtocolIntegrity.matches(protocol, photos, ink)) return false
    if (protocol.details.attachments.any { it.file.isNotBlank() && runCatching { ProtocolIntegrity.fileHash(File(photos, it.file)) != it.sha256 }.getOrDefault(true) }) return false
    val describedPhotos = protocol.allPhotoEvidence()
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
