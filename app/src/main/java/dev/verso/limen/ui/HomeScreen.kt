package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.SwissDate
import dev.verso.limen.theme.Limen

enum class HomePage { Landing, NewSession, Sessions, DataPrivacy }

/**
 * The landing offers choices, not an identity gate or a partially exposed case list.
 *
 * The page is held by the caller. Held here, it was remembered inside a composable the
 * activity swaps out whole whenever a record opens, so every return from a handover forgot
 * the session list the handover was opened from.
 */
@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  vm: LimenViewModel = viewModel(),
  s: Str,
  page: HomePage,
  entryToSeedId: String?,
  onPage: (HomePage) -> Unit,
  onSeed: (String?) -> Unit,
) {
  BackHandler(enabled = page != HomePage.Landing) {
    onSeed(null)
    onPage(HomePage.Landing)
  }

  when (page) {
    HomePage.Landing -> LandingScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onNewSession = { onSeed(null); onPage(HomePage.NewSession) },
      onSessions = { onPage(HomePage.Sessions) },
      onDataPrivacy = { onPage(HomePage.DataPrivacy) },
    )
    HomePage.NewSession -> NewSessionScreen(
      vm = vm,
      s = s,
      entryToSeed = entryToSeedId?.let { id -> vm.protocols.firstOrNull { it.id == id } },
      // Back to where the form was opened from: an exit seeded from the session list used
      // to drop the operator on the landing, two taps away from the list it came from.
      onBack = {
        val fromSessions = entryToSeedId != null
        onSeed(null)
        onPage(if (fromSessions) HomePage.Sessions else HomePage.Landing)
      },
    )
    HomePage.Sessions -> SessionsScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onBack = { onPage(HomePage.Landing) },
      onEmpty = { onPage(HomePage.Landing) },
      onSeedExit = { entry -> onSeed(entry.id); onPage(HomePage.NewSession) },
    )
    HomePage.DataPrivacy -> DataPrivacyScreen(
      modifier = modifier,
      vm = vm,
      s = s,
      onBack = { onPage(HomePage.Landing) },
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
  BoxWithConstraints(modifier.fillMaxSize().background(Limen.colors.landing)) {
    val compactHeight = maxHeight < 600.dp
    Column(
      Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LangMenu(vm.lang, vm::selectLang)
        Spacer(Modifier.weight(1f))
        ThemeToggle(vm.darkTheme, s.themeMode, vm::toggleTheme)
      }
      if (compactHeight) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
      LimenMark(size = if (compactHeight) 88.dp else 172.dp)
      Spacer(Modifier.height(if (compactHeight) 10.dp else 20.dp))
      Text("LIMEN", style = MaterialTheme.typography.displayLarge, color = Limen.colors.onInk)
      Spacer(Modifier.height(10.dp))
      Text(
        s.welcomeLead,
        style = MaterialTheme.typography.bodyLarge,
        color = Limen.colors.onInk.copy(alpha = 0.88f),
      )
      if (compactHeight) Spacer(Modifier.height(18.dp)) else Spacer(Modifier.weight(1f))
      LandingKey(s.startNewSession, onPress = onNewSession)
      Spacer(Modifier.height(10.dp))
      LandingKey(s.allSessions, enabled = vm.protocols.isNotEmpty(), onPress = onSessions)
      Spacer(Modifier.height(10.dp))
      LandingKey(s.dataAndPrivacy, onPress = onDataPrivacy)
      Spacer(Modifier.height(24.dp))
    }
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
            SwissDate.display(p.handoverDate),
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
