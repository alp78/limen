package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.CautionProvider
import dev.verso.limen.data.Defect
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.Grade
import dev.verso.limen.data.GradeStyle
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.TenantResponse
import dev.verso.limen.theme.Limen

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

/** The flow's own masthead: the way out, the station lights, the theme. */
@Composable
private fun FlowMast(vm: LimenViewModel, s: Str, p: Protocol) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      "‹",
      fontFamily = FontFamily.Monospace,
      fontSize = 24.sp,
      color = Limen.colors.ink,
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable { vm.flowBack() }
        .padding(horizontal = 12.dp, vertical = 2.dp),
    )
    Spacer(Modifier.width(4.dp))
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
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Panel(label = s.protocolKind) {
      PillRow(
        listOf(Kind.Entry to s.entry, Kind.Exit to s.exit),
        p.kind,
      ) { k -> vm.edit { it.copy(kind = k) } }
    }

    // The grading canon. It is set from the language when the protocol is created, so this
    // is a correction and not a question: it decides what one tap on a room item does for
    // the rest of the walk, and the tip says which region writes which.
    Panel(label = s.styleLabel, info = s.tipStyle()) {
      PillRow(
        listOf(GradeStyle.Binary to s.styleBinary, GradeStyle.Grades to s.styleGrades),
        p.style,
      ) { st -> vm.edit { it.copy(style = st) } }
    }

    Panel(label = s.objectLabel) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.address, p.address) { v -> vm.edit { it.copy(address = v) } }
        Field(s.place, p.place) { v -> vm.edit { it.copy(place = v) } }
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
        Text(
          "×",
          fontFamily = FontFamily.Monospace,
          fontSize = 16.sp,
          color = Limen.colors.inkDim,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
              vm.edit { it.copy(parties = it.parties.filterIndexed { j, _ -> j != index }) }
            }
            .padding(horizontal = 8.dp),
        )
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

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      p.rooms.forEachIndexed { i, r ->
        Chip(r.name.ifBlank { s.room(r.kind) }, active = i == vm.roomIndex) { vm.selectRoom(i) }
      }
      Chip("+") { addingRoom = !addingRoom }
    }

    Spacer(Modifier.height(8.dp))
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

      if (p.kind == Kind.Exit) {
        Panel(label = s.entryBaseline) {
          val prior = entryRoom
          if (prior == null) {
            Text(s.noEntryBaseline, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
          } else {
            val flags = buildList {
              if (prior.freshPaintedWalls) add(s.freshWalls)
              if (prior.freshPaintedWoodwork) add(s.freshWood)
              if (prior.newFloor) add(s.newFloor)
            }
            Text(
              if (flags.isEmpty()) s.atEntry else "${s.atEntry}: ${flags.joinToString(" · ")}",
              style = MaterialTheme.typography.labelSmall,
              color = Limen.colors.inkDim,
            )
          }
        }
      }

      // What was fresh when the tenant arrived: the amortization clock starts here.
      if (p.kind == Kind.Entry && room.kind != RoomKind.Mailbox) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Toggle(s.freshWalls, room.freshPaintedWalls, Modifier.weight(1f), dense = true) {
            vm.edit { it.copy(rooms = it.rooms.map { r -> if (r.id == room.id) r.copy(freshPaintedWalls = !r.freshPaintedWalls) else r }) }
          }
          Toggle(s.newFloor, room.newFloor, Modifier.weight(1f), dense = true) {
            vm.edit { it.copy(rooms = it.rooms.map { r -> if (r.id == room.id) r.copy(newFloor = !r.newFloor) else r }) }
          }
        }
      }

      // The rows that carry a defect, counted once. Asked per row it was a scan of every
      // finding for every line, on every recomposition of the walk.
      val flagged = remember(p.defects, room.id) {
        p.defects.filter { it.roomId == room.id }.mapNotNull { it.item }.toSet()
      }
      room.items.forEachIndexed { i, item ->
        val entryItem = entryRoom?.items?.firstOrNull { prior ->
          prior.key == item.key && (item.key != ItemKey.Custom || prior.label == item.label)
        }
        val entryDefect = vm.entryBaseline?.defects?.any { defect ->
          defect.roomId == room.id && defect.item == item.key
        } == true
        ItemRow(
          label = if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key),
          state = s.gradeShort(item.grade),
          graded = item.grade != Grade.NotChecked,
          flagged = item.key in flagged,
          baseline = entryItem?.let { prior ->
            buildString {
              append(s.atEntry).append(": ").append(s.gradeShort(prior.grade))
              if (entryDefect) append(" · ").append(s.defects)
            }
          },
          onGrade = { vm.cycleGrade(room.id, i) },
          onDefect = { vm.openDefect(room.id, item.key) },
        )
      }

      // The room's own findings, under its rows.
      val roomDefects = p.defects.filter { it.roomId == room.id }
      if (roomDefects.isNotEmpty()) {
        Panel(label = s.defects) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            roomDefects.forEach { d ->
              DefectLine(vm, s, d)
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
        SmallKey(s.addDefect.uppercase(), Modifier.weight(1f)) { vm.openDefect(room.id, null) }
        if (vm.roomIndex < p.rooms.size - 1) {
          SmallKey(s.next, Modifier.weight(1f)) { vm.selectRoom(vm.roomIndex + 1) }
        } else {
          SmallKey(s.stepReview.uppercase(), Modifier.weight(1f)) { vm.goTo(FlowStep.Review) }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
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
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Limen.colors.plateRaised)
      .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(8.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
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
        fontSize = 15.sp,
        color = Limen.colors.ink,
        modifier = Modifier.padding(end = 8.dp),
      )
    }
    // The state chip: one tap ticks or walks the grade ladder.
    Chip(state, active = graded, onPress = onGrade)
    Spacer(Modifier.width(6.dp))
    // The defect affordance, on every row, because a defect is a first-class record.
    Chip("+", onPress = onDefect)
  }
}

/** One finding, one line: where it hangs, what it says, and the way into the editor. */
@Composable
private fun DefectLine(vm: LimenViewModel, s: Str, d: Defect) {
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
      d.item?.let { parts.add(s.item(it)) }
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
    Panel(label = "${s.defects} (${p.defects.size})", info = s.tipCharged()) {
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
                d.item?.let { parts.add(s.item(it)) }
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
