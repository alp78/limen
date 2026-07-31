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
 * The first question the instrument asks, once: who holds this phone. The answer sets
 * whose deadlines come first and whose half of the protocol is prepared; it never gates
 * the one-phone handover walk, which stays the same ceremony for both sides. The side
 * can be changed later on the link face.
 */
@Composable
fun RoleGate(vm: LimenViewModel, s: Str) {
  var name by rememberSaveable { mutableStateOf("") }

  Column(
    Modifier.fillMaxSize().background(Limen.colors.plate).padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      LimenMark(size = 72.dp)
    }
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
    Spacer(Modifier.height(16.dp))
    Field(s.profileName, name) { name = it }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      BigKey(s.role(Role.Tenant).uppercase(), active = true, Modifier.fillMaxWidth()) {
        vm.chooseRole(Role.Tenant, name)
      }
      BigKey(s.role(Role.Landlord).uppercase(), active = true, Modifier.fillMaxWidth()) {
        vm.chooseRole(Role.Landlord, name)
      }
    }
  }
}
