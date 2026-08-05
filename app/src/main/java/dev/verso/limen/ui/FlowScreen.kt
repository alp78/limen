package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.Appliance
import dev.verso.limen.data.Defect
import dev.verso.limen.data.Furniture
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.Kind
import dev.verso.limen.data.ObjectEntry
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.TenantResponse
import dev.verso.limen.theme.Limen

/**
 * The protocol flow. Not a stepper any more: [FlowHub] is the home, and the two halves of a
 * handover, what is in the flat and what is on paper, are entered in whatever order the
 * handover actually takes. The draft is on disk after every keystroke, because the camera
 * app kills this process whenever it pleases.
 */
@Composable
fun FlowScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  val p = vm.working ?: return
  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
      FlowMast(vm, s, p)
      Spacer(Modifier.height(8.dp))
      // The place's one instruction, at reading size, before any control.
      StepLead(s.stepLead(vm.flowStep))
      Spacer(Modifier.height(8.dp))
      when (vm.flowStep) {
        FlowStep.Hub -> HubStep(vm, s, p)
        FlowStep.Inspection -> InspectionStep(vm, s, p)
        FlowStep.Objects -> ObjectsStep(vm, s, p)
        FlowStep.Dossier -> DossierStep(vm, s, p)
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

/** The flow's own masthead: the way out, where you are, the theme. */
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
}

// ── Naming a finding ───────────────────────────────────────────────────────────────────

/** Where a finding hangs: a room of the flat, or a supplied object. */
internal fun Protocol.defectPlace(d: Defect, s: Str): String {
  if (d.objectId.isNotBlank()) {
    return objects.firstOrNull { it.id == d.objectId }?.displayName(s) ?: s.objects
  }
  val room = rooms.firstOrNull { it.id == d.roomId } ?: return ""
  return room.name.ifBlank { s.room(room.kind) }
}

/**
 * The item a finding names, or null when it is about the room or the object as a whole.
 *
 * A finding written by this version carries its own label. Older records kept it on the
 * room's item row instead, so those are still resolved through the row.
 */
internal fun Protocol.defectItemLabel(d: Defect, s: Str): String? {
  val key = d.item ?: return null
  if (key == ItemKey.Custom) {
    if (d.itemLabel.isNotBlank()) return d.itemLabel
    val legacy = rooms.firstOrNull { it.id == d.roomId }
      ?.items?.firstOrNull { it.key == key && (d.itemId.isBlank() || it.itemId.isBlank() || d.itemId == it.itemId) }
    return legacy?.label?.takeIf { it.isNotBlank() } ?: s.item(key)
  }
  return s.item(key)
}

/** What an object row is called: its catalogue entry when it has one, else what was typed. */
internal fun ObjectEntry.displayName(s: Str): String = when (group) {
  ObjectGroup.Appliance ->
    Appliance.entries.firstOrNull { it.name == catalogue }?.let(s::appliance) ?: label
  ObjectGroup.Furniture ->
    Furniture.entries.firstOrNull { it.name == catalogue }?.let(s::furniture) ?: label
  ObjectGroup.Other -> label
}

// ── Shared keys and confirmations ──────────────────────────────────────────────────────

/** The one prominent action a place offers, coloured for what it does. */
@Composable
internal fun RoomActionKey(
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

/** Asked before anything recorded is thrown away, and never for anything reversible. */
@Composable
internal fun ConfirmDialog(s: Str, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Limen.colors.plateRaised,
    titleContentColor = Limen.colors.ink,
    textContentColor = Limen.colors.inkDim,
    title = { Text(s.areYouSure, fontWeight = FontWeight.Bold) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
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
internal fun DismissibleItemRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
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

/** One finding, one line: what it says, what hangs on it, and the way into the editor. */
@Composable
internal fun DefectLine(vm: LimenViewModel, s: Str, p: Protocol, d: Defect, showPlace: Boolean = false) {
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
      if (showPlace) p.defectPlace(d, s).takeIf { it.isNotBlank() }?.let(parts::add)
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
        Text(s.nothingYet, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          p.defects.forEachIndexed { i, d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                "${i + 1}.",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Limen.colors.inkDim,
                modifier = Modifier.width(30.dp),
              )
              Box(Modifier.weight(1f)) { DefectLine(vm, s, p, d, showPlace = true) }
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
