package dev.verso.limen.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decodes a photographed scene for marking: downsampled so a 50-megapixel shot does not
 * sink the canvas, and rotated the way the camera said it was held. Marks live in the
 * pixels of exactly this bitmap, so the homography needs no other bookkeeping.
 */
object PhotoLoad {

  fun load(resolver: ContentResolver, uri: Uri, maxDim: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim / 2 &&
      maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1
    ) {
      if (maxOf(bounds.outWidth, bounds.outHeight) / sample <= maxDim) break
      sample *= 2
    }

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
      ?: return null

    val rotation = resolver.openInputStream(uri)?.use { stream ->
      when (ExifInterface(stream).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
      )) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
    } ?: 0f
    if (rotation == 0f) return raw

    val m = Matrix().apply { postRotate(rotation) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    if (rotated !== raw) raw.recycle()
    return rotated
  }
}
