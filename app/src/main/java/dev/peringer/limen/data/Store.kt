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

  private val json = Json {
    // A version-1 reader opening a version-2 file skips what it does not know rather
    // than refusing the user their own protocol.
    ignoreUnknownKeys = true
    encodeDefaults = false
  }

  private val dir = File(root, "protocols").apply { mkdirs() }

  fun photoDir(id: String): File = File(File(root, "photos"), id).apply { mkdirs() }
  fun signatureDir(id: String): File = File(File(root, "signatures"), id).apply { mkdirs() }

  fun list(): List<Protocol> =
    (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
      .mapNotNull { f -> runCatching { json.decodeFromString<Protocol>(f.readText()) }.getOrNull() }
      .sortedByDescending { it.createdEpochMs }

  fun load(id: String): Protocol? {
    val f = File(dir, "$id.json")
    if (!f.exists()) return null
    return runCatching { json.decodeFromString<Protocol>(f.readText()) }.getOrNull()
  }

  @Synchronized
  fun save(p: Protocol) {
    // Keep the previous record until the replacement is fully written. A shared fixed .tmp
    // let a lifecycle flush race the debounce and could leave no readable protocol behind.
    val target = File(dir, "${p.id}.json")
    val tmp = File.createTempFile("${p.id}-", ".tmp", dir)
    try {
      tmp.writeText(json.encodeToString(Protocol.serializer(), p))
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
    File(dir, "$id.json").delete()
    photoDir(id).deleteRecursively()
    signatureDir(id).deleteRecursively()
  }

  /** Writes every protocol and its original evidence into an authenticated encrypted archive. */
  fun exportArchive(password: CharArray, output: OutputStream) =
    ArchiveCrypto.export(root, password, output)

  /** Restores encrypted or legacy archives, validates evidence, and never replaces an id. */
  fun importArchive(password: CharArray, input: InputStream): ArchiveImportResult =
    ArchiveCrypto.import(root, password, input)
}
