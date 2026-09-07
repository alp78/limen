package dev.peringer.limen.ui

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.peringer.limen.R
import dev.peringer.limen.theme.Limen

/** Compact mode control using Material's native track and thumb, not a switch inside a button. */
@Composable
fun ThemeToggle(dark: Boolean, description: String, onToggle: () -> Unit) {
  // Material's Switch carries no haptics of its own: there is not one reference to them
  // anywhere in material3 1.4.0, so without this the only control on the screen that is
  // not a KeySurface is also the only one that feels dead.
  val play = rememberTick()
  Switch(
    checked = dark,
    onCheckedChange = { checked ->
      play(if (checked) Tick.ToggleOn else Tick.ToggleOff)
      onToggle()
    },
    modifier = Modifier.width(52.dp).semantics { contentDescription = description },
    thumbContent = {
      if (dark) {
        Icon(
          imageVector = Icons.Outlined.DarkMode,
          contentDescription = null,
          modifier = Modifier.size(SwitchDefaults.IconSize),
          tint = Limen.colors.action,
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_theme_sun),
          contentDescription = null,
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
