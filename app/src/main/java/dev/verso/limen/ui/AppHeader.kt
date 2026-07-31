package dev.verso.limen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.verso.limen.theme.Limen

/**
 * The instrument's masthead: the mark on the left, which is the way back to the case, then
 * the faces, then day/night. Shared by every face so nothing moves when they are switched.
 */
@Composable
fun AppHeader(vm: LimenViewModel, s: Str) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable { vm.selectMode(AppMode.Home) }
        .padding(5.dp)
    ) {
      LimenMark(size = 26.dp)
    }
    Spacer(Modifier.width(6.dp))
    ModePill(s.faceValue, vm.appMode == AppMode.Value) { vm.selectMode(AppMode.Value) }
    Spacer(Modifier.width(4.dp))
    ModePill(s.faceClocks, vm.appMode == AppMode.Clocks) { vm.selectMode(AppMode.Clocks) }
    Spacer(Modifier.weight(1f))
    LangMenu(vm.lang, vm::selectLang)
    Spacer(Modifier.width(4.dp))
    ThemeToggle(vm.darkTheme, vm::toggleTheme)
  }
}

@Composable
private fun ModePill(label: String, active: Boolean, onSelect: () -> Unit) {
  Chip(label, active = active, onPress = onSelect)
}

/**
 * Day/night. A half-filled disc: the one glyph that means contrast rather than standing
 * for a sun or a moon, and it stays legible at this size in either scheme.
 */
@Composable
fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
  val ink = Limen.colors.inkDim
  Canvas(
    Modifier
      .size(34.dp)
      .clip(CircleShape)
      .clickable(onClick = onToggle)
      .padding(9.dp)
  ) {
    val r = size.minDimension / 2f
    drawCircle(color = ink, radius = r, style = Stroke(width = 1.6f * density))
    drawArc(
      color = ink,
      startAngle = if (dark) 90f else 270f,
      sweepAngle = 180f,
      useCenter = true,
      topLeft = Offset(center.x - r, center.y - r),
      size = Size(r * 2, r * 2),
    )
  }
}
