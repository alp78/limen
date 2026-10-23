package dev.peringer.limen.data

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * The protocol store: one JSON file per protocol under [root]/protocols, photos under
 * [root]/photos/(id), ink under [root]/signatures/(id). Plain files in the app's private
 * dir, no database: a handover is a document, documents are files, and files survive
 * schema changes and app reinstalls (via the export) better than any ORM.
 *
 * Pure JVM on purpose, constructed with a [File] rather than a Context, so the round-trip
 * and migration tests run without an emulator.
 */
class Store(private val root: File) {
  data class UnreadableRecord(val id: String, val hasRecovery: Boolean)
  data class Catalogue(val records: List<Protocol>, val unreadable: List<UnreadableRecord>)

  private val json = Json {
    // A version-1 reader opening a version-2 file skips what it does not know rather
    // than refusing the user their own protocol.
    ignoreUnknownKeys = true
    encodeDefaults = false
  }

  private val dir = File(root, "protocols").apply { mkdirs() }

  /**
   * What a process killed part way leaves in [root]: the stage and the decrypted copy of a
   * restore, which only the next restore ever removed, and the half-copied folder of an amendment.
   * Gigabytes of it, so the caller runs this off the main thread. Once per root and process: a
   * restore that is running must never find its stage deleted by a second sweep.
   */
  fun sweepStale() {
    if (!swept.add(root.absolutePath)) return
    ArchiveCrypto.sweep(root)
    File(root, "photos").listFiles()?.filter { it.isDirectory && it.name.startsWith(AMEND_STAGE) }?.forEach { it.deleteRecursively() }
  }

  fun photoDir(id: String): File = File(File(root, "photos"), id).apply { mkdirs() }
  fun signatureDir(id: String): File = File(File(root, "signatures"), id).apply { mkdirs() }

  @Synchronized
  fun catalogue(): Catalogue {
    val records = mutableListOf<Protocol>()
    val failures = mutableListOf<UnreadableRecord>()
    (dir.listFiles { f -> f.extension == "json" } ?: emptyArray()).forEach { file ->
      val p = read(file)?.takeIf { it.id == file.nameWithoutExtension }
      if (p != null) records += p else failures += UnreadableRecord(file.nameWithoutExtension,
        read(File(dir, "${file.name}.last-good")) != null)
    }
    return Catalogue(records.sortedByDescending { it.createdEpochMs }, failures)
  }

  fun list(): List<Protocol> = catalogue().records

  // No size cap: the app must always read back a record it has written.
  private fun read(file: File): Protocol? = runCatching {
    json.decodeFromString<Protocol>(file.readText())
  }.getOrNull()

  @Synchronized
  fun recover(id: String): Boolean {
    require(safeId(id))
    val recovered = read(File(dir, "$id.json.last-good")) ?: return false
    // No delete first: save quarantines an unreadable target itself and then replaces it in
    // one move, so a write that fails leaves the unreadable record listed to try again instead
    // of a record that no catalogue entry points at.
    save(recovered)
    return true
  }

  private fun safeId(id: String) = id !in setOf(".", "..") && id.matches(Regex("[A-Za-z0-9._-]{1,160}"))

  fun load(id: String): Protocol? {
    if (!safeId(id)) return null
    val f = File(dir, "$id.json")
    if (!f.exists()) return null
    return read(f)?.takeIf { it.id == id }
  }

  @Synchronized
  fun save(p: Protocol) {
    require(safeId(p.id))
    // Keep the previous record until the replacement is fully written. A shared fixed .tmp
    // let a lifecycle flush race the debounce and could leave no readable protocol behind.
    val target = File(dir, "${p.id}.json")
    val previous = read(target)
    if (target.exists() && previous == null) target.copyTo(File(dir, "${p.id}.${System.nanoTime()}.quarantine"))
    if (previous != null && (previous.saveSequence > p.saveSequence ||
        previous.contentRevision > p.contentRevision ||
        (previous.isFinalized() && !p.isFinalized()))) return
    val tmp = File.createTempFile("${p.id}-", ".tmp", dir)
    try {
      java.io.FileOutputStream(tmp).use { output ->
        output.write(json.encodeToString(Protocol.serializer(), p).toByteArray(Charsets.UTF_8))
        output.fd.sync()
      }
      if (previous != null) target.copyTo(File(dir, "${p.id}.json.last-good"), overwrite = true)
      try {
        Files.move(tmp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), target.toPath(), REPLACE_EXISTING)
      }
    } finally {
      if (tmp.exists()) tmp.delete()
    }
  }

  fun delete(id: String) {
    require(safeId(id))
    File(dir, "$id.json").delete()
    File(dir, "$id.json.last-good").delete()
    photoDir(id).deleteRecursively()
    signatureDir(id).deleteRecursively()
    File(root, "exports/$id").deleteRecursively()
    dir.listFiles()?.filter { it.name.startsWith("$id.") && it.extension == "quarantine" &&
      it.name.removePrefix("$id.").removeSuffix(".quarantine").matches(Regex("[0-9]+")) }?.forEach { it.delete() }
    File(root, "recovery").listFiles()?.filter { it.isDirectory && it.name.startsWith("$id-") &&
      it.name.removePrefix("$id-").matches(Regex("[0-9]+")) }?.forEach { it.deleteRecursively() }
  }

  /**
   * Saves the draft of an amendment with the photographs and documents it refers to, copied from
   * [parent]'s folder. What was removed from the parent since stays there for recovery and is not
   * carried forward. The draft's folder is not a record until its JSON exists, so no list shows
   * it and no [delete] would ever be asked for it: a failure removes it again here.
   */
  fun saveAmendment(parent: Protocol, draft: Protocol) {
    val photos = File(root, "photos")
    // Copied under a name no record has, then renamed into place: a process killed part way
    // leaves a folder that [sweepStale] knows to remove, where the draft's own folder would sit
    // in no list for good.
    val stage = File(photos, "$AMEND_STAGE${draft.id}")
    try {
      val from = photoDir(parent.id)
      stage.mkdirs()
      (parent.allPhotoFiles() + parent.details.attachments.map { it.file })
        .filter { it.isNotBlank() && it == File(it).name && it !in setOf(".", "..") }
        .distinct()
        .map { File(from, it) }
        .filter(File::isFile)
        .forEach { source -> source.copyTo(File(stage, source.name), overwrite = false) }
      Files.move(stage.toPath(), File(photos, draft.id).toPath())
      save(draft)
    } catch (failure: Exception) {
      stage.deleteRecursively()
      delete(draft.id)
      throw failure
    }
  }

  /** Writes referenced evidence and retained exports; a password enables authenticated encryption. */
  fun exportArchive(password: CharArray, output: OutputStream, protocolIds: Set<String>? = null) =
    ArchiveCrypto.export(root, password, output, protocolIds)

  /** Validates before restore. Healthy IDs are preserved; explicit repair retains corrupt originals. */
  fun importArchive(password: CharArray, input: InputStream, repairCorrupt: Boolean = false): ArchiveImportResult =
    ArchiveCrypto.import(root, password, input, repairCorrupt)

  private companion object {
    val swept: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    const val AMEND_STAGE = ".amend-"
  }
}
