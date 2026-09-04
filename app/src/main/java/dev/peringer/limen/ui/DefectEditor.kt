package dev.peringer.limen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
  val intake = rememberPhotoIntake(s) { file, source -> vm.attachPhoto(defectId, file, source) }

  BackHandler { vm.closeDefect() }

  Box(Modifier.fillMaxSize().background(Limen.colors.plate)) {
    Column(
      Modifier
        .fillMaxSize()
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
                  chargedToTenant = if (response == TenantResponse.NormalWear) false else it.chargedToTenant,
                )
              }
            }
            Field(s.tenantComment, d.tenantComment, singleLine = false) { value ->
              vm.updateDefect(defectId) { it.copy(tenantComment = value) }
            }
          }
        }

        if (d.tenantResponse != TenantResponse.NormalWear) Panel(label = s.chargedToTenant, info = s.tipCharged()) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DateField(s.fixDeadline, d.fixDeadline, s, Modifier.weight(1f)) { v ->
                vm.updateDefect(defectId) { it.copy(fixDeadline = v) }
              }
              Field(s.costNote, d.costNote, Modifier.weight(1.4f)) { v ->
                vm.updateDefect(defectId) { it.copy(costNote = v) }
              }
            }
            Field(s.reservationLabel, d.reservation) { v ->
              vm.updateDefect(defectId) { it.copy(reservation = v) }
            }
          }
        }
        if (d.chargedToTenant) WearAssessment(s)
      }

      if (p.kind == Kind.Entry) {
        Panel(label = s.reservationLabel) {
          Field(s.reservationLabel, d.reservation) { value ->
            vm.updateDefect(defectId) { it.copy(reservation = value) }
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
