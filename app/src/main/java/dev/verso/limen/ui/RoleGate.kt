package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.verso.limen.data.Role
import dev.verso.limen.theme.Limen

/**
 * The welcome: the first page anyone sees, so it speaks their language from the first
 * line (the menu sits over the mark) and says in four numbered steps what the instrument
 * is for. Then the one question it needs answered: who holds this phone. The answer sets
 * whose deadlines come first; it never gates the one-phone handover walk, and it can be
 * changed any time from the profile on the case.
 */
@Composable
fun RoleGate(vm: LimenViewModel, s: Str) {
  var name by rememberSaveable { mutableStateOf("") }

  Column(
    // The background paints edge to edge; the content stays out of the status bar.
    Modifier
      .fillMaxSize()
      .background(Limen.colors.plate)
      .safeDrawingPadding()
      .padding(horizontal = 24.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Spacer(Modifier.weight(1f))
      LangMenu(vm.lang, vm::selectLang)
    }
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.Center,
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        LimenMark(size = 72.dp)
      }
      Spacer(Modifier.height(20.dp))
      Text(s.welcomeLead, style = MaterialTheme.typography.bodyLarge, color = Limen.colors.ink)
      Spacer(Modifier.height(14.dp))
      GuideSteps(s)
      Spacer(Modifier.height(24.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          s.roleQuestion,
          style = MaterialTheme.typography.titleMedium,
          color = Limen.colors.ink,
          modifier = Modifier.weight(1f),
        )
        InfoTip(s.tipRole())
      }
      Spacer(Modifier.height(14.dp))
      Field(s.profileName, name) { name = it }
      Spacer(Modifier.height(14.dp))
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BigKey(s.role(Role.Tenant).uppercase(), active = true, Modifier.fillMaxWidth()) {
          vm.chooseRole(Role.Tenant, name)
        }
        BigKey(s.role(Role.Landlord).uppercase(), active = true, Modifier.fillMaxWidth()) {
          vm.chooseRole(Role.Landlord, name)
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
