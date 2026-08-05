package dev.verso.limen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.Appliance
import dev.verso.limen.data.Furniture
import dev.verso.limen.data.ObjectEntry
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Protocol
import dev.verso.limen.theme.Limen

/**
 * Everything handed over with the flat, one row per thing.
 *
 * A row rather than a toggle, because two fridges in a kitchen are two facts and each needs
 * its own photograph. A photograph of an appliance on the day of the handover is what ends
 * the argument about it two years later, so the camera sits on the row itself.
 */
@Composable
internal fun ObjectsStep(vm: LimenViewModel, s: Str, p: Protocol) {
  var addingCategory by remember { mutableStateOf(false) }
  var categoryDraft by remember { mutableStateOf("") }
  val customGroups = vm.customGroups(p)

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ObjectGroupPanel(
      vm = vm,
      s = s,
      p = p,
      label = s.appliances,
      group = ObjectGroup.Appliance,
      groupLabel = "",
      catalogue = Appliance.entries.map { it.name to s.appliance(it) },
    )

    ObjectGroupPanel(
      vm = vm,
      s = s,
      p = p,
      label = s.furniture,
      group = ObjectGroup.Furniture,
      groupLabel = "",
      catalogue = Furniture.entries.map { it.name to s.furniture(it) },
    )

    // Anything the two shipped lists have no word for: garden tools, a bicycle rack, the
    // ski cellar's shelving. The person names the category and it behaves like the others.
    customGroups.forEach { name ->
      key(name) {
        ObjectGroupPanel(
          vm = vm,
          s = s,
          p = p,
          label = name,
          group = ObjectGroup.Other,
          groupLabel = name,
          catalogue = emptyList(),
        )
      }
    }

    RoomActionKey(
      label = s.addCategory,
      fill = Limen.colors.positive,
      outline = Limen.colors.positive,
      text = Limen.colors.onPositive,
      glyph = ButtonGlyph.Add,
    ) { addingCategory = !addingCategory }

    if (addingCategory) {
      Panel(label = s.addCategory) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.categoryName, categoryDraft) { categoryDraft = it }
          SmallKey(
            s.add,
            Modifier.fillMaxWidth(),
            enabled = categoryDraft.trim().isNotEmpty(),
          ) {
            val name = categoryDraft.trim()
            // The category exists once it holds something. An empty heading is not a fact.
            vm.addObject(ObjectGroup.Other, label = name, groupLabel = name)
            categoryDraft = ""
            addingCategory = false
          }
        }
      }
    }

    BigKey(s.stepDossier.uppercase(), active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Dossier) }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun ObjectGroupPanel(
  vm: LimenViewModel,
  s: Str,
  p: Protocol,
  label: String,
  group: ObjectGroup,
  groupLabel: String,
  catalogue: List<Pair<String, String>>,
) {
  var freeDraft by remember(groupLabel) { mutableStateOf("") }
  val rows = p.objects.filter {
    it.group == group && (group != ObjectGroup.Other || it.groupLabel == groupLabel)
  }

  Panel(label = label) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      rows.forEach { entry ->
        key(entry.id) { ObjectRow(vm, s, p, entry) }
      }

      // Pressing a catalogue key appends: the same key twice records two of the thing.
      if (catalogue.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          catalogue.forEach { (name, shown) ->
            Chip("+ $shown", dim = false) {
              vm.addObject(group, catalogue = name, groupLabel = groupLabel)
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(s.addObject, freeDraft, Modifier.weight(1f)) { freeDraft = it }
        SmallKey(s.add, enabled = freeDraft.trim().isNotEmpty()) {
          vm.addObject(group, label = freeDraft.trim(), groupLabel = groupLabel)
          freeDraft = ""
        }
      }
    }
  }
}

/** One supplied thing: what it is, what it looks like today, and any finding about it. */
@Composable
private fun ObjectRow(vm: LimenViewModel, s: Str, p: Protocol, entry: ObjectEntry) {
  val intake = rememberPhotoIntake(s) { file, source -> vm.attachObjectPhoto(entry.id, file, source) }
  val defect = p.defects.firstOrNull { it.objectId == entry.id }
  val flagged = defect != null

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .padding(1.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          entry.displayName(s),
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = Limen.colors.ink,
        )
        val where = p.rooms.firstOrNull { it.id == entry.roomId }
          ?.let { it.name.ifBlank { s.room(it.kind) } }
          ?: s.wholeFlat
        Text(where, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      }
      IconKey(ButtonGlyph.Camera, s.photo, onPress = intake.takePhoto)
      Spacer(Modifier.width(6.dp))
      IconKey(ButtonGlyph.Gallery, s.gallery, onPress = intake.pickPhoto)
      Spacer(Modifier.width(6.dp))
      IconKey(
        ButtonGlyph.Add,
        s.addDefect,
        fillColor = if (flagged) Limen.colors.warning else null,
        outlineColor = if (flagged) Limen.colors.warningOutline else null,
        tint = if (flagged) Limen.colors.warningOutline else null,
      ) {
        if (defect != null) vm.editDefect(defect.id) else vm.openObjectDefect(entry.id)
      }
      Spacer(Modifier.width(6.dp))
      IconKey(ButtonGlyph.Delete, s.delete, destructive = true, tick = Tick.Reject) {
        vm.removeObject(entry.id)
      }
    }

    Field(s.objectNote, entry.note) { v -> vm.updateObject(entry.id) { it.copy(note = v) } }

    if (entry.photos.isNotEmpty()) {
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        entry.photos.forEach { name ->
          PhotoThumb(vm, s, name, size = 72) { vm.removeObjectPhoto(entry.id, name) }
        }
      }
    }

    defect?.let {
      Text(
        "! ${it.text.ifBlank { s.defectText }}",
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        color = Limen.colors.dangerOutline,
      )
    }

    // Which room it stands in, when that matters. Blank is the honest default: most of what
    // is supplied belongs to the flat rather than to one of its rooms.
    if (p.rooms.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Chip(s.wholeFlat, active = entry.roomId.isBlank()) {
          vm.updateObject(entry.id) { it.copy(roomId = "") }
        }
        p.rooms.forEach { room ->
          Chip(room.name.ifBlank { s.room(room.kind) }, active = entry.roomId == room.id) {
            vm.updateObject(entry.id) { it.copy(roomId = room.id) }
          }
        }
      }
    }
    Spacer(Modifier.height(2.dp))
  }
}
