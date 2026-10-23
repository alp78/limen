package dev.peringer.limen.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.peringer.limen.data.PdfExport
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.theme.Limen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DocumentPreviewButton(vm: LimenViewModel, s: Str, record: Protocol) {
  var open by remember { mutableStateOf(false) }
  SmallKey(s.preview, Modifier.fillMaxWidth()) { open = true }
  if (open) DocumentPreview(vm, s, record) { open = false }
}

@Composable
internal fun DocumentPreview(vm: LimenViewModel, s: Str, record: Protocol, onClose: () -> Unit) {
  val context = LocalContext.current
  val html by produceState<Result<String>?>(null, record, s.lang) {
    value = withContext(Dispatchers.IO) {
      runCatching { PdfExport.preview(context, record, s, vm.recordPhotoDir(record.id), vm.recordSignatureDir(record.id)) }
    }
  }
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(Modifier.fillMaxSize().background(Limen.colors.plate).safeDrawingPadding().padding(12.dp)) {
      Text(s.preview, color = Limen.colors.ink)
      Text(s.translationNotice, color = Limen.colors.inkDim)
      SmallKey(s.done, Modifier.fillMaxWidth(), onPress = onClose)
      val result = html
      when {
        result == null -> Text("…", color = Limen.colors.ink)
        result.isFailure -> {
          // Only evidence that cannot be rendered is a file to restore; a preview that ran out of memory says nothing about the photographs.
          val unrenderable = result.exceptionOrNull() as? dev.peringer.limen.photo.UnrenderableEvidence
          Text(
            if (unrenderable == null) s.previewFailed
            else s.finalizationIssue(FinalizationIssue.Evidence) + "\n" + unrenderable.files.joinToString("\n"),
            color = Limen.colors.dangerOutline,
          )
        }
        else -> AndroidView(
          modifier = Modifier.fillMaxWidth().weight(1f),
          factory = { WebView(it).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = (resources.configuration.fontScale * 100).toInt()
          } },
          update = { it.loadDataWithBaseURL(null, result.getOrThrow(), "text/html", "utf-8", null) },
          onRelease = { it.destroy() },
        )
      }
    }
  }
}
