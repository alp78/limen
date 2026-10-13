package dev.peringer.limen.ui

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.peringer.limen.theme.Limen

val Str.libraryProtection get() = words("Protect the saved handover library with the device lock", "Protéger les constats sauvegardés avec le verrouillage du téléphone", "Gespeicherte Protokolle mit der Gerätesperre schützen", "Proteggi i verbali salvati con il blocco del dispositivo", "Proteger ils protocols memorisads cun la bloccada dal telefon")

@Composable
internal fun LibraryUnlock(s: Str, onUnlocked: () -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  val manager = context.getSystemService(KeyguardManager::class.java)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (it.resultCode == Activity.RESULT_OK) onUnlocked()
  }
  Column(Modifier.fillMaxSize().background(Limen.colors.plate).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(s.libraryProtection, color = Limen.colors.ink)
    SmallKey(s.open, Modifier.fillMaxWidth(), enabled = manager.isDeviceSecure) {
      @Suppress("DEPRECATION")
      val intent = manager.createConfirmDeviceCredentialIntent("Limen", s.libraryProtection)
      intent?.let(launcher::launch)
    }
    SmallKey(s.back, onPress = onBack)
  }
}
