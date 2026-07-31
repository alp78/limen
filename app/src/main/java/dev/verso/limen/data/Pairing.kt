package dev.verso.limen.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/**
 * The handshake. At the handover, with both people in the same room, each phone exports
 * its key card as a small file and imports the other's. The room is the trust anchor:
 * there is no man in the middle standing between two people at one door. The pair is
 * sealed only when the holder confirms that the joint fingerprint read aloud from the
 * other screen matches this one; from that moment the linked features unlock, and every
 * exchanged document can be tied to exactly these two keys.
 */
@Serializable
data class KeyCard(
  val schema: Int = 1,
  val kind: String = "limen-pair",
  val name: String = "",
  val role: Role = Role.Tenant,
  val publicKey: String = "",
  val epochMs: Long = 0,
  /** Self-signature over the card: proves the sender holds the private half. */
  val signature: String = "",
) {
  fun signedBytes(): ByteArray =
    "limen-pair|$schema|$name|${role.name}|$publicKey|$epochMs".toByteArray(Charsets.UTF_8)
}

/** A sealed pair: the counterparty this phone is linked with. */
@Serializable
data class PairRecord(
  val name: String = "",
  val role: Role = Role.Tenant,
  val publicKey: String = "",
  val sealedEpochMs: Long = 0,
)

object Pairing {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

  fun makeCard(signer: SealSigner, name: String, role: Role, epochMs: Long): KeyCard {
    val unsigned = KeyCard(name = name, role = role, publicKey = signer.publicKeyBase64, epochMs = epochMs)
    return unsigned.copy(
      signature = Base64.getEncoder().encodeToString(signer.sign(unsigned.signedBytes()))
    )
  }

  fun encodeCard(card: KeyCard): String = json.encodeToString(KeyCard.serializer(), card)

  /** Decodes and verifies a received card; null when it is not a self-consistent card. */
  fun decodeCard(text: String): KeyCard? {
    val card = runCatching { json.decodeFromString(KeyCard.serializer(), text) }.getOrNull() ?: return null
    if (card.kind != "limen-pair" || card.publicKey.isBlank()) return null
    if (!Seal.verify(card.publicKey, card.signedBytes(), card.signature)) return null
    return card
  }

  /**
   * The joint fingerprint both screens must show identically: a hash over BOTH public
   * keys in a fixed order, so neither side's display depends on who imported first.
   */
  fun jointFingerprint(keyA: String, keyB: String): String {
    val (first, second) = listOf(keyA, keyB).sorted()
    return Seal.fingerprint(Seal.sha256Hex("$first|$second".toByteArray(Charsets.UTF_8)))
  }

  // ── The pair on disk ─────────────────────────────────────────────────────────────────

  private fun file(root: File) = File(root, "pair.json")

  fun load(root: File): PairRecord? =
    file(root).takeIf { it.exists() }?.let { f ->
      runCatching { json.decodeFromString(PairRecord.serializer(), f.readText()) }.getOrNull()
    }

  fun save(root: File, pair: PairRecord) {
    file(root).writeText(json.encodeToString(PairRecord.serializer(), pair))
  }

  fun clear(root: File) {
    file(root).delete()
  }
}
