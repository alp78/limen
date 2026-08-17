package dev.verso.limen.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

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
  /** The person who started this handover; immutable once the session exists. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val sessionRole: Role? = null,
  /** The starter's declared name, kept with this record rather than only on the device. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val sessionName: String = "",
  /** The starter's verified contact details, retained with this handover rather than on the phone. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val sessionIdentity: SessionIdentity = SessionIdentity(),
  /** Retained to export historic drafts; new Limen protocols use a simple condition check. */
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
  /** Legacy set-of-toggles inventory, superseded by [objects]; read only to export old records. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val extras: FlatExtras = FlatExtras(),
  /** Supplied appliances and furniture, one row each, so a row can carry its own photo. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val objects: List<ObjectEntry> = emptyList(),
  /** The tenancy's money and dates, present only when the handover has the contract to hand. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val contract: Contract = Contract(),
  /** The optional dossier components this handover carries, held sorted by ordinal. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val dossier: List<DossierCard> = emptyList(),
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

/** Historic grading scales retained so older saved protocols can still be read and exported. */
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

/** The person who opened this session and their contact address for this handover. */
@Serializable
data class SessionIdentity(
  val firstName: String = "",
  val lastName: String = "",
  /**
   * The managing agency's firm name, when the landlord side is a Verwaltung/regie rather
   * than a private person. The names above then belong to the representative present at
   * the handover. NEVER-encoded so finalized pre-agency records keep their fingerprint.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val company: String = "",
  val street: String = "",
  /** Building number, with an optional flat supplement supplied by the person. */
  val flatNumber: String = "",
  val postalCode: String = "",
  val city: String = "",
  /** ISO 3166-1 alpha-2 country of the contact address. */
  val addressCountry: String = "CH",
  /** Swiss contact addresses are selected from swisstopo; foreign addresses are entered directly. */
  val usesSwissAddress: Boolean = true,
  val phone: String = "",
  /** ISO 3166-1 alpha-2 region used to validate and format [phone]. */
  val phoneRegion: String = "CH",
) {
  val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
  val addressLine: String get() = listOf(street, flatNumber).filter { it.isNotBlank() }.joinToString(" ")
  val placeLine: String
    get() = listOf(
      postalCode,
      city,
      CountryDirectory.name(addressCountry).takeIf { !usesSwissAddress },
    ).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
}

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
  /** Legacy v2 value retained solely to verify finalized v2 records; no longer shown or exported. */
  val freshPaintedWalls: Boolean = false,
  val freshPaintedWoodwork: Boolean = false,
  val newFloor: Boolean = false,
  /** Legacy v2 value retained solely to verify finalized v2 records; no longer shown or exported. */
  val fixtures: Set<RoomFixture> = emptySet(),
)

@Serializable
enum class RoomFixture { Fridge, Freezer, Dishwasher, WashingMachine, Dryer, Furniture }

/**
 * One supplied appliance, piece of furniture or anything else handed over with the flat.
 *
 * A row, not a toggle, because two identical fridges are two facts and each needs its own
 * photo. [catalogue] names an [Appliance] or [Furniture] entry when the row came from the
 * shipped list, so it stays translatable; [label] carries the rest verbatim.
 */
@Serializable
data class ObjectEntry(
  val id: String,
  val group: ObjectGroup,
  /** Names an [ObjectGroup.Other] group: "Garden tools", "Cellar shelving". */
  val groupLabel: String = "",
  /** [Appliance] or [Furniture] entry name when the row came from the shipped list. */
  val catalogue: String = "",
  /** Free text name when no shipped entry stands behind the row. */
  val label: String = "",
  /** Which room it stands in. Blank means the object as a whole. */
  val roomId: String = "",
  val note: String = "",
  /** File names under the protocol's photo directory, in the order they were taken. */
  val photos: List<String> = emptyList(),
  val photoEvidence: List<PhotoEvidence> = emptyList(),
)

@Serializable
enum class ObjectGroup { Appliance, Furniture, Other }

/**
 * The tenancy itself: what is paid and from when to when. Amounts are held as typed for the
 * same reason [Deposit.amountChf] is, and dates are free text because a contract's dates are
 * copied off paper that does not always agree with ISO.
 */
@Serializable
data class Contract(
  val monthlyRentChf: String = "",
  val serviceChargesChf: String = "",
  val startDate: String = "",
  val endDate: String = "",
  /** When notice was given, the date every deposit argument eventually turns on. */
  val noticeDate: String = "",
)

/**
 * A dossier component the person adds only when this handover has it. Address, parties and
 * identity are not here: a protocol without them is not a protocol, so they are always shown.
 */
@Serializable
enum class DossierCard { Contract, Deposit, Meters, Keys, Notes }

/** The supplied extras are about the flat, not an artificial attachment to one room. */
@Serializable
data class FlatExtras(
  val appliances: Set<Appliance> = emptySet(),
  val furniture: Set<Furniture> = emptySet(),
  val customAppliances: List<String> = emptyList(),
  val customFurniture: List<String> = emptyList(),
)

@Serializable
enum class Appliance { Fridge, Freezer, Oven, Hob, Microwave, Dishwasher, WashingMachine, Dryer }

@Serializable
enum class Furniture { Bed, Wardrobe, Table, Chairs, Sofa, Desk, Shelving, Bookcase }

@Serializable
data class RoomItem(
  val key: ItemKey,
  /** Names a [ItemKey.Custom] row; empty otherwise. */
  val label: String = "",
  val grade: Grade = Grade.NotChecked,
  /** Stable row identity: permits repeated custom rows to carry separate defects. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val itemId: String = "",
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
  /** The room it belongs to, or blank when [objectId] carries the location instead. */
  val roomId: String = "",
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
  /** The exact [RoomItem] this finding belongs to; blank retains historic key-only records. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val itemId: String = "",
  /** Names an [ItemKey.Custom] finding, where no template row carries the wording. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val itemLabel: String = "",
  /** Set instead of [roomId] when the finding is about a supplied [ObjectEntry]. */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val objectId: String = "",
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

/** Nothing was read off any dial. */
fun Meters.isBlank(): Boolean =
  listOf(electricity1, electricity2, gas, water, hotWater, heatingOil).all { it.isBlank() }

/**
 * Which dossier components this record carries.
 *
 * Records written before the dossier existed answer from their content instead, and the
 * derived answer is never written back: [Protocol.schema] cannot identify them (the store
 * omits defaulted fields, so no file ever carried it), and writing a derived value into a
 * finalized record would change its canonical JSON and void its fingerprint.
 */
fun Protocol.dossierCards(): List<DossierCard> {
  if (dossier.isNotEmpty()) return dossier
  return buildList {
    if (contract != Contract()) add(DossierCard.Contract)
    if (deposit.kind != DepositKind.None) add(DossierCard.Deposit)
    if (!meters.isBlank()) add(DossierCard.Meters)
    if (selfMadeKeys || keys.any { it.count > 0 } || rooms.any { it.keysCount > 0 }) {
      add(DossierCard.Keys)
    }
    if (remarks.isNotBlank() || chargesStatementBy.isNotBlank() || oneSided) add(DossierCard.Notes)
  }
}

fun Protocol.carries(card: DossierCard): Boolean = card in dossierCards()

/**
 * True when this room was recorded with the exhaustive item grid, which the export still
 * prints so a signed record keeps showing the parties what they signed. New rooms hold no
 * items at all: a room's record is its defects.
 */
fun Room.hasLegacyGrid(): Boolean = items.isNotEmpty()

/** Every photo file this defect points at, from either list, once each. */
fun Defect.photoFiles(): List<String> =
  (photos + photoEvidence.map { it.file }).filter { it.isNotBlank() }.distinct()

fun ObjectEntry.photoFiles(): List<String> =
  (photos + photoEvidence.map { it.file }).filter { it.isNotBlank() }.distinct()
