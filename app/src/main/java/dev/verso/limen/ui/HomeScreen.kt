package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Protocol
import dev.verso.limen.theme.Limen

private enum class HomePage { Landing, NewSession, Sessions, DataPrivacy }

/** The landing offers choices, not an identity gate or a partially exposed case list. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: LimenViewModel = viewModel(), s: Str) {
  var page by rememberSaveable { mutableStateOf(HomePage.Landing) }
  var entryToSeedId by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = page != HomePage.Landing) {
    page = HomePage.Landing
    entryToSeedId = null
  }

  when (page) {
    HomePage.Landing -> LandingScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onNewSession = { entryToSeedId = null; page = HomePage.NewSession },
      onSessions = { page = HomePage.Sessions },
      onDataPrivacy = { page = HomePage.DataPrivacy },
    )
    HomePage.NewSession -> NewSessionScreen(
      vm = vm,
      s = s,
      entryToSeed = entryToSeedId?.let { id -> vm.protocols.firstOrNull { it.id == id } },
      onBack = { entryToSeedId = null; page = HomePage.Landing },
    )
    HomePage.Sessions -> SessionsScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onBack = { page = HomePage.Landing },
      onEmpty = { page = HomePage.Landing },
      onSeedExit = { entry -> entryToSeedId = entry.id; page = HomePage.NewSession },
    )
    HomePage.DataPrivacy -> DataPrivacyScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onBack = { page = HomePage.Landing },
    )
  }
}

@Composable
private fun LandingScreen(
  modifier: Modifier,
  vm: LimenViewModel,
  s: Str,
  onNewSession: () -> Unit,
  onSessions: () -> Unit,
  onDataPrivacy: () -> Unit,
) {
  Column(
    modifier
      .fillMaxSize()
      .background(Limen.colors.landing)
      .safeDrawingPadding()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      LangMenu(vm.lang, vm::selectLang)
      Spacer(Modifier.weight(1f))
      ThemeToggle(vm.darkTheme, s.themeMode, vm::toggleTheme)
    }
    Spacer(Modifier.weight(1f))
    LimenMark(size = 172.dp)
    Spacer(Modifier.height(20.dp))
    Text("LIMEN", style = MaterialTheme.typography.displayLarge, color = Limen.colors.onInk)
    Spacer(Modifier.height(10.dp))
    Text(
      s.welcomeLead,
      style = MaterialTheme.typography.bodyLarge,
      color = Limen.colors.onInk.copy(alpha = 0.88f),
    )
    Spacer(Modifier.weight(1f))
    LandingKey(s.startNewSession, onPress = onNewSession)
    Spacer(Modifier.height(10.dp))
    LandingKey(s.allSessions, enabled = vm.protocols.isNotEmpty(), onPress = onSessions)
    Spacer(Modifier.height(10.dp))
    LandingKey(s.dataAndPrivacy, onPress = onDataPrivacy)
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun LandingKey(label: String, enabled: Boolean = true, onPress: () -> Unit) {
  KeySurface(
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    shape = RoundedCornerShape(12.dp),
    fillColor = Limen.colors.plate,
    outlineColor = Limen.colors.onInk,
    onPress = onPress,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      color = if (enabled) Limen.colors.ink else Limen.colors.disabledInk,
      modifier = Modifier.padding(vertical = 18.dp),
    )
  }
}

@Composable
private fun SessionsScreen(
  modifier: Modifier,
  vm: LimenViewModel,
  s: Str,
  onBack: () -> Unit,
  onEmpty: () -> Unit,
  onSeedExit: (Protocol) -> Unit,
) {
  LaunchedEffect(vm.protocols.isEmpty()) {
    if (vm.protocols.isEmpty()) onEmpty()
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
      Text(s.allSessions, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink, modifier = Modifier.weight(1f))
      LangMenu(vm.lang, vm::selectLang)
      Spacer(Modifier.width(4.dp))
      ThemeToggle(vm.darkTheme, s.themeMode, vm::toggleTheme)
    }
    Spacer(Modifier.height(14.dp))
    if (vm.protocols.isEmpty()) {
      Text(s.sessionsEmpty, style = MaterialTheme.typography.bodyLarge, color = Limen.colors.inkDim)
    } else {
      LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(vm.protocols, key = { it.id }) { p ->
          ProtocolCard(vm, s, p, onSeedExit)
        }
      }
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

/** One dossier: what, where, when, and how far it got. Delete asks twice, quietly. */
@Composable
private fun ProtocolCard(
  vm: LimenViewModel,
  s: Str,
  p: Protocol,
  onSeedExit: (Protocol) -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  var armed by remember(p.id) { mutableStateOf(false) }
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Limen.colors.plateRaised)
      .border(1.5.dp, Limen.colors.actionOutline, shape)
      .clickable { vm.openProtocol(p.id) }
      .padding(horizontal = 16.dp, vertical = 12.dp),
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
        p.isFinalized() && p.isExported() && p.deliveryComplete() -> s.copyDelivery to true
        p.isFinalized() && p.isExported() -> s.exported to true
        p.isFinalized() -> s.finalized to true
        p.isSigned() -> s.awaitingSignatures to false
        else -> s.draft to false
      }
      Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (sealed) Limen.colors.onSeal else Limen.colors.inkDim,
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(if (sealed) Limen.colors.seal else Limen.colors.input)
          .border(1.dp, Limen.colors.actionOutline, RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (p.kind == Kind.Entry) {
        val canCreateExit = p.isFinalized()
        Chip(
          if (canCreateExit) s.exitFromThis else s.finalizeEntryFirst,
          dim = !canCreateExit,
          enabled = canCreateExit,
        ) { onSeedExit(p) }
      }
      Spacer(Modifier.weight(1f))
      Chip(
        if (armed) s.sure else s.delete,
        active = armed,
        tick = if (armed) Tick.Reject else Tick.Key,
        destructive = true,
      ) {
        if (armed) vm.deleteProtocol(p.id) else armed = true
      }
    }
  }
}
