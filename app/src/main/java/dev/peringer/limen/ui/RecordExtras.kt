package dev.peringer.limen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.*
import dev.peringer.limen.theme.Limen
import java.util.UUID

@Composable
internal fun ScopeReason(vm: LimenViewModel, s: Str, p: Protocol, id: String) {
  Field(s.reason, p.details.scopeNotes[id].orEmpty(), singleLine = false) { text ->
    vm.edit { it.copy(details = it.details.copy(scopeNotes = if (text.isBlank()) it.details.scopeNotes - id else it.details.scopeNotes + (id to text))) }
  }
}

@Composable
internal fun TransferDetails(vm: LimenViewModel, s: Str, p: Protocol) {
  Panel(label = s.actualTransfer) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      DateAndTimeRow(s.handoverDate, p.details.actualTransferDate, s.handoverTime, p.details.actualTransferTime, s,
        { v -> vm.edit { it.copy(details = it.details.copy(actualTransferDate = v)) } },
        { v -> vm.edit { it.copy(details = it.details.copy(actualTransferTime = v)) } })
      if (p.kind == Kind.Entry) {
        Field(s.notificationClause, p.details.notificationClause, singleLine = false) { v -> vm.edit { it.copy(details = it.details.copy(notificationClause = v)) } }
        Field(s.words("Agreed days (optional)", "Jours convenus (facultatif)", "Vereinbarte Tage (optional)", "Giorni concordati (facoltativo)", "Dis convenids (facultativ)"), p.details.notificationDays) { v -> vm.edit { it.copy(details = it.details.copy(notificationDays = v)) } }
      }
      if (DossierCard.Keys !in p.dossierCards()) {
        Text(s.keysLabel, color = Limen.colors.ink)
        ScopeReason(vm, s, p, DossierCard.Keys.name)
      }
      if (p.rooms.isEmpty()) { Text(s.stepInspection, color = Limen.colors.ink); ScopeReason(vm, s, p, "Inspection") }
    }
  }
}

@Composable
internal fun MeterReadings(vm: LimenViewModel, s: Str, p: Protocol) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    p.details.readings.forEach { r ->
      fun update(change: (MeterReading) -> MeterReading) = vm.edit { it.copy(details = it.details.copy(readings = it.details.readings.map { old -> if (old.id == r.id) change(old) else old })) }
      Panel(label = r.label.ifBlank { s.meters }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.meters, r.label) { v -> update { it.copy(label = v) } }
          Field(s.identifier, r.identifier) { v -> update { it.copy(identifier = v) } }
          Field(s.reading, r.value) { v -> update { it.copy(value = v) } }
          Field(s.unit, r.unit) { v -> update { it.copy(unit = v) } }
          DateAndTimeRow(s.handoverDate, r.date, s.handoverTime, r.time, s,
            { v -> update { it.copy(date = v) } }, { v -> update { it.copy(time = v) } })
          Field(s.reason, r.note, singleLine = false) { v -> update { it.copy(note = v) } }
          val intake = rememberPhotoIntake(s) { file, source -> vm.attachOverview(r.id, file, source, meter = true) }
          if (!LocalRecordLocked.current) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(s.photo, onPress = intake.takePhoto)
            SmallKey(s.gallery, onPress = intake.pickPhoto)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.photos.forEach { photo ->
            PhotoThumb(vm, s, photo.file) { vm.removeReadingPhoto(r.id, photo.file) }
          } }
          if (!LocalRecordLocked.current) SmallKey(s.delete) { vm.removeReading(r.id) }
        }
      }
    }
    if (!LocalRecordLocked.current) IconKey(ButtonGlyph.Add, "${s.add} · ${s.meters}") {
      vm.edit { it.copy(details = it.details.copy(readings = it.details.readings + MeterReading(UUID.randomUUID().toString(), date = it.handoverDate, time = it.handoverTime))) }
    }
    ScopeReason(vm, s, p, DossierCard.Meters.name)
  }
}

/**
 * A count typed with the keyboard. The typed text is held here because an empty field has no
 * count to show: rebuilt from the number, it snapped back to the old digit on backspace and a
 * retyped number came out as "35". The record holds 0 until a number is typed.
 */
@Composable
private fun CountField(label: String, id: String, count: Int, onChange: (Int) -> Unit) {
  var text by remember(id) { mutableStateOf(count.toString()) }
  // Follows the record when something else moved the count, an undo for one.
  val shown = if ((text.toIntOrNull() ?: 0) == count) text else count.toString()
  Field(label, shown, keyboardType = KeyboardType.Number) { typed ->
    val n = if (typed.isEmpty()) 0 else typed.toIntOrNull()?.takeIf { it >= 0 }
    if (n != null) {
      text = if (typed.isEmpty()) typed else n.toString()
      onChange(n)
    }
  }
}

@Composable
internal fun AccessDevices(vm: LimenViewModel, s: Str, p: Protocol) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    p.details.accessDevices.forEach { a ->
      fun update(change: (AccessDevice) -> AccessDevice) = vm.edit { it.copy(details = it.details.copy(accessDevices = it.details.accessDevices.map { old -> if (old.id == a.id) change(old) else old })) }
      Panel(label = s.accessDevices) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Field(s.keyLabel, a.label) { v -> update { it.copy(label = v) } }
          Field(s.identifier, a.identifier) { v -> update { it.copy(identifier = v) } }
          CountField(s.expected, a.id, a.expected) { n -> update { it.copy(expected = n) } }
          CountField(s.transferred, a.id, a.transferred) { n -> update { it.copy(transferred = n) } }
          Field(s.keyTransferNote, a.note, singleLine = false) { v -> update { it.copy(note = v) } }
          if (!LocalRecordLocked.current) SmallKey(s.delete) { vm.removeAccessDevice(a.id) }
        }
      }
    }
    if (!LocalRecordLocked.current) IconKey(ButtonGlyph.Add, "${s.add} · ${s.accessDevices}") {
      vm.edit { it.copy(details = it.details.copy(accessDevices = it.details.accessDevices + AccessDevice(UUID.randomUUID().toString()))) }
    }
  }
}

@Composable
internal fun DocumentsPanel(vm: LimenViewModel, s: Str, p: Protocol) {
  // Saveable, like the camera slot: the picker puts this activity in the background, and when
  // the system recreates it the result still arrives, to a callback that lost the slot it was
  // opened for and dropped the document without a word.
  var target by rememberSaveable { mutableStateOf<String?>(null) }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    val id = target
    if (uri != null && id != null) vm.attachDocument(id, uri)
  }
  Panel(label = s.attachments) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      p.details.attachments.forEach { a ->
        fun update(change: (SupportingDocument) -> SupportingDocument) = vm.edit { it.copy(details = it.details.copy(attachments = it.details.attachments.map { old -> if (old.id == a.id) change(old) else old })) }
        PillRow(DocumentKind.entries.map { it to s.documentKind(it) }, a.kind, dense = true, enabled = !LocalRecordLocked.current) { kind -> update { it.copy(kind = kind) } }
        Field(s.attachments, a.title) { v -> update { it.copy(title = v) } }
        Field(s.externalReference, a.externalReference, singleLine = false) { v -> update { it.copy(externalReference = v) } }
        if (a.file.isNotBlank()) {
          Text("${a.file} · ${a.bytes} B\nSHA-256: ${a.sha256}", color = Limen.colors.inkDim)
          val context = androidx.compose.ui.platform.LocalContext.current
          SmallKey(s.open) { vm.openSupportingDocument(context, a) }
        }
        if (!LocalRecordLocked.current) {
          SmallKey(s.addDocument) { target = a.id; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }
          SmallKey(s.delete) { vm.removeAttachment(a.id) }
        }
      }
      if (!LocalRecordLocked.current) IconKey(ButtonGlyph.Add, "${s.add} · ${s.attachments}") {
        vm.edit { it.copy(details = it.details.copy(attachments = it.details.attachments + SupportingDocument(UUID.randomUUID().toString()))) }
      }
    }
  }
}

@Composable
internal fun FollowUpPanel(vm: LimenViewModel, s: Str, p: Protocol) {
  Panel(label = s.followUps) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      p.details.followUps.forEach { f ->
        fun update(change: (FollowUp) -> FollowUp) = vm.edit { it.copy(details = it.details.copy(followUps = it.details.followUps.map { old -> if (old.id == f.id) change(old) else old })) }
        Field(s.defectText, f.description, singleLine = false) { v -> update { it.copy(description = v) } }
        DateField(s.discoveredOn, f.discoveredDate, s) { v -> update { it.copy(discoveredDate = v) } }
        DateField(s.notifiedOn, f.notifiedDate, s) { v -> update { it.copy(notifiedDate = v) } }
        Field(s.words("Recipient", "Destinataire", "Empfänger", "Destinatario", "Destinatari"), f.recipient) { v -> update { it.copy(recipient = v) } }
        Field(s.deliveryDetail, f.method) { v -> update { it.copy(method = v) } }
        DateField(s.fixDeadline, f.deadline, s) { v -> update { it.copy(deadline = v) } }
        Toggle(s.done, f.done, enabled = !LocalRecordLocked.current) { update { it.copy(done = !it.done) } }
        if (!LocalRecordLocked.current) SmallKey(s.delete) { vm.removeFollowUp(f.id) }
      }
      if (!LocalRecordLocked.current) IconKey(ButtonGlyph.Add, "${s.add} · ${s.followUps}") {
        vm.edit { it.copy(details = it.details.copy(followUps = it.details.followUps + FollowUp(UUID.randomUUID().toString()))) }
      }
    }
  }
}

@Composable
internal fun RecordBackupButton(vm: LimenViewModel, s: Str, p: Protocol) {
  var open by remember { mutableStateOf(false) }
  SmallKey(s.createBackup, Modifier.fillMaxWidth(), enabled = !vm.archiveBusy) { open = true }
  // The backup state is shared by every screen: only this record's own backup is reported here,
  // and in the backup's own words, not the sentence for a record that failed to save.
  if (vm.archiveRecordId == p.id) {
    if (vm.archiveFailed) Text(s.archiveFailure(vm.archiveRefused, false), color = Limen.colors.dangerOutline)
    vm.archiveExported?.let {
      Text(if (vm.archiveProtected) s.archiveSaved(it.displayName) else s.archiveSavedOpen(it.displayName), color = Limen.colors.ink)
    }
  }
  if (open) ArchivePasswordDialog(ArchiveAction.Export, s, onDismiss = { open = false }) { password ->
    vm.exportArchive(password, p.id)
    open = false
  }
}
