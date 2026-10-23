package dev.peringer.limen.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.peringer.limen.data.PhotoSource
import dev.peringer.limen.data.allPhotoEvidence
import dev.peringer.limen.photo.PhotoDecode
import dev.peringer.limen.theme.Limen
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
 * actions. [onFile] receives a file in the cache that it must move or delete. [vm] is only
 * asked to hold a gallery copy and to say whether anything is still being stored. [owner] is
 * the defect id of an editor that can close while the pick is still arriving, and is blank
 * for the rest.
 */
@Composable
fun rememberPhotoIntake(s: Str, vm: LimenViewModel = viewModel(), owner: String = "", onFile: (File, PhotoSource) -> Unit): PhotoIntake {
  val context = LocalContext.current
  // The slot path survives process death on purpose: the camera app routinely kills this
  // process while the shutter is open, and the result still arrives afterwards.
  var cameraSlot by rememberSaveable { mutableStateOf<String?>(null) }

  val takePicture =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
      val slot = cameraSlot?.let(::File)
      cameraSlot = null
      // length, not exists: the slot is a temp file this app created, so it exists whatever
      // the camera did. An OEM camera that reports success without writing, or a full disk,
      // left a zero-byte file that the evidence hash then refused outright.
      if (ok && slot != null && slot.length() > 0L) onFile(slot, PhotoSource.Camera) else slot?.delete()
    }

  val pickVisual =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val slot = File(captureDir(context), "pick${System.currentTimeMillis()}.jpg")
        vm.copyPicked(uri, slot, owner) { onFile(it, PhotoSource.Gallery) }
      }
    }

  return remember(takePicture, pickVisual, context, s) {
    PhotoIntake(
      takePhoto = {
        val dir = captureDir(context)
        // Old slots are swept rather than reused: a fixed name plus a camera crash equals
        // a stale photo silently attached to the wrong finding. Not while a photograph is
        // being stored: the previous capture's slot is deleted only after it was copied, and
        // a pick's slot is still being written, so sweeping them lost a photograph the
        // camera had reported.
        if (vm.evidenceBusy == 0) dir.listFiles()?.forEach { it.delete() }
        val slot = File.createTempFile("cap", ".jpg", dir)
        cameraSlot = slot.absolutePath
        // Read from the package rather than written twice: the manifest declares the
        // authority as ${applicationId}.files, and the debug build carries a .uitest suffix,
        // so a hardcoded authority threw on every debug install the moment the key was hit.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", slot)
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
  var open by remember { mutableStateOf(false) }
  val evidence = vm.working?.allPhotoEvidence()?.firstOrNull { it.file == name }
  val subject = vm.working?.defects?.firstOrNull { name in it.photos }?.text?.take(120)
    ?: vm.working?.objects?.firstOrNull { name in it.photos }?.displayName(s) ?: s.overviewPhotos
  val caption = listOf(subject, evidence?.source?.let { s.words("Source", "Source", "Quelle", "Fonte", "Funtauna") + ": " + when (it) { PhotoSource.Camera -> s.photo; PhotoSource.Gallery -> s.gallery; PhotoSource.Unknown -> s.toVerify } },
    evidence?.importedEpochMs?.takeIf { it > 0 }?.let { s.importedOn + ": " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() })
    .filterNotNull().joinToString(" · ")
  if (open) PhotoViewer(file, caption, s) { open = false }
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
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    if (shown == null) {
      // The same square, empty, so nothing jumps when the picture arrives. A photograph that
      // never decodes stays this square, and the key below is then the only way to take it
      // off the record and clear the evidence issue it raises.
      Box(frame)
    } else {
      Image(
        bitmap = shown.asImageBitmap(),
        contentDescription = caption,
        contentScale = ContentScale.Crop,
        modifier = frame.clickable { open = true },
      )
    }
    // On a finalized record the photograph is evidence the fingerprint covers, so the
    // square is a picture and nothing else.
    if (LocalRecordLocked.current) return@Column
    KeySurface(
      // 44dp, not the 30dp it was: this is a destructive key and it was the one target in
      // the app below the size a thumb actually hits.
      modifier = Modifier.padding(top = 3.dp).size(48.dp),
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
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** The thumbnail read, through the same upright decoder the exported annex prints from. */
private fun decodeThumb(file: File): Bitmap? = PhotoDecode.sampled(file, 320)
