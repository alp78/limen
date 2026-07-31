package dev.verso.limen.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.DeviceKey
import dev.verso.limen.data.ExportedFile
import dev.verso.limen.data.Role
import dev.verso.limen.data.Seal
import dev.verso.limen.data.exportStamp
import dev.verso.limen.data.writeToDownloads
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * The other side of the seal exchange, running on the OTHER party's phone: receive the
 * exported PDF by any direct share, pick it here, and this phone's key signs its digest.
 * The little .limensig file goes back the same way and the first phone attaches it.
 * No account, no network; the trust is two keys over one digest.
 */
@Composable
fun CounterSignScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var pdfName by rememberSaveable { mutableStateOf("") }
  var pdfHash by rememberSaveable { mutableStateOf("") }
  var name by rememberSaveable { mutableStateOf("") }
  var role by rememberSaveable { mutableStateOf(Role.Landlord) }
  var written by androidx.compose.runtime.remember { mutableStateOf<ExportedFile?>(null) }

  BackHandler { vm.closeCounterSign() }

  val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val (n, h) = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
          val buf = ByteArray(64 * 1024)
          while (true) {
            val read = input.read(buf)
            if (read < 0) break
            digest.update(buf, 0, read)
          }
        }
        val display = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
          val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
          if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: ""
        display to digest.digest().joinToString("") { "%02x".format(it) }
      }
      pdfName = n
      pdfHash = h
      written = null
    }
  }

  Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    AppHeader(vm, s)
    Spacer(Modifier.height(12.dp))
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Panel(label = s.counterSealLabel, info = s.tipCounterSign()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          BigKey(s.pickPdf, active = pdfHash.isBlank(), Modifier.fillMaxWidth()) {
            pickPdf.launch(arrayOf("application/pdf"))
          }
          if (pdfHash.isNotBlank()) {
            Text(pdfName, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
            Row {
              Text(
                "${s.pdfFingerprint}  ",
                style = MaterialTheme.typography.labelSmall,
                color = Limen.colors.inkDim,
              )
              Text(
                Seal.fingerprint(pdfHash),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = Limen.colors.ink,
              )
            }
          }
        }
      }

      if (pdfHash.isNotBlank()) {
        Panel(label = s.stepSign) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(s.signerName, name) { name = it }
            PillRow(
              listOf(Role.Tenant, Role.Landlord, Role.Witness, Role.Expert).map { it to s.role(it) },
              role,
              dense = true,
            ) { role = it }
            BigKey(s.counterSignKey, active = name.isNotBlank(), Modifier.fillMaxWidth()) {
              if (name.isBlank()) return@BigKey
              scope.launch {
                val out = withContext(Dispatchers.IO) {
                  runCatching {
                    val c = Seal.makeCounterSeal(
                      DeviceKey, "", pdfHash, name.trim(), role, System.currentTimeMillis(),
                    )
                    writeToDownloads(
                      context,
                      "limen-countersign-${exportStamp()}.limensig",
                      "application/json",
                    ) { it.write(Seal.encodeCounterSeal(c).toByteArray(Charsets.UTF_8)) }
                  }.getOrNull()
                }
                written = out
              }
            }
          }
        }
      }

      written?.let { file ->
        Panel(label = s.exported) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.displayName, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
            Text(
              "${s.exportedTo} Download/Limen",
              style = MaterialTheme.typography.labelSmall,
              color = Limen.colors.inkDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SmallKey(s.share, Modifier.weight(1f)) {
                runCatching {
                  val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, file.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  context.startActivity(Intent.createChooser(send, s.share))
                }
              }
              SmallKey(s.done, Modifier.weight(1f)) { vm.closeCounterSign() }
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}
