package dev.verso.limen.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.verso.limen.data.PhotoSource
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The two ways a photograph reaches a record, held together so a defect and a supplied
 * object take one the same way.
 *
 * Both go through the SYSTEM camera and the system picker, which is why the manifest asks
 * for no CAMERA and no storage permission: the camera app writes into a cache slot this
 * app grants it through the FileProvider, and the picker hands back a single Uri.
 */
@Stable
class PhotoIntake(
  val takePhoto: () -> Unit,
  val pickPhoto: () -> Unit,
)

/**
 * Wires the launchers for one owner, a defect id or an object id, and returns the two
 * actions. [onFile] receives a file in the cache that it must move or delete.
 */
@Composable
fun rememberPhotoIntake(s: Str, onFile: (File, PhotoSource) -> Unit): PhotoIntake {
  val context = LocalContext.current
  // The slot path survives process death on purpose: the camera app routinely kills this
  // process while the shutter is open, and the result still arrives afterwards.
  var cameraSlot by rememberSaveable { mutableStateOf<String?>(null) }

  val takePicture =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
      val slot = cameraSlot?.let(::File)
      cameraSlot = null
      if (ok && slot != null && slot.exists()) onFile(slot, PhotoSource.Camera)
    }

  val pickVisual =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val slot = File(captureDir(context), "pick${System.currentTimeMillis()}.jpg")
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { input ->
            slot.outputStream().use { input.copyTo(it) }
          }
        }
        if (slot.exists()) onFile(slot, PhotoSource.Gallery)
      }
    }

  return remember(takePicture, pickVisual, context, s) {
    PhotoIntake(
      takePhoto = {
        val dir = captureDir(context)
        // Old slots are swept rather than reused: a fixed name plus a camera crash equals
        // a stale photo silently attached to the wrong finding.
        dir.listFiles()?.forEach { it.delete() }
        val slot = File.createTempFile("cap", ".jpg", dir)
        cameraSlot = slot.absolutePath
        val uri = FileProvider.getUriForFile(context, "dev.verso.limen.files", slot)
        try {
          takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
          cameraSlot = null
          Toast.makeText(context, s.cameraOpenFailed, Toast.LENGTH_SHORT).show()
        }
      },
      pickPhoto = {
        pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
    )
  }
}

private fun captureDir(context: Context): File =
  File(context.cacheDir, "captures").apply { mkdirs() }

/** A small look at one photo, and the way to take it back off the record. */
@Composable
fun PhotoThumb(vm: LimenViewModel, s: Str, name: String, size: Int = 84, onRemove: () -> Unit) {
  val dir = vm.photoDir() ?: return
  val file = File(dir, name)
  // Decoded off this thread. Done inside the composition, as it was, opening a defect that
  // already carried three photos stalled the frame for as long as three JPEGs take, which
  // is the editor feeling heavy exactly when a hand is holding the phone up to a wall.
  val bitmap by produceState<Bitmap?>(null, file.absolutePath, file.length()) {
    value = withContext(Dispatchers.IO) { decodeThumb(file) }
  }
  val shown = bitmap
  val frame = Modifier
    .size(size.dp)
    .clip(RoundedCornerShape(8.dp))
    .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(10.dp))
  if (shown == null) {
    // The same square, empty, so nothing jumps when the picture arrives.
    Box(frame)
    return
  }
  Box {
    Image(
      bitmap = shown.asImageBitmap(),
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = frame,
    )
    KeySurface(
      modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(30.dp),
      shape = RoundedCornerShape(8.dp),
      fillColor = Limen.colors.danger,
      outlineColor = Limen.colors.dangerOutline,
      tick = Tick.Reject,
      onPress = onRemove,
    ) {
      Icon(
        ButtonGlyph.Delete.vector(),
        contentDescription = s.delete,
        tint = Limen.colors.dangerOutline,
        modifier = Modifier.size(15.dp),
      )
    }
  }
}

/** The thumbnail read: bounds first, then a sample big enough for the square shown. */
private fun decodeThumb(file: File): Bitmap? {
  if (!file.exists()) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, bounds)
  if (bounds.outWidth <= 0) return null
  val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 320).coerceAtLeast(1)
  return BitmapFactory.decodeFile(
    file.absolutePath,
    BitmapFactory.Options().apply { inSampleSize = sample },
  )
}
