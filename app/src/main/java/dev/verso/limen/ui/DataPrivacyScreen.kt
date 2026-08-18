package dev.verso.limen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.verso.limen.theme.Limen

@Composable
fun DataPrivacyScreen(
  modifier: Modifier,
  vm: LimenViewModel,
  s: Str,
  onBack: () -> Unit,
) {
  val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) vm.importArchive(uri)
  }
  Column(
    modifier
      .fillMaxSize()
      .background(Limen.colors.plate)
      .safeDrawingPadding()
      .padding(horizontal = 20.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconKey(ButtonGlyph.Back, s.back, Modifier.width(48.dp), onPress = onBack)
      Spacer(Modifier.width(10.dp))
      Text(s.dataAndPrivacy, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
    }
    Spacer(Modifier.height(14.dp))
    Column(
      Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Panel(label = s.privacyTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          s.privacyParagraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          }
        }
      }
      Panel(label = s.backupTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(s.backupBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(s.createBackup, Modifier.weight(1f), enabled = !vm.archiveBusy) {
              vm.exportArchive()
            }
            SmallKey(s.restoreBackup, Modifier.weight(1f), enabled = !vm.archiveBusy) {
              restore.launch(arrayOf("application/zip", "application/octet-stream"))
            }
          }
          when {
            vm.archiveBusy -> Text(s.working, style = MaterialTheme.typography.bodySmall, color = Limen.colors.inkDim)
            vm.archiveFailed -> Text(s.archiveFailed, style = MaterialTheme.typography.bodySmall, color = Limen.colors.dangerOutline)
            vm.archiveExported != null -> Text(
              s.archiveSaved(vm.archiveExported!!.displayName),
              style = MaterialTheme.typography.bodySmall,
              color = Limen.colors.seal,
            )
            vm.archiveImported != null -> Text(
              s.archiveRestored(vm.archiveImported!!),
              style = MaterialTheme.typography.bodySmall,
              color = Limen.colors.seal,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}
