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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.theme.Limen

/**
 * The case, ordered the way the handover actually goes: who you are, how it works, the
 * dossier you are in the middle of, the way into a new one, and only then the side tools.
 * The first thing a lost user reads is the four steps, at full body size.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: LimenViewModel = viewModel(), s: Str = Str(vm.lang)) {
  var showProfile by remember { mutableStateOf(false) }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        LimenMark(size = 40.dp)
        Spacer(Modifier.weight(1f))
        vm.myRole?.let { role ->
          Chip(
            listOf(vm.profileName, s.role(role)).filter { it.isNotBlank() }.joinToString(" · "),
            dim = false,
          ) { showProfile = true }
        }
        LangMenu(vm.lang, vm::selectLang)
        ThemeToggle(vm.darkTheme, vm::toggleTheme)
      }

      Spacer(Modifier.height(12.dp))
      Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (vm.protocols.isEmpty()) {
          // First contact: what this is and how it goes, before any control.
          Panel(label = s.howItWorks) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(s.welcomeLead, style = MaterialTheme.typography.bodyLarge, color = Limen.colors.ink)
              GuideSteps(s)
            }
          }
          Spacer(Modifier.weight(1f))
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              s.protocolsLabel,
              style = MaterialTheme.typography.titleMedium,
              color = Limen.colors.ink,
              modifier = Modifier.weight(1f),
            )
            InfoTip(Tip(s.welcomeLead, s.guideSteps))
          }
          LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(vm.protocols, key = { it.id }) { p ->
              ProtocolCard(vm, s, p)
            }
          }
        }

        // Both ways over the threshold, side by side. The exit from a stored entry
        // protocol has its own key on the entry's card.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          BigKey("+ ${s.entry.uppercase()}", active = true, Modifier.weight(1f)) {
            vm.createProtocol(Kind.Entry)
          }
          BigKey("+ ${s.exit.uppercase()}", active = true, Modifier.weight(1f)) {
            vm.createProtocol(Kind.Exit)
          }
        }

        // The side tools, clearly apart from the main way through.
        Text(
          s.toolsLabel,
          style = MaterialTheme.typography.labelSmall,
          color = Limen.colors.inkDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SmallKey(s.faceValue, Modifier.weight(1f)) { vm.selectMode(AppMode.Value) }
          SmallKey(s.faceClocks, Modifier.weight(1f)) { vm.selectMode(AppMode.Clocks) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SmallKey(s.faceLink, Modifier.weight(1f)) { vm.selectMode(AppMode.Link) }
          SmallKey(s.counterSignKey, Modifier.weight(1f)) { vm.openCounterSign() }
        }
      }
      Spacer(Modifier.height(12.dp))
    }

    if (showProfile) {
      ProfileOverlay(vm, s) { showProfile = false }
    }
  }
}

/** The four steps of the handover, numbered, at reading size. */
@Composable
fun GuideSteps(s: Str) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    s.guideSteps.forEachIndexed { i, step ->
      Row(verticalAlignment = Alignment.Top) {
        Text(
          "${i + 1}",
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodyLarge,
          color = Limen.colors.inkDim,
          modifier = Modifier.width(24.dp),
        )
        Text(step, style = MaterialTheme.typography.bodyLarge, color = Limen.colors.ink)
      }
    }
  }
}

/**
 * The profile, one tap from the case: name, side, and the way to hand the phone to
 * someone else entirely. Nothing else in the app talks about accounts, because there are
 * none: the profile is this phone's answer to the first question.
 */
@Composable
private fun ProfileOverlay(vm: LimenViewModel, s: Str, onClose: () -> Unit) {
  Box(
    Modifier
      .fillMaxSize()
      .background(Limen.colors.plate.copy(alpha = 0.94f))
      .clickable(onClick = onClose),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.padding(20.dp).clickable(enabled = false) {}) {
      Panel(label = s.profileKey, info = s.tipRole()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Field(s.profileName, vm.profileName) { vm.updateProfileName(it) }
          PillRow(
            listOf(Role.Tenant, Role.Landlord).map { it to s.role(it) },
            vm.myRole ?: Role.Tenant,
          ) { r -> vm.chooseRole(r, vm.profileName) }
          BigKey(s.doneKey, active = true, Modifier.fillMaxWidth()) { onClose() }
          SmallKey(s.resetProfile, Modifier.fillMaxWidth()) {
            vm.clearProfile()
            onClose()
          }
        }
      }
    }
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
