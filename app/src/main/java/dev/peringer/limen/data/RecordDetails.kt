package dev.peringer.limen.data

import kotlinx.serialization.Serializable

/** Optional additions are absent in legacy canonical records, preserving their signatures. */
@Serializable
data class RecordDetails(
  val municipality: String = "",
  val district: String = "",
  val habitableRooms: String = "",
  val actualTransferDate: String = "",
  val actualTransferTime: String = "",
  val notificationDays: String = "",
  val notificationClause: String = "",
  val scopeNotes: Map<String, String> = emptyMap(),
  val roomPhotos: Map<String, List<PhotoEvidence>> = emptyMap(),
  val readings: List<MeterReading> = emptyList(),
  val accessDevices: List<AccessDevice> = emptyList(),
  val attachments: List<SupportingDocument> = emptyList(),
  val followUps: List<FollowUp> = emptyList(),
  val estimates: Map<String, ReplacementEstimate> = emptyMap(),
  val reservations: Map<String, String> = emptyMap(),
  val legalSnapshot: LegalSnapshot? = null,
  val understoodLanguage: String = "",
  val participantNoticeShown: Boolean = false,
  val reviewRevision: Long = -1,
)

@Serializable
data class MeterReading(
  val id: String,
  val label: String = "",
  val identifier: String = "",
  val value: String = "",
  val unit: String = "",
  val date: String = "",
  val time: String = "",
  val note: String = "",
  val photos: List<PhotoEvidence> = emptyList(),
)

@Serializable
data class AccessDevice(
  val id: String,
  val label: String = "",
  val identifier: String = "",
  val expected: Int = 0,
  val transferred: Int = 0,
  val note: String = "",
)

@Serializable
enum class DocumentKind { Lease, RentForm, Mandate, PriorReport, Deposit, Delivery, Other }

@Serializable
data class SupportingDocument(
  val id: String,
  val kind: DocumentKind = DocumentKind.Other,
  val title: String = "",
  val file: String = "",
  val mime: String = "application/pdf",
  val sha256: String = "",
  val bytes: Long = 0,
  val importedEpochMs: Long = 0,
  val externalReference: String = "",
)

@Serializable
data class FollowUp(
  val id: String,
  val description: String = "",
  val discoveredDate: String = "",
  val notifiedDate: String = "",
  val recipient: String = "",
  val method: String = "",
  val deadline: String = "",
  val done: Boolean = false,
)

@Serializable
data class ReplacementEstimate(
  val replacementChf: String = "",
  val ageYears: String = "",
  val lifeYears: String = "",
  val basis: String = "",
  val date: String = "",
  val source: String = "https://www.mieterverband.ch/mietrecht/unterlagen-und-tools/lebensdauertabelle/",
)

@Serializable
data class LegalSnapshot(
  val ruleSetId: String,
  val profile: String,
  val initialRentFormRule: String,
  val requirements: List<LegalRequirement>,
  val sources: List<String>,
  val checkedOn: String = "2026-10-13",
)

@Serializable
data class ExportCopy(
  val uri: String,
  val name: String,
  val bytes: Long,
  val language: String,
  val deliveryRevision: Long,
  val epochMs: Long,
  val companionUri: String = "",
  val sha256: String = "",
)

fun Party.participantKey(): String = id.ifBlank { "legacy-${role.name}" }
fun Party.signerName(): String = representedBy.ifBlank { name }
fun Protocol.partyFor(signature: Signature): Party? = parties.firstOrNull {
  if (signature.participantId.isBlank()) it.role == signature.role else it.participantKey() == signature.participantId
}
fun Protocol.signatureFor(party: Party, plane: Plane = Plane.Record): Signature? = signatures.firstOrNull {
  it.plane == plane && if (it.participantId.isBlank()) it.role == party.role && party.id.isBlank()
    else it.participantId == party.participantKey()
}

fun Protocol.allPhotoEvidence(): List<PhotoEvidence> = defects.flatMap { it.photoEvidence } +
  objects.flatMap { it.photoEvidence } + details.roomPhotos.values.flatten() + details.readings.flatMap { it.photos }

fun Protocol.allPhotoFiles(): List<String> = (defects.flatMap { it.photoFiles() } +
  objects.flatMap { it.photoFiles() } + allPhotoEvidence().map { it.file }).distinct()

/** Starting an exit must never replace every co-tenant with the phone holder's name. */
fun Protocol.partiesForExit(role: Role, identity: SessionIdentity): List<Party> = parties.map { party ->
  val base = party.copy(representedBy = "", authority = "")
  when {
    role == Role.Tenant && party.role == role && party.name.trim().equals(identity.fullName, ignoreCase = true) ->
      base.copy(address = listOf(identity.addressLine, identity.placeLine).filter(String::isNotBlank).joinToString(", "),
        phone = identity.phone.ifBlank { party.phone })
    role == Role.Landlord && party.role == role && identity.company.isNotBlank() &&
      (parties.count { it.role == Role.Landlord } == 1 || party.agency == identity.company) ->
      base.copy(agency = identity.company, representedBy = identity.fullName, phone = identity.phone.ifBlank { party.phone })
    else -> base
  }
}
