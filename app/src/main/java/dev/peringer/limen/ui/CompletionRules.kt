package dev.peringer.limen.ui

import dev.peringer.limen.data.Contract
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.Meters
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.ProtocolIntegrity
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.dossierCards
import dev.peringer.limen.data.resolvedLegalOverlay
import java.io.File
import dev.peringer.limen.data.*

internal fun requiredRecordRoles(record: Protocol, fallbackRole: Role?): List<Role> = buildList {
  val side = record.sessionRole ?: fallbackRole
  // A one-sided record with no side known falls back to both, because the Sign step draws a
  // row per required role: an empty list left the gate demanding a signature it never showed.
  if (record.oneSided && side != null) {
    add(side)
  } else {
    add(Role.Tenant)
    add(Role.Landlord)
  }
  if (record.parties.any { it.role == Role.Witness }) add(Role.Witness)
  if (record.parties.any { it.role == Role.Expert }) add(Role.Expert)
}.distinct()

/**
 * The key outcome a record needs before it can be frozen. Shared with the dossier's own
 * section chip, which used to report the card complete on the pill alone and so sat ticked
 * beside a blocked FINALIZE with nothing on screen naming the missing count.
 */
internal fun keyOutcomeRecorded(record: Protocol): Boolean {
  if (record.keys.any { it.count < 0 } || record.rooms.any { it.keysCount < 0 }) return false
  if (record.details.accessDevices.any { it.expected < 0 || it.transferred < 0 || it.label.isBlank() ||
      (it.expected != it.transferred && it.note.isBlank()) }) return false
  val physical = record.keys.sumOf { it.count } + record.rooms.sumOf { it.keysCount } + record.details.accessDevices.sumOf { it.transferred }
  return when (record.keyTransfer) {
    KeyTransferStatus.NotRecorded -> false
    KeyTransferStatus.AllTransferred -> physical > 0 && record.details.accessDevices.all { it.expected == it.transferred }
    KeyTransferStatus.NoPhysicalKeys -> physical == 0 && !record.selfMadeKeys && record.details.accessDevices.all { it.expected == 0 }
    KeyTransferStatus.Incomplete -> record.keyTransferNote.isNotBlank()
  }
}

internal fun depositRecorded(p: Protocol): Boolean = p.details.scopeNotes["Deposit"].orEmpty().isNotBlank() || when (p.deposit.kind) {
  DepositKind.None -> true
  DepositKind.Unknown -> false
  DepositKind.BankAccount -> p.deposit.amountChf.isNotBlank() && p.deposit.bank.isNotBlank() && p.deposit.account.isNotBlank()
  DepositKind.Insurance -> p.deposit.amountChf.isNotBlank() && p.deposit.provider.isNotBlank() && p.deposit.policyNo.isNotBlank()
}

/** The first dossier section to correct, shared by actionable completion messages. */
internal fun incompleteDossierTarget(p: Protocol): String {
  fun invalidDate(value: String) = value.isNotBlank() && SwissDate.parse(value) == null
  if (invalidDate(p.details.actualTransferDate) || p.details.notificationDays.isNotBlank() &&
      ((p.details.notificationDays.toLongOrNull() ?: 0) !in 1L..365L || p.details.notificationClause.isBlank())) return "transfer"
  if (listOf(p.contract.conclusionDate, p.contract.startDate, p.contract.endDate, p.contract.noticeDate).any(::invalidDate) ||
      listOf(p.contract.monthlyRentChf, p.contract.serviceChargesChf).any { it.isNotBlank() && nonNegativeNumber(it) == null } ||
      DossierCard.Contract in p.dossierCards() && p.contract == Contract()) return "card-Contract"
  if (!depositRecorded(p)) return "card-Deposit"
  if (p.details.attachments.any { it.title.isBlank() || it.file.isBlank() && it.externalReference.isBlank() }) return "documents"
  if (p.details.followUps.any { it.description.isBlank() || listOf(it.discoveredDate, it.notifiedDate, it.deadline).any(::invalidDate) }) return "follow-up"
  if (DossierCard.Meters in p.dossierCards() && (p.meters == Meters() && p.details.readings.isEmpty() && p.details.scopeNotes["Meters"].isNullOrBlank() ||
      p.details.readings.any { it.label.isBlank() || invalidDate(it.date) || (it.value.isBlank() || it.unit.isBlank() || it.identifier.isBlank()) && it.note.isBlank() })) return "card-Meters"
  return "card-Notes"
}

internal fun protocolCompletionIssues(
  record: Protocol,
  requiredRoles: List<Role>,
  photoDir: File,
  signatureDir: File,
): List<FinalizationIssue> = buildList {
  if (record.address.isBlank()) add(FinalizationIssue.Address)
  if (record.place.isBlank()) add(FinalizationIssue.Place)
  if (record.objectCanton == null) add(FinalizationIssue.Canton)
  if (dev.peringer.limen.data.SwissDate.parse(record.handoverDate) == null) add(FinalizationIssue.HandoverDate)
  if (runCatching { java.time.LocalTime.parse(record.handoverTime) }.isFailure) add(FinalizationIssue.HandoverTime)
  val optionalDates = listOf(record.contract.conclusionDate, record.contract.startDate,
    record.contract.endDate, record.contract.noticeDate, record.chargesStatementBy, record.details.actualTransferDate) + record.defects.map { it.fixDeadline } +
    record.details.readings.map { it.date } + record.details.followUps.flatMap { listOf(it.discoveredDate, it.notifiedDate, it.deadline) }
  if (optionalDates.any { it.isNotBlank() && dev.peringer.limen.data.SwissDate.parse(it) == null }) {
    add(FinalizationIssue.Dossier)
  }
  record.resolvedLegalOverlay()?.let { overlay ->
    if (!overlay.isComplete(record.legalOverlay)) add(FinalizationIssue.LegalOverlay)
  }
  if (listOf(Role.Tenant, Role.Landlord).any { role ->
      record.parties.firstOrNull { it.role == role }?.name.isNullOrBlank()
    }) add(FinalizationIssue.Parties)
  // Two different states with two different answers: an empty inspection needs a room added,
  // and a populated one needs its rooms confirmed. One message covered both and told a record
  // with no rooms at all to "confirm every listed room was inspected".
  if (record.rooms.isEmpty() && record.details.scopeNotes["Inspection"].isNullOrBlank()) add(FinalizationIssue.NoRooms)
  else if (record.rooms.any { !it.inspected && record.details.scopeNotes[it.id].isNullOrBlank() }) add(FinalizationIssue.Rooms)
  if (record.objects.any { !it.inspected && record.details.scopeNotes[it.id].isNullOrBlank() }) add(FinalizationIssue.Objects)

  val cards = record.dossierCards()
  val depositIncomplete = !depositRecorded(record)
  val emptyAddedComponent =
    (DossierCard.Contract in cards && record.contract == Contract() && record.details.scopeNotes["Contract"].isNullOrBlank()) ||
      (DossierCard.Deposit in cards && depositIncomplete) ||
      (DossierCard.Meters in cards && record.meters == Meters() && record.details.readings.isEmpty() && record.details.scopeNotes["Meters"].isNullOrBlank()) ||
      (DossierCard.Notes in cards && record.remarks.isBlank() && record.chargesStatementBy.isBlank())
  if (emptyAddedComponent) add(FinalizationIssue.Dossier)

  // Gated on the card like every other component above it: removing the Keys card takes the
  // only control that can record an outcome off the screen, and an ungated rule then blocked
  // finalization forever with nothing left anywhere to answer it.
  if (DossierCard.Keys in cards && !keyOutcomeRecorded(record) ||
      DossierCard.Keys !in cards && record.details.scopeNotes["Keys"].isNullOrBlank()) add(FinalizationIssue.Keys)
  if (record.details.readings.any { it.label.isBlank() || ((it.value.isBlank() || it.unit.isBlank() || it.identifier.isBlank()) && it.note.isBlank()) } ||
      record.details.attachments.any { it.title.isBlank() || (it.file.isBlank() && it.externalReference.isBlank()) } ||
      record.details.followUps.any { it.description.isBlank() }) add(FinalizationIssue.Dossier)
  val amounts = listOf(record.contract.monthlyRentChf, record.contract.serviceChargesChf, record.deposit.amountChf)
  if (amounts.any { it.isNotBlank() && nonNegativeNumber(it) == null }) add(FinalizationIssue.Dossier)
  if (record.details.estimates.values.any { nonNegativeNumber(it.replacementChf) == null ||
      nonNegativeNumber(it.ageYears) == null || (nonNegativeNumber(it.lifeYears) ?: 0.0) <= 0.0 }) add(FinalizationIssue.Dossier)
  if (record.details.notificationDays.isNotBlank() &&
      (record.details.notificationDays.toLongOrNull() !in 1L..365L || record.details.notificationClause.isBlank())) add(FinalizationIssue.Dossier)
  if ((record.details.actualTransferTime.isNotBlank() && runCatching { java.time.LocalTime.parse(record.details.actualTransferTime) }.isFailure) ||
    record.details.readings.any { it.time.isNotBlank() && runCatching { java.time.LocalTime.parse(it.time) }.isFailure }) add(FinalizationIssue.HandoverTime)
  if (record.oneSided && (record.unilateralReason == null ||
      (record.unilateralReason == dev.peringer.limen.data.UnilateralReason.Other && record.unilateralNote.isBlank()))) {
    add(FinalizationIssue.UnilateralReason)
  }
  if (record.defects.any { it.text.isBlank() }) add(FinalizationIssue.DefectText)
  if (ProtocolIntegrity.missingEvidence(record, photoDir, signatureDir).isNotEmpty()) {
    add(FinalizationIssue.Evidence)
  }
  if (record.parties.any { it.name.isBlank() || (it.representedBy.isNotBlank() && it.authority.isBlank()) }) add(FinalizationIssue.Parties)
  if (record.parties.map { it.participantKey() }.distinct().size != record.parties.size) add(FinalizationIssue.Parties)
  if (requiredRoles.any { role -> record.parties.none { it.role == role } } ||
    record.parties.filter { it.role in requiredRoles }.any { party ->
      val signature = record.signatureFor(party)
      signature == null || !record.isCurrent(signature) ||
        !(signature.refused || signature.file.isNotBlank() || signature.outcome in setOf("absent", "unable")) ||
        (signature.outcome.isNotBlank() && signature.reservation.isBlank())
    }) add(FinalizationIssue.RecordOutcomes)
}
