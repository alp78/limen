package dev.peringer.limen.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What an export produced: where it is, what it is called, how heavy it got. */
data class ExportedFile(val uri: Uri, val displayName: String, val bytes: Long, val companionUri: Uri? = null, val sha256: String = "")

internal fun exportStamp(): String =
  SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

/**
 * The names already in Download/Limen, as far as this app can see them, so a second export
 * of the day takes a numbered name rather than the " (1)" MediaStore would append on its own
 * while the history still recorded the plain one.
 */
internal fun downloadNames(context: Context): Set<String> {
  val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
  val names = mutableSetOf<String>()
  context.contentResolver.query(
    collection, arrayOf(MediaStore.Downloads.DISPLAY_NAME),
    "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf("Download/Limen/"), null,
  )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(names::add) }
  return names
}

/**
 * Writes a file into the public Downloads collection under Download/Limen via MediaStore,
 * the same helper the rest of the portfolio ships: IS_PENDING while writing, the row
 * deleted again on failure, no storage permission needed on API 29+.
 */
internal fun writeToDownloads(
  context: Context,
  name: String,
  mime: String,
  write: (OutputStream) -> Unit,
): ExportedFile {
  val resolver = context.contentResolver
  val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
  val values = ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, name)
    put(MediaStore.Downloads.MIME_TYPE, mime)
    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Limen")
    put(MediaStore.Downloads.IS_PENDING, 1)
  }
  val uri = resolver.insert(collection, values)
    ?: throw IllegalStateException("MediaStore refused the export row")
  var bytes = 0L
  try {
    resolver.openOutputStream(uri)?.use(write)
      ?: throw IllegalStateException("MediaStore returned no stream")
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    // Inside the try: the file is already visible here, and a failure must not leave it in Downloads.
    resolver.query(uri, arrayOf(MediaStore.Downloads.SIZE), null, null, null)?.use { c ->
      if (c.moveToFirst()) bytes = c.getLong(0)
    }
  } catch (t: Throwable) {
    resolver.delete(uri, null, null)
    throw t
  }
  return ExportedFile(uri, name, bytes)
}
