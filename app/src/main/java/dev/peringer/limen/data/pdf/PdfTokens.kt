package dev.peringer.limen.data.pdf

import dev.peringer.limen.theme.LimenPalette

/**
 * The printed record's design tokens, every one derived from the app's own: the Day
 * palette, the theme's sp sizes, its dp radii and paddings. The document is drawn on a
 * 1240 × 1754 unit grid, A4 at 150 dpi, which the iPhone draws on as well, so these
 * numbers are the contract between the two ports.
 *
 * Pure Kotlin: no Paint, no Color, so the scale and the palette are tested on the JVM.
 */
internal object PdfTokens {

  const val W = 1240f
  const val H = 1754f
  const val MARGIN = 90f
  const val FOOT = 60f
  /** Where the body starts under the running header. */
  const val HEADER_BOTTOM = 130f
  /** Where the body ends above the footer. */
  const val BOTTOM = H - FOOT - 30f

  /**
   * Units per dp. A dp is 1/160 inch and a unit 1/150, so a one-to-one print would set
   * the 15sp body at 6.75pt; 1.6 sets it at 24 units, 11.5pt, the size paper forms use,
   * and carries every radius and padding up by the same factor.
   */
  const val U = 1.6f
  fun dp(value: Float): Float = value * U

  // Type, from the theme's DocumentTypography in sp.
  val DISPLAY = dp(42f)
  val TITLE = dp(21f)
  val BODY_LARGE = dp(16f)
  val BODY = dp(15f)
  val LABEL_LARGE = dp(13f)
  val LABEL_SMALL = dp(12f)
  val BODY_LINE = dp(21f)
  val BODY_LARGE_LINE = dp(23f)
  /** The line of a style the theme gives no line height. */
  const val LINE = 1.35f
  // Tracking as the em fraction Paint.letterSpacing takes: the theme's sp over the size.
  const val DISPLAY_TRACK = -0.5f / 42f
  const val LABEL_LARGE_TRACK = 0.3f / 13f
  const val LABEL_SMALL_TRACK = 0.2f / 12f

  // Shapes, from the theme's Shapes and the radii the screens repeat.
  val PANEL_RADIUS = dp(12f)
  val DOOR_RADIUS = dp(14f)
  val CHIP_RADIUS = dp(10f)
  val THUMB_RADIUS = dp(8f)
  val PILL_RADIUS = dp(6f)
  const val BADGE_RADIUS = 8f
  val BORDER = dp(1.5f)
  val PANEL_PAD = dp(16f)
  val PANEL_GAP = dp(10f)
  val LABEL_GAP = dp(8f)
  val CHIP_GAP = dp(6f)
  /** A card's padding in the annex: it leaves [dev.peringer.limen.data.AnnexPlate.MAX_WIDTH] for the photograph. */
  const val FRAME_PAD = 20f

  // Colours, ARGB.
  const val PAPER = -1
  val panel = LimenPalette.PLATE_RAISED.toInt()
  val input = LimenPalette.INPUT.toInt()
  val edge = LimenPalette.PLATE_EDGE.toInt()
  val navy = LimenPalette.NAVY.toInt()
  val ink = LimenPalette.INK.toInt()
  val inkDim = LimenPalette.INK_DIM.toInt()
  val warning = LimenPalette.WARNING.toInt()
  val warningOutline = LimenPalette.WARNING_OUTLINE.toInt()
  val danger = LimenPalette.DANGER.toInt()
  val dangerOutline = LimenPalette.DANGER_OUTLINE.toInt()
  val positive = LimenPalette.POSITIVE.toInt()
  /**
   * Lettering on the navy banner at the landing's 88 % white, flattened in advance: an
   * alpha on a paint would make PdfDocument emit a transparency state for it.
   */
  const val ON_NAVY_DIM = 0xFFE5E7EA.toInt()

  /** The one family the record is set in, as the app is. */
  const val FAMILY = "sans-serif"
}
