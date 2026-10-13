package dev.peringer.limen.photo

import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.allPhotoFiles
import dev.peringer.limen.data.allPhotoEvidence
import dev.peringer.limen.data.ProtocolIntegrity
import java.io.File

class UnrenderableEvidence(val files: List<String>) : IllegalArgumentException("Evidence cannot be rendered: ${files.joinToString()}")

/** Android decoding belongs at intake/rendering boundaries, independent of canonical hashing. */
object EvidenceValidation {
  fun requireReadable(p: Protocol, photos: File, ink: File) {
    val failures = unreadable(p, photos, ink).distinct()
    if (failures.isNotEmpty()) throw UnrenderableEvidence(failures)
  }
  fun unreadable(p: Protocol, photos: File, ink: File): List<String> = buildList {
    p.allPhotoFiles().forEach { if (!safe(it) || !PhotoDecode.readable(File(photos, it))) add(it) }
    p.signatures.filter { it.file.isNotBlank() }.forEach {
      if (!safe(it.file) || !PhotoDecode.readable(File(ink, it.file))) add(it.file)
    }
    p.allPhotoEvidence().filter { it.sha256.isNotBlank() }.forEach {
      if (runCatching { ProtocolIntegrity.fileHash(File(photos, it.file)).equals(it.sha256, ignoreCase = true) }.getOrDefault(false).not()) add(it.file)
    }
    p.details.attachments.filter { it.file.isNotBlank() }.forEach {
      if (!safe(it.file) || runCatching { ProtocolIntegrity.fileHash(File(photos, it.file)).equals(it.sha256, ignoreCase = true) }.getOrDefault(false).not()) add(it.file)
    }
  }
  private fun safe(name: String) = name == File(name).name && name !in setOf(".", "..")
}
