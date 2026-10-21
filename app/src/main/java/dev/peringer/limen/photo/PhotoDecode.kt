package dev.peringer.limen.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * One rotation-aware read of a photograph on disk.
 *
 * The editor's thumbnails and the exported annex used to hold a decoder each, and only the
 * export applied EXIF rotation: a capture taken sideways showed sideways in the app and
 * printed upright on the paper, which reads as the export having altered the evidence.
 */
object PhotoDecode {

  /** True when the file decodes at all, which is what the annex's two passes must agree on. */
  fun readable(file: File): Boolean = sampled(file, 256)?.let { it.recycle(); true } ?: false

  /**
   * The photograph at roughly [targetWidth], turned upright. Sampling happens during the
   * decode rather than after it, so a twelve-megapixel capture never occupies full size in
   * memory: three of those at once is the allocation an inspector's phone actually refuses.
   */
  fun sampled(file: File, targetWidth: Int): Bitmap? {
    val (width, height) = bounds(file) ?: return null
    if (targetWidth <= 0) return null
    val sample = (maxOf(width, height) / targetWidth).coerceAtLeast(1)
    val bitmap = runCatching {
      BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
      )
    }.getOrNull() ?: return null
    return upright(bitmap, file)
  }

  /** The photograph's pixel size once turned upright, read from its header without decoding it. */
  fun uprightSize(file: File): Pair<Int, Int>? {
    val (width, height) = bounds(file) ?: return null
    return if (quarterTurned(file)) height to width else width to height
  }

  /**
   * The photograph upright at exactly [width] × [height] pixels, both upright dimensions,
   * which is what the PDF annex prints. Sampling during the decode only gets within a factor
   * of two, because BitmapFactory rounds the sample down to a power of two: the long edge of
   * a twelve-megapixel capture asked for 510 came out anywhere from 510 to 1,020. One
   * filtered scale afterwards makes the size exact.
   */
  fun uprightAtSize(file: File, width: Int, height: Int): Bitmap? {
    val (rawWidth, rawHeight) = bounds(file) ?: return null
    if (width <= 0 || height <= 0) return null
    val (targetWidth, targetHeight) = if (quarterTurned(file)) height to width else width to height
    var sample = 1
    while (rawWidth / (sample * 2) >= targetWidth && rawHeight / (sample * 2) >= targetHeight) sample *= 2
    val decoded = runCatching {
      BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true },
      )
    }.getOrNull() ?: return null
    withoutGainmap(decoded)
    val scaled = if (decoded.width == targetWidth && decoded.height == targetHeight) decoded else {
      val exact = runCatching { Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true) }.getOrNull()
      decoded.recycle()
      exact ?: return null
    }
    return upright(scaled, file)
  }

  /**
   * An Ultra HDR capture, which is what a Pixel takes by default, decodes with its gain map
   * attached. Drawn into PdfDocument, such a bitmap goes through a tone-mapping shader the
   * PDF cannot hold, and up to 1.2.2 every plate came out as a page-sized image at 72 dpi.
   * Compressed to JPEG, it writes the map along as a second image that a PDF reader never
   * shows. The base image under the map is the standard-range picture, the one paper prints.
   */
  private fun withoutGainmap(bitmap: Bitmap) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) bitmap.gainmap = null
  }

  /** A quarter turn swaps the edges: the annex fits the picture as it will stand on paper. */
  private fun quarterTurned(file: File): Boolean =
    runCatching { ExifInterface(file.absolutePath).rotationDegrees % 180 != 0 }.getOrDefault(false)

  private fun bounds(file: File): Pair<Int, Int>? {
    if (!file.isFile) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return options.outWidth to options.outHeight
  }

  private fun upright(bitmap: Bitmap, file: File): Bitmap {
    val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bitmap
    val matrix = Matrix()
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
      else -> return bitmap
    }
    val rotated = runCatching {
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrNull() ?: return bitmap
    if (rotated != bitmap) bitmap.recycle()
    return rotated
  }
}
