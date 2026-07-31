package dev.verso.limen.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature as JSignature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The tamper-evidence layer. A finger-signed protocol is admissible evidence in
 * Switzerland; what a court probes is whether the file changed after the signatures.
 * The seal answers that: a content fingerprint over the protocol data, its photos and
 * its ink, printed on the PDF, and an ECDSA signature by this device's key over the
 * digest of the exported PDF bytes. The counterseal is the same signature made by the
 * OTHER party's phone over the same PDF digest: two devices, one document, neither
 * side able to alter it alone afterwards.
 *
 * This is deliberately not a qualified electronic signature and never claims to be:
 * only a QES with a qualified timestamp equals a handwritten signature under Swiss law.
 */
@Serializable
data class SealInfo(
  val contentHash: String = "",
  val pdfHash: String = "",
  /** Base64 of the X.509 encoding of the device's public EC key. */
  val publicKey: String = "",
  val signature: String = "",
  val epochMs: Long = 0,
)

/**
 * The other phone's signature over the exported PDF, carried as a small JSON file from
 * device to device by any offline share, then attached and verified here. Also the
 * wire format of the .limensig file itself.
 */
@Serializable
data class CounterSeal(
  val schema: Int = 1,
  val forContentHash: String = "",
  val forPdfHash: String = "",
  val name: String = "",
  val role: Role = Role.Tenant,
  val publicKey: String = "",
  val signature: String = "",
  val epochMs: Long = 0,
)

/** Whatever can sign for a device: the Android keystore in the app, a plain key in tests. */
interface SealSigner {
  val publicKeyBase64: String
  fun sign(message: ByteArray): ByteArray
}

object Seal {

  const val ALG = "SHA256withECDSA"

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

  fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  /**
   * The content fingerprint: SHA-256 over the canonical JSON of the protocol with the
   * seal fields blanked, then every photo and every ink file in name order. Computed
   * before the PDF exists, printed inside it, so the paper itself names its own data.
   */
  fun contentHash(p: Protocol, photoDir: File?, signatureDir: File?): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bare = p.copy(seal = null, counterSeal = null, exportedEpochMs = 0)
    md.update(json.encodeToString(Protocol.serializer(), bare).toByteArray(Charsets.UTF_8))
    listOf(photoDir, signatureDir).forEach { dir ->
      (dir?.listFiles()?.sortedBy { it.name } ?: emptyList()).forEach { f ->
        md.update(f.name.toByteArray(Charsets.UTF_8))
        md.update(f.readBytes())
      }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  /** What the device seal signs. Versioned, so a future format never verifies against this one. */
  fun sealMessage(contentHash: String, pdfHash: String, epochMs: Long): ByteArray =
    "limen-seal|1|$contentHash|$pdfHash|$epochMs".toByteArray(Charsets.UTF_8)

  /** What a counterseal signs: the digests plus who is signing, bound together. */
  fun counterMessage(c: CounterSeal): ByteArray =
    "limen-counterseal|1|${c.forContentHash}|${c.forPdfHash}|${c.name}|${c.role.name}|${c.epochMs}"
      .toByteArray(Charsets.UTF_8)

  fun makeSeal(signer: SealSigner, contentHash: String, pdfHash: String, epochMs: Long): SealInfo =
    SealInfo(
      contentHash = contentHash,
      pdfHash = pdfHash,
      publicKey = signer.publicKeyBase64,
      signature = Base64.getEncoder().encodeToString(
        signer.sign(sealMessage(contentHash, pdfHash, epochMs))
      ),
      epochMs = epochMs,
    )

  fun makeCounterSeal(
    signer: SealSigner,
    forContentHash: String,
    forPdfHash: String,
    name: String,
    role: Role,
    epochMs: Long,
  ): CounterSeal {
    val unsigned = CounterSeal(
      forContentHash = forContentHash,
      forPdfHash = forPdfHash,
      name = name,
      role = role,
      publicKey = signer.publicKeyBase64,
      epochMs = epochMs,
    )
    return unsigned.copy(
      signature = Base64.getEncoder().encodeToString(signer.sign(counterMessage(unsigned)))
    )
  }

  fun verifySeal(seal: SealInfo): Boolean =
    verify(seal.publicKey, sealMessage(seal.contentHash, seal.pdfHash, seal.epochMs), seal.signature)

  fun verifyCounterSeal(c: CounterSeal): Boolean =
    verify(c.publicKey, counterMessage(c), c.signature)

  fun verify(publicKeyBase64: String, message: ByteArray, signatureBase64: String): Boolean =
    runCatching {
      val key = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
      JSignature.getInstance(ALG).run {
        initVerify(key)
        update(message)
        verify(Base64.getDecoder().decode(signatureBase64))
      }
    }.getOrDefault(false)

  fun encodeCounterSeal(c: CounterSeal): String = json.encodeToString(CounterSeal.serializer(), c)

  fun decodeCounterSeal(text: String): CounterSeal? =
    runCatching { json.decodeFromString(CounterSeal.serializer(), text) }.getOrNull()

  /** A hash shown to people: the first sixteen hex, grouped in fours, enough to compare aloud. */
  fun fingerprint(hex: String): String =
    hex.take(16).chunked(4).joinToString(" ").uppercase()

  /** The full hash for paper, grouped in eights so a line of it stays readable. */
  fun grouped(hex: String): String = hex.chunked(8).joinToString(" ")

  fun keyFingerprint(publicKeyBase64: String): String =
    runCatching { fingerprint(sha256Hex(Base64.getDecoder().decode(publicKeyBase64))) }
      .getOrDefault("")
}
