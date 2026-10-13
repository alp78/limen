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
import dev.peringer.limen.data.*
import dev.peringer.limen.theme.Limen

/** The dated canton layer, kept beside the object facts that select it. */
@Composable
internal fun LegalOverlayPanel(vm: LimenViewModel, s: Str, p: Protocol) {
  val overlay = p.resolvedLegalOverlay() ?: return
  val locked = LocalRecordLocked.current
  val complete = overlay.isComplete(p.legalOverlay)

  val unresolved = overlay.requirements.any { p.legalOverlay.status(it) !in setOf(LegalCheckStatus.Confirmed, LegalCheckStatus.NotApplicable) }
  Panel(label = s.legalOverlayTitle, info = s.tipLegalOverlay(), warning = !complete || unresolved) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (unresolved) Text(s.toVerify, color = Limen.colors.warningOutline)
      if (vm.legalChecksReset && !complete) Text(s.words("Property or legal facts changed. Review the affected checks again.", "Les données du bien ou les faits juridiques ont changé. Vérifiez à nouveau les contrôles concernés.", "Objektangaben oder rechtliche Grundlagen wurden geändert. Die betroffenen Prüfungen erneut durchführen.", "I dati dell’immobile o i presupposti giuridici sono cambiati. Verificare di nuovo i controlli interessati.", "Las datas da l’object u ils fatgs giuridics èn sa midads. Controllar danovamain ils puncts pertutgads."), color = Limen.colors.warningOutline)
      if (p.objectCanton == SwissCanton.NE) {
        Field(s.municipality, p.details.municipality) { v -> vm.edit { it.copy(details = it.details.copy(municipality = v)) } }
        Field(s.habitableRooms, p.details.habitableRooms) { v -> vm.edit { it.copy(details = it.details.copy(habitableRooms = v)) } }
      }
      if (p.objectCanton == SwissCanton.VD) {
        Text(s.district, color = Limen.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          vaudDistricts.forEach { district ->
            Chip(district, p.details.district == district, enabled = !locked) {
              vm.edit { it.copy(details = it.details.copy(district = district)) }
            }
          }
        }
      }
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
      if (LegalRequirement.CantonalDepositHandling in overlay.requirements) Text(s.depositLocal(p.objectCanton), color = Limen.colors.ink)
      if (p.objectCanton == SwissCanton.TI) Text(s.words("In Ticino, parties or the conciliation authority may request the municipal property expert. This is an available procedure, not a mandatory visit for every handover.", "Au Tessin, les parties ou l’autorité de conciliation peuvent solliciter l’expert communal immobilier. Cette procédure est disponible, sans visite obligatoire pour chaque remise.", "Im Tessin können die Parteien oder die Schlichtungsbehörde die kommunale Immobilienfachperson beiziehen. Dies ist keine Pflichtbesichtigung bei jeder Übergabe.", "In Ticino le parti o l’autorità di conciliazione possono richiedere l’intervento del perito comunale degli immobili. Non è una visita obbligatoria per ogni consegna.", "En il Tessin pon las partidas u l’autoritad da mediaziun dumandar il perit communal d’immobiglias. Quai n’è betg ina visita obligatorica per mintga surdada."), color = Limen.colors.ink)
      Text("${overlay.ruleSetId} · 13/10/2026", color = Limen.colors.inkDim)
      val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
      val context = androidx.compose.ui.platform.LocalContext.current
      fun openSource(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure {
          android.widget.Toast.makeText(context, s.openLinkFailed, android.widget.Toast.LENGTH_LONG).show()
        }
      }
      (p.details.legalSnapshot?.sources ?: legalSources(p.objectCanton)).forEachIndexed { index, url ->
        SmallKey("${s.sourceLabel} ${index + 1}", Modifier.fillMaxWidth()) { openSource(url) }
      }
      SmallKey(s.conciliation, Modifier.fillMaxWidth()) { openSource(CONCILIATION_DIRECTORY) }

      Field(
        label = "${s.remarks} · ${s.legalOverlayTitle}",
        value = p.legalOverlay.note,
        singleLine = false,
        hint = null,
        isError = false,
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
    if (selected != null) Field(s.legalOverlayNote, p.legalOverlay.notes[requirement].orEmpty(), singleLine = false,
      isError = (selected != LegalCheckStatus.Confirmed || requirement == LegalRequirement.InitialRentForm &&
        p.resolvedLegalOverlay()?.initialRentFormRule in setOf(InitialRentFormRule.VerifyCurrent, InitialRentFormRule.ConditionalLocal)) && p.legalOverlay.notes[requirement].orEmpty().isBlank()) { note ->
      vm.edit { it.copy(legalOverlay = it.legalOverlay.copy(notes = it.legalOverlay.notes + (requirement to note))) }
    }
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
