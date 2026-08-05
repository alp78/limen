package dev.verso.limen.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.verso.limen.R
import dev.verso.limen.theme.Limen

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
