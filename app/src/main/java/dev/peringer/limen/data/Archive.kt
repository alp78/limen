package dev.peringer.limen.data

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.zip.CRC32
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
 * An export that refused to write, instead of reporting a backup that leaves a record out or
 * holds one a restore would reject. [reason] is what the screen tells the person, and [record]
 * names the record when one is to blame.
 */
class ArchiveRefused(val reason: Reason, val record: String = "") : IllegalArgumentException("Backup refused: $reason $record") {
  enum class Reason { UnreadableRecord, DamagedEvidence }
}

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
  /**
   * A backup holding repacked photographs. One with none stays version 1, which 1.2.4 and
   * earlier restore; those refuse version 2 as not a Limen archive, and install nothing.
   */
  private const val MARKER_PACKED = "LIMEN_ARCHIVE\nversion=2\n"
  /** `jpegxl/<record>/<file>` holds photograph `photos/<record>/<file>` repacked by a [JpegPacker]. */
  private const val PACKED = "jpegxl"
  private val JPEG_START = byteArrayOf(-1, -40, -1)
  private const val STAGE_PREFIX = ".archive-import-"

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * No size or count limit, on this side or on [import]. A cap of 64 MB refused a single
   * record of thirty camera originals, which weigh 69 MB; the only bound a backup meets is
   * the storage it is written to and restored onto.
   *
   * Each JPEG photograph goes in repacked by [packer] when that comes out smaller and unpacks
   * to the very same bytes; anything else goes in as it is.
   */
  fun export(root: File, output: OutputStream, protocolIds: Set<String>? = null, packer: JpegPacker? = JpegXl.loaded) {
    // Closing the zip finishes the archive without closing the caller's stream.
    val open = object : OutputStream() {
      override fun write(value: Int) = output.write(value)
      override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(buffer, offset, length)
      override fun flush() = output.flush()
    }
    ZipOutputStream(open.buffered()).use { zip ->
      var packedAny = false
      fun putChecked(name: String, file: File) = put(zip, name, file)
      val catalogue = Store(root).catalogue()
      if (catalogue.unreadable.any { protocolIds == null || it.id in protocolIds }) throw ArchiveRefused(ArchiveRefused.Reason.UnreadableRecord)
      catalogue.records.filter { protocolIds == null || it.id in protocolIds }.forEach { p ->
        require(safe(p.id))
        // The test a restore applies, so a backup is refused while the original can still be
        // looked at, not found unrestorable on the new phone.
        if (!validEvidence(p, File(root, "photos/${p.id}"), File(root, "signatures/${p.id}"))) {
          throw ArchiveRefused(ArchiveRefused.Reason.DamagedEvidence, p.address.ifBlank { p.id })
        }
        putChecked("protocols/${p.id}.json", File(root, "protocols/${p.id}.json"))
        val photos = (p.allPhotoFiles() + p.details.attachments.map { it.file }).filter(String::isNotBlank).distinct()
        photos.forEach { name ->
          require(safe(name))
          val file = File(root, "photos/${p.id}/$name")
          val packed = packer?.let { repack(it, file) }
          if (packed == null) {
            putChecked("photos/${p.id}/$name", file)
          } else {
            putStored(zip, "$PACKED/${p.id}/$name", packed)
            packedAny = true
          }
        }
        p.signatures.map { it.file }.filter(String::isNotBlank).distinct().forEach { name ->
          require(safe(name)); putChecked("signatures/${p.id}/$name", File(root, "signatures/${p.id}/$name"))
        }
        File(root, "exports/${p.id}").listFiles()?.filter { it.isFile && safe(it.name) }?.forEach { file -> putChecked("exports/${p.id}/${file.name}", file) }
      }
      // Last, because only now is it known whether anything was repacked.
      put(zip, MANIFEST, (if (packedAny) MARKER_PACKED else MARKER).toByteArray(Charsets.UTF_8))
    }
  }

  fun import(root: File, input: InputStream, repairCorrupt: Boolean = false, packer: JpegPacker? = JpegXl.loaded): ArchiveImportResult {
    root.mkdirs()
    sweepStages(root)
    val stage = File(root, "$STAGE_PREFIX${System.nanoTime()}").apply { mkdirs() }
    try {
      unpack(stage, input, packer)
      require(File(stage, MANIFEST).readText() in setOf(MARKER, MARKER_PACKED)) { "Not a Limen archive" }
      return installValidated(root, stage, repairCorrupt)
    } finally {
      stage.deleteRecursively()
    }
  }

  /**
   * A restore killed part way through leaves its stage behind. On a phone that lost the app
   * mid-import twice, two unpacked copies of the whole archive sat in filesDir for good, so the
   * start of the app sweeps them as well as the next restore.
   */
  fun sweepStages(root: File) {
    root.listFiles()
      ?.filter { it.isDirectory && it.name.startsWith(STAGE_PREFIX) }
      ?.forEach { it.deleteRecursively() }
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

  /** Already compressed, so stored rather than deflated a second time for nothing. */
  private fun putStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    zip.putNextEntry(ZipEntry(name).apply {
      time = 0L
      method = ZipEntry.STORED
      size = bytes.size.toLong()
      compressedSize = bytes.size.toLong()
      crc = CRC32().apply { update(bytes) }.value
    })
    zip.write(bytes)
    zip.closeEntry()
  }

  /**
   * [file] repacked, or null when it is not a JPEG, does not come out smaller, or does not
   * unpack to the same bytes. libjxl's maintainers count about one JPEG in a hundred that it
   * refuses, and the check costs one unpack per photograph: a backup never holds a photograph
   * it cannot give back exactly.
   */
  private fun repack(packer: JpegPacker, file: File): ByteArray? {
    // The first bytes alone, so a large supporting PDF is never read into memory to be skipped.
    val isJpeg = java.io.RandomAccessFile(file, "r").use { f -> f.length() >= 3 && ByteArray(3).also(f::readFully).contentEquals(JPEG_START) }
    if (!isJpeg) return null
    // Repacking only saves room, so it never fails a backup. The file, its packed form and the
    // unpacked check are held at once, about three times its size: a JPEG too large for the
    // heap goes in as it is, which the zip entry then streams from disk.
    return try {
      val jpeg = file.readBytes()
      val packed = packer.pack(jpeg) ?: return null
      packed.takeIf { it.size < jpeg.size && packer.unpack(it)?.contentEquals(jpeg) == true }
    } catch (_: OutOfMemoryError) {
      null
    }
  }

  private fun unpack(stage: File, input: InputStream, packer: JpegPacker?) {
    val rootPath = stage.canonicalPath + File.separator
    val seen = mutableSetOf<String>()
    val space = FreeSpace(stage)
    var hasManifest = false
    ZipInputStream(input.buffered()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        val name = entry.name
        // A repacked photograph unpacks to its original's place, and counts as that name.
        val packed = name.startsWith("$PACKED/")
        val path = if (packed) "photos/" + name.removePrefix("$PACKED/") else name
        require(!entry.isDirectory && isAllowed(name) && seen.add(path)) { "Invalid archive entry" }
        val target = File(stage, path).canonicalFile
        require(target.path.startsWith(rootPath)) { "Archive entry escapes its root" }
        target.parentFile?.mkdirs()
        if (packed) {
          val bytes = ByteArrayOutputStream().also { copy(zip, it, space) }.toByteArray()
          val jpeg = checkNotNull(packer) { "This backup holds repacked photographs and this build cannot unpack them" }.unpack(bytes)
          requireNotNull(jpeg) { "A repacked photograph does not unpack" }
          space.before(jpeg.size)
          target.writeBytes(jpeg)
        } else {
          target.outputStream().buffered().use { copy(zip, it, space) }
        }
        if (name == MANIFEST) hasManifest = true
        zip.closeEntry()
      }
    }
    require(hasManifest) { "Archive manifest is missing" }
  }

  private fun copy(input: InputStream, out: OutputStream, space: FreeSpace) {
    val buffer = ByteArray(32 * 1024)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      space.before(read)
      out.write(buffer, 0, read)
    }
  }

  private fun isAllowed(name: String): Boolean {
    if (name == MANIFEST) return true
    if (name.contains('\\') || name.startsWith('/') || name.contains("../")) return false
    val parts = name.split('/')
    if (parts.size !in 2..3) return false
    return when (parts.first()) {
      "protocols" -> parts.size == 2 && parts[1].endsWith(".json") && safe(parts[1])
      "photos", "signatures", "exports", PACKED -> parts.size == 3 && safe(parts[1]) && safe(parts[2])
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
      val replacing = targetJson.exists()
      // Evidence folders with no record file are what a restore killed during its install leaves,
      // since the JSON is written last. No record owns them and every retry was refused for
      // them, so they are moved into recovery with the rest, never deleted.
      val orphaned = !replacing && (targetPhotos.exists() || targetInk.exists() || File(root, "exports/${protocol.id}").exists())
      if (replacing) {
        val old = runCatching { json.decodeFromString<Protocol>(targetJson.readText()) }.getOrNull()
        val healthy = old?.let { validEvidence(it, targetPhotos, targetInk) } == true
        if (healthy || !repairCorrupt) {
          if (!healthy) repairable++
          skipped++
          return@forEach
        }
      }
      if (replacing || orphaned) {
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
      }

      val installed = runCatching {
        installDirectory(stagedPhotos, targetPhotos)
        installDirectory(stagedInk, targetInk)
        installDirectory(File(stage, "exports/${protocol.id}"), File(root, "exports/${protocol.id}"))
        atomicCopy(sourceJson, targetJson)
      }
      if (installed.isSuccess) {
        imported++
        if (replacing) repaired++
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

  /**
   * Moved out of the stage, not copied. The stage sits in the same root, so this is a rename:
   * copying wrote every record a second time and ran out of room at an install that FreeSpace
   * never checks, which then counted valid records as rejected.
   */
  private fun installDirectory(source: File, target: File) {
    if (!source.exists()) return
    require(!target.exists()) { "Evidence destination already exists" }
    target.parentFile?.mkdirs()
    Files.move(source.toPath(), target.toPath())
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
