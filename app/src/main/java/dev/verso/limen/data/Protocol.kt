package dev.verso.limen.data

import kotlinx.serialization.Serializable

/**
 * The protocol on disk, one JSON file per handover, written by this app and read back
 * years later when the exit protocol is seeded from the entry one. Every enum lands in
 * the JSON by name and every field has a default, so a protocol written by version 1 must
 * open in version 20: [Protocol.schema] is bumped on breaking changes and the store
 * migrates, never rejects.
 *
 * The model follows the paper canon (the MV mp form's 215 positions, the HEV form's cost
 * column, the romand TB/B/M/O grades) rather than inventing its own vocabulary; the field
 * names below are the English of those forms' terms.
 */
@Serializable
data class Protocol(
  val schema: Int = 2,
  val id: String,
  val kind: Kind,
  /** How items are graded: the Deutschschweiz binary tick or the romand four grades. */
  val style: GradeStyle = GradeStyle.Binary,
  /** Street and number. */
  val address: String = "",
  /** Postcode and place. */
  val place: String = "",
  /** Floor and position, the way regies write it: "3. OG links". */
  val objectDetail: String = "",
  /** ISO date of the handover itself, and the clock time beside it. */
  val handoverDate: String = "",
  val handoverTime: String = "",
  /** When the previous protocol was made, from the form header; free text. */
  val lastProtocolDate: String = "",
  val createdEpochMs: Long,
  val parties: List<Party> = emptyList(),
  val deposit: Deposit = Deposit(),
  val meters: Meters = Meters(),
  /** Keys that belong to no single room: house door, mailbox, cellar, garage. */
  val keys: List<KeyCount> = emptyList(),
  /** Whether the tenant declared having had copies made, the question everyone forgets. */
  val selfMadeKeys: Boolean = false,
  val rooms: List<Room> = emptyList(),
  val defects: List<Defect> = emptyList(),
  /** Free remarks, including the reservation lines a party dictates. */
  val remarks: String = "",
  /** "The service-charge statement will be drawn up by DATE", from the form footer. */
  val chargesStatementBy: String = "",
  /** Nobody on the other side of the threshold: the one-sided handover documented alone. */
  val oneSided: Boolean = false,
  val signatures: List<Signature> = emptyList(),
  /** The id of the entry protocol this exit protocol was seeded from, if any. */
  val seedId: String = "",
  /** The frozen document this draft amends, if it was changed after finalization. */
  val amendedFromId: String = "",
  /** Bumped for every material edit; ink is valid only for the revision it saw. */
  val contentRevision: Long = 0,
  /** A finalized protocol is never edited in place. An edit becomes a new amendment. */
  val finalizedEpochMs: Long = 0,
  /** SHA-256 over the finalized record and its original photo and ink files. */
  val evidenceFingerprint: String = "",
  /** A self-declared record that an identical copy was handed to a party. */
  val deliveryReceipts: List<DeliveryReceipt> = emptyList(),
  val exportedEpochMs: Long = 0,
) {
  /** The record plane has current ink from at least one person. */
  fun isSigned(): Boolean = signatures.any {
    it.plane == Plane.Record && !it.refused && it.contentRevision == contentRevision
  }
  fun isFinalized(): Boolean = finalizedEpochMs > 0 && evidenceFingerprint.isNotBlank()
  fun isExported(): Boolean = exportedEpochMs > 0
  fun isCurrent(signature: Signature): Boolean = signature.contentRevision == contentRevision
}

@Serializable
enum class Kind { Entry, Exit }

/** The two grading canons a Swiss protocol is written in. */
@Serializable
enum class GradeStyle {
  /** Deutschschweiz: each item "in Ordnung" or carrying a defect. */
  Binary,
  /** Romandie: TB / B / M / O on every item, defects on top. */
  Grades,
}

/**
 * An item's recorded state. [NotChecked] is the honest default: an unvisited row prints
 * blank, never as silently fine. Binary style uses [Ok] only; the romand style maps
 * TB=VeryGood, B=Good, M=Fair, O=Poor.
 */
@Serializable
enum class Grade { NotChecked, Ok, VeryGood, Good, Fair, Poor }

@Serializable
enum class Role { Tenant, Landlord, NextTenant, Witness, Expert }

@Serializable
data class Party(
  val role: Role,
  val name: String = "",
  /** The tenant's NEW address at an exit protocol, where the deposit letter will go. */
  val address: String = "",
  /** "vertreten durch": the regie's person, the tenant's proxy. */
  val representedBy: String = "",
  val phone: String = "",
  val email: String = "",
)

/**
 * The deposit, asked once at entry and carried to exit, because the exit protocol ends
 * with the release instruction that matches [kind]. Insurance is SwissCaution and its
 * competitors; the premium world and the blocked-account world release differently.
 */
@Serializable
data class Deposit(
  val kind: DepositKind = DepositKind.None,
  /** Held as typed: an amount is evidence, not arithmetic. */
  val amountChf: String = "",
  /** Insurance: the provider's name. */
  val provider: String = "",
  /** Insurance: certificate or policy number. */
  val policyNo: String = "",
  /** Bank account: the institute. */
  val bank: String = "",
  /** Bank account: IBAN or account number as printed on the deposit confirmation. */
  val account: String = "",
)

@Serializable
enum class DepositKind { None, BankAccount, Insurance }

/**
 * The recurring Swiss rental-guarantee insurers shown in the setup picker. Only the
 * certificate's printed provider name is stored, so older and uncommon insurers remain
 * intact through [Other] rather than being forced into a made-up category.
 */
enum class CautionProvider(val storedName: String?) {
  SwissCaution("SwissCaution"),
  GoCaution("goCaution"),
  Firstcaution("Firstcaution"),
  SmartCaution("SmartCaution"),
  Axa("AXA"),
  Helvetia("Helvetia"),
  Zurich("Zurich"),
  Other(null);

  companion object {
    fun fromStoredName(name: String): CautionProvider =
      entries.firstOrNull { it.storedName == name.trim() } ?: Other
  }
}

/** Meter readings from the form header. Strings, exactly as read off the dial. */
@Serializable
data class Meters(
  val electricity1: String = "",
  val electricity2: String = "",
  val gas: String = "",
  val water: String = "",
  val hotWater: String = "",
  val heatingOil: String = "",
)

/** One kind of key and how many changed hands. */
@Serializable
data class KeyCount(val kind: KeyKind, val label: String = "", val count: Int = 0)

@Serializable
enum class KeyKind { Apartment, HouseDoor, Mailbox, Cellar, Attic, Garage, Other }

@Serializable
data class Room(
  val id: String,
  val kind: RoomKind,
  /** Overrides the template name: "Chambre 2", "Bastelraum". */
  val name: String = "",
  val items: List<RoomItem> = emptyList(),
  /** Keys of this room's own door, the way every MV room block ends. */
  val keysCount: Int = 0,
  /** The form header's per-room flags: what was new when the tenant arrived. */
  val freshPaintedWalls: Boolean = false,
  val freshPaintedWoodwork: Boolean = false,
  val newFloor: Boolean = false,
)

@Serializable
data class RoomItem(
  val key: ItemKey,
  /** Names a [ItemKey.Custom] row; empty otherwise. */
  val label: String = "",
  val grade: Grade = Grade.NotChecked,
)

/**
 * One defect, itemized the way Art. 267a OR demands: location (room, item), nature
 * (text, in the "3 cm2 water stain under the north window" register), and the explicit
 * charge. Ticino case law voids an exit notice whose defects are not specifically imputed,
 * so [chargedToTenant] is first-class and prints as its own column.
 */
@Serializable
data class Defect(
  val id: String,
  val roomId: String,
  /** The item row it hangs on, or null for a whole-room finding. */
  val item: ItemKey? = null,
  val text: String = "",
  /** File names under the protocol's photo directory, in the order they were taken. */
  val photos: List<String> = emptyList(),
  /** Hashes and capture moments for photos added in version 2 or later. */
  val photoEvidence: List<PhotoEvidence> = emptyList(),
  val chargedToTenant: Boolean = false,
  val fixBy: FixBy = FixBy.Open,
  /** "by DATE" of the remediation table; free text. */
  val fixDeadline: String = "",
  /** Cost note or estimate reservation, the HEV form's KA column. */
  val costNote: String = "",
  /** The party's dictated reservation on this one finding: "conteste", "already at entry". */
  val reservation: String = "",
  /** The tenant can state a position without the app treating it as an admission. */
  val tenantResponse: TenantResponse = TenantResponse.NotStated,
  val tenantComment: String = "",
)

@Serializable
enum class FixBy { Open, Tenant, Landlord }

@Serializable
data class PhotoEvidence(
  val file: String,
  val sha256: String = "",
  val capturedEpochMs: Long = 0,
  val source: PhotoSource = PhotoSource.Unknown,
)

@Serializable
enum class PhotoSource { Camera, Gallery, Unknown }

@Serializable
enum class TenantResponse { NotStated, Agrees, Disputes, NormalWear, ExistingAtEntry }

@Serializable
data class DeliveryReceipt(
  val role: Role,
  val epochMs: Long = 0,
)

/**
 * One ink signature. The two planes are the design the paper canon insists on and the
 * apps all flatten: [Plane.Record] confirms only that the defects are correctly recorded
 * (in Vaud, by law, this is not a debt acknowledgment); [Plane.Costs] is the separate,
 * refusable acceptance of the charges. The app never collects the second inside the first.
 */
@Serializable
data class Signature(
  val role: Role,
  val name: String = "",
  val plane: Plane,
  /** PNG file name under the protocol's signature directory; empty when [refused]. */
  val file: String = "",
  /** Refusal is a recorded outcome, not a failure, and prints as such. */
  val refused: Boolean = false,
  /** The signer's reservation, printed beside the signature: "sous reserve d'un devis". */
  val reservation: String = "",
  val epochMs: Long = 0,
  /** The protocol revision shown to the signer. */
  val contentRevision: Long = 0,
)

@Serializable
enum class Plane { Record, Costs }
