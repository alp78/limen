package dev.verso.limen.data

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
    val digest = MessageDigest.getInstance("SHA-256")
    val canonical = protocol.copy(
      evidenceFingerprint = "",
      deliveryReceipts = emptyList(),
      exportedEpochMs = 0,
    )
    digest.update(json.encodeToString(Protocol.serializer(), canonical).toByteArray(Charsets.UTF_8))

    protocol.defects
      .flatMap { defect -> defect.photoFiles().map { name -> "photo/$name" to File(photoDir, name) } }
      // Object photos hash under the same label scheme, so a record with no objects, which is
      // every record written before they existed, produces the identical fingerprint it always did.
      .plus(
        protocol.objects
          .flatMap { entry -> entry.photoFiles().map { name -> "photo/$name" to File(photoDir, name) } }
      )
      .plus(
        protocol.signatures
          .filter { it.file.isNotBlank() }
          .map { signature -> "ink/${signature.file}" to File(signatureDir, signature.file) }
      )
      .distinctBy { (label, _) -> label }
      .sortedBy { (label, _) -> label }
      .forEach { (label, file) ->
        digest.update(label.toByteArray(Charsets.UTF_8))
        digest.update(fileHash(file).toByteArray(Charsets.UTF_8))
      }
    return digest.digest().joinToString("") { "%02X".format(it) }
  }

  fun matches(protocol: Protocol, photoDir: File, signatureDir: File): Boolean =
    protocol.isFinalized() && protocol.evidenceFingerprint == fingerprint(protocol, photoDir, signatureDir)

  fun fileHash(file: File): String {
    if (!file.exists() || !file.isFile) return "MISSING"
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(8 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
  }
}
