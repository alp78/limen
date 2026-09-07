package dev.peringer.limen.ui

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
 * Five weights. [Key] is every control. [ToggleOn] and [ToggleOff] are a switch changing
 * state, the pair Android added in API 34 for exactly that control, and on a Pixel they
 * are lighter than a key (a half-strength tick and a low tick against a strong click),
 * which is the platform's own rule that changing a toggle should feel less than
 * submitting something. [Confirm] marks the two moments that end something: a signature
 * kept, a document written. [Reject] marks a refusal or a deletion, so a destructive tap
 * never feels the same as an ordinary one.
 *
 * This asks the window to play a feedback rather than driving the vibrator, and that is
 * not merely the polite choice, it is the only one that buys anything. Measured against
 * the vibrator service on 2026-07-31: with the phone's touch feedback switched off, a
 * prebaked CLICK sent straight to the vibrator, a 24 ms one-shot carrying an explicit
 * USAGE_UNKNOWN, and even a 220 ms buzz were each reclassified to touch usage and dropped
 * with "ignored_for_settings". An app cannot vibrate on a tap against that switch, so the
 * VIBRATE permission would cost the manifest's promise and return nothing. When the keys
 * feel dead, that setting is off, and it is the phone's answer to give.
 */
enum class Tick { Key, ToggleOn, ToggleOff, Confirm, Reject }

/** The tick this composition can play, bound to the window that owns it. */
@Composable
fun rememberTick(): (Tick) -> Unit {
  val view = LocalView.current
  return remember(view) { { weight -> view.tick(weight) } }
}

fun View.tick(weight: Tick) {
  // CONFIRM and REJECT arrived in API 30 and the TOGGLE pair in API 34. Below those the
  // nearest honest stand-ins are the ordinary key press and the long press, the same
  // feelings coarsened, so a phone on 29 to 33 ticks for a switch exactly as it did
  // before the pair existed instead of going silent on a constant it does not know.
  val hasTogglePair = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
  val constant = when (weight) {
    Tick.Key -> HapticFeedbackConstants.VIRTUAL_KEY
    Tick.ToggleOn ->
      if (hasTogglePair) HapticFeedbackConstants.TOGGLE_ON
      else HapticFeedbackConstants.VIRTUAL_KEY
    Tick.ToggleOff ->
      if (hasTogglePair) HapticFeedbackConstants.TOGGLE_OFF
      else HapticFeedbackConstants.VIRTUAL_KEY
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
