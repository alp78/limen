package dev.peringer.limen.theme

/**
 * The Day palette as plain ARGB values. The Compose theme reads them for the screen and
 * the printed record reads them for the paper, so the two cannot drift: a navy changed
 * here changes both. Pure Kotlin on purpose, because the document's tokens are tested on
 * the JVM and the data layer imports nothing from Compose.
 */
object LimenPalette {
  const val PLATE = 0xFFF6F7F9
  const val PLATE_RAISED = 0xFFE7E9EE
  const val INPUT = 0xFFEFF1F4
  const val PLATE_EDGE = 0xFFD7DAE0
  /** The mark's navy: action, outline, landing and seal are one colour by day. */
  const val NAVY = 0xFF233350
  const val INK = 0xFF1A1D23
  const val INK_DIM = 0xFF5B6472
  const val WARNING = 0xFFFCEBD2
  const val WARNING_OUTLINE = 0xFF835000
  const val POSITIVE = 0xFF287A42
  const val DANGER = 0xFFFDE8E8
  const val DANGER_OUTLINE = 0xFFC94242
  const val DISABLED_INK = 0xFFA7ADB8
}
