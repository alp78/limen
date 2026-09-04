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
  val physical = record.keys.sumOf { it.count } + record.rooms.sumOf { it.keysCount }
  return when (record.keyTransfer) {
    KeyTransferStatus.NotRecorded -> false
    KeyTransferStatus.AllTransferred -> physical > 0
    KeyTransferStatus.NoPhysicalKeys -> physical == 0 && !record.selfMadeKeys
    KeyTransferStatus.Incomplete -> record.keyTransferNote.isNotBlank()
  }
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
  if (record.handoverDate.isBlank()) add(FinalizationIssue.HandoverDate)
  if (record.handoverTime.isBlank()) add(FinalizationIssue.HandoverTime)
  record.resolvedLegalOverlay()?.let { overlay ->
    if (!overlay.isComplete(record.legalOverlay)) add(FinalizationIssue.LegalOverlay)
  }
  if (listOf(Role.Tenant, Role.Landlord).any { role ->
      record.parties.firstOrNull { it.role == role }?.name.isNullOrBlank()
    }) add(FinalizationIssue.Parties)
  // Two different states with two different answers: an empty inspection needs a room added,
  // and a populated one needs its rooms confirmed. One message covered both and told a record
  // with no rooms at all to "confirm every listed room was inspected".
  if (record.rooms.isEmpty()) add(FinalizationIssue.NoRooms)
  else if (record.rooms.any { !it.inspected }) add(FinalizationIssue.Rooms)
  if (record.objects.any { !it.inspected }) add(FinalizationIssue.Objects)

  val cards = record.dossierCards()
  val depositIncomplete = when (record.deposit.kind) {
    DepositKind.None -> true
    DepositKind.BankAccount ->
      record.deposit.amountChf.isBlank() || record.deposit.bank.isBlank() || record.deposit.account.isBlank()
    DepositKind.Insurance ->
      record.deposit.amountChf.isBlank() || record.deposit.provider.isBlank() || record.deposit.policyNo.isBlank()
  }
  val emptyAddedComponent =
    (DossierCard.Contract in cards && record.contract == Contract()) ||
      (DossierCard.Deposit in cards && depositIncomplete) ||
      (DossierCard.Meters in cards && record.meters == Meters()) ||
      (DossierCard.Notes in cards && record.remarks.isBlank() && record.chargesStatementBy.isBlank())
  if (emptyAddedComponent) add(FinalizationIssue.Dossier)

  // Gated on the card like every other component above it: removing the Keys card takes the
  // only control that can record an outcome off the screen, and an ungated rule then blocked
  // finalization forever with nothing left anywhere to answer it.
  if (DossierCard.Keys in cards && !keyOutcomeRecorded(record)) add(FinalizationIssue.Keys)
  if (record.oneSided && record.unilateralReason == null) add(FinalizationIssue.UnilateralReason)
  if (record.defects.any { it.text.isBlank() }) add(FinalizationIssue.DefectText)
  if (ProtocolIntegrity.missingEvidence(record, photoDir, signatureDir).isNotEmpty()) {
    add(FinalizationIssue.Evidence)
  }
  if (requiredRoles.any { role ->
      record.signatures.none { signature ->
        signature.role == role && signature.plane == Plane.Record && record.isCurrent(signature) &&
          (signature.refused || signature.file.isNotBlank())
      }
    }) add(FinalizationIssue.RecordOutcomes)
}
