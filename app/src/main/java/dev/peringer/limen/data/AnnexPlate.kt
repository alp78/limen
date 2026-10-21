package dev.peringer.limen.data

import kotlin.math.roundToInt

/**
 * The size a photograph prints at in the annex, and the pixels it is decoded to for that.
 *
 * The iPhone port computes the same numbers from the same rule and asserts the same table,
 * so a record exported on either phone carries the same plates at the same resolution.
 * Pure arithmetic on purpose: the decoding needs Android, the decision does not.
 */
object AnnexPlate {

  /**
   * The width inside an annex card's frame, 1240 units less both margins and the frame's
   * padding, and the height that still leaves the card's legend and a section title on the
   * page. A landscape photograph prints about 17 cm wide, a portrait one 19 cm high.
   */
  const val MAX_WIDTH = 1020.0
  const val MAX_HEIGHT = 1100.0

  /**
   * 200 dpi. One unit is 0.169 mm on the 1240-unit A4 page, so one pixel per unit is 150
   * dpi. Thirty photographs made a 137 MB PDF at 300 dpi lossless, and make 7.7 MB at 200
   * dpi as JPEG at [JPEG_QUALITY].
   */
  private const val PIXELS_PER_UNIT = 200.0 / 150.0

  /** The plates' JPEG quality, the same in the PDF and in its HTML copy. */
  const val JPEG_QUALITY = 85

  /** [boxWidth] × [boxHeight] in page units; [pixelWidth] × [pixelHeight] decoded, both upright. */
  data class Fit(val boxWidth: Float, val boxHeight: Float, val pixelWidth: Int, val pixelHeight: Int)

  /** [width] × [height] are the photograph's upright pixel dimensions, read before decoding. */
  fun fit(width: Int, height: Int): Fit {
    val scale = minOf(MAX_WIDTH / width, MAX_HEIGHT / height)
    val boxWidth = width * scale
    val boxHeight = height * scale
    // A picture smaller than its box is drawn as it is: enlarging adds pixels and no detail.
    val long = minOf(maxOf(width, height), (PIXELS_PER_UNIT * maxOf(boxWidth, boxHeight)).roundToInt())
    val (pixelWidth, pixelHeight) =
      if (width >= height) long to (long.toDouble() * height / width).roundToInt()
      else (long.toDouble() * width / height).roundToInt() to long
    return Fit(boxWidth.toFloat(), boxHeight.toFloat(), pixelWidth, pixelHeight)
  }
}
