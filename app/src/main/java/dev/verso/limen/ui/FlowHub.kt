package dev.verso.limen.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.dossierCards
import dev.verso.limen.theme.Limen

/**
 * The handover's home. Two doors, neither ahead of the other, and the closing sequence
 * underneath.
 *
 * Only the closing is ordered, because a signature that saw no review is worth nothing.
 * Everything above it is entered in whatever order the handover itself takes: a person who
 * starts in the kitchen because that is where the damage is should not be asked for the
 * meter readings first.
 */
@Composable
internal fun HubStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val defects = p.defects.size
  // Keyed on the record: the check stats every photograph and every signature on disk, and
  // it was rerun by a theme toggle, a language change and every undo notice besides.
  val issues = remember(p) { vm.completionIssues(p) }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    val objectIssues = issues.filter { it in setOf(
      FinalizationIssue.Address,
      FinalizationIssue.Place,
      FinalizationIssue.Canton,
      FinalizationIssue.HandoverDate,
      FinalizationIssue.HandoverTime,
      FinalizationIssue.LegalOverlay,
    ) }
    if (objectIssues.isNotEmpty() && !p.isFinalized()) {
      Panel(label = s.objectPreflight, warning = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            p.objectCanton?.let { CantonFlag(it, contentDescription = s.canton(it), size = 30.dp) }
            Text(
              s.objectPreflightBody,
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
              modifier = Modifier.weight(1f),
            )
          }
          SmallKey(s.completeObjectAndCanton, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Dossier) }
        }
      }
    }

    HubDoor(
      title = s.stepInspection,
      lead = s.hubInspectionLead,
      readout = listOf(s.countRooms(p.rooms.size), s.countObjects(p.objects.size)).joinToString(" · "),
      alert = if (defects > 0) s.countDefects(defects) else null,
      glyph = ButtonGlyph.Camera,
    ) { vm.goTo(FlowStep.Inspection) }

    HubDoor(
      title = s.stepDossier,
      lead = s.hubDossierLead,
      readout = s.countComponents(p.dossierCards().size),
      alert = null,
      glyph = ButtonGlyph.Edit,
    ) { vm.goTo(FlowStep.Dossier) }

    Spacer(Modifier.height(2.dp))

    // What is still missing, said here rather than found at the signature. Nothing is
    // blocked by it: Review is always reachable, and it repeats the same list.
    if (issues.isNotEmpty()) {
      Panel(label = s.stillMissing, warning = true) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          issues.forEach { issue ->
            Text(
              "· ${s.finalizationIssue(issue)}",
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
            )
          }
        }
      }
    }

    // A signed record has nothing left to close; what it still offers is its document.
    val locked = LocalRecordLocked.current
    BigKey(
      if (locked) s.stepExport.uppercase() else s.hubClose,
      active = true,
      Modifier.fillMaxWidth(),
    ) { vm.goTo(if (locked) FlowStep.Export else FlowStep.Review) }
    Spacer(Modifier.height(12.dp))
  }
}

/** One half of the handover, named, counted, and open at a touch. */
@Composable
private fun HubDoor(
  title: String,
  lead: String,
  readout: String,
  alert: String?,
  glyph: ButtonGlyph,
  onPress: () -> Unit,
) {
  KeySurface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    fillColor = Limen.colors.plateRaised,
    outlineColor = Limen.colors.actionOutline,
    onPress = onPress,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        glyph.vector(),
        contentDescription = null,
        tint = Limen.colors.actionOutline,
        modifier = Modifier.size(26.dp),
      )
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          title.uppercase(),
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          letterSpacing = 0.4.sp,
          color = Limen.colors.ink,
        )
        Text(lead, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            readout,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = Limen.colors.inkDim,
          )
          alert?.let {
            Spacer(Modifier.width(8.dp))
            Text(
              it,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Limen.colors.warningOutline,
            )
          }
        }
      }
      Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Limen.colors.inkDim)
    }
  }
}
