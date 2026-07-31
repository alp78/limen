package dev.verso.limen.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The dossier's journal: an append-only chain of signed events, one file per dossier,
 * one JSON line per event. Every discrete act (a grade, a defect, a signature, a pairing,
 * an export) is recorded with its author key, its moment, a summary a person can read at
 * a hearing, the fingerprint of the protocol state it produced, and the hash of the chain
 * before it. Each party's next event countersigns everything before it, the other side's
 * entries included: history cannot be rewritten without breaking a signature the rewriter
 * does not own. Text typing is deliberately not journaled keystroke by keystroke; the
 * state fingerprint of the next discrete event captures it.
 */
@Serializable
data class LedgerEvent(
  val schema: Int = 1,
  val seq: Int,
  val dossierId: String,
  val kind: EventKind,
  /** What happened, in words: "defect added · Kitchen · Sink". */
  val summary: String = "",
  /** SHA-256 of the protocol content after this event, when the event touches it. */
  val stateHash: String = "",
  val actorKey: String = "",
  val actorName: String = "",
  val actorRole: Role = Role.Tenant,
  val epochMs: Long = 0,
  val prevHash: String = "",
  val signature: String = "",
) {
  /** The chain hash of this event: everything above, minus the signature itself. */
  fun hash(): String = Seal.sha256Hex(signedBytes())

  fun signedBytes(): ByteArray =
    "limen-event|$schema|$seq|$dossierId|${kind.name}|$summary|$stateHash|$actorKey|$actorName|${actorRole.name}|$epochMs|$prevHash"
      .toByteArray(Charsets.UTF_8)
}

@Serializable
enum class EventKind {
  DossierOpened, ProtocolEdit, DefectAdded, DefectRemoved, DefectCharged, PhotoAdded,
  PhotoRemoved, RoomAdded, RoomRemoved, GradeSet, SignatureKept, SignatureRefused,
  Exported, CounterSealed, PairSealed, AuditExported,
}

class Ledger(private val root: File) {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
  private val dir = File(root, "ledger").apply { mkdirs() }

  private fun file(dossierId: String) = File(dir, "$dossierId.jsonl")

  fun events(dossierId: String): List<LedgerEvent> =
    file(dossierId).takeIf { it.exists() }?.readLines()
      ?.mapNotNull { line -> runCatching { json.decodeFromString<LedgerEvent>(line) }.getOrNull() }
      ?: emptyList()

  /** Appends one signed event; the chain grows, nothing is ever rewritten. */
  fun append(
    dossierId: String,
    kind: EventKind,
    summary: String,
    stateHash: String,
    signer: SealSigner,
    actorName: String,
    actorRole: Role,
    epochMs: Long,
  ): LedgerEvent {
    val prior = events(dossierId)
    val prev = prior.lastOrNull()
    val unsigned = LedgerEvent(
      seq = (prev?.seq ?: 0) + 1,
      dossierId = dossierId,
      kind = kind,
      summary = summary,
      stateHash = stateHash,
      actorKey = signer.publicKeyBase64,
      actorName = actorName,
      actorRole = actorRole,
      epochMs = epochMs,
      prevHash = prev?.hash() ?: "",
    )
    val signed = unsigned.copy(
      signature = java.util.Base64.getEncoder().encodeToString(signer.sign(unsigned.signedBytes()))
    )
    file(dossierId).appendText(json.encodeToString(LedgerEvent.serializer(), signed) + "\n")
    return signed
  }

  /**
   * Walks the whole chain: every link's prevHash must name its predecessor and every
   * signature must verify against its author's own key. Returns the first broken seq,
   * or null when the chain holds.
   */
  fun verify(dossierId: String): Int? {
    var prevHash = ""
    events(dossierId).forEach { e ->
      if (e.prevHash != prevHash) return e.seq
      if (!Seal.verify(e.actorKey, e.signedBytes(), e.signature)) return e.seq
      prevHash = e.hash()
    }
    return null
  }

  fun delete(dossierId: String) {
    file(dossierId).delete()
  }
}
