package dev.peringer.limen

import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.peringer.limen.theme.Limen
import dev.peringer.limen.theme.LimenTheme
import dev.peringer.limen.ui.FlowScreen
import dev.peringer.limen.ui.HomePage
import dev.peringer.limen.ui.HomeScreen
import dev.peringer.limen.ui.Lang
import dev.peringer.limen.ui.LimenViewModel
import dev.peringer.limen.ui.Str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(base: android.content.Context) {
    // With no stored choice the app speaks Lang.default(), which is English for any system
    // language outside the five. The resources have to speak it too: left on Spanish, the
    // check in onCreate compared "es" with "en" and recreated the activity without end.
    val tag = base.getSharedPreferences("limen", MODE_PRIVATE).getString("lang", null)?.lowercase()
      ?: Lang.default().name.lowercase().takeIf { it != base.resources.configuration.locales[0].language }
    if (tag == null) { super.attachBaseContext(base); return }
    val locale = java.util.Locale.forLanguageTag(tag)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocales(android.os.LocaleList(locale))
    super.attachBaseContext(base.createConfigurationContext(config))
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val vm: LimenViewModel = viewModel()
      LaunchedEffect(vm.lang) {
        vm.followSystemLang()
        if (resources.configuration.locales[0].language != vm.lang.name.lowercase()) recreate()
      }

      // The draft reaches the disk whenever this window stops: the camera app kills this
      // process routinely mid-handover, and the debounced write must never be what a
      // handover loses. Everything else it protects, a phone call, a swipe away, a flat
      // battery, arrives through the same edge.
      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_STOP) vm.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      // Held here rather than inside HomeScreen, which is swapped out of the composition
      // whole whenever a record opens: remembered there, the page was forgotten on every
      // return from a handover.
      var homePage by rememberSaveable { mutableStateOf(HomePage.Landing) }
      var entryToSeedId by rememberSaveable { mutableStateOf<String?>(null) }

      // The system bars follow the in-app switch rather than the system setting, otherwise
      // the status icons go invisible the moment the plaster inverts.
      // Keyed on whether a record is open, not on the record: keyed on the record itself it
      // reconfigured the window on every keystroke.
      val recordOpen = vm.working != null
      LaunchedEffect(vm.darkTheme, recordOpen, homePage) {
        val transparent = android.graphics.Color.TRANSPARENT
        val darkBackground = vm.darkTheme || (!recordOpen && homePage == HomePage.Landing)
        enableEdgeToEdge(
          statusBarStyle =
            if (darkBackground) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
          navigationBarStyle =
            if (darkBackground) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
        )
      }

      LimenTheme(dark = vm.darkTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = if (vm.working == null) Limen.colors.action else MaterialTheme.colorScheme.background,
        ) {
          val screenModifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp)
          val s = remember(vm.lang) { Str(vm.lang) }
          if (vm.working != null) {
            var backProgress by remember { mutableFloatStateOf(0f) }
            PredictiveBackHandler { progress ->
              try {
                progress.collect { event ->
                  backProgress = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                    -event.progress
                  } else {
                    event.progress
                  }
                }
                backProgress = 0f
                vm.flowBack()
              } catch (_: CancellationException) {
                backProgress = 0f
              }
            }
            FlowScreen(
              modifier = screenModifier,
              vm = vm,
              s = s,
              predictiveBackProgress = backProgress,
            )
          } else {
            HomeScreen(
              modifier = Modifier.fillMaxSize(),
              vm = vm,
              s = s,
              page = homePage,
              entryToSeedId = entryToSeedId,
              onPage = { homePage = it },
              onSeed = { entryToSeedId = it },
            )
          }
        }
      }
    }
  }
}
