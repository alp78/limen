package dev.verso.limen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.verso.limen.theme.LimenTheme
import dev.verso.limen.ui.AppMode
import dev.verso.limen.ui.ClocksScreen
import dev.verso.limen.ui.FlowScreen
import dev.verso.limen.ui.HomeScreen
import dev.verso.limen.ui.LimenViewModel
import dev.verso.limen.ui.Str
import dev.verso.limen.ui.ValueScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val vm: LimenViewModel = viewModel()

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

      // The system bars follow the in-app switch rather than the system setting, otherwise
      // the status icons go invisible the moment the plaster inverts.
      LaunchedEffect(vm.darkTheme) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
          statusBarStyle =
            if (vm.darkTheme) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
          navigationBarStyle =
            if (vm.darkTheme) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
        )
      }

      LimenTheme(dark = vm.darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val screenModifier = Modifier.safeDrawingPadding().padding(vertical = 8.dp)
          val s = remember(vm.lang) { Str(vm.lang) }
          if (vm.myRole == null) {
            // The first question, before anything else: who holds this phone.
            dev.verso.limen.ui.RoleGate(vm = vm, s = s)
          } else if (vm.working != null) {
            // The flow owns the screen until it is closed; back walks its stations.
            BackHandler { vm.flowBack() }
            FlowScreen(modifier = screenModifier, vm = vm, s = s)
          } else {
            // Back from a face returns to the case rather than leaving the app, which is
            // what the mark in the masthead does too.
            BackHandler(enabled = vm.appMode != AppMode.Home) { vm.selectMode(AppMode.Home) }
            when (vm.appMode) {
              AppMode.Home -> HomeScreen(modifier = screenModifier, vm = vm, s = s)
              AppMode.Value -> ValueScreen(modifier = screenModifier, vm = vm, s = s)
              AppMode.Clocks -> ClocksScreen(modifier = screenModifier, vm = vm, s = s)
            }
          }
        }
      }
    }
  }
}
