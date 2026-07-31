package dev.verso.limen.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The instrument's touch. Keys in this app visibly sink under the thumb, and a key that
 * looks mechanical should feel mechanical: the tick lands on the press, not on the
 * release, so the finger learns that the surface took the tap even when the eye is on
 * the flat rather than the screen.
 *
 * Three weights only. [Key] is every control. [Confirm] marks the two moments that end
 * something: a signature kept, a document written. [Reject] marks a refusal or a
 * deletion, so a destructive tap never feels the same as an ordinary one.
 *
 * The system setting governs all of it: [View.performHapticFeedback] stays silent when
 * the phone's touch feedback is off, and nothing here overrides that.
 */
enum class Tick { Key, Confirm, Reject }

/** The tick this composition can play, bound to the window that owns it. */
@Composable
fun rememberTick(): (Tick) -> Unit {
  val view = LocalView.current
  return remember(view) { { weight -> view.tick(weight) } }
}

fun View.tick(weight: Tick) {
  // CONFIRM and REJECT arrived in API 30; on 29 the nearest honest stand-ins are the
  // ordinary key press and the long press, which are the same two feelings coarsened.
  val constant = when (weight) {
    Tick.Key -> HapticFeedbackConstants.VIRTUAL_KEY
    Tick.Confirm ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
      else HapticFeedbackConstants.VIRTUAL_KEY
    Tick.Reject ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
      else HapticFeedbackConstants.LONG_PRESS
  }
  performHapticFeedback(constant)
}
