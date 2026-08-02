package dev.verso.limen.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The product palette sampled from the Limen identity system. Both schemes stay neutral
 * grey, while the mark's navy becomes the consistent outline and action colour.
 */
@Immutable
data class LimenColors(
  val plate: Color,
  val plateRaised: Color,
  val input: Color,
  val plateEdge: Color,
  val actionOutline: Color,
  val action: Color,
  /** The fixed navy field used by the home landing in both display modes. */
  val landing: Color,
  /** All lettering. A fact, never a status. */
  val ink: Color,
  val inkDim: Color,
  /** Lettering on top of an [ink] fill: the active pill, the pressed key. */
  val onInk: Color,
  /** Signed, agreed, settled. The only colour in the instrument. */
  val seal: Color,
  /** Lettering on a [seal] fill. */
  val onSeal: Color,
  val warning: Color,
  val warningOutline: Color,
  val positive: Color,
  val onPositive: Color,
  val danger: Color,
  val dangerOutline: Color,
  val disabled: Color,
  val disabledInk: Color,
  val isDark: Boolean,
)

val Day = LimenColors(
  plate = Color(0xFFF6F7F9),
  plateRaised = Color(0xFFE7E9EE),
  input = Color(0xFFEFF1F4),
  plateEdge = Color(0xFFD7DAE0),
  actionOutline = Color(0xFF233350),
  action = Color(0xFF233350),
  landing = Color(0xFF233350),
  ink = Color(0xFF1A1D23),
  inkDim = Color(0xFF5B6472),
  onInk = Color.White,
  seal = Color(0xFF233350),
  onSeal = Color.White,
  warning = Color(0xFFFCEBD2),
  warningOutline = Color(0xFFB26A00),
  positive = Color(0xFF287A42),
  onPositive = Color.White,
  danger = Color(0xFFFDE8E8),
  dangerOutline = Color(0xFFC94242),
  disabled = Color(0xFFE7E9EE),
  disabledInk = Color(0xFFA7ADB8),
  isDark = false,
)

val Night = LimenColors(
  plate = Color(0xFF1C1F24),
  plateRaised = Color(0xFF2A2D33),
  input = Color(0xFF24272D),
  plateEdge = Color(0xFF3A3E46),
  actionOutline = Color(0xFF7C93C4),
  action = Color(0xFF4863A0),
  landing = Color(0xFF233350),
  ink = Color(0xFFF2F3F5),
  inkDim = Color(0xFFA7ADB8),
  onInk = Color.White,
  seal = Color(0xFF4863A0),
  onSeal = Color.White,
  warning = Color(0xFF3A2D1C),
  warningOutline = Color(0xFFFFB74D),
  positive = Color(0xFF57C778),
  onPositive = Color(0xFF102216),
  danger = Color(0xFF3B262A),
  dangerOutline = Color(0xFFFF7878),
  disabled = Color(0xFF2A2D33),
  disabledInk = Color(0xFF5B6472),
  isDark = true,
)

val LocalLimenColors = staticCompositionLocalOf { Day }

object Limen {
  val colors: LimenColors
    @Composable @ReadOnlyComposable get() = LocalLimenColors.current
}

private fun LimenColors.toScheme() =
  if (isDark) {
    darkColorScheme(
      primary = action,
      onPrimary = onInk,
      primaryContainer = action,
      onPrimaryContainer = onInk,
      secondary = actionOutline,
      onSecondary = ink,
      secondaryContainer = input,
      onSecondaryContainer = ink,
      tertiary = seal,
      onTertiary = onSeal,
      background = plate,
      onBackground = ink,
      surface = plateRaised,
      onSurface = ink,
      surfaceVariant = input,
      onSurfaceVariant = inkDim,
      outline = actionOutline,
    )
  } else {
    lightColorScheme(
      primary = action,
      onPrimary = onInk,
      primaryContainer = action,
      onPrimaryContainer = onInk,
      secondary = actionOutline,
      onSecondary = onInk,
      secondaryContainer = input,
      onSecondaryContainer = ink,
      tertiary = seal,
      onTertiary = onSeal,
      background = plate,
      onBackground = ink,
      surface = plateRaised,
      onSurface = ink,
      surfaceVariant = input,
      onSurfaceVariant = inkDim,
      outline = actionOutline,
    )
  }

private val DocumentShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
  )

/**
 * Clear sans hierarchy for field work, with monospaced figures where readings and dates need
 * to line up at a glance.
 */
private val DocumentTypography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 42.sp,
      letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 21.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontSize = 16.sp,
      lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontSize = 15.sp,
      lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      letterSpacing = 0.2.sp,
    ),
  )

@Composable
fun LimenTheme(dark: Boolean = false, content: @Composable () -> Unit) {
  val colors = if (dark) Night else Day
  CompositionLocalProvider(LocalLimenColors provides colors) {
    MaterialTheme(
      colorScheme = colors.toScheme(),
      shapes = DocumentShapes,
      typography = DocumentTypography,
      content = content,
    )
  }
}
