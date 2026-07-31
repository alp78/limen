package dev.verso.limen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.verso.limen.theme.Limen

/**
 * The ink pad, Signet's proven capture brought over: quadratic-midpoint smoothing, a tap
 * detector so dots register (the crossing of a t never becomes a drag), a baseline guide,
 * and an alpha-bounding-box trim before the PNG leaves the pad. Always dark ink on white,
 * whatever the theme: the paper the signature lands on is white.
 */
private data class InkStroke(val points: List<Offset>, val width: Float)

private const val INK_WIDTH = 9f
private val INK_COLOR = Color(0xFF15181D)

@Composable
fun SignPadOverlay(
  title: String,
  meaning: String,
  s: Str,
  onKeep: (Bitmap) -> Unit,
  onCancel: () -> Unit,
) {
  val strokes = remember { mutableStateListOf<InkStroke>() }
  var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
  var padSize by remember { mutableStateOf(IntSize.Zero) }

  Box(Modifier.fillMaxSize().background(Limen.colors.plate.copy(alpha = 0.97f))) {
    Column(
      Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
      Spacer(Modifier.height(8.dp))
      // The one line that belongs above the ink, at reading size, never in small print.
      Text(meaning, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      Spacer(Modifier.height(14.dp))

      Box(
        Modifier
          .fillMaxWidth()
          .aspectRatio(2.1f)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.White)
          .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(12.dp))
          .pointerInput(Unit) {
            detectDragGestures(
              onDragStart = { start -> live = listOf(start) },
              onDrag = { change, _ ->
                change.consume()
                live = live + change.position
              },
              onDragEnd = {
                if (live.isNotEmpty()) strokes.add(InkStroke(live, INK_WIDTH))
                live = emptyList()
              },
              onDragCancel = { live = emptyList() },
            )
          }
          .pointerInput(Unit) {
            // Dots: a tap never registers as a drag, and initials have dots.
            detectTapGestures { p -> strokes.add(InkStroke(listOf(p), INK_WIDTH)) }
          },
      ) {
        Canvas(
          Modifier
            .fillMaxSize()
            .padding(0.dp)
        ) {
          padSize = IntSize(size.width.toInt(), size.height.toInt())
          // The baseline the hand expects on paper.
          drawLine(
            Color(0xFFD9D9D9),
            Offset(size.width * 0.07f, size.height * 0.72f),
            Offset(size.width * 0.93f, size.height * 0.72f),
            strokeWidth = 2f,
          )
          strokes.forEach { drawInk(it) }
          if (live.isNotEmpty()) drawInk(InkStroke(live, INK_WIDTH))
        }
      }

      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallKey(s.clearInk, Modifier.weight(1f)) {
          strokes.clear()
          live = emptyList()
        }
        SmallKey(s.cancel, Modifier.weight(1f), onPress = onCancel)
        SmallKey(s.saveInk, Modifier.weight(1.4f)) {
          val bmp = renderStrokes(strokes, padSize)
          if (bmp != null) onKeep(bmp)
        }
      }
    }
  }
}

private fun buildPath(points: List<Offset>): Path {
  val path = Path()
  if (points.isEmpty()) return path
  path.moveTo(points.first().x, points.first().y)
  if (points.size == 1) {
    // A dot: a stub so something renders.
    path.lineTo(points.first().x + 0.1f, points.first().y)
    return path
  }
  var prev = points.first()
  for (i in 1 until points.size) {
    val p = points[i]
    val mid = Offset((prev.x + p.x) / 2f, (prev.y + p.y) / 2f)
    path.quadraticTo(prev.x, prev.y, mid.x, mid.y)
    prev = p
  }
  path.lineTo(prev.x, prev.y)
  return path
}

private fun DrawScope.drawInk(stroke: InkStroke) {
  drawPath(
    buildPath(stroke.points),
    INK_COLOR,
    style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/** Replays the ink onto a transparent bitmap and trims it to what was actually written. */
private fun renderStrokes(strokes: List<InkStroke>, size: IntSize): Bitmap? {
  if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
  val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bmp)
  val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.rgb(0x15, 0x18, 0x1D)
    style = android.graphics.Paint.Style.STROKE
    strokeCap = android.graphics.Paint.Cap.ROUND
    strokeJoin = android.graphics.Paint.Join.ROUND
  }
  strokes.forEach { stroke ->
    paint.strokeWidth = stroke.width
    canvas.drawPath(buildPath(stroke.points).asAndroidPath(), paint)
  }
  return trim(bmp)
}

/** The alpha bounding box, with a little air, so the PNG is the signature and not the pad. */
private fun trim(bmp: Bitmap): Bitmap? {
  val w = bmp.width
  val h = bmp.height
  val pixels = IntArray(w * h)
  bmp.getPixels(pixels, 0, w, 0, 0, w, h)
  var minX = w
  var minY = h
  var maxX = -1
  var maxY = -1
  for (y in 0 until h) {
    for (x in 0 until w) {
      if (pixels[y * w + x] ushr 24 != 0) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
      }
    }
  }
  if (maxX < 0) return null
  val pad = 12
  minX = (minX - pad).coerceAtLeast(0)
  minY = (minY - pad).coerceAtLeast(0)
  maxX = (maxX + pad).coerceAtMost(w - 1)
  maxY = (maxY + pad).coerceAtMost(h - 1)
  return Bitmap.createBitmap(bmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
}
