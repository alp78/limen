package dev.verso.limen.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The instrument's touch. Keys in this app visibly sink under the thumb, and a key that
 * looks mechanical should feel mechanical.
 *
 * Three weights only. [Key] is every control. [Confirm] marks the two moments that end
 * something: a signature kept, a document written. [Reject] marks a refusal or a
 * deletion, so a destructive tap never feels the same as an ordinary one.
 *
 * The phone's own touch-feedback setting governs all of it, and nothing here talks around
 * it: the window asks the system to play a feedback, the system decides. That also keeps
 * the manifest's promise, since asking this way needs no VIBRATE permission, while driving
 * the vibrator directly would.
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
  // FLAG_IGNORE_VIEW_SETTING passes the per-View legacy toggle. That toggle is not the
  // user's preference, it is a leftover from the days of XML layouts, and where a build
  // leaves it off this call silently returns false and plays nothing on that device while
  // working everywhere else. Compose's own haptics pass the same flag. The phone's
  // touch-feedback setting is a different thing entirely and still decides.
  performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
}
