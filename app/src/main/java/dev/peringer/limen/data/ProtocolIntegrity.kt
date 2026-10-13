package dev.peringer.limen.data

import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Builds the fingerprint displayed on a finalized record. It covers the canonical protocol
 * data and every original photo and ink file, without claiming to be a qualified signature.
 */
object ProtocolIntegrity {

  private val json = Json { encodeDefaults = true }

  fun fingerprint(protocol: Protocol, photoDir: File, signatureDir: File): String {
    val evidence = evidenceFiles(protocol, photoDir, signatureDir)
    val missing = evidence.filterNot { (_, file) -> file.isFile && file.canRead() && file.length() > 0L }
    require(missing.isEmpty()) {
      "Missing or unreadable evidence: ${missing.joinToString { (label, _) -> label }}"
    }
    val digest = MessageDigest.getInstance("SHA-256")
    val canonical = protocol.copy(
      evidenceFingerprint = "",
      deliveryReceipts = emptyList(),
      deliveryMetadataEpochMs = 0,
      exportedEpochMs = 0,
      deliveryRevision = 0,
      exportHistory = emptyList(),
      saveSequence = 0,
    )
    digest.update(json.encodeToString(Protocol.serializer(), canonical).toByteArray(Charsets.UTF_8))

    evidence
      .distinctBy { (label, _) -> label }
      .sortedBy { (label, _) -> label }
      .forEach { (label, file) ->
        digest.update(label.toByteArray(Charsets.UTF_8))
        digest.update(fileHash(file).toByteArray(Charsets.UTF_8))
      }
    return digest.digest().hex()
  }

  fun matches(protocol: Protocol, photoDir: File, signatureDir: File): Boolean =
    protocol.isFinalized() && runCatching {
      protocol.evidenceFingerprint == fingerprint(protocol, photoDir, signatureDir)
    }.getOrDefault(false)

  fun missingEvidence(protocol: Protocol, photoDir: File, signatureDir: File): List<String> =
    evidenceFiles(protocol, photoDir, signatureDir)
      .filterNot { (_, file) -> file.isFile && file.canRead() && file.length() > 0L }
      .map { (label, _) -> label }

  fun fileHash(file: File): String {
    require(file.isFile && file.canRead() && file.length() > 0L) {
      "Evidence file is missing, unreadable, or empty: ${file.name}"
    }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(8 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().hex()
  }

  /**
   * Locale.ROOT, not the handset's. "%02X" is a Formatter conversion and Formatter localizes
   * digits: on a handset set to a locale with Arabic-Indic numerals the same bytes rendered as
   * a different string, so the same record fingerprinted differently there than anywhere else.
   */
  private fun ByteArray.hex(): String =
    joinToString("") { String.format(java.util.Locale.ROOT, "%02X", it) }

  private fun evidenceFiles(
    protocol: Protocol,
    photoDir: File,
    signatureDir: File,
  ): List<Pair<String, File>> =
    protocol.allPhotoFiles().map { name -> "photo/$name" to resolvedFile(photoDir, name) }
      .plus(protocol.details.attachments.filter { it.file.isNotBlank() }.map {
        "document/${it.file}" to resolvedFile(photoDir, it.file)
      })
      .plus(
        protocol.signatures
          .filter { it.file.isNotBlank() }
          .map { signature -> "ink/${signature.file}" to resolvedFile(signatureDir, signature.file) }
      )

  /** Invalid names resolve to a guaranteed-missing child, never outside the evidence root. */
  private fun resolvedFile(root: File, name: String): File =
    if (name == File(name).name && name !in setOf(".", "..")) {
      File(root, name)
    } else {
      File(File(root, ".invalid-evidence"), name.hashCode().toString())
    }
}
