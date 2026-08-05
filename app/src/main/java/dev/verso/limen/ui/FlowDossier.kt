package dev.verso.limen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.CautionProvider
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.KeyCount
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.data.SwissAddressCandidate
import dev.verso.limen.data.SwissAddressLookup
import dev.verso.limen.data.dossierCards
import dev.verso.limen.sense.Clocks
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.delay
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
@Composable
internal fun DossierStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val cards = p.dossierCards()
  var removing by remember { mutableStateOf<DossierCard?>(null) }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ObjectCard(vm, s, p)
    HandoverDeadline(p, s)
    PartiesCard(vm, s, p)

    cards.forEach { card ->
      Box {
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
    if (absent.isNotEmpty()) {
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

    BigKey(s.hubClose, active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Review) }
    Spacer(Modifier.height(12.dp))
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

/**
 * The removal affordance every optional component carries, in the same place on each.
 * An icon rather than a word: a component's own fields are what the eye should reach
 * first, and a red DELETE across the top of every card reads as a warning about the card.
 */
@Composable
private fun CardHeader(s: Str, onRemove: () -> Unit) {
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
  var verified by remember(p.id) { mutableStateOf(p.address.isNotBlank() && p.place.isNotBlank()) }
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

  fun editAddress(transform: (Protocol) -> Protocol) {
    verified = false
    matches = emptyList()
    searching = false
    vm.edit(transform)
  }

  Panel(label = s.objectLabel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(
        s.address,
        p.address,
        hint = s.addressLookupHint,
        onFocusChanged = { setFocused("address", it) },
      ) { value -> editAddress { it.copy(address = value) } }
      Field(s.place, p.place, onFocusChanged = { setFocused("place", it) }) { value ->
        editAddress { it.copy(place = value) }
      }
      if (anyFocus && !verified && searching) {
        Text(s.addressSearching, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      }
      if (anyFocus && !verified) {
        AddressMatchList(matches) { candidate ->
          vm.edit {
            it.copy(
              address = listOf(candidate.street, candidate.flatNumber)
                .filter { part -> part.isNotBlank() }.joinToString(" "),
              place = listOf(candidate.postalCode, candidate.city)
                .filter { part -> part.isNotBlank() }.joinToString(" "),
            )
          }
          matches = emptyList()
          verified = true
        }
      }
      Field(s.objectDetail, p.objectDetail) { v -> vm.edit { it.copy(objectDetail = v) } }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.handoverDate, p.handoverDate, Modifier.weight(1.4f)) { v ->
          vm.edit { it.copy(handoverDate = v) }
        }
        Field(s.handoverTime, p.handoverTime, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(handoverTime = v) }
        }
      }
      Field(s.lastProtocol, p.lastProtocolDate) { v -> vm.edit { it.copy(lastProtocolDate = v) } }
    }
  }
}

/** Legal timing is shown where the handover date is entered, not as a detached utility. */
@Composable
private fun HandoverDeadline(p: Protocol, s: Str) {
  if (p.kind == Kind.Entry) {
    Panel(label = s.clockEntryTitle) {
      Text(s.clockEntryBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
    }
    return
  }

  Panel(label = s.clockExitTitle, info = s.tipClocks()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(s.clockExitBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      val returned = runCatching { LocalDate.parse(p.handoverDate) }.getOrNull()
      if (returned != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(Modifier.weight(1f)) {
            Text(s.safeBy, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            Text(
              Clocks.exitNoticeSafeBy(returned).toString(),
              fontFamily = FontFamily.Monospace,
              fontSize = 22.sp,
              color = Limen.colors.ink,
            )
          }
          Column(Modifier.weight(1f)) {
            Text(s.latestBy, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            Text(
              Clocks.exitNoticeLatest(returned).toString(),
              fontFamily = FontFamily.Monospace,
              fontSize = 22.sp,
              color = Limen.colors.ink,
            )
          }
        }
      }
    }
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
      if (party.role == Role.Witness || party.role == Role.Expert || party.role == Role.NextTenant) {
        IconKey(ButtonGlyph.Delete, s.delete, destructive = true) {
          vm.edit { it.copy(parties = it.parties.filterIndexed { j, _ -> j != index }) }
        }
      }
    }
    Field(s.role(party.role), party.name) { v ->
      vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(name = v) else q }) }
    }
    if (party.role == Role.Landlord) {
      Field(s.representedBy, party.representedBy) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(representedBy = v) else q }) }
      }
    }
    if (party.role == Role.Tenant && p.kind == Kind.Exit) {
      Field(s.newAddress, party.address) { v ->
        vm.edit { it.copy(parties = it.parties.mapIndexed { j, q -> if (j == index) q.copy(address = v) else q }) }
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
        Field(s.monthlyRent, p.contract.monthlyRentChf, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(monthlyRentChf = v)) }
        }
        Field(s.serviceCharges, p.contract.serviceChargesChf, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(serviceChargesChf = v)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.tenancyStart, p.contract.startDate, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(startDate = v)) }
        }
        Field(s.tenancyEnd, p.contract.endDate, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(contract = it.contract.copy(endDate = v)) }
        }
      }
      Field(s.noticeDate, p.contract.noticeDate) { v ->
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
      PillRow(DepositKind.entries.map { it to s.depositKind(it) }, p.deposit.kind) { k ->
        vm.edit { it.copy(deposit = it.deposit.copy(kind = k)) }
      }
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
                Field(s.depositAmount, p.deposit.amountChf, Modifier.weight(1f)) { v ->
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
                Field(s.depositAmount, p.deposit.amountChf, Modifier.weight(1f)) { v ->
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
      Row(verticalAlignment = Alignment.CenterVertically) {
        SmallKey(s.compareKey, Modifier.weight(1f)) { vm.setDepositCompare(true) }
        InfoTip(s.tipCompare())
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
        Field(s.meterElec1, p.meters.electricity1, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(electricity1 = v)) }
        }
        Field(s.meterElec2, p.meters.electricity2, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(electricity2 = v)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.meterGas, p.meters.gas, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(gas = v)) }
        }
        Field(s.meterWater, p.meters.water, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(water = v)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.meterHotWater, p.meters.hotWater, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(hotWater = v)) }
        }
        Field(s.meterOil, p.meters.heatingOil, Modifier.weight(1f)) { v ->
          vm.edit { it.copy(meters = it.meters.copy(heatingOil = v)) }
        }
      }
    }
  }
}

@Composable
private fun KeysCard(vm: LimenViewModel, s: Str, p: Protocol, onRemove: () -> Unit) {
  var adding by remember { mutableStateOf(false) }
  var otherLabel by remember { mutableStateOf("") }

  Panel(label = s.keysLabel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CardHeader(s, onRemove)
      p.keys.forEachIndexed { i, key ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (key.kind == KeyKind.Other && key.label.isNotBlank()) key.label else s.keyKind(key.kind),
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
            )
          }
          Stepper(key.count) { c ->
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

      SmallKey(s.addKeyKind, Modifier.fillMaxWidth()) { adding = !adding }
      if (adding) {
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

      Toggle(s.selfMadeKeys, p.selfMadeKeys, Modifier.fillMaxWidth()) {
        vm.edit { it.copy(selfMadeKeys = !it.selfMadeKeys) }
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
      Field(s.chargesBy, p.chargesStatementBy) { v -> vm.edit { it.copy(chargesStatementBy = v) } }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Toggle(s.oneSided, p.oneSided, Modifier.weight(1f)) {
          vm.edit { it.copy(oneSided = !it.oneSided) }
        }
        InfoTip(s.tipOneSided())
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
        Text(
          candidate.label,
          style = MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
          maxLines = 2,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
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
        Text("▾", style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
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
            { Text("✓", color = Limen.colors.inkDim) }
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
