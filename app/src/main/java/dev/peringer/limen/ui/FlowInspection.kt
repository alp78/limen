package dev.peringer.limen.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.data.ItemKey
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Room
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.Templates
import dev.peringer.limen.data.hasLegacyGrid
import dev.peringer.limen.theme.Limen

/**
 * The inspection: a grid of rooms and, inside one, its findings.
 *
 * No forced walking order. People go through a flat in the order it is built, explicitly
 * confirm each room they inspect, and add as many findings as its condition requires.
 */
@Composable
internal fun InspectionStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val open = vm.openRoomId?.let { id -> p.rooms.firstOrNull { it.id == id } }
  if (open == null) RoomGrid(vm, s, p) else key(open.id) { RoomDetail(vm, s, p, open) }
}

// ── The grid of rooms ──────────────────────────────────────────────────────────────────

@Composable
private fun RoomGrid(vm: LimenViewModel, s: Str, p: Protocol) {
  val locked = LocalRecordLocked.current
  var addingRoom by remember { mutableStateOf(false) }
  var customName by remember { mutableStateOf("") }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!locked && p.rooms.isEmpty() && !p.roomSetupComplete) {
      InitialRoomSetup(vm, s, p)
      Spacer(Modifier.height(12.dp))
      return@Column
    }

    FlowRow(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      p.rooms.forEach { room ->
        RoomKey(
          s = s,
          label = room.name.ifBlank { s.room(room.kind) },
          defects = p.defects.count { it.roomId == room.id },
          inspected = room.inspected,
        ) { vm.openRoom(room.id) }
      }
    }

    if (!locked) {
      RoomActionKey(
        label = s.addRoom,
        fill = Limen.colors.positive,
        outline = Limen.colors.positive,
        text = Limen.colors.onPositive,
        glyph = ButtonGlyph.Add,
      ) { addingRoom = !addingRoom }
    }

    if (addingRoom && !locked) {
      Panel(label = s.addRoom) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // Every kind, not a chosen nine: a flat that has a wine cellar and no balcony
            // should not have to call the cellar a balcony.
            RoomKind.entries.filterNot { it == RoomKind.Custom }.forEach { kind ->
              Chip(s.room(kind)) {
                vm.addRoom(kind)
                addingRoom = false
              }
            }
          }
          // A room the canon has no word for: the freezing room, the darkroom, the workshop.
          Field(s.roomName, customName, hint = s.room(RoomKind.Custom)) { customName = it }
          SmallKey(
            s.add,
            Modifier.fillMaxWidth(),
            enabled = customName.trim().isNotEmpty(),
          ) {
            vm.addRoom(RoomKind.Custom, customName)
            customName = ""
            addingRoom = false
          }
        }
      }
    }

    SmallKey(s.stepObjects.uppercase(), Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Objects) }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun InitialRoomSetup(vm: LimenViewModel, s: Str, p: Protocol) {
  var selected by remember(p.id) { mutableStateOf(emptySet<RoomKind>()) }
  Panel(label = s.chooseRoomsPresent) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(s.chooseRoomsPresentBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Templates.defaultRooms.forEach { kind ->
          val active = kind in selected
          Chip(
            label = s.room(kind),
            active = active,
            selectionState = active,
          ) {
            selected = if (active) selected - kind else selected + kind
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallKey(s.selectAll, Modifier.weight(1f)) { selected = Templates.defaultRooms.toSet() }
        SmallKey(s.clear, Modifier.weight(1f), enabled = selected.isNotEmpty()) { selected = emptySet() }
      }
      BigKey(
        s.useSelectedRooms,
        active = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        enabled = selected.isNotEmpty(),
      ) {
        vm.setInitialRooms(Templates.defaultRooms.filter { it in selected })
      }
    }
  }
}

/** A room on the grid: its name, and how many findings it holds, amber when it holds any. */
@Composable
private fun RoomKey(s: Str, label: String, defects: Int, inspected: Boolean, onPress: () -> Unit) {
  val flagged = defects > 0
  KeySurface(
    modifier = Modifier.width(164.dp),
    shape = RoundedCornerShape(12.dp),
    fillColor = if (flagged) Limen.colors.warning else Limen.colors.plateRaised,
    outlineColor = if (flagged) Limen.colors.warningOutline else Limen.colors.actionOutline,
    onPress = onPress,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
      Text(
        label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 2,
        color = Limen.colors.ink,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        when {
          flagged -> "! $defects${if (inspected) " · ✓" else ""}"
          inspected -> "✓ ${s.inspectedNoDefects}"
          else -> s.notInspected
        },
        fontFamily = FontFamily.Monospace,
        fontWeight = if (flagged) FontWeight.Bold else FontWeight.Normal,
        fontSize = 13.sp,
        color = when {
          flagged -> Limen.colors.dangerOutline
          inspected -> Limen.colors.seal
          else -> Limen.colors.inkDim
        },
      )
    }
  }
}

// ── One room ───────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomDetail(vm: LimenViewModel, s: Str, p: Protocol, room: Room) {
  val locked = LocalRecordLocked.current
  var confirmingClear by remember { mutableStateOf(false) }
  var confirmingRemove by remember { mutableStateOf(false) }
  val roomDefects = p.defects.filter { it.roomId == room.id }
  val entryRoom = vm.entryBaseline?.rooms?.firstOrNull { it.id == room.id }
  val entryDefects = vm.entryBaseline?.defects?.count { it.roomId == room.id } ?: 0

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Back on the left, where the other five back keys in the app are, and where the
    // masthead's own sits. On the right it was directly under that one and read as its twin.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconKey(ButtonGlyph.Back, s.back) { vm.closeRoom() }
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(
          room.name.ifBlank { s.room(room.kind) }.uppercase(),
          style = MaterialTheme.typography.titleMedium,
          color = Limen.colors.ink,
        )
        if (entryRoom != null && entryDefects > 0) {
          Text(
            "${s.atEntry}: ${s.countDefects(entryDefects)}",
            style = MaterialTheme.typography.labelSmall,
            color = Limen.colors.inkDim,
          )
        }
      }
    }

    // Only a room the canon has no word for needs naming; the rest are named already.
    if (room.kind == RoomKind.Custom) {
      Field(s.roomName, room.name) { v -> vm.renameRoom(room.id, v) }
    }

    Toggle(
      s.roomInspected,
      room.inspected,
      Modifier.fillMaxWidth(),
      enabled = !locked,
    ) { vm.setRoomInspected(room.id, !room.inspected) }

    if (!locked) ItemPicker(s, room.kind) { key, label -> vm.openDefect(room.id, key, label) }

    if (roomDefects.isEmpty()) {
      Text(
        if (room.inspected) s.inspectedNoDefects else s.roomClean,
        style = MaterialTheme.typography.bodyMedium,
        color = if (room.inspected) Limen.colors.seal else Limen.colors.inkDim,
      )
    } else {
      Panel(label = "${s.defects} (${roomDefects.size})", warning = true) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          roomDefects.forEach { d ->
            key(d.id) {
              if (locked) DefectLine(vm, s, p, d)
              else DismissibleItemRow(s, onRemove = { vm.removeDefect(d.id) }) {
                DefectLine(vm, s, p, d)
              }
            }
          }
        }
      }
    }

    // An older record still shows the grid it was signed with, read only: those grades are
    // part of what the parties agreed to and this version has no way to change them.
    if (room.hasLegacyGrid()) {
      Panel(label = s.atEntry) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          room.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(
                if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key),
                style = MaterialTheme.typography.labelSmall,
                color = Limen.colors.inkDim,
                modifier = Modifier.weight(1f),
              )
              Text(
                s.gradeShort(item.grade),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Limen.colors.inkDim,
              )
            }
          }
        }
      }
    }

    if (!locked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RoomActionKey(
        label = s.clearRoom,
        fill = Limen.colors.danger,
        outline = Limen.colors.dangerOutline,
        text = Limen.colors.dangerOutline,
        glyph = ButtonGlyph.Clear,
        modifier = Modifier.weight(1f),
      ) { confirmingClear = true }
      RoomActionKey(
        label = s.removeRoom,
        fill = Limen.colors.danger,
        outline = Limen.colors.dangerOutline,
        text = Limen.colors.dangerOutline,
        glyph = ButtonGlyph.Delete,
        modifier = Modifier.weight(1f),
      ) { confirmingRemove = true }
    }
    Spacer(Modifier.height(12.dp))
  }

  if (confirmingClear) {
    ConfirmDialog(
      s = s,
      body = s.clearRoomBody,
      onConfirm = {
        vm.clearRoom(room.id)
        confirmingClear = false
      },
      onDismiss = { confirmingClear = false },
    )
  }
  if (confirmingRemove) {
    ConfirmDialog(
      s = s,
      body = s.removeRoomBody,
      onConfirm = {
        vm.removeRoom(room.id)
        confirmingRemove = false
      },
      onDismiss = { confirmingRemove = false },
    )
  }
}

/**
 * The way a finding starts: pick what it is about, then write it.
 *
 * The list is this room's template, which is now a catalogue to choose from rather than a
 * checklist to work through. Anything it does not name is typed instead, so the vocabulary
 * never becomes the limit on what can be recorded.
 */
@Composable
private fun ItemPicker(s: Str, kind: RoomKind, onPick: (ItemKey, String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  var namingOther by remember { mutableStateOf(false) }
  var otherName by remember { mutableStateOf("") }
  val items = remember(kind) { Templates.itemsFor(kind) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.fillMaxWidth()) {
      KeySurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        fillColor = Limen.colors.warning,
        outlineColor = Limen.colors.warningOutline,
        onPress = { expanded = true },
      ) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            ButtonGlyph.Add.vector(),
            contentDescription = null,
            tint = Limen.colors.warningOutline,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            s.recordDefect,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.4.sp,
            color = Limen.colors.warningOutline,
            modifier = Modifier.weight(1f),
          )
          Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Limen.colors.warningOutline)
        }
      }
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        containerColor = Limen.colors.plateRaised,
        modifier = Modifier.height(420.dp),
      ) {
        items.forEach { item ->
          DropdownMenuItem(
            text = { Text(s.item(item), color = Limen.colors.ink) },
            onClick = {
              expanded = false
              onPick(item, "")
            },
          )
        }
        DropdownMenuItem(
          text = { Text(s.otherItem, color = Limen.colors.inkDim) },
          onClick = {
            expanded = false
            namingOther = true
          },
        )
      }
    }

    if (namingOther) {
      Panel(label = s.otherItem) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.otherItemName, otherName, hint = s.pickItem) { otherName = it }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(s.cancel, Modifier.weight(1f)) {
              namingOther = false
              otherName = ""
            }
            SmallKey(
              s.add,
              Modifier.weight(1f),
              enabled = otherName.trim().isNotEmpty(),
            ) {
              onPick(ItemKey.Custom, otherName.trim())
              otherName = ""
              namingOther = false
            }
          }
        }
      }
    }
  }
}
