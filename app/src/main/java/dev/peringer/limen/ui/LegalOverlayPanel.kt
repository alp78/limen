package dev.peringer.limen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.isVaudRulv
import dev.peringer.limen.data.resolvedLegalOverlay
import dev.peringer.limen.data.supportsRomandFramework
import dev.peringer.limen.theme.Limen

/** The dated canton layer, kept beside the object facts that select it. */
@Composable
internal fun LegalOverlayPanel(vm: LimenViewModel, s: Str, p: Protocol) {
  val overlay = p.resolvedLegalOverlay() ?: return
  val locked = LocalRecordLocked.current
  val complete = overlay.isComplete(p.legalOverlay)

  Panel(label = s.legalOverlayTitle, info = s.tipLegalOverlay(), warning = !complete) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CantonFlag(overlay.canton, contentDescription = null, size = 34.dp)
        Column(Modifier.weight(1f)) {
          Text(s.canton(overlay.canton), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          Text(
            "${s.legalBasis}: ${s.legalProfile(overlay.profile)}",
            style = MaterialTheme.typography.labelSmall,
            color = Limen.colors.inkDim,
          )
        }
      }

      if (p.kind == Kind.Entry) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(s.initialRentForm, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          Text(
            s.initialRentFormRule(overlay.initialRentFormRule),
            style = MaterialTheme.typography.bodyMedium,
            color = Limen.colors.ink,
          )
        }
        if (overlay.profile.isVaudRulv()) {
          Text(
            s.vaudPreviousExitReport,
            style = MaterialTheme.typography.bodyMedium,
            color = Limen.colors.ink,
          )
        }
      }

      if (overlay.canton.supportsRomandFramework() && !overlay.profile.isVaudRulv()) {
        Toggle(
          s.romandFrameworkInLease,
          p.romandFrameworkInLease,
          Modifier.fillMaxWidth(),
          enabled = !locked,
        ) { vm.edit { it.copy(romandFrameworkInLease = !it.romandFrameworkInLease) } }
      }

      overlay.requirements.forEach { requirement ->
        LegalRequirementRow(vm, s, p, requirement, locked)
      }

      val needsNote = overlay.requirements.any {
        val status = p.legalOverlay.status(it)
        status != null && status != LegalCheckStatus.Confirmed
      }
      Field(
        label = s.legalOverlayNote,
        value = p.legalOverlay.note,
        singleLine = false,
        hint = if (needsNote && p.legalOverlay.note.isBlank()) s.legalOverlayNoteRequired else null,
        isError = needsNote && p.legalOverlay.note.isBlank(),
      ) { note -> vm.edit { it.copy(legalOverlay = it.legalOverlay.copy(note = note)) } }
    }
  }
}

@Composable
private fun LegalRequirementRow(
  vm: LimenViewModel,
  s: Str,
  p: Protocol,
  requirement: LegalRequirement,
  locked: Boolean,
) {
  val selected = p.legalOverlay.status(requirement)
  Column(
    Modifier.fillMaxWidth().padding(vertical = 2.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      s.legalRequirement(requirement),
      style = MaterialTheme.typography.bodyMedium,
      color = Limen.colors.ink,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      LegalCheckStatus.entries.forEach { status ->
        Chip(
          label = s.legalCheckStatus(status),
          active = selected == status,
          selectionState = selected == status,
          enabled = !locked,
          destructive = status == LegalCheckStatus.MissingOrDisputed,
          tick = if (status == LegalCheckStatus.Confirmed) Tick.Confirm else Tick.Key,
        ) {
          vm.edit {
            it.copy(legalOverlay = it.legalOverlay.withStatus(requirement, status))
          }
        }
      }
    }
  }
}
