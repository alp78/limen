package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
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
  Chip(label, active = active, enabled = !active, onPress = onSelect)
}

/** Compact light/dark switch matching the shared outlined control language. */
@Composable
fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
  KeySurface(shape = RoundedCornerShape(20.dp), onPress = onToggle) {
    Box(
      Modifier
        .width(42.dp)
        .height(28.dp)
        .padding(3.dp)
        .clip(CircleShape)
        .background(if (dark) Limen.colors.action else Limen.colors.plateEdge),
    ) {
      Box(
        Modifier
          .size(22.dp)
          .align(if (dark) Alignment.CenterEnd else Alignment.CenterStart)
          .clip(CircleShape)
          .background(Limen.colors.onInk),
      ) {
        Text(
          if (dark) "☾" else "☼",
          fontWeight = FontWeight.Bold,
          color = Limen.colors.actionOutline,
          modifier = Modifier.align(Alignment.Center),
        )
      }
    }
  }
}
