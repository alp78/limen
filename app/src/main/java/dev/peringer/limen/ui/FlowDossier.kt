package dev.peringer.limen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.data.CautionProvider
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.KeyCount
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.LegalOverlayRecord
import dev.peringer.limen.data.Party
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.SwissAddressCandidate
import dev.peringer.limen.data.SwissAddressLookup
import dev.peringer.limen.data.SwissDate
import dev.peringer.limen.data.dossierCards
import dev.peringer.limen.data.isBlank
import dev.peringer.limen.data.resolvedLegalOverlay
import dev.peringer.limen.sense.Clocks
import dev.peringer.limen.theme.Limen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The paperwork half of a handover: the object, the people, and whichever of the money and
 * meter rubrics this particular handover actually has.
 *
 * The three at the top are permanent, because a record without an address, a date and two
 * named parties is not a record. Everything below them is added on demand and printed only
 * when added, so a furnished sublet with no deposit and no meter of its own never has to
 * look at a deposit field, and its PDF never shows an empty one.
 */
@OptIn(ExperimentalFoundationApi::class)
/**
 * How far above the target a section chip has to stop. The header is opaque and sticky, so
 * scrolling the panel to the exact top put its first lines behind the header.
 */
private const val HEADER_OVERLAP_PX = 160

@Composable
internal fun DossierStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val cards = p.dossierCards()
  val locked = LocalRecordLocked.current
  var removing by remember { mutableStateOf<DossierCard?>(null) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val sections = listOf(
    DossierSection(s.objectLabel, objectSectionComplete(p)),
    DossierSection(s.parties, partiesSectionComplete(p)),
  ) + cards.map { card -> DossierSection(s.dossierCard(card), dossierSectionComplete(p, card)) }
  val activeSection by remember(listState, sections.size) {
    derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceIn(0, sections.lastIndex) }
  }

  LazyColumn(
    Modifier.fillMaxSize(),
    state = listState,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(bottom = 12.dp),
  ) {
    stickyHeader {
      Row(
        Modifier
          .fillMaxWidth()
          .background(Limen.colors.plate)
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        sections.forEachIndexed { index, section ->
          Chip(
            label = "${if (section.complete) "✓" else "○"} ${section.label}",
            active = index == activeSection,
            selectionState = index == activeSection,
          ) {
            scope.launch { listState.animateScrollToItem(index + 1, scrollOffset = -HEADER_OVERLAP_PX) }
          }
        }
      }
    }

    item(key = "object") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ObjectCard(vm, s, p)
        LegalOverlayPanel(vm, s, p)
        HandoverDeadline(p, s)
      }
    }
    item(key = "parties") { PartiesCard(vm, s, p) }

    cards.forEach { card ->
      item(key = "card-${card.name}") {
        when (card) {
          DossierCard.Contract -> ContractCard(vm, s, p) { removing = card }
          DossierCard.Deposit -> DepositCard(vm, s, p) { removing = card }
          DossierCard.Meters -> MetersCard(vm, s, p) { removing = card }
          DossierCard.Keys -> KeysCard(vm, s, p) { removing = card }
          DossierCard.Notes -> NotesCard(vm, s, p) { removing = card }
        }
      }
    }

    val absent = DossierCard.entries.filterNot { it in cards }
    if (absent.isNotEmpty() && !locked) {
      item(key = "add-component") {
        Panel(label = s.addComponent) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            absent.forEach { card ->
              Chip("+ ${s.dossierCard(card)}", dim = false) { vm.addDossierCard(card) }
            }
          }
        }
      }
    }

    item(key = "close") {
      BigKey(
        if (locked) s.stepExport.uppercase() else s.hubClose,
        active = true,
        Modifier.fillMaxWidth(),
      ) { vm.goTo(if (locked) FlowStep.Export else FlowStep.Review) }
    }
  }

  removing?.let { card ->
    ConfirmDialog(
      s = s,
      body = s.removeComponentBody,
      onConfirm = {
        vm.removeDossierCard(card)
        removing = null
      },
      onDismiss = { removing = null },
    )
  }
}

private data class DossierSection(val label: String, val complete: Boolean)

private fun objectSectionComplete(p: Protocol): Boolean =
  p.address.isNotBlank() && p.place.isNotBlank() && p.objectCanton != null &&
    p.handoverDate.isNotBlank() && p.handoverTime.isNotBlank() &&
    (p.resolvedLegalOverlay()?.isComplete(p.legalOverlay) == true)

private fun partiesSectionComplete(p: Protocol): Boolean =
  listOf(Role.Tenant, Role.Landlord).all { role ->
    p.parties.firstOrNull { it.role == role }?.name?.isNotBlank() == true
  }

private fun dossierSectionComplete(p: Protocol, card: DossierCard): Boolean = when (card) {
  DossierCard.Contract -> p.contract != dev.peringer.limen.data.Contract()
  DossierCard.Deposit -> when (p.deposit.kind) {
    DepositKind.None -> false
    DepositKind.BankAccount -> listOf(
      p.deposit.amountChf,
      p.deposit.bank,
      p.deposit.account,
    ).all { it.isNotBlank() }
    DepositKind.Insurance -> listOf(
      p.deposit.amountChf,
      p.deposit.provider,
      p.deposit.policyNo,
    ).all { it.isNotBlank() }
  }
  DossierCard.Meters -> !p.meters.isBlank()
  // The same rule the finalization gate applies, so a ticked chip cannot sit beside a
  // blocked FINALIZE with nothing on screen naming what is missing.
  DossierCard.Keys -> keyOutcomeRecorded(p)
  DossierCard.Notes -> p.remarks.isNotBlank() || p.chargesStatementBy.isNotBlank()
}

/**
 * The removal affordance every optional component carries, in the same place on each.
 * An icon rather than a word: a component's own fields are what the eye should reach
 * first, and a red DELETE across the top of every card reads as a warning about the card.
 */
@Composable
private fun CardHeader(s: Str, onRemove: () -> Unit) {
  if (LocalRecordLocked.current) return
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    IconKey(ButtonGlyph.Delete, s.delete, destructive = true, onPress = onRemove)
  }
}

// ── The object ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ObjectCard(vm: LimenViewModel, s: Str, p: Protocol) {
  var searching by remember(p.id) { mutableStateOf(false) }
  var matches by remember(p.id) { mutableStateOf(emptyList<SwissAddressCandidate>()) }
  var focused by remember(p.id) { mutableStateOf(emptySet<String>()) }
  var verified by remember(p.id) {
    mutableStateOf(p.address.isNotBlank() && p.place.isNotBlank() && p.objectCanton != null)
  }
  val anyFocus = focused.isNotEmpty()
  val query = listOf(p.address, p.place).filter { it.isNotBlank() }.joinToString(" ")

  LaunchedEffect(p.id, query, anyFocus, verified) {
    if (!anyFocus || verified || query.trim().length < 3) {
      matches = emptyList()
      searching = false
      return@LaunchedEffect
    }
    delay(350)
    searching = true
    matches = SwissAddressLookup.search(query)
    searching = false
  }

  fun setFocused(field: String, isFocused: Boolean) {
    focused = if (isFocused) focused + field else focused - field
  }

  // Typing a street name does NOT clear the canton or the cantonal answers. It used to, and
  // one keystroke in Place then wiped every overlay answer with no undo and no panel left on
  // screen to show what had gone. The canton changes where the canton is chosen: the picker
  // below and the candidate list above, both of which reset the answers only when the canton
  // itself actually moves.
  fun editAddress(transform: (Protocol) -> Protocol) {
    verified = false
    matches = emptyList()
    searching = false
    vm.edit(transform)
  }

  Panel(label = s.objectLabel, info = s.tipAddress()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Above the fields they fill: below them, the keyboard covered the answer.
      if (anyFocus && !verified && searching) {
        Text(s.addressSearching, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      }
      if (anyFocus && !verified) {
        AddressMatchList(matches) { candidate ->
          vm.edit {
            val moved = it.objectCanton != candidate.canton
            it.copy(
              address = listOf(candidate.street, candidate.flatNumber)
                .filter { part -> part.isNotBlank() }.joinToString(" "),
              place = listOf(candidate.postalCode, candidate.city)
                .filter { part -> part.isNotBlank() }.joinToString(" "),
              objectCanton = candidate.canton,
              // Re-picking the same address must not discard answers already given for it.
              romandFrameworkInLease = if (moved) false else it.romandFrameworkInLease,
              legalOverlay = if (moved) LegalOverlayRecord() else it.legalOverlay,
            )
          }
          matches = emptyList()
          verified = true
        }
      }
      Field(
        s.address,
        p.address,
        onFocusChanged = { setFocused("address", it) },
      ) { value -> editAddress { it.copy(address = value) } }
      Field(s.place, p.place, onFocusChanged = { setFocused("place", it) }) { value ->
        editAddress { it.copy(place = value) }
      }
      CantonPicker(s, p.objectCanton) { canton ->
        vm.edit { current ->
          if (current.objectCanton == canton) current
          else current.copy(
            objectCanton = canton,
            romandFrameworkInLease = false,
            legalOverlay = LegalOverlayRecord(),
          )
        }
      }
      Field(s.objectDetail, p.objectDetail) { v -> vm.edit { it.copy(objectDetail = v) } }
      DateAndTimeRow(
        dateLabel = s.handoverDate,
        date = p.handoverDate,
        timeLabel = s.handoverTime,
        time = p.handoverTime,
        s = s,
        onDate = { v ->
          vm.edit {
            it.copy(
              handoverDate = v,
              legalOverlay = if (it.handoverDate == v) it.legalOverlay else LegalOverlayRecord(),
            )
          }
        },
        onTime = { v -> vm.edit { it.copy(handoverTime = v) } },
      )
      DateField(s.lastProtocol, p.lastProtocolDate, s) { v ->
        vm.edit { it.copy(lastProtocolDate = v) }
      }
    }
  }
}

/**
 * Legal timing is shown where the handover date is entered, not as a detached utility.
 *
 * Both kinds used to state their deadline as a paragraph. Both now state it as the date
 * itself, computed from the handover, with the reasoning and the article behind the mark.
 * A date is what a person acts on; the paragraph is what they read once.
 */
@Composable
private fun HandoverDeadline(p: Protocol, s: Str) {
  val handover = SwissDate.parse(p.handoverDate) ?: return

  if (p.kind == Kind.Entry) {
    Panel(label = s.clockEntryTitle, info = s.tipEntryNotice()) {
      Deadline(s.listBy, Clocks.entryListBy(handover))
    }
    return
  }

  Panel(label = s.clockExitTitle, info = s.tipExitNotice()) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(Modifier.weight(1f)) { Deadline(s.safeBy, Clocks.exitNoticeSafeBy(handover)) }
      Box(Modifier.weight(1f)) { Deadline(s.latestBy, Clocks.exitNoticeLatest(handover)) }
    }
  }
}

/** One computed date, read the way it is written on a form. */
@Composable
private fun Deadline(label: String, date: LocalDate) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
    Text(
      SwissDate.display(date.toString()),
      fontFamily = FontFamily.Monospace,
      fontSize = 22.sp,
      color = Limen.colors.ink,
    )
  }
}

// ── The people ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PartiesCard(vm: LimenViewModel, s: Str, p: Protocol) {
  Panel(label = s.parties) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      p.parties.forEachIndexed { i, party ->
        // The two mandatory roles are always there and need no announcing; a role that a
        // key just added does.
        if (i < 2) PartyBlock(vm, s, p, i, party)
        else Revealed(party.role) { PartyBlock(vm, s, p, i, party) }
      }
      if (!LocalRecordLocked.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(Role.Witness, Role.Expert, Role.NextTenant).forEach { role ->
            if (p.parties.none { it.role == role }) {
              SmallKey("+ ${s.role(role)}", Modifier.weight(1f)) {
                vm.edit { it.copy(parties = it.parties + Party(role)) }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PartyBlock(vm: LimenViewModel, s: Str, p: Protocol, index: Int, party: Party) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        s.role(party.role).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Limen.colors.inkDim,
        modifier = Modifier.weight(1f),
      )
      val removable = party.role == Role.Witness || party.role == Role.Expert || party.role == Role.NextTenant
      if (removable && !LocalRecordLocked.current) {
        IconKey(ButtonGlyph.Delete, s.delete, destructive = true) {
          vm.edit { it.copy(parties = it.parties.filterIndexed { j, _ -> j != index }) }
        }
      }
    }
    Field(s.role(party.role), party.name) { v ->
      vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(name = v) else q }) }
    }
    if (party.role == Role.Landlord || party.role == Role.Tenant) {
      Field(s.representedBy, party.representedBy) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(representedBy = v) else q }) }
      }
    }
    if (party.role == Role.Tenant && p.kind == Kind.Exit) {
      Field(s.newAddress, party.address) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(address = v) else q }) }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(
        s.phoneNumber,
        party.phone,
        Modifier.weight(1f),
        keyboardType = KeyboardType.Phone,
      ) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(phone = v) else q }) }
      }
      Field(
        s.email,
        party.email,
        Modifier.weight(1f),
        keyboardType = KeyboardType.Email,
      ) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(email = v) else q }) }
      }
    }
  }
}

// ── The optional components ────────────────────────────────────────────────────────────

@Composable
private fun ContractCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  Panel(label = s.contract) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.monthlyRent, p.contract.monthlyRentChf, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(contract = it.contract.copy(monthlyRentChf = v)) }
        }
        Field(s.serviceCharges, p.contract.serviceChargesChf, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(contract = it.contract.copy(serviceChargesChf = v)) }
        }
      }
      DateField(s.leaseConcludedOn, p.contract.conclusionDate, s) { v ->
        vm.edit { record ->
          record.copy(
            contract = record.contract.copy(conclusionDate = v),
            legalOverlay = if (record.contract.conclusionDate == v) {
              record.legalOverlay
            } else {
              LegalOverlayRecord()
            },
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(s.tenancyStart, p.contract.startDate, s, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(startDate = v)) }
        }
        DateField(s.tenancyEnd, p.contract.endDate, s, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(endDate = v)) }
        }
      }
      DateField(s.noticeDate, p.contract.noticeDate, s) { v ->
        vm.edit { it.copy(contract = it.contract.copy(noticeDate = v)) }
      }
    }
  }
}

@Composable
private fun DepositCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  Panel(label = s.deposit, info = s.tipDeposit()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      PillRow(
        DepositKind.entries.map { it to s.depositKind(it) },
        p.deposit.kind,
        enabled = !LocalRecordLocked.current,
      ) { k -> vm.edit { it.copy(deposit = it.deposit.copy(kind = k)) } }
      // Whichever road the pill above chose, its fields arrive under the highlight.
      if (p.deposit.kind != DepositKind.None) {
        Revealed(p.deposit.kind) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.deposit.kind == DepositKind.Insurance) {
              CautionProviderPicker(s, p.deposit.provider) { provider ->
                vm.edit { current ->
                  val existing = current.deposit.provider
                  val providerName = provider.storedName ?: existing.takeIf {
                    CautionProvider.fromStoredName(it) == CautionProvider.Other
                  }.orEmpty()
                  current.copy(deposit = current.deposit.copy(provider = providerName))
                }
              }
              if (CautionProvider.fromStoredName(p.deposit.provider) == CautionProvider.Other) {
                Field(s.otherCautionProvider, p.deposit.provider) { v ->
                  vm.edit { it.copy(deposit = it.deposit.copy(provider = v)) }
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(s.depositPolicy, p.deposit.policyNo, Modifier.weight(1.3f)) { v ->
                  vm.edit { it.copy(deposit = it.deposit.copy(policyNo = v)) }
                }
                Field(s.depositAmount, p.deposit.amountChf, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
                  vm.edit { it.copy(deposit = it.deposit.copy(amountChf = v)) }
                }
              }
            } else {
              Field(s.depositBankName, p.deposit.bank) { v ->
                vm.edit { it.copy(deposit = it.deposit.copy(bank = v)) }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(s.depositAccount, p.deposit.account, Modifier.weight(1.3f)) { v ->
                  vm.edit { it.copy(deposit = it.deposit.copy(account = v)) }
                }
                Field(s.depositAmount, p.deposit.amountChf, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
                  vm.edit { it.copy(deposit = it.deposit.copy(amountChf = v)) }
                }
              }
            }
          }
        }
      }

      // The comparison gets its own line and is named for what it opens. Sharing the pill
      // row it had room for one letter, and a key that says "C" teaches nobody that the two
      // roads for a deposit are a blocked account or an insurance, or what either costs.
      // Only at entry: the deposit is chosen before moving in. At exit it already exists
      // and the card merely documents it for the release instruction.
      if (p.kind == Kind.Entry) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          SmallKey(s.compareKey, Modifier.weight(1f)) { vm.setDepositCompare(true) }
          InfoTip(s.tipCompare())
        }
      }
    }
  }
}

@Composable
private fun MetersCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  Panel(label = s.meters) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.meterElec1, p.meters.electricity1, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(electricity1 = v)) }
        }
        Field(s.meterElec2, p.meters.electricity2, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(electricity2 = v)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.meterGas, p.meters.gas, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(gas = v)) }
        }
        Field(s.meterWater, p.meters.water, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(water = v)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.meterHotWater, p.meters.hotWater, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(hotWater = v)) }
        }
        Field(s.meterOil, p.meters.heatingOil, Modifier.weight(1f), keyboardType = KeyboardType.Decimal) { v ->
          vm.edit { it.copy(meters = it.meters.copy(heatingOil = v)) }
        }
      }
    }
  }
}

@Composable
private fun KeysCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  val locked = LocalRecordLocked.current
  var adding by remember { mutableStateOf(false) }
  var otherLabel by remember { mutableStateOf("") }

  Panel(label = s.keysLabel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      Text(s.keyTransferLabel, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      PillRow(
        KeyTransferStatus.entries.map { it to s.keyTransfer(it) },
        p.keyTransfer,
        enabled = !locked,
      ) { status ->
        // "No physical keys" zeroes every count the operator already entered, so it is the
        // one pill that goes through the undo bar rather than a plain edit.
        val write: ((Protocol) -> Protocol) -> Unit =
          if (status == KeyTransferStatus.NoPhysicalKeys) vm::editUndoably else vm::edit
        write { record ->
          if (status == KeyTransferStatus.NoPhysicalKeys) {
            record.copy(
              keyTransfer = status,
              keyTransferNote = "",
              keys = record.keys.map { it.copy(count = 0) },
              rooms = record.rooms.map { it.copy(keysCount = 0) },
              selfMadeKeys = false,
            )
          } else {
            record.copy(
              keyTransfer = status,
              keyTransferNote = record.keyTransferNote.takeIf {
                status == KeyTransferStatus.Incomplete
              }.orEmpty(),
            )
          }
        }
      }
      if (p.keyTransfer == KeyTransferStatus.Incomplete) {
        Field(s.keyTransferNote, p.keyTransferNote, singleLine = false) { note ->
          vm.edit { it.copy(keyTransferNote = note) }
        }
      }
      if (p.keyTransfer != KeyTransferStatus.NoPhysicalKeys) {
        p.keys.forEachIndexed { i, key ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (key.kind == KeyKind.Other && key.label.isNotBlank()) key.label else s.keyKind(key.kind),
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
            )
          }
          if (locked) {
            Text(
              key.count.toString(),
              fontFamily = FontFamily.Monospace,
              fontSize = 17.sp,
              color = Limen.colors.ink,
            )
          } else {
            Stepper(key.count, s) { c ->
              vm.edit {
                it.copy(keys = it.keys.mapIndexed { j, k -> if (j == i) k.copy(count = c) else k })
              }
            }
            Spacer(Modifier.width(6.dp))
            IconKey(ButtonGlyph.Delete, s.delete, destructive = true) {
              vm.edit { it.copy(keys = it.keys.filterIndexed { j, _ -> j != i }) }
            }
          }
        }
      }

        if (!locked) SmallKey(s.addKeyKind, Modifier.fillMaxWidth()) { adding = !adding }
        if (adding && !locked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeyKind.entries.filterNot { it == KeyKind.Other }.forEach { kind ->
              Chip("+ ${s.keyKind(kind)}", dim = false) {
                vm.edit { it.copy(keys = it.keys + KeyCount(kind)) }
                adding = false
              }
            }
          }
          // A key the canon has no word for: the bike shed, the laundry, the gate code fob.
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(s.keyLabel, otherLabel, Modifier.weight(1f)) { otherLabel = it }
            SmallKey(s.add, enabled = otherLabel.trim().isNotEmpty()) {
              vm.edit { it.copy(keys = it.keys + KeyCount(KeyKind.Other, label = otherLabel.trim())) }
              otherLabel = ""
              adding = false
            }
          }
        }
        }

        Toggle(s.selfMadeKeys, p.selfMadeKeys, Modifier.fillMaxWidth(), enabled = !locked) {
          vm.edit { it.copy(selfMadeKeys = !it.selfMadeKeys) }
        }
      }
    }
  }
}

@Composable
private fun NotesCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  Panel(label = s.notes) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      Field(s.remarks, p.remarks, singleLine = false) { v -> vm.edit { it.copy(remarks = v) } }
      // A date, and now typed as one: the label has always said "by", in every language.
      DateField(s.chargesBy, p.chargesStatementBy, s) { v ->
        vm.edit { it.copy(chargesStatementBy = v) }
      }
    }
  }
}

// ── Shared pickers ─────────────────────────────────────────────────────────────────────

/** The handed-over flat is resolved independently from any participant contact address. */
@Composable
private fun AddressMatchList(
  matches: List<SwissAddressCandidate>,
  onSelect: (SwissAddressCandidate) -> Unit,
) {
  if (matches.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    matches.forEach { candidate ->
      KeySurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        onPress = { onSelect(candidate) },
      ) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CantonFlag(candidate.canton, contentDescription = null, size = 26.dp)
          Text(
            candidate.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Limen.colors.ink,
            maxLines = 2,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun CautionProviderPicker(s: Str, storedName: String, onSelect: (CautionProvider) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val selected = CautionProvider.fromStoredName(storedName)

  Box(Modifier.fillMaxWidth()) {
    KeySurface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(6.dp),
      onPress = { expanded = true },
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(s.depositProvider, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          Text(s.cautionProvider(selected), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        }
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Limen.colors.inkDim)
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = Limen.colors.plateRaised,
    ) {
      CautionProvider.entries.forEach { provider ->
        DropdownMenuItem(
          text = { Text(s.cautionProvider(provider), color = Limen.colors.ink) },
          enabled = provider != selected,
          trailingIcon = if (provider == selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = Limen.colors.inkDim, modifier = Modifier.size(16.dp)) }
          } else null,
          onClick = {
            onSelect(provider)
            expanded = false
          },
        )
      }
    }
  }
}
