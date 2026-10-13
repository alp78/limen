package dev.peringer.limen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import dev.peringer.limen.theme.Limen

@Composable
internal fun AddressPrivacyChoice(vm: LimenViewModel, s: Str) {
  var explaining by remember { mutableStateOf(false) }
  Column {
    Toggle(s.onlineLookup, vm.onlineAddresses) {
      if (vm.onlineAddresses) vm.chooseOnlineAddresses(false) else explaining = true
    }
    if (vm.onlineAddresses) Text(s.lookupDisclosure, color = Limen.colors.inkDim)
  }
  if (explaining) androidx.compose.material3.AlertDialog(onDismissRequest = { explaining = false },
    title = { Text(s.onlineLookup) }, text = { Text(s.lookupDisclosure) },
    confirmButton = { SmallKey(s.done) { vm.chooseOnlineAddresses(true); explaining = false } },
    dismissButton = { SmallKey(s.cancel) { explaining = false } })
}
