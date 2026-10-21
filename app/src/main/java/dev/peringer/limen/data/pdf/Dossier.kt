package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*

/** The dated canton checks actually recorded, never inferred into an older frozen PDF. */
internal fun Sheet.legalOverlay(p: Protocol) {
  val overlay = p.resolvedLegalOverlay() ?: return
  val blocks = buildList<Block> {
    add(Fields(listOf(s.cantonLabel to s.canton(overlay.canton), s.legalBasis to s.legalProfile(overlay.profile))))
    if (p.kind == Kind.Entry) add(Text("${s.initialRentForm}: ${s.initialRentFormRule(overlay.initialRentFormRule)}", Style.Small))
    // Only the requirements this record still resolves. Printed from the raw answer map, a
    // deposit set back to None left "Deposit proof: Confirmed" on a record stating there is
    // no deposit, which neither the panel nor the completeness gate would ever have shown.
    overlay.requirements.sortedBy { it.ordinal }.forEach { requirement ->
      val status = p.legalOverlay.status(requirement) ?: return@forEach
      add(Text("${s.legalRequirement(requirement)}: ${s.legalCheckStatus(status)}", Style.Body))
      p.legalOverlay.notes[requirement]?.takeIf(String::isNotBlank)?.let { add(Text(it, Style.Small)) }
    }
    if (p.legalOverlay.note.isNotBlank()) add(Text("${s.legalOverlayNote}: ${p.legalOverlay.note}", Style.Small))
    val ruleSet = p.legalOverlay.ruleSetId.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    add(Text("${s.legalSnapshotFor(overlay.canton)}$ruleSet", Style.Small))
  }
  panel(Panel(s.legalOverlayTitle, blocks))
}

internal fun Sheet.dataController(p: Protocol) {
  val controller = p.controllerIdentity
  if (controller.isBlank()) return
  val blocks = buildList<Block> {
    if (controller.organisation.isNotBlank()) add(Text(controller.organisation, Style.BodyMedium))
    controller.contactName.takeIf { it.isNotBlank() }?.let { add(Text(it, Style.Body)) }
    controller.email.takeIf { it.isNotBlank() }?.let { add(Text(it, Style.Body)) }
    controller.phone.takeIf { it.isNotBlank() }?.let { add(Text(it, Style.Body)) }
    listOf(
      controller.street,
      listOf(controller.postalCode, controller.city).filter { it.isNotBlank() }.joinToString(" "),
    ).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { add(Text(it.joinToString(", "), Style.Body)) }
  }
  panel(Panel(s.controllerTitle, blocks))
}

internal fun Sheet.contract(p: Protocol) = panel(Panel(s.contract, listOf(
  Fields(listOf(
    s.monthlyRent to p.contract.monthlyRentChf,
    s.serviceCharges to p.contract.serviceChargesChf,
    s.noticeDate to SwissDate.display(p.contract.noticeDate),
  )),
  Fields(listOf(
    s.leaseConcludedOn to SwissDate.display(p.contract.conclusionDate),
    s.tenancyStart to SwissDate.display(p.contract.startDate),
    s.tenancyEnd to SwissDate.display(p.contract.endDate),
  )),
)))

internal fun Sheet.deposit(p: Protocol) {
  val blocks: List<Block> = when (p.deposit.kind) {
    DepositKind.None -> listOf(Text(s.depositKind(DepositKind.None), Style.Body))
    DepositKind.Insurance -> listOf(Fields(listOf(
      s.depositProvider to p.deposit.provider,
      s.depositPolicy to p.deposit.policyNo,
      s.depositAmount to p.deposit.amountChf,
    )))
    DepositKind.BankAccount -> listOf(Fields(listOf(
      s.depositBankName to p.deposit.bank,
      s.depositAccount to p.deposit.account,
      s.depositAmount to p.deposit.amountChf,
    )))
    DepositKind.Unknown -> listOf(Text(s.toVerify, Style.Body))
  }
  panel(Panel(s.deposit, blocks))
}

internal fun Sheet.meters(p: Protocol) = panel(Panel(s.meters, listOf(
  Fields(listOf(s.meterElec1 to p.meters.electricity1, s.meterElec2 to p.meters.electricity2, s.meterGas to p.meters.gas)),
  Fields(listOf(s.meterWater to p.meters.water, s.meterHotWater to p.meters.hotWater, s.meterOil to p.meters.heatingOil)),
)))

/** The keys: the transfer's status as the panel's pill, then one chip per kind with its count. */
internal fun Sheet.keys(p: Protocol) {
  val tone = when (p.keyTransfer) {
    KeyTransferStatus.AllTransferred -> PillTone.Seal
    KeyTransferStatus.Incomplete -> PillTone.Warning
    KeyTransferStatus.NoPhysicalKeys, KeyTransferStatus.NotRecorded -> PillTone.Dim
  }
  val blocks = buildList<Block> {
    if (p.keyTransferNote.isNotBlank()) add(Text(p.keyTransferNote, Style.Small))
    if (p.keyTransfer != KeyTransferStatus.NoPhysicalKeys) {
      val chips = p.keys.filter { it.count > 0 }.map { k ->
        val label = if (k.kind == KeyKind.Other && k.label.isNotBlank()) k.label else s.keyKind(k.kind)
        Chip("$label: ${k.count}")
      }
      add(if (chips.isEmpty()) Text("–", Style.Body) else Chips(chips))
      // Older records counted a room's own door keys inside the room. Those still print,
      // once, here rather than twice as they used to.
      val roomKeys = p.rooms.filter { it.keysCount > 0 }
      if (roomKeys.isNotEmpty()) add(Chips(roomKeys.map { Chip("${Outline.roomName(it, s)}: ${it.keysCount}") }))
    }
    if (p.selfMadeKeys) add(Text(s.selfMadeKeys, Style.Body))
  }
  panel(Panel(s.keysLabel, blocks, pill = Pill(s.keyTransfer(p.keyTransfer), tone)))
}
