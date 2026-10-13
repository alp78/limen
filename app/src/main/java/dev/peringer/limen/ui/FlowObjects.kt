package dev.peringer.limen.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.data.Appliance
import dev.peringer.limen.data.Furniture
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.photoFiles
import dev.peringer.limen.theme.Limen

/**
 * Everything handed over with the flat, one row per thing.
 *
 * A row rather than a toggle, because two fridges in a kitchen are two facts and each needs
 * its own condition, evidence and findings. The camera sits on the row itself.
 */
@Composable
internal fun ObjectsStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val locked = LocalRecordLocked.current
  var addingCategory by remember { mutableStateOf(false) }
  var categoryDraft by rememberSaveable { mutableStateOf("") }
  // A category the operator has named but not yet filled. Held here rather than written to the
  // record: creating it used to add an object row labelled after the category itself, so the
  // paper listed a supplied item that was never supplied, under a heading of the same name.
  var pendingGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
  val recorded = vm.customGroups(p)
  val customGroups = (recorded + pendingGroups.filterNot { it in recorded }).distinct()

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

    if (!locked) {
      RoomActionKey(
        label = s.addCategory,
        fill = Limen.colors.positive,
        outline = Limen.colors.positive,
        text = Limen.colors.onPositive,
        glyph = ButtonGlyph.Add,
      ) { addingCategory = !addingCategory }
    }

    if (addingCategory && !locked) {
      Panel(label = s.addCategory) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.categoryName, categoryDraft) { categoryDraft = it }
          SmallKey(
            s.add,
            Modifier.fillMaxWidth(),
            enabled = categoryDraft.trim().isNotEmpty(),
          ) {
            val name = categoryDraft.trim()
            // The heading appears; nothing is written to the record until a row is added under
            // it. An empty category is not a fact about the flat.
            if (name.isNotBlank() && name !in customGroups) pendingGroups = pendingGroups + name
            categoryDraft = ""
            addingCategory = false
          }
        }
      }
    }

    BigKey(s.stepDossier.uppercase(), active = true, Modifier.fillMaxWidth()) {
      vm.goTo(FlowStep.Dossier)
    }
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
  val locked = LocalRecordLocked.current
  var freeDraft by remember(groupLabel) { mutableStateOf("") }
  val rows = p.objects.filter {
    it.group == group && (group != ObjectGroup.Other || it.groupLabel == groupLabel)
  }
  // A group nobody filled in is not part of a signed record, so it is not shown on one.
  if (locked && rows.isEmpty()) return

  Panel(label = label) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      rows.forEach { entry ->
        key(entry.id) { ObjectRow(vm, s, p, entry) }
      }

      // Pressing a catalogue key appends: the same key twice records two of the thing.
      if (catalogue.isNotEmpty() && !locked) {
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

      if (!locked) {
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
}

/** One supplied thing: what it is, what it looks like today, and any finding about it. */
@Composable
private fun ObjectRow(vm: LimenViewModel, s: Str, p: Protocol, entry: ObjectEntry) {
  val locked = LocalRecordLocked.current
  val intake = rememberPhotoIntake(s) { file, source -> vm.attachObjectPhoto(entry.id, file, source) }
  val defects = p.defects.filter { it.objectId == entry.id }
  val flagged = defects.isNotEmpty()

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .padding(1.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (!entry.inspected) ScopeReason(vm, s, p, entry.id)
    vm.entryBaseline?.let { baseline -> BaselinePanel(vm, s, baseline, objectId = entry.id) }
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
      if (!locked) {
        IconKey(ButtonGlyph.Camera, s.photo, onPress = intake.takePhoto)
        Spacer(Modifier.width(6.dp))
        IconKey(ButtonGlyph.Gallery, s.gallery, onPress = intake.pickPhoto)
        Spacer(Modifier.width(6.dp))
      }
      if (!locked) {
        IconKey(
          ButtonGlyph.Add,
          s.addDefect,
          fillColor = if (flagged) Limen.colors.warning else null,
          outlineColor = if (flagged) Limen.colors.warningOutline else null,
          tint = if (flagged) Limen.colors.warningOutline else null,
        ) {
          vm.openObjectDefect(entry.id)
        }
      }
      if (!locked) {
        Spacer(Modifier.width(6.dp))
        IconKey(ButtonGlyph.Delete, s.delete, destructive = true, tick = Tick.Reject) {
          vm.removeObject(entry.id)
        }
      }
    }

    Field(s.objectNote, entry.note) { v -> vm.updateObject(entry.id) { it.copy(note = v) } }
    Toggle(
      s.objectInspected,
      entry.inspected,
      Modifier.fillMaxWidth(),
      enabled = !locked,
    ) { vm.setObjectInspected(entry.id, !entry.inspected) }

    val evidenceFiles = entry.photoFiles()
    if (evidenceFiles.isNotEmpty()) {
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        evidenceFiles.forEach { name ->
          PhotoThumb(vm, s, name, size = 72) { vm.removeObjectPhoto(entry.id, name) }
        }
      }
    }

    if (defects.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        defects.forEach { defect ->
          key(defect.id) {
            if (locked) {
              DefectLine(vm, s, p, defect)
            } else {
              DismissibleItemRow(s, onRemove = { vm.removeDefect(defect.id) }) {
                DefectLine(vm, s, p, defect)
              }
            }
          }
        }
      }
    }

    // Which room it stands in, when that matters. Blank is the honest default: most of what
    // is supplied belongs to the flat rather than to one of its rooms.
    if (p.rooms.isNotEmpty() && !locked) {
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
