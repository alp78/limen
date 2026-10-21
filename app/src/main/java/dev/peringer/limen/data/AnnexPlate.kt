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

  /** The content width of the page, 1240 units less both margins. */
  const val MAX_WIDTH = 1060.0
  const val MAX_HEIGHT = 530.0

  /**
   * One unit is 0.169 mm on the 1240-unit A4 page, so two pixels per unit is 300 dpi, the
   * resolution a printed photograph stops looking like a screen capture at.
   */
  private const val PIXELS_PER_UNIT = 2.0

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
