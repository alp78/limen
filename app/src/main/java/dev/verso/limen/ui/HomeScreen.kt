package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Protocol
import dev.verso.limen.theme.Limen

/**
 * The case: the mark, the whole-instrument switches, the protocol dossiers, and the way
 * into a new one. The two reference faces (Zeitwert, deadlines) sit beside the language.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: LimenViewModel = viewModel(), s: Str = Str(vm.lang)) {
  Column(modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LimenMark(size = 40.dp)
      Spacer(Modifier.weight(1f))
      ThemeToggle(vm.darkTheme, vm::toggleTheme)
    }

    Spacer(Modifier.height(8.dp))
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SmallKey(s.faceValue, Modifier.weight(1f)) { vm.selectMode(AppMode.Value) }
      Spacer(Modifier.width(6.dp))
      SmallKey(s.faceClocks, Modifier.weight(1f)) { vm.selectMode(AppMode.Clocks) }
      Spacer(Modifier.weight(0.1f))
      PillRow(
        Lang.entries.map { it to it.name.uppercase() },
        vm.lang,
        dense = true,
        onSelect = vm::selectLang,
      )
    }

    Spacer(Modifier.height(14.dp))
    Column(
      Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (vm.protocols.isEmpty()) {
        Box(
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Limen.colors.plateRaised)
            .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(16.dp))
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(s.emptyCase, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
        }
      } else {
        LazyColumn(
          Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(vm.protocols, key = { it.id }) { p ->
            ProtocolCard(vm, s, p)
          }
        }
      }

      // Both ways over the threshold, side by side. The exit from a stored entry protocol
      // has its own key on the entry's card.
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigKey("+ ${s.entry.uppercase()}", active = true, Modifier.weight(1f)) {
          vm.createProtocol(Kind.Entry)
        }
        BigKey("+ ${s.exit.uppercase()}", active = true, Modifier.weight(1f)) {
          vm.createProtocol(Kind.Exit)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

/** One dossier: what, where, when, and how far it got. Delete asks twice, quietly. */
@Composable
private fun ProtocolCard(vm: LimenViewModel, s: Str, p: Protocol) {
  val shape = RoundedCornerShape(12.dp)
  var armed by remember(p.id) { mutableStateOf(false) }
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Limen.colors.plateRaised)
      .border(1.dp, Limen.colors.plateEdge, shape)
      .clickable { vm.openProtocol(p.id) }
      .padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          p.address.ifBlank { s.noAddress },
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          color = Limen.colors.ink,
        )
        Text(
          listOf(
            if (p.kind == Kind.Entry) s.entry else s.exit,
            p.handoverDate,
            p.place,
          ).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          color = Limen.colors.inkDim,
        )
      }
      // The status chip: the one place the seal green is allowed to appear.
      val (label, sealed) = when {
        p.isExported() -> s.exported to true
        p.isSigned() -> s.signed to true
        else -> s.draft to false
      }
      Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (sealed) Limen.colors.onSeal else Limen.colors.inkDim,
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(if (sealed) Limen.colors.seal else Limen.colors.plate)
          .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(6.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (p.kind == Kind.Entry) {
        Chip(s.exitFromThis, dim = false) { vm.seedExitFrom(p) }
      }
      Spacer(Modifier.weight(1f))
      Chip(if (armed) s.sure else s.delete) {
        if (armed) vm.deleteProtocol(p.id) else armed = true
      }
    }
  }
}
