package dev.peringer.limen.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
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

/**
 * Whether the phone has a screen lock to ask. The gate stands in front of the switch that turns
 * it off, so on a phone whose lock was removed afterwards it could only ever refuse, and the
 * saved records would stay shut until a lock was set again.
 */
internal fun Context.hasScreenLock(): Boolean = getSystemService(KeyguardManager::class.java).isDeviceSecure

@Composable
internal fun LibraryUnlock(s: Str, onUnlocked: () -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  val manager = context.getSystemService(KeyguardManager::class.java)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (it.resultCode == Activity.RESULT_OK) onUnlocked()
  }
  Column(Modifier.fillMaxSize().background(Limen.colors.plate).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(s.libraryProtection, color = Limen.colors.ink)
    SmallKey(s.open, Modifier.fillMaxWidth()) {
      @Suppress("DEPRECATION")
      val intent = manager.createConfirmDeviceCredentialIntent("Limen", s.libraryProtection)
      // No intent means the screen lock was removed while this gate was up: nothing is left to ask.
      if (intent == null) onUnlocked() else launcher.launch(intent)
    }
    SmallKey(s.back, onPress = onBack)
  }
}
