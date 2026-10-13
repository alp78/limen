package dev.peringer.limen.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
