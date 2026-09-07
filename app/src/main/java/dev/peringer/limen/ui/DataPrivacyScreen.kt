package dev.peringer.limen.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.peringer.limen.theme.Limen

private enum class ArchiveAction { Export, Import }

@Composable
fun DataPrivacyScreen(
  modifier: Modifier,
  vm: LimenViewModel,
  s: Str,
  onBack: () -> Unit,
) {
  var showLicences by rememberSaveable { mutableStateOf(false) }
  var archiveAction by rememberSaveable { mutableStateOf<ArchiveAction?>(null) }
  // Saveable alongside the action it belongs to. Held in a plain remember, a recreation after
  // the document picker brought the password dialog back with no Uri behind it, and the
  // restore then did nothing at all: no busy state, no error, no message.
  var pendingRestore by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
  val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      pendingRestore = uri
      archiveAction = ArchiveAction.Import
    }
  }
  if (showLicences) {
    LicencesView(s, modifier) { showLicences = false }
    return
  }

  Column(
    modifier
      .fillMaxSize()
      .background(Limen.colors.plate)
      .safeDrawingPadding()
      .padding(horizontal = 20.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconKey(ButtonGlyph.Back, s.back, Modifier.width(48.dp), onPress = onBack)
      Spacer(Modifier.width(10.dp))
      Text(s.dataAndPrivacy, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
    }
    Spacer(Modifier.height(14.dp))
    Column(
      Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Panel(label = s.privacyTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          s.privacyParagraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          }
        }
      }
      Panel(label = s.controllerTitle) {
        val controller = vm.controllerIdentity
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(s.controllerBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          Field(s.organisation, controller.organisation) {
            vm.updateController(controller.copy(organisation = it))
          }
          Field(s.controllerContact, controller.contactName) {
            vm.updateController(controller.copy(contactName = it))
          }
          Field(s.street, controller.street) { vm.updateController(controller.copy(street = it)) }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(s.postalCode, controller.postalCode, Modifier.weight(0.38f)) {
              vm.updateController(controller.copy(postalCode = it))
            }
            Field(s.city, controller.city, Modifier.weight(0.62f)) {
              vm.updateController(controller.copy(city = it))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
              s.email,
              controller.email,
              Modifier.weight(1f),
              keyboardType = KeyboardType.Email,
            ) { vm.updateController(controller.copy(email = it)) }
            Field(
              s.phoneNumber,
              controller.phone,
              Modifier.weight(1f),
              keyboardType = KeyboardType.Phone,
            ) { vm.updateController(controller.copy(phone = it)) }
          }
        }
      }
      Panel(label = s.licencesTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(s.licencesBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          SmallKey(s.openLicences, Modifier.fillMaxWidth()) { showLicences = true }
        }
      }
      Panel(label = s.backupTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(s.backupBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(s.createBackup, Modifier.weight(1f), enabled = !vm.archiveBusy) {
              archiveAction = ArchiveAction.Export
            }
            SmallKey(s.restoreBackup, Modifier.weight(1f), enabled = !vm.archiveBusy) {
              restore.launch(arrayOf("application/zip", "application/octet-stream"))
            }
          }
          when {
            vm.archiveBusy -> Text(s.working, style = MaterialTheme.typography.bodySmall, color = Limen.colors.inkDim)
            vm.archiveFailed -> Text(s.archiveFailed, style = MaterialTheme.typography.bodySmall, color = Limen.colors.dangerOutline)
            vm.archiveExported != null -> Text(
              s.archiveSaved(vm.archiveExported!!.displayName),
              style = MaterialTheme.typography.bodySmall,
              color = Limen.colors.seal,
            )
            vm.archiveImported != null -> Text(
              s.archiveRestored(vm.archiveImported!!),
              style = MaterialTheme.typography.bodySmall,
              color = Limen.colors.seal,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  archiveAction?.let { action ->
    ArchivePasswordDialog(
      action = action,
      s = s,
      onDismiss = {
        archiveAction = null
        if (action == ArchiveAction.Import) pendingRestore = null
      },
      onConfirm = { password ->
        when (action) {
          ArchiveAction.Export -> vm.exportArchive(password)
          ArchiveAction.Import -> pendingRestore?.let { vm.importArchive(it, password) }
        }
        archiveAction = null
        pendingRestore = null
      },
    )
  }
}

@Composable
private fun ArchivePasswordDialog(
  action: ArchiveAction,
  s: Str,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  // Deliberately NOT saveable: saved instance state is written out to the system, and a
  // backup password is the one thing in this app that must not go anywhere near it. The cost
  // is retyping it after a rotation, which is the right side of that trade.
  var password by remember(action) { mutableStateOf("") }
  var confirmation by remember(action) { mutableStateOf("") }
  val isExport = action == ArchiveAction.Export
  val valid = password.isNotBlank() && (!isExport || password.length >= 12 && password == confirmation)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Limen.colors.plateRaised,
    title = { Text(if (isExport) s.protectBackup else s.unlockBackup) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          if (isExport) s.backupPasswordBody else s.restorePasswordBody,
          style = MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(s.backupPassword) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          visualTransformation = PasswordVisualTransformation(),
        )
        if (isExport) {
          OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = { Text(s.confirmBackupPassword) },
            singleLine = true,
            isError = confirmation.isNotEmpty() && confirmation != password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
          )
        }
      }
    },
    confirmButton = {
      SmallKey(
        if (isExport) s.createBackup else s.restoreBackup,
        enabled = valid,
      ) { onConfirm(password) }
    },
    dismissButton = { SmallKey(s.cancel, onPress = onDismiss) },
  )
}

/**
 * The third-party notice, read from the copy of the repo's NOTICE that the build stages into
 * assets. Verbatim and unlocalized on purpose: a licence text that has been translated or
 * reflowed is no longer the licence text. It scrolls both ways because the notice is written to
 * a fixed column a phone is narrower than.
 *
 * Its own screen rather than a panel: the notice is several thousand characters and the privacy
 * page is already a scrolling column, which cannot hold a second one.
 */
@Composable
private fun LicencesView(s: Str, modifier: Modifier, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val notice = remember {
    runCatching { context.assets.open("NOTICE").bufferedReader().use { it.readText() } }
      .getOrElse { s.licencesMissing }
  }
  Column(
    modifier
      .fillMaxSize()
      .background(Limen.colors.plate)
      .safeDrawingPadding()
      .padding(horizontal = 20.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconKey(ButtonGlyph.Back, s.back, Modifier.width(48.dp), onPress = onBack)
      Spacer(Modifier.width(10.dp))
      Text(s.licencesTitle, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
    }
    Spacer(Modifier.height(14.dp))
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
      Text(
        notice,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = Limen.colors.ink,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      )
      Spacer(Modifier.height(24.dp))
    }
  }
}
