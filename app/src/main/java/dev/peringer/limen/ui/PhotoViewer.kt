package dev.peringer.limen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.peringer.limen.photo.PhotoDecode
import dev.peringer.limen.theme.Limen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PhotoViewer(file: File, caption: String, s: Str, onClose: () -> Unit) {
  val bitmap by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { PhotoDecode.sampled(file, 2048) } }
  DisposableEffect(bitmap) { val retained = bitmap; onDispose { retained?.recycle() } }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(Modifier.fillMaxSize().background(Limen.colors.plate).safeDrawingPadding().padding(12.dp)) {
      Text(caption, color = Limen.colors.ink)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallKey(s.words("Zoom out", "Réduire", "Verkleinern", "Riduci", "Empitschnir"), enabled = scale > 1f) { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) offset = androidx.compose.ui.geometry.Offset.Zero }
        SmallKey(s.words("Zoom in", "Agrandir", "Vergrössern", "Ingrandisci", "Engrondir"), enabled = scale < 5f) { scale = (scale + 0.5f).coerceAtMost(5f) }
        SmallKey(s.words("Fit image", "Image entière", "Ganzes Bild", "Immagine intera", "Entir maletg")) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero }
        SmallKey(s.done, onPress = onClose)
      }
      bitmap?.let { image -> Image(image.asImageBitmap(), caption, contentScale = ContentScale.Fit,
        modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset += pan }
        }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) }
    }
  }
}
