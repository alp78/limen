package dev.verso.limen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.verso.limen.theme.Limen

/**
 * The mark: a doorframe seen straight on, and under it, heavier than anything else, the
 * sill. Limen is Latin for exactly that stone. The key hangs in the opening because the
 * handover is the moment the keys change hands. Drawn in ink; the seal green appears
 * nowhere here, because a mark is not a signature.
 *
 * Placeholder geometry until the identity round with the user; the launcher icon will
 * follow whatever this becomes.
 */
@Composable
fun LimenMark(size: Dp = 30.dp, modifier: Modifier = Modifier) {
  val ink = Limen.colors.ink
  val dim = Limen.colors.inkDim

  Canvas(modifier.size(size)) {
    val w = this.size.minDimension
    val stroke = w * 0.075f

    // The jambs and the lintel: an open doorframe.
    val left = w * 0.16f
    val right = w * 0.84f
    val top = w * 0.10f
    val bottom = w * 0.78f
    drawLine(ink, Offset(left, bottom), Offset(left, top), stroke, StrokeCap.Round)
    drawLine(ink, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
    drawLine(ink, Offset(right, top), Offset(right, bottom), stroke, StrokeCap.Round)

    // The sill, wider and heavier than the frame: the limen itself.
    drawLine(
      ink,
      Offset(w * 0.06f, w * 0.90f),
      Offset(w * 0.94f, w * 0.90f),
      stroke * 1.7f,
      StrokeCap.Round,
    )

    // The key in the opening: bow, stem, and two bits.
    val kx = w * 0.5f
    drawCircle(dim, radius = w * 0.075f, center = Offset(kx, w * 0.315f), style = Stroke(stroke * 0.85f))
    drawLine(dim, Offset(kx, w * 0.39f), Offset(kx, w * 0.66f), stroke * 0.85f, StrokeCap.Round)
    drawLine(dim, Offset(kx, w * 0.575f), Offset(kx + w * 0.085f, w * 0.575f), stroke * 0.85f, StrokeCap.Round)
    drawLine(dim, Offset(kx, w * 0.66f), Offset(kx + w * 0.115f, w * 0.66f), stroke * 0.85f, StrokeCap.Round)
  }
}
