package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.verso.limen.R
import dev.verso.limen.theme.Limen

/**
 * The instrument's masthead keeps language and day/night together. The protocol walk itself
 * holds every working control.
 */
@Composable
fun AppHeader(vm: LimenViewModel, s: Str) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .clip(RoundedCornerShape(8.dp))
        .padding(5.dp)
    ) {
      LimenMark(size = 42.dp)
    }
    Spacer(Modifier.weight(1f))
    LangMenu(vm.lang, vm::selectLang)
    Spacer(Modifier.width(4.dp))
    ThemeToggle(vm.darkTheme, vm::toggleTheme)
  }
}

/** Compact mode control using Material's native track and thumb, not a switch inside a button. */
@Composable
fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
  Switch(
    checked = dark,
    onCheckedChange = { onToggle() },
    modifier = Modifier.width(52.dp),
    thumbContent = {
      if (dark) {
        Icon(
          imageVector = Icons.Outlined.DarkMode,
          contentDescription = "Dark mode",
          modifier = Modifier.size(SwitchDefaults.IconSize),
          tint = Limen.colors.action,
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_theme_sun),
          contentDescription = "Light mode",
          modifier = Modifier.size(SwitchDefaults.IconSize),
          tint = Color.Unspecified,
        )
      }
    },
    colors = SwitchDefaults.colors(
      checkedThumbColor = Limen.colors.onInk,
      checkedTrackColor = Limen.colors.action,
      checkedBorderColor = Color.Transparent,
      checkedIconColor = Limen.colors.action,
      uncheckedThumbColor = Limen.colors.plate,
      uncheckedTrackColor = Limen.colors.plateEdge,
      uncheckedBorderColor = Color.Transparent,
      uncheckedIconColor = Limen.colors.action,
    ),
  )
}
