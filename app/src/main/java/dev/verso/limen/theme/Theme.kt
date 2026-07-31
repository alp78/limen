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
 * The corridor and the document, almost colourless.
 *
 * A handover happens in an empty flat: plaster walls, bare floors, a paper protocol on a
 * windowsill. Day is ink on warm plaster; night inverts to bone on a dark stairwell.
 * Hierarchy is carried by size and weight, and the one colour is [seal], a deep enamel
 * green, and it means exactly one thing: this part of the record is signed, agreed,
 * settled. A defect is not red and a draft is not grey; only settledness wears colour,
 * because in this instrument the signature is the whole point.
 */
@Immutable
data class LimenColors(
  val plate: Color,
  val plateRaised: Color,
  val plateEdge: Color,
  /** All lettering. A fact, never a status. */
  val ink: Color,
  val inkDim: Color,
  /** Lettering on top of an [ink] fill: the active pill, the pressed key. */
  val onInk: Color,
  /** Signed, agreed, settled. The only colour in the instrument. */
  val seal: Color,
  /** Lettering on a [seal] fill. */
  val onSeal: Color,
  val isDark: Boolean,
)

val Day = LimenColors(
  plate = Color(0xFFF0EDE6),
  plateRaised = Color(0xFFFBF9F3),
  plateEdge = Color(0xFFD6D2C5),
  ink = Color(0xFF262620),
  inkDim = Color(0xFF6C695B),
  onInk = Color(0xFFFBF9F3),
  seal = Color(0xFF175E54),
  onSeal = Color(0xFFFBF9F3),
  isDark = false,
)

val Night = LimenColors(
  plate = Color(0xFF1D1E1A),
  plateRaised = Color(0xFF272822),
  plateEdge = Color(0xFF3C3E35),
  ink = Color(0xFFE9E7DD),
  inkDim = Color(0xFF9C9A8B),
  onInk = Color(0xFF1D1E1A),
  seal = Color(0xFF63BCA6),
  onSeal = Color(0xFF1D1E1A),
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
      primary = ink,
      onPrimary = onInk,
      primaryContainer = inkDim,
      onPrimaryContainer = ink,
      secondary = inkDim,
      onSecondary = plate,
      secondaryContainer = plateEdge,
      onSecondaryContainer = ink,
      tertiary = seal,
      onTertiary = onSeal,
      background = plate,
      onBackground = ink,
      surface = plateRaised,
      onSurface = ink,
      surfaceVariant = plateEdge,
      onSurfaceVariant = inkDim,
      outline = plateEdge,
    )
  } else {
    lightColorScheme(
      primary = ink,
      onPrimary = onInk,
      primaryContainer = inkDim,
      onPrimaryContainer = ink,
      secondary = inkDim,
      onSecondary = plateRaised,
      secondaryContainer = plateEdge,
      onSecondaryContainer = ink,
      tertiary = seal,
      onTertiary = onSeal,
      background = plate,
      onBackground = ink,
      surface = plateRaised,
      onSurface = ink,
      surfaceVariant = plateEdge,
      onSurfaceVariant = inkDim,
      outline = plateEdge,
    )
  }

private val DocumentShapes =
  Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
  )

/**
 * Monospace where figures are read against each other: meter readings, key counts, dates,
 * the Zeitwert arithmetic. Sans for prose. A protocol is a document; it should set like one.
 */
private val DocumentTypography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Light,
      fontSize = 64.sp,
      letterSpacing = (-1).sp,
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
      fontSize = 18.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontSize = 17.sp,
      lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontSize = 16.sp,
      lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontSize = 12.5.sp,
      letterSpacing = 0.4.sp,
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
