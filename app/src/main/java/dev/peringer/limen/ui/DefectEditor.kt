package dev.peringer.limen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.FixBy
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.TenantResponse
import dev.peringer.limen.data.participantKey
import dev.peringer.limen.data.TenantPosition
import dev.peringer.limen.theme.Limen

/**
 * The defect editor, over the whole screen: a finding deserves the room a dialog does not
 * give it. It serves a room finding and a finding about a supplied object alike, since
 * everything below the heading is the same question either way.
 */
@Composable
fun DefectEditor(vm: LimenViewModel, s: Str, p: Protocol, defectId: String) {
  val d = p.defects.firstOrNull { it.id == defectId } ?: return
  val locked = LocalRecordLocked.current
  val intake = rememberPhotoIntake(s, vm, defectId) { file, source -> vm.attachPhoto(defectId, file, source) }

  BackHandler { vm.closeDefect() }

  Box(
    Modifier.fillMaxSize().background(Limen.colors.plate),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      Modifier
        .readable()
        .fillMaxHeight()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconKey(ButtonGlyph.Back, s.back) { vm.closeDefect() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            p.defectPlace(d, s).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Limen.colors.ink,
          )
          p.defectItemLabel(d, s)?.let { label ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
        }
      }

      Panel(label = s.defects, info = s.tipDefect(), warning = true) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Field(s.defectText, d.text, singleLine = false) { v ->
            vm.updateDefect(defectId) { it.copy(text = v) }
          }
        }
      }

      if (!locked || d.photos.isNotEmpty()) Panel(label = s.photos) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (vm.evidenceError.isNotBlank()) Text(vm.evidenceError, color = Limen.colors.dangerOutline)
          if (!locked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconKey(ButtonGlyph.Camera, s.photo, Modifier.weight(1f), onPress = intake.takePhoto)
            IconKey(ButtonGlyph.Gallery, s.gallery, Modifier.weight(1f), onPress = intake.pickPhoto)
          }
          if (d.photos.isNotEmpty()) {
            Row(
              Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              d.photos.forEach { name ->
                PhotoThumb(vm, s, name) { vm.removePhoto(defectId, name) }
              }
            }
          }
        }
      }

      if (p.kind == Kind.Exit) {
        Panel(label = s.tenantResponse, info = s.tipNormalWear()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillRow(
              TenantResponse.entries.map { it to s.tenantResponse(it) },
              d.tenantResponse,
              dense = true,
              enabled = !locked,
            ) { response ->
              vm.updateDefect(defectId) {
                it.copy(
                  tenantResponse = response,
                )
              }
            }
            Field(s.tenantComment, d.tenantComment, singleLine = false) { value ->
              vm.updateDefect(defectId) { it.copy(tenantComment = value) }
            }
            if (p.parties.count { it.role == Role.Tenant } > 1) p.parties.filter { it.role == Role.Tenant }.forEach { party ->
              val position = d.participantPositions[party.participantKey()] ?: TenantPosition()
              Text(party.name, color = Limen.colors.ink)
              PillRow(TenantResponse.entries.map { it to s.tenantResponse(it) }, position.response, dense = true, enabled = !locked) { response ->
                vm.updateDefect(defectId) { it.copy(participantPositions = it.participantPositions + (party.participantKey() to position.copy(response = response))) }
              }
              Field("${s.tenantComment} · ${party.name}", position.comment, singleLine = false) { comment ->
                vm.updateDefect(defectId) { it.copy(participantPositions = it.participantPositions + (party.participantKey() to position.copy(comment = comment))) }
              }
            }
          }
        }

        Panel(label = s.chargedToTenant, info = s.tipCharged()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(s.chargedToTenant, d.chargedToTenant, Modifier.fillMaxWidth(), enabled = !locked) {
              vm.updateDefect(defectId) { it.copy(chargedToTenant = !it.chargedToTenant) }
            }
            Text(s.fixByLabel, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            PillRow(
              listOf(
                FixBy.Open to s.fixOpen,
                FixBy.Tenant to s.role(Role.Tenant),
                FixBy.Landlord to s.role(Role.Landlord),
              ),
              d.fixBy,
              dense = true,
              enabled = !locked,
            ) { f -> vm.updateDefect(defectId) { it.copy(fixBy = f) } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              DateField(s.fixDeadline, d.fixDeadline, s, Modifier.fillMaxWidth()) { v ->
                vm.updateDefect(defectId) { it.copy(fixDeadline = v) }
              }
              Field(s.costNote, d.costNote, Modifier.fillMaxWidth()) { v ->
                vm.updateDefect(defectId) { it.copy(costNote = v) }
              }
            }
            Field(s.reservationLabel, d.reservation) { v ->
              vm.updateDefect(defectId) { it.copy(reservation = v) }
            }
          }
        }
        if (d.chargedToTenant || p.details.estimates.containsKey(defectId)) WearAssessment(s, vm, defectId)
      }

      if (p.kind == Kind.Entry) {
        Panel(label = s.reservationLabel) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.reservationLabel, d.reservation) { value ->
            vm.updateDefect(defectId) { it.copy(reservation = value) }
          }
          PillRow(FixBy.entries.map { it to when (it) { FixBy.Open -> s.fixOpen; FixBy.Tenant -> s.role(Role.Tenant); FixBy.Landlord -> s.role(Role.Landlord) } }, d.fixBy, enabled = !locked) { value -> vm.updateDefect(defectId) { it.copy(fixBy = value) } }
          DateField(s.fixDeadline, d.fixDeadline, s) { value -> vm.updateDefect(defectId) { it.copy(fixDeadline = value) } }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!locked) IconKey(
          ButtonGlyph.Delete,
          s.delete,
          Modifier.weight(1f),
          destructive = true,
          tick = Tick.Reject,
        ) { vm.removeDefect(defectId) }
        IconKey(
          ButtonGlyph.Confirm,
          s.done,
          Modifier.weight(1.6f),
          fillColor = Limen.colors.action,
          outlineColor = Limen.colors.action,
          tint = Limen.colors.onInk,
          tick = Tick.Confirm,
        ) { vm.closeDefect() }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}
