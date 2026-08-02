package dev.verso.limen.ui

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.verso.limen.data.FixBy
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.Kind
import dev.verso.limen.data.PhotoSource
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.TenantResponse
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The defect editor, over the whole screen: a finding deserves the room a dialog does not
 * give it. The photos come from the SYSTEM camera through the FileProvider grant, into a
 * fresh cache slot per shot; the slot path survives in saveable state because the camera
 * app routinely kills this process while the shutter is open.
 */
@Composable
fun DefectEditor(vm: LimenViewModel, s: Str, p: Protocol, defectId: String) {
  val d = p.defects.firstOrNull { it.id == defectId } ?: return
  val context = LocalContext.current
  var cameraSlot by rememberSaveable { mutableStateOf<String?>(null) }

  val takePicture =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
      val slot = cameraSlot?.let(::File)
      cameraSlot = null
      if (ok && slot != null && slot.exists()) vm.attachPhoto(defectId, slot, PhotoSource.Camera)
    }

  val pickPhoto =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val slot = File(captureDir(context), "pick${System.currentTimeMillis()}.jpg")
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { input ->
            slot.outputStream().use { input.copyTo(it) }
          }
        }
        if (slot.exists()) vm.attachPhoto(defectId, slot, PhotoSource.Gallery)
      }
    }

  fun launchCamera() {
    val dir = captureDir(context)
    // Old slots are swept rather than reused: a fixed name plus a camera crash equals a
    // stale photo silently attached to the wrong defect.
    dir.listFiles()?.forEach { it.delete() }
    val slot = File.createTempFile("cap", ".jpg", dir)
    cameraSlot = slot.absolutePath
    val uri = FileProvider.getUriForFile(context, "dev.verso.limen.files", slot)
    try {
      takePicture.launch(uri)
    } catch (e: ActivityNotFoundException) {
      cameraSlot = null
      Toast.makeText(context, s.cameraOpenFailed, Toast.LENGTH_SHORT).show()
    }
  }

  BackHandler { vm.closeDefect() }

  Box(Modifier.fillMaxSize().background(Limen.colors.plate)) {
    Column(
      Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "‹",
          fontFamily = FontFamily.Monospace,
          fontSize = 24.sp,
          color = Limen.colors.ink,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { vm.closeDefect() }
            .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Column(Modifier.weight(1f)) {
          val room = p.rooms.firstOrNull { it.id == d.roomId }
          Text(
            (room?.let { r -> r.name.ifBlank { s.room(r.kind) } } ?: "").uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Limen.colors.ink,
          )
          d.item?.let {
            Text(s.item(it), style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
        }
      }

      Panel(label = s.defects) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Field(s.defectText, d.text, singleLine = false, hint = s.defectHint) { v ->
            vm.updateDefect(defectId) { it.copy(text = v) }
          }
        }
      }

      Panel(label = s.photos) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(s.photo, Modifier.weight(1f)) { launchCamera() }
            SmallKey(s.gallery, Modifier.weight(1f)) {
              pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
          }
          if (d.photos.isNotEmpty()) {
            Row(
              Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              d.photos.forEach { name ->
                PhotoThumb(vm, defectId, name)
              }
            }
          }
        }
      }

      if (p.kind == Kind.Exit) {
        Panel(label = s.tenantResponse) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillRow(
              TenantResponse.entries.map { it to s.tenantResponse(it) },
              d.tenantResponse,
              dense = true,
            ) { response -> vm.updateDefect(defectId) { it.copy(tenantResponse = response) } }
            Field(s.tenantComment, d.tenantComment, singleLine = false) { value ->
              vm.updateDefect(defectId) { it.copy(tenantComment = value) }
            }
            Text(s.normalWearNote, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
        }

        Panel(label = s.chargedToTenant, info = s.tipCharged()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(s.chargedToTenant, d.chargedToTenant, Modifier.fillMaxWidth()) {
              vm.updateDefect(defectId) { it.copy(chargedToTenant = !it.chargedToTenant) }
            }
            Text(s.fixByLabel, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            PillRow(
              listOf(
                FixBy.Open to s.fixOpen,
                FixBy.Tenant to s.role(dev.verso.limen.data.Role.Tenant),
                FixBy.Landlord to s.role(dev.verso.limen.data.Role.Landlord),
              ),
              d.fixBy,
              dense = true,
            ) { f -> vm.updateDefect(defectId) { it.copy(fixBy = f) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Field(s.fixDeadline, d.fixDeadline, Modifier.weight(1f)) { v ->
                vm.updateDefect(defectId) { it.copy(fixDeadline = v) }
              }
              Field(s.costNote, d.costNote, Modifier.weight(1.4f)) { v ->
                vm.updateDefect(defectId) { it.copy(costNote = v) }
              }
            }
            Field(s.reservationLabel, d.reservation) { v ->
              vm.updateDefect(defectId) { it.copy(reservation = v) }
            }
          }
        }
      }

      if (p.kind == Kind.Entry) {
        Panel(label = s.reservationLabel) {
          Field(s.reservationLabel, d.reservation) { value ->
            vm.updateDefect(defectId) { it.copy(reservation = value) }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallKey(s.delete, Modifier.weight(1f)) { vm.removeDefect(defectId) }
        SmallKey(s.done, Modifier.weight(1.6f)) { vm.closeDefect() }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

private fun captureDir(context: android.content.Context): File =
  File(context.cacheDir, "captures").apply { mkdirs() }

/** A small look at one photo, and the way to take it back off the record. */
@Composable
private fun PhotoThumb(vm: LimenViewModel, defectId: String, name: String) {
  val dir = vm.photoDir() ?: return
  val file = File(dir, name)
  // Decoded off this thread. Done inside the composition, as it was, opening a defect that
  // already carried three photos stalled the frame for as long as three JPEGs take, which
  // is the editor feeling heavy exactly when a hand is holding the phone up to a wall.
  val bitmap by produceState<android.graphics.Bitmap?>(null, file.absolutePath, file.length()) {
    value = withContext(Dispatchers.IO) { decodeThumb(file) }
  }
  val shown = bitmap
  if (shown == null) {
    // The same square, empty, so nothing jumps when the picture arrives.
    Box(
      Modifier
        .size(84.dp)
        .clip(RoundedCornerShape(8.dp))
        .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(10.dp)),
    )
    return
  }
  Box {
    Image(
      bitmap = shown.asImageBitmap(),
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .size(84.dp)
        .clip(RoundedCornerShape(8.dp))
        .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(10.dp)),
    )
    Text(
      "×",
      fontFamily = FontFamily.Monospace,
      fontSize = 14.sp,
      color = Limen.colors.onInk,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(3.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Limen.colors.ink)
        .clickable { vm.removePhoto(defectId, name) }
        .padding(horizontal = 7.dp, vertical = 1.dp),
    )
  }
}

/** The thumbnail read: bounds first, then a sample big enough for an 84 dp square. */
private fun decodeThumb(file: File): android.graphics.Bitmap? {
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
