package dev.peringer.limen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.*
import dev.peringer.limen.theme.Limen
import java.io.File

@Composable
internal fun OverviewPhotos(vm: LimenViewModel, s: Str, p: Protocol, room: Room) {
  val intake = rememberPhotoIntake(s) { file, source -> vm.attachOverview(room.id, file, source) }
  Panel(label = s.overviewPhotos) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.details.roomPhotos[room.id].orEmpty().forEach { photo ->
          PhotoThumb(vm, s, photo.file) { vm.edit { it.copy(details = it.details.copy(roomPhotos = it.details.roomPhotos +
            (room.id to it.details.roomPhotos[room.id].orEmpty().filterNot { old -> old.file == photo.file }))) } }
        }
      }
      if (!LocalRecordLocked.current) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallKey(s.photo, onPress = intake.takePhoto)
        SmallKey(s.gallery, onPress = intake.pickPhoto)
      }
    }
  }
}

@Composable
internal fun BaselinePanel(vm: LimenViewModel, s: Str, baseline: Protocol, roomId: String = "", objectId: String = "") {
  var expanded by remember { mutableStateOf(false) }
  var viewing by remember { mutableStateOf<String?>(null) }
  Panel(label = "${s.atEntry} · ${SwissDate.display(baseline.handoverDate)}") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SmallKey(s.open, Modifier.fillMaxWidth()) { expanded = !expanded }
      if (expanded) {
        DocumentPreviewButton(vm, s, baseline)
        baseline.objects.firstOrNull { it.id == objectId }?.let { entry ->
          Text("${entry.displayName(s)}\n${entry.note}", color = Limen.colors.ink)
          entry.photoFiles().forEachIndexed { i, name -> SmallKey("${s.photos} ${i + 1}") { viewing = name } }
        }
        val found = baseline.defects.filter { if (objectId.isNotBlank()) it.objectId == objectId else it.roomId == roomId }
        if (found.isEmpty()) Text(s.countDefects(0), color = Limen.colors.ink)
        found.forEach { defect ->
          Text(listOf(defect.text, defect.reservation, defect.tenantComment, defect.costNote,
            SwissDate.display(defect.fixDeadline), s.tenantResponse(defect.tenantResponse)).filter(String::isNotBlank).joinToString("\n"), color = Limen.colors.ink)
          defect.photoFiles().forEachIndexed { i, name -> SmallKey("${s.photos} ${i + 1}") { viewing = name } }
        }
        baseline.details.roomPhotos[roomId].orEmpty().forEachIndexed { i, evidence ->
          SmallKey("${s.overviewPhotos} ${i + 1}") { viewing = evidence.file }
        }
      }
    }
  }
  viewing?.let { PhotoViewer(File(vm.recordPhotoDir(baseline.id), it), s.atEntry, s) { viewing = null } }
}
