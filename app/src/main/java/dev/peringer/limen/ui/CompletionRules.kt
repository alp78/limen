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

internal fun invalidDate(value: String): Boolean = value.isNotBlank() && SwissDate.parse(value) == null

internal fun invalidTime(value: String): Boolean = value.isNotBlank() && runCatching { java.time.LocalTime.parse(value) }.isFailure

internal fun invalidAmount(value: String): Boolean = value.isNotBlank() && nonNegativeNumber(value) == null

/**
 * The deposit amount as the Deposit card shows it. Only the two kinds that print an amount
 * have a field for it, and the pill leaves the typed text behind when it moves to None or
 * Unknown: the gate used to read that text and block finalization over a value nobody could
 * reach to correct.
 */
internal fun depositAmountShown(p: Protocol): String =
  if (p.deposit.kind == DepositKind.BankAccount || p.deposit.kind == DepositKind.Insurance) p.deposit.amountChf else ""

// The rows the gate rejects, one definition each: the gate, the dossier chips and the messages
// that name the cause all ask these, so none of them can accept what another refuses.
internal fun readingFaulty(r: MeterReading): Boolean = r.label.isBlank() || invalidDate(r.date) ||
  ((r.value.isBlank() || r.unit.isBlank() || r.identifier.isBlank()) && r.note.isBlank())

internal fun attachmentFaulty(a: SupportingDocument): Boolean = a.title.isBlank() || (a.file.isBlank() && a.externalReference.isBlank())

internal fun followUpFaulty(f: FollowUp): Boolean =
  f.description.isBlank() || listOf(f.discoveredDate, f.notifiedDate, f.deadline).any(::invalidDate)

internal fun estimateFaulty(e: ReplacementEstimate): Boolean = nonNegativeNumber(e.replacementChf) == null ||
  nonNegativeNumber(e.ageYears) == null || (nonNegativeNumber(e.lifeYears) ?: 0.0) <= 0.0

internal fun notificationFaulty(d: RecordDetails): Boolean =
  d.notificationDays.isNotBlank() && (d.notificationDays.toLongOrNull() !in 1L..365L || d.notificationClause.isBlank())

internal fun contractFilled(p: Protocol): Boolean = p.contract != Contract() || p.details.scopeNotes["Contract"].orEmpty().isNotBlank()

internal fun contractValid(p: Protocol): Boolean =
  listOf(p.contract.conclusionDate, p.contract.startDate, p.contract.endDate, p.contract.noticeDate).none(::invalidDate) &&
    listOf(p.contract.monthlyRentChf, p.contract.serviceChargesChf).none(::invalidAmount)

internal fun metersFilled(p: Protocol): Boolean =
  p.meters != Meters() || p.details.readings.isNotEmpty() || p.details.scopeNotes["Meters"].orEmpty().isNotBlank()

internal fun notesFilled(p: Protocol): Boolean = p.remarks.isNotBlank() || p.chargesStatementBy.isNotBlank()

internal fun transferValid(d: RecordDetails): Boolean = !invalidDate(d.actualTransferDate) && !notificationFaulty(d)

// The ticks on the dossier's section chips. A section ticks only when it holds something and
// the gate has nothing to say about it, so a ticked chip cannot sit beside a blocked FINALIZE.
internal fun objectRecorded(p: Protocol): Boolean =
  p.address.isNotBlank() && p.place.isNotBlank() && p.objectCanton != null &&
    SwissDate.parse(p.handoverDate) != null && p.handoverTime.isNotBlank() && !invalidTime(p.handoverTime) &&
    !invalidDate(p.lastProtocolDate) && p.resolvedLegalOverlay()?.isComplete(p.legalOverlay) == true

internal fun partiesRecorded(p: Protocol): Boolean =
  listOf(Role.Tenant, Role.Landlord).all { role -> p.parties.firstOrNull { it.role == role }?.name?.isNotBlank() == true } &&
    p.parties.none { it.name.isBlank() || (it.representedBy.isNotBlank() && it.authority.isBlank()) } &&
    p.parties.map { it.participantKey() }.distinct().size == p.parties.size

internal fun dossierCardRecorded(p: Protocol, card: DossierCard): Boolean = when (card) {
  DossierCard.Contract -> contractFilled(p) && contractValid(p)
  DossierCard.Deposit -> depositRecorded(p) && !invalidAmount(depositAmountShown(p))
  DossierCard.Meters -> metersFilled(p) && p.details.readings.none { readingFaulty(it) || invalidTime(it.time) }
  DossierCard.Keys -> keyOutcomeRecorded(p)
  DossierCard.Notes -> notesFilled(p) && !invalidDate(p.chargesStatementBy)
}

internal fun transferRecorded(p: Protocol): Boolean =
  p.details.actualTransferDate.isNotBlank() && transferValid(p.details) && !invalidTime(p.details.actualTransferTime)

internal fun attachmentsRecorded(p: Protocol): Boolean = p.details.attachments.isNotEmpty() && p.details.attachments.none(::attachmentFaulty)

internal fun followUpsRecorded(p: Protocol): Boolean = p.details.followUps.isNotEmpty() && p.details.followUps.none(::followUpFaulty)

/** The first dossier section to correct, shared by actionable completion messages. */
internal fun incompleteDossierTarget(p: Protocol): String {
  val cards = p.dossierCards()
  if (invalidDate(p.lastProtocolDate)) return "object"
  if (!transferValid(p.details)) return "transfer"
  if (!contractValid(p) || DossierCard.Contract in cards && !contractFilled(p)) return "card-Contract"
  if (!depositRecorded(p) || invalidAmount(depositAmountShown(p))) return "card-Deposit"
  if (p.details.attachments.any(::attachmentFaulty)) return "documents"
  if (p.details.followUps.any(::followUpFaulty)) return "follow-up"
  if (DossierCard.Meters in cards && !metersFilled(p) || p.details.readings.any(::readingFaulty)) return "card-Meters"
  return "card-Notes"
}

/**
 * Each value the dossier check rejected, by the name it carries on screen. The message used
 * to list Deposit, Meters and Attachments whenever they existed, which sent a person to three
 * sections that were fine while the cause sat in a rent, a wear estimate or a follow-up.
 */
internal fun dossierCauses(p: Protocol, s: Str): List<String> = buildList {
  val cards = p.dossierCards()
  listOf(s.leaseConcludedOn to p.contract.conclusionDate, s.tenancyStart to p.contract.startDate,
    s.tenancyEnd to p.contract.endDate, s.noticeDate to p.contract.noticeDate, s.chargesBy to p.chargesStatementBy,
    s.lastProtocol to p.lastProtocolDate, s.actualTransfer to p.details.actualTransferDate).forEach { (name, date) ->
    if (invalidDate(date)) add(name)
  }
  listOf(s.monthlyRent to p.contract.monthlyRentChf, s.serviceCharges to p.contract.serviceChargesChf,
    s.depositAmount to depositAmountShown(p)).forEach { (name, amount) ->
    if (invalidAmount(amount)) add(name)
  }
  p.defects.filter { invalidDate(it.fixDeadline) }.forEach { add("${s.fixDeadline}: ${it.text}") }
  p.defects.filter { p.details.estimates[it.id]?.let(::estimateFaulty) == true }.forEach { add("${s.faceValue}: ${it.text}") }
  if (notificationFaulty(p.details)) add(s.notificationClause)
  if (p.details.readings.any(::readingFaulty)) add(s.meters)
  if (p.details.attachments.any(::attachmentFaulty)) add(s.attachments)
  if (p.details.followUps.any(::followUpFaulty)) add(s.followUps)
  if (DossierCard.Contract in cards && !contractFilled(p)) add(s.contract)
  if (DossierCard.Deposit in cards && !depositRecorded(p)) add(s.deposit)
  if (DossierCard.Meters in cards && !metersFilled(p)) add(s.meters)
  if (DossierCard.Notes in cards && !notesFilled(p)) add(s.notes)
}.distinct()

/** The times other than the handover time that the gate rejected, since the message itself names only that one. */
internal fun timeCauses(p: Protocol, s: Str): List<String> = buildList {
  if (invalidTime(p.details.actualTransferTime)) add(s.actualTransfer)
  p.details.readings.filter { invalidTime(it.time) }.forEach { add(if (it.label.isBlank()) s.meters else "${s.meters}: ${it.label}") }
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
  // Free text a date field keeps verbatim while it is half typed: whatever is not a date
  // would print as typed on the signed record.
  val optionalDates = listOf(record.chargesStatementBy, record.lastProtocolDate) + record.defects.map { it.fixDeadline }
  if (optionalDates.any(::invalidDate) || !contractValid(record) || !transferValid(record.details)) {
    add(FinalizationIssue.Dossier)
  }
  record.resolvedLegalOverlay()?.let { overlay ->
    if (!overlay.isComplete(record.legalOverlay)) add(FinalizationIssue.LegalOverlay)
  }
  if (!partiesRecorded(record)) add(FinalizationIssue.Parties)
  // Two different states with two different answers: an empty inspection needs a room added,
  // and a populated one needs its rooms confirmed. One message covered both and told a record
  // with no rooms at all to "confirm every listed room was inspected".
  if (record.rooms.isEmpty() && record.details.scopeNotes["Inspection"].isNullOrBlank()) add(FinalizationIssue.NoRooms)
  else if (record.rooms.any { !it.inspected && record.details.scopeNotes[it.id].isNullOrBlank() }) add(FinalizationIssue.Rooms)
  if (record.objects.any { !it.inspected && record.details.scopeNotes[it.id].isNullOrBlank() }) add(FinalizationIssue.Objects)

  val cards = record.dossierCards()
  val emptyAddedComponent =
    (DossierCard.Contract in cards && !contractFilled(record)) ||
      (DossierCard.Deposit in cards && !depositRecorded(record)) ||
      (DossierCard.Meters in cards && !metersFilled(record)) ||
      (DossierCard.Notes in cards && !notesFilled(record))
  if (emptyAddedComponent) add(FinalizationIssue.Dossier)

  // Gated on the card like every other component above it: removing the Keys card takes the
  // only control that can record an outcome off the screen, and an ungated rule then blocked
  // finalization forever with nothing left anywhere to answer it.
  if (DossierCard.Keys in cards && !keyOutcomeRecorded(record) ||
      DossierCard.Keys !in cards && record.details.scopeNotes["Keys"].isNullOrBlank()) add(FinalizationIssue.Keys)
  if (record.details.readings.any(::readingFaulty) || record.details.attachments.any(::attachmentFaulty) ||
      record.details.followUps.any(::followUpFaulty)) add(FinalizationIssue.Dossier)
  if (invalidAmount(depositAmountShown(record))) add(FinalizationIssue.Dossier)
  if (record.details.estimates.values.any(::estimateFaulty)) add(FinalizationIssue.Dossier)
  if (invalidTime(record.details.actualTransferTime) || record.details.readings.any { invalidTime(it.time) }) add(FinalizationIssue.HandoverTime)
  if (record.oneSided && (record.unilateralReason == null ||
      (record.unilateralReason == dev.peringer.limen.data.UnilateralReason.Other && record.unilateralNote.isBlank()))) {
    add(FinalizationIssue.UnilateralReason)
  }
  if (record.defects.any { it.text.isBlank() }) add(FinalizationIssue.DefectText)
  if (ProtocolIntegrity.missingEvidence(record, photoDir, signatureDir).isNotEmpty()) {
    add(FinalizationIssue.Evidence)
  }
  if (requiredRoles.any { role -> record.parties.none { it.role == role } } ||
    record.parties.filter { it.role in requiredRoles }.any { party ->
      val signature = record.signatureFor(party)
      signature == null || !record.isCurrent(signature) ||
        !(signature.refused || signature.file.isNotBlank() || signature.outcome in setOf("absent", "unable")) ||
        (signature.outcome.isNotBlank() && signature.reservation.isBlank())
    }) add(FinalizationIssue.RecordOutcomes)
}
