package dev.verso.limen.data

import kotlinx.serialization.json.Json
import java.io.File

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

  fun save(p: Protocol) {
    // Write-then-rename, so a process death mid-write never eats the only copy.
    val target = File(dir, "${p.id}.json")
    val tmp = File(dir, "${p.id}.json.tmp")
    tmp.writeText(json.encodeToString(Protocol.serializer(), p))
    if (target.exists()) target.delete()
    tmp.renameTo(target)
  }

  fun delete(id: String) {
    File(dir, "$id.json").delete()
    photoDir(id).deleteRecursively()
    signatureDir(id).deleteRecursively()
  }
}
