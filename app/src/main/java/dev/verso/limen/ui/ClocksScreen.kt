package dev.verso.limen.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.sense.Clocks
import dev.verso.limen.theme.Limen
import java.time.LocalDate

/**
 * The deadlines face: the legal clocks of the handover as cards, with the one that
 * decides most disputes made interactive: type the day the keys came back and read the
 * posting dates. Everything is referenced to the OR and the case law; nothing here is
 * legal advice, and the face says so.
 */
@Composable
fun ClocksScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  var keysDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

  Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    AppHeader(vm, s)
    Spacer(Modifier.height(12.dp))

    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // The tenant's own clocks come first on a tenant's phone; the landlord's on theirs.
      if (vm.myRole == dev.verso.limen.data.Role.Tenant) {
        Panel(label = s.clockEntryTitle) {
          Text(s.clockEntryBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        }
        Panel(label = s.clockDepositTitle) {
          Text(s.clockDepositBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        }
      }

      Panel(label = s.clockExitTitle, info = s.tipClocks()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(s.clockExitBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          Field(s.keysReturnedOn, keysDate) { v -> keysDate = v.take(10) }
          val parsed = runCatching { LocalDate.parse(keysDate) }.getOrNull()
          if (parsed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Column(Modifier.weight(1f)) {
                Text(s.safeBy, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
                Text(
                  Clocks.exitNoticeSafeBy(parsed).toString(),
                  fontFamily = FontFamily.Monospace,
                  fontSize = 22.sp,
                  color = Limen.colors.ink,
                )
              }
              Column(Modifier.weight(1f)) {
                Text(s.latestBy, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
                Text(
                  Clocks.exitNoticeLatest(parsed).toString(),
                  fontFamily = FontFamily.Monospace,
                  fontSize = 22.sp,
                  color = Limen.colors.ink,
                )
              }
            }
          }
        }
      }

      if (vm.myRole != dev.verso.limen.data.Role.Tenant) {
        Panel(label = s.clockEntryTitle) {
          Text(s.clockEntryBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        }
      }
      Panel(label = s.clockHiddenTitle) {
        Text(s.clockHiddenBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      }
      if (vm.myRole != dev.verso.limen.data.Role.Tenant) {
        Panel(label = s.clockDepositTitle) {
          Text(s.clockDepositBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        }
      }
      Panel(label = s.clockReturnTitle) {
        Text(s.clockReturnBody, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}
