package dev.verso.limen.ui

import dev.verso.limen.data.Contract
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.KeyTransferStatus
import dev.verso.limen.data.Meters
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.ProtocolIntegrity
import dev.verso.limen.data.Role
import dev.verso.limen.data.dossierCards
import dev.verso.limen.data.resolvedLegalOverlay
import java.io.File

internal fun requiredRecordRoles(record: Protocol, fallbackRole: Role?): List<Role> = buildList {
  if (record.oneSided) {
    (record.sessionRole ?: fallbackRole)?.let(::add)
  } else {
    add(Role.Tenant)
    add(Role.Landlord)
  }
  if (record.parties.any { it.role == Role.Witness }) add(Role.Witness)
  if (record.parties.any { it.role == Role.Expert }) add(Role.Expert)
}.distinct()

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
  if (record.rooms.isEmpty() || record.rooms.any { !it.inspected }) add(FinalizationIssue.Rooms)
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

  val physicalKeyCount = record.keys.sumOf { it.count } + record.rooms.sumOf { it.keysCount }
  val invalidKeyCount = record.keys.any { it.count < 0 } || record.rooms.any { it.keysCount < 0 }
  val keyOutcomeComplete = !invalidKeyCount && when (record.keyTransfer) {
    KeyTransferStatus.NotRecorded -> false
    KeyTransferStatus.AllTransferred -> physicalKeyCount > 0
    KeyTransferStatus.NoPhysicalKeys -> physicalKeyCount == 0 && !record.selfMadeKeys
    KeyTransferStatus.Incomplete -> record.keyTransferNote.isNotBlank()
  }
  if (!keyOutcomeComplete) add(FinalizationIssue.Keys)
  if (record.oneSided && record.unilateralReason == null) add(FinalizationIssue.UnilateralReason)
  if (record.defects.any { it.text.isBlank() }) add(FinalizationIssue.DefectText)
  if (ProtocolIntegrity.missingEvidence(record, photoDir, signatureDir).isNotEmpty()) {
    add(FinalizationIssue.Evidence)
  }
  val unilateralSideMissing = record.oneSided && requiredRoles.none {
    it == Role.Tenant || it == Role.Landlord
  }
  if (unilateralSideMissing || requiredRoles.any { role ->
      record.signatures.none { signature ->
        signature.role == role && signature.plane == Plane.Record && record.isCurrent(signature) &&
          (signature.refused || signature.file.isNotBlank())
      }
    }) add(FinalizationIssue.RecordOutcomes)
}
