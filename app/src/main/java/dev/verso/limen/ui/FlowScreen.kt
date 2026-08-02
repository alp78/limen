package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.Appliance
import dev.verso.limen.data.CautionProvider
import dev.verso.limen.data.Defect
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.Furniture
import dev.verso.limen.data.Grade
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.RoomItem
import dev.verso.limen.data.SwissAddressCandidate
import dev.verso.limen.data.SwissAddressLookup
import dev.verso.limen.data.TenantResponse
import dev.verso.limen.R
import dev.verso.limen.sense.Clocks
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * The protocol flow: one guided walk from the header data to the exported PDF. A stepper,
 * not faces: the way through is forward, the way back is [LimenViewModel.flowBack], and
 * the draft is on disk after every keystroke, because the camera app kills this process
 * whenever it pleases.
 */
@Composable
fun FlowScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  val p = vm.working ?: return
  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
      FlowMast(vm, s, p)
      Spacer(Modifier.height(8.dp))
      // The step's one instruction, at reading size, before any control.
      StepLead(s.stepLead(vm.flowStep))
      Spacer(Modifier.height(8.dp))
      when (vm.flowStep) {
        FlowStep.Setup -> SetupStep(vm, s, p)
        FlowStep.Rooms -> RoomsStep(vm, s, p)
        FlowStep.Extras -> ExtrasStep(vm, s, p)
        FlowStep.Review -> ReviewStep(vm, s, p)
        FlowStep.Sign -> SignStep(vm, s, p)
        FlowStep.Export -> ExportStep(vm, s, p)
      }
    }
    if (vm.editingDefectId != null) {
      DefectEditor(vm, s, p, vm.editingDefectId!!)
    }
    if (vm.showDepositCompare) {
      DepositCompareOverlay(vm, s)
    }
  }
}

// ── Flat extras ───────────────────────────────────────────────────────────────────────

/** Everything supplied with the flat is selected once here, never attached to an arbitrary room. */
@Composable
private fun ExtrasStep(vm: LimenViewModel, s: Str, p: Protocol) {
  var applianceDraft by remember { mutableStateOf("") }
  var furnitureDraft by remember { mutableStateOf("") }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Panel(label = s.appliances) {
      Text(s.appliancesHint, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
      Spacer(Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Appliance.entries.forEach { appliance ->
          Chip(s.appliance(appliance), active = appliance in p.extras.appliances, dim = false) {
            vm.edit { record ->
              val selected = record.extras.appliances
              record.copy(
                extras = record.extras.copy(
                  appliances = if (appliance in selected) selected - appliance else selected + appliance,
                ),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      CustomExtrasInput(
        label = s.addCustomAppliance,
        addLabel = s.add,
        entries = p.extras.customAppliances,
        value = applianceDraft,
        onValueChange = { applianceDraft = it },
        onAdd = {
          val entry = applianceDraft.trim()
          if (entry.isNotEmpty() && p.extras.customAppliances.none { it.equals(entry, ignoreCase = true) }) {
            vm.edit { record ->
              record.copy(extras = record.extras.copy(customAppliances = record.extras.customAppliances + entry))
            }
          }
          applianceDraft = ""
        },
        onRemove = { entry ->
          vm.edit { record ->
            record.copy(extras = record.extras.copy(customAppliances = record.extras.customAppliances - entry))
          }
        },
      )
    }

    Panel(label = s.furniture) {
      Text(s.furnitureHint, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
      Spacer(Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Furniture.entries.forEach { furniture ->
          Chip(s.furniture(furniture), active = furniture in p.extras.furniture, dim = false) {
            vm.edit { record ->
              val selected = record.extras.furniture
              record.copy(
                extras = record.extras.copy(
                  furniture = if (furniture in selected) selected - furniture else selected + furniture,
                ),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      CustomExtrasInput(
        label = s.addCustomFurniture,
        addLabel = s.add,
        entries = p.extras.customFurniture,
        value = furnitureDraft,
        onValueChange = { furnitureDraft = it },
        onAdd = {
          val entry = furnitureDraft.trim()
          if (entry.isNotEmpty() && p.extras.customFurniture.none { it.equals(entry, ignoreCase = true) }) {
            vm.edit { record ->
              record.copy(extras = record.extras.copy(customFurniture = record.extras.customFurniture + entry))
            }
          }
          furnitureDraft = ""
        },
        onRemove = { entry ->
          vm.edit { record ->
            record.copy(extras = record.extras.copy(customFurniture = record.extras.customFurniture - entry))
          }
        },
      )
    }

    BigKey(s.stepReview.uppercase(), active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Review) }
    Spacer(Modifier.height(12.dp))
  }
}

/** Adds a named supplied extra and makes an existing custom choice removable in the same place. */
@Composable
private fun CustomExtrasInput(
  label: String,
  addLabel: String,
  entries: List<String>,
  value: String,
  onValueChange: (String) -> Unit,
  onAdd: () -> Unit,
  onRemove: (String) -> Unit,
) {
  if (entries.isNotEmpty()) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      entries.forEach { entry ->
        Chip("$entry  ×", dim = false, destructive = true) { onRemove(entry) }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
  Field(label, value, hint = label, onChange = onValueChange)
  Spacer(Modifier.height(6.dp))
  SmallKey(addLabel, Modifier.fillMaxWidth(), enabled = value.trim().isNotEmpty(), onPress = onAdd)
}

/** The flow's own masthead: the way out, the station lights, the theme. */
@Composable
private fun FlowMast(vm: LimenViewModel, s: Str, p: Protocol) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconKey(ButtonGlyph.Back, s.back) { vm.flowBack() }
    Spacer(Modifier.width(6.dp))
    LimenMark(size = 42.dp)
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(
        (if (p.kind == Kind.Entry) s.entry else s.exit).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Limen.colors.ink,
      )
      Text(
        p.address.ifBlank { s.noAddress },
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        color = Limen.colors.inkDim,
      )
    }
    ThemeToggle(vm.darkTheme, vm::toggleTheme)
  }
  Spacer(Modifier.height(8.dp))
  val stations = listOf(
    FlowStep.Setup to s.stepSetup,
    FlowStep.Rooms to s.stepRooms,
    FlowStep.Extras to s.stepExtras,
    FlowStep.Review to s.stepReview,
    FlowStep.Sign to s.stepSign,
    FlowStep.Export to s.stepExport,
  )
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    stations.forEachIndexed { i, (step, label) ->
      Chip(
        "${i + 1} · $label",
        active = vm.flowStep == step,
        enabled = vm.flowStep != step,
      ) { vm.goTo(step) }
    }
  }
}

// ── Setup ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupStep(vm: LimenViewModel, s: Str, p: Protocol) {
  var objectSearching by remember(p.id) { mutableStateOf(false) }
  var objectMatches by remember(p.id) { mutableStateOf(emptyList<SwissAddressCandidate>()) }
  var focusedObjectFields by remember(p.id) { mutableStateOf(emptySet<String>()) }
  var objectAddressVerified by remember(p.id) { mutableStateOf(p.address.isNotBlank() && p.place.isNotBlank()) }
  val objectAddressFocused = focusedObjectFields.isNotEmpty()
  val objectAddressQuery = listOf(p.address, p.place)
    .filter { it.isNotBlank() }
    .joinToString(" ")

  LaunchedEffect(p.id, objectAddressQuery, objectAddressFocused, objectAddressVerified) {
    if (!objectAddressFocused || objectAddressVerified || objectAddressQuery.trim().length < 3) {
      objectMatches = emptyList()
      objectSearching = false
      return@LaunchedEffect
    }
    delay(350)
    objectSearching = true
    objectMatches = SwissAddressLookup.search(objectAddressQuery)
    objectSearching = false
  }

  fun setObjectFieldFocused(field: String, focused: Boolean) {
    focusedObjectFields = if (focused) focusedObjectFields + field else focusedObjectFields - field
  }

  fun editObjectAddress(transform: (Protocol) -> Protocol) {
    objectAddressVerified = false
    objectMatches = emptyList()
    objectSearching = false
    vm.edit(transform)
  }

  fun selectObjectAddress(candidate: SwissAddressCandidate) {
    vm.edit {
      it.copy(
        address = listOf(candidate.street, candidate.flatNumber).filter { part -> part.isNotBlank() }.joinToString(" "),
        place = listOf(candidate.postalCode, candidate.city).filter { part -> part.isNotBlank() }.joinToString(" "),
      )
    }
    objectMatches = emptyList()
    objectAddressVerified = true
  }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Panel(label = s.objectLabel) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
          s.address,
          p.address,
          hint = s.addressLookupHint,
          onFocusChanged = { setObjectFieldFocused("address", it) },
        ) { value -> editObjectAddress { it.copy(address = value) } }
        Field(s.place, p.place, onFocusChanged = { setObjectFieldFocused("place", it) }) { value ->
          editObjectAddress { it.copy(place = value) }
        }
        if (objectAddressFocused && !objectAddressVerified && objectSearching) {
          Text(s.addressSearching, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
        }
        if (objectAddressFocused && !objectAddressVerified) OfficialAddressMatches(objectMatches, ::selectObjectAddress)
        Field(s.objectDetail, p.objectDetail) { v -> vm.edit { it.copy(objectDetail = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.handoverDate, p.handoverDate, Modifier.weight(1.4f)) { v ->
            vm.edit { it.copy(handoverDate = v) }
          }
          Field(s.handoverTime, p.handoverTime, Modifier.weight(1f)) { v ->
            vm.edit { it.copy(handoverTime = v) }
          }
        }
      }
    }

    HandoverDeadline(p, s)

    Panel(label = s.parties) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        p.parties.forEachIndexed { i, party ->
          // The two mandatory roles are always there and need no announcing; a role that a
          // key just added does.
          if (i < 2) PartyBlock(vm, s, p, i, party)
          else Revealed(party.role) { PartyBlock(vm, s, p, i, party) }
        }
        // The optional roles arrive on demand; the two mandatory ones are always there.
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

    Panel(label = s.deposit, info = s.tipDeposit()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillRow(
          DepositKind.entries.map { it to s.depositKind(it) },
          p.deposit.kind,
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

    Panel(label = s.meters) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Panel(label = s.keysLabel) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.keys.forEachIndexed { i, key ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (key.kind == dev.verso.limen.data.KeyKind.Other && key.label.isNotBlank()) key.label
              else s.keyKind(key.kind),
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
              modifier = Modifier.weight(1f),
            )
            Stepper(key.count) { c ->
              vm.edit {
                it.copy(keys = it.keys.mapIndexed { j, k -> if (j == i) k.copy(count = c) else k })
              }
            }
          }
        }
        Toggle(s.selfMadeKeys, p.selfMadeKeys, Modifier.fillMaxWidth()) {
          vm.edit { it.copy(selfMadeKeys = !it.selfMadeKeys) }
        }
      }
    }

    BigKey(s.next, active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Rooms) }
    Spacer(Modifier.height(12.dp))
  }
}

/** The handed-over flat is resolved independently from any participant contact address. */
@Composable
private fun OfficialAddressMatches(
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

@Composable
private fun CautionProviderPicker(
  s: Str,
  storedName: String,
  onSelect: (CautionProvider) -> Unit,
) {
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

// ── Rooms ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsStep(vm: LimenViewModel, s: Str, p: Protocol) {
  if (p.rooms.isEmpty()) return
  val room = p.rooms[vm.roomIndex.coerceIn(0, p.rooms.size - 1)]
  val entryRoom = vm.entryBaseline?.rooms?.firstOrNull { it.id == room.id }
  var addingRoom by remember { mutableStateOf(false) }
  var addingItem by remember(room.id) { mutableStateOf(false) }
  var itemName by remember(room.id) { mutableStateOf("") }
  var confirmingReset by remember(room.id) { mutableStateOf(false) }

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      p.rooms.forEachIndexed { i, r ->
        RoomChip(
          label = r.name.ifBlank { s.room(r.kind) },
          active = i == vm.roomIndex,
          hasDefects = p.defects.any { it.roomId == r.id },
        ) { vm.selectRoom(i) }
      }
      IconKey(ButtonGlyph.Add, s.addRoom) { addingRoom = !addingRoom }
    }

    Spacer(Modifier.height(8.dp))
    key(room.id) {
    Column(
      Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (addingRoom) {
        Panel(label = s.addRoom) {
          Revealed(addingRoom) {
            Row(
              Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              listOf(
                RoomKind.Room, RoomKind.Bedroom, RoomKind.SepWC, RoomKind.Bathroom,
                RoomKind.Attic, RoomKind.Garage, RoomKind.LaundryRoom, RoomKind.HobbyRoom,
                RoomKind.Custom,
              ).forEach { kind ->
                Chip(s.room(kind)) {
                  vm.addRoom(kind)
                  addingRoom = false
                }
              }
            }
          }
        }
      }

      room.items.forEachIndexed { i, item ->
        val itemDefect = p.defects.firstOrNull { defect ->
          defect.roomId == room.id && defect.belongsTo(item)
        }
        val entryItem = entryRoom?.items?.firstOrNull { prior ->
          prior.key == item.key &&
            (item.key != ItemKey.Custom || prior.label == item.label) &&
            (prior.itemId.isBlank() || item.itemId.isBlank() || prior.itemId == item.itemId)
        }
        val entryDefect = vm.entryBaseline?.defects?.any { defect ->
          defect.roomId == room.id && defect.belongsTo(item)
        } == true
        key(item.itemId.ifBlank { "$i-${item.key}-${item.label}" }) {
          DismissibleItemRow(onRemove = { vm.removeRoomItem(room.id, i) }) {
            ItemRow(
              label = if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key),
              state = s.grade(item.grade),
              graded = item.grade != Grade.NotChecked,
              flagged = itemDefect != null,
              baseline = entryItem?.let { prior ->
                buildString {
                  append(s.atEntry).append(": ").append(s.gradeShort(prior.grade))
                  if (entryDefect) append(" · ").append(s.defects)
                }
              },
              onGrade = { vm.cycleGrade(room.id, i) },
              onDefect = { vm.openDefect(room.id, item.key, item.itemId) },
              onEditDefect = { itemDefect?.let { vm.editDefect(it.id) } },
              editDefectLabel = s.editDefect,
            )
          }
        }
      }

      RoomActionKey(
        label = s.addRoomItem.uppercase(),
        fill = Limen.colors.positive,
        outline = Limen.colors.positive,
        text = Limen.colors.onPositive,
        glyph = ButtonGlyph.Add,
      ) { addingItem = !addingItem }

      if (addingItem) {
        Panel(label = s.addRoomItem) {
          Field(s.roomItemName, itemName) { itemName = it }
          Spacer(Modifier.height(8.dp))
          RoomActionKey(
            label = s.addRoomItem.uppercase(),
            fill = Limen.colors.positive,
            outline = Limen.colors.positive,
          text = Limen.colors.onPositive,
          glyph = ButtonGlyph.Add,
          ) {
            vm.addRoomItem(room.id, itemName)
            itemName = ""
            addingItem = false
          }
        }
      }

      // The room's own findings, under its rows.
      val roomDefects = p.defects.filter { it.roomId == room.id }
      if (roomDefects.isNotEmpty()) {
        Panel(label = s.defects, warning = true) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            roomDefects.forEach { d ->
              DefectLine(vm, s, p, d)
            }
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(s.roomKeys, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          entryRoom?.let { prior ->
            Text(
              "${s.atEntry}: ${prior.keysCount}",
              style = MaterialTheme.typography.labelSmall,
              color = Limen.colors.inkDim,
            )
          }
        }
        Stepper(room.keysCount) { c ->
          vm.edit { it.copy(rooms = it.rooms.map { r -> if (r.id == room.id) r.copy(keysCount = c) else r }) }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoomActionKey(
          label = s.resetRoom,
          fill = Limen.colors.danger,
          outline = Limen.colors.dangerOutline,
          text = Limen.colors.dangerOutline,
          glyph = ButtonGlyph.Clear,
          modifier = Modifier.weight(1f),
        ) { confirmingReset = true }
        if (vm.roomIndex < p.rooms.size - 1) {
          IconKey(ButtonGlyph.Next, s.next, Modifier.weight(1f)) { vm.selectRoom(vm.roomIndex + 1) }
        } else {
          SmallKey(s.stepExtras.uppercase(), Modifier.weight(1f)) { vm.goTo(FlowStep.Extras) }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
    }
  }
  if (confirmingReset) {
    ResetRoomDialog(
      s = s,
      onConfirm = {
        vm.resetRoom(room.id)
        confirmingReset = false
      },
      onDismiss = { confirmingReset = false },
    )
  }
}

private fun Defect.belongsTo(item: RoomItem): Boolean =
  this.item == item.key &&
    (itemId.isBlank() || item.itemId.isBlank() || itemId == item.itemId)

private fun Protocol.defectItemLabel(defect: Defect, s: Str): String? {
  val key = defect.item ?: return null
  val item = rooms.firstOrNull { it.id == defect.roomId }?.items?.firstOrNull { it.key == key && defect.belongsTo(it) }
  return if (item?.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(key)
}

/** The only prominent room action is adding a row or deliberately resetting the room. */
@Composable
private fun RoomActionKey(
  label: String,
  fill: Color,
  outline: Color,
  text: Color,
  glyph: ButtonGlyph? = null,
  modifier: Modifier = Modifier.fillMaxWidth(),
  onPress: () -> Unit,
) {
  KeySurface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    fillColor = fill,
    outlineColor = outline,
    onPress = onPress,
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      glyph?.let {
        Icon(it.vector(), contentDescription = null, tint = text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(
        label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        color = text,
      )
    }
  }
}

@Composable
private fun ResetRoomDialog(s: Str, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Limen.colors.plateRaised,
    titleContentColor = Limen.colors.ink,
    textContentColor = Limen.colors.inkDim,
    title = { Text(s.resetRoomQuestion, fontWeight = FontWeight.Bold) },
    text = { Text(s.resetRoomBody, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      RoomActionKey(
        label = s.yes,
        fill = Limen.colors.positive,
        outline = Limen.colors.positive,
        text = Limen.colors.onPositive,
        modifier = Modifier.width(88.dp),
        onPress = onConfirm,
      )
    },
    dismissButton = {
      RoomActionKey(
        label = s.no,
        fill = Limen.colors.danger,
        outline = Limen.colors.dangerOutline,
        text = Limen.colors.dangerOutline,
        modifier = Modifier.width(88.dp),
        onPress = onDismiss,
      )
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleItemRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
  val state = rememberSwipeToDismissBoxState(
    positionalThreshold = { distance -> distance * 0.35f },
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) {
        onRemove()
        true
      } else {
        false
      }
    },
  )
  SwipeToDismissBox(
    state = state,
    backgroundContent = {
      Box(
        Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(10.dp))
          .background(Limen.colors.dangerOutline)
          .padding(end = 18.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        TrashGlyph(Modifier.size(24.dp))
      }
    },
    enableDismissFromStartToEnd = false,
    modifier = Modifier.fillMaxWidth(),
  ) {
    content()
  }
}

/** Drawn to match the app's line icons and to remain crisp against the red swipe reveal. */
@Composable
private fun TrashGlyph(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val stroke = size.minDimension * 0.12f
    val left = size.width * 0.28f
    val right = size.width * 0.72f
    val top = size.height * 0.32f
    val bottom = size.height * 0.78f
    drawLine(Color.White, Offset(size.width * 0.2f, top), Offset(size.width * 0.8f, top), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(size.width * 0.42f, size.height * 0.2f), Offset(size.width * 0.58f, size.height * 0.2f), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(left, top), Offset(left, bottom), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(right, top), Offset(right, bottom), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(left, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
  }
}

/**
 * One line of the walk. It takes plain values rather than the item itself, so that grading
 * one line leaves the other nineteen untouched: Compose can skip a row whose arguments did
 * not move, and on a room of thirty positions that is the difference between redrawing one
 * and redrawing all of them.
 */
@Composable
private fun ItemRow(
  label: String,
  state: String,
  graded: Boolean,
  flagged: Boolean,
  baseline: String?,
  onGrade: () -> Unit,
  onDefect: () -> Unit,
  onEditDefect: () -> Unit,
  editDefectLabel: String,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(if (flagged) Limen.colors.warning else Limen.colors.plateRaised)
      .border(
        1.5.dp,
        if (flagged) Limen.colors.warningOutline else Limen.colors.actionOutline,
        RoundedCornerShape(10.dp),
      )
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      baseline?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      }
    }
    if (flagged) {
      Text(
        "!",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = Limen.colors.dangerOutline,
        modifier = Modifier.padding(end = 8.dp),
      )
      EditDefectKey(editDefectLabel, onEditDefect)
      Spacer(Modifier.width(6.dp))
    }
    // The full label makes the first, uninspected state explicit instead of a bare dot.
    Chip(state, active = graded, onPress = onGrade)
    Spacer(Modifier.width(6.dp))
    // The defect affordance, on every row, because a defect is a first-class record.
    IconKey(ButtonGlyph.Add, "Record defect", onPress = onDefect)
  }
}

/** An explicit edit affordance prevents an existing finding from turning the whole row into a button. */
@Composable
private fun EditDefectKey(contentDescription: String, onPress: () -> Unit) {
  KeySurface(
    modifier = Modifier.width(38.dp).height(38.dp),
    shape = RoundedCornerShape(10.dp),
    onPress = onPress,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_defect_edit),
      contentDescription = contentDescription,
      tint = Limen.colors.ink,
      modifier = Modifier.width(20.dp).height(20.dp),
    )
  }
}

/** A room stays visible in the walk, with an amber warning whenever it has a finding. */
@Composable
private fun RoomChip(label: String, active: Boolean, hasDefects: Boolean, onPress: () -> Unit) {
  val fill = when {
    hasDefects -> Limen.colors.warning
    active -> Limen.colors.action
    else -> Limen.colors.input
  }
  val outline = when {
    hasDefects -> Limen.colors.warningOutline
    active -> Limen.colors.action
    else -> Limen.colors.actionOutline
  }
  KeySurface(
    shape = RoundedCornerShape(10.dp),
    fillColor = fill,
    outlineColor = outline,
    onPress = onPress,
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        maxLines = 1,
        color = if (active && !hasDefects) Limen.colors.onInk else Limen.colors.ink,
      )
      if (hasDefects) {
        Spacer(Modifier.width(4.dp))
        Text(
          "!",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
          color = Limen.colors.dangerOutline,
        )
      }
    }
  }
}

/** One finding, one line: where it hangs, what it says, and the way into the editor. */
@Composable
private fun DefectLine(vm: LimenViewModel, s: Str, p: Protocol, d: Defect) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .clickable { vm.editDefect(d.id) }
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        d.text.ifBlank { s.defectText },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        color = Limen.colors.ink,
      )
      val parts = mutableListOf<String>()
      p.defectItemLabel(d, s)?.let(parts::add)
      if (d.photos.isNotEmpty()) parts.add("${s.photos} ${d.photos.size}")
      if (d.chargedToTenant) parts.add(s.chargedToTenant)
      if (d.tenantResponse != TenantResponse.NotStated) parts.add(s.tenantResponse(d.tenantResponse))
      if (parts.isNotEmpty()) {
        Text(
          parts.joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = Limen.colors.inkDim,
        )
      }
    }
    Text("›", fontSize = 18.sp, color = Limen.colors.inkDim)
  }
}

// ── Review ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewStep(vm: LimenViewModel, s: Str, p: Protocol) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Panel(
      label = "${s.defects} (${p.defects.size})",
      info = s.tipCharged(),
      warning = p.defects.isNotEmpty(),
    ) {
      if (p.defects.isEmpty()) {
        Text(s.grade(Grade.Ok), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          p.defects.forEachIndexed { i, d ->
            val room = p.rooms.firstOrNull { it.id == d.roomId }
            Row(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.editDefect(d.id) },
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                "${i + 1}.",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Limen.colors.inkDim,
                modifier = Modifier.width(30.dp),
              )
              Column(Modifier.weight(1f)) {
                Text(
                  d.text.ifBlank { "…" },
                  style = MaterialTheme.typography.bodyMedium,
                  color = Limen.colors.ink,
                )
                val where = room?.let { r -> r.name.ifBlank { s.room(r.kind) } } ?: ""
                val parts = mutableListOf(where)
                p.defectItemLabel(d, s)?.let(parts::add)
                if (d.chargedToTenant) parts.add(s.chargedToTenant)
                if (d.tenantResponse != TenantResponse.NotStated) parts.add(s.tenantResponse(d.tenantResponse))
                if (d.tenantComment.isNotBlank()) parts.add(d.tenantComment)
                Text(
                  parts.filter { it.isNotBlank() }.joinToString(" · "),
                  style = MaterialTheme.typography.labelSmall,
                  color = Limen.colors.inkDim,
                )
              }
            }
          }
        }
      }
    }

    Panel(label = s.remarks) {
      Field(s.remarks, p.remarks, singleLine = false) { v -> vm.edit { it.copy(remarks = v) } }
    }

    Panel(label = s.chargesBy) {
      Field(s.chargesBy, p.chargesStatementBy) { v -> vm.edit { it.copy(chargesStatementBy = v) } }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Toggle(s.oneSided, p.oneSided, Modifier.weight(1f)) {
        vm.edit { it.copy(oneSided = !it.oneSided) }
      }
      InfoTip(s.tipOneSided())
    }

    BigKey(s.stepSign.uppercase(), active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Sign) }
    Spacer(Modifier.height(12.dp))
  }
}

// The Sign and Export stations live in FlowSignExport.kt.
