package dev.verso.limen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.launch

/**
 * What an info mark explains: a lead sentence, then bullets, never a wall of text. The
 * house rule for this app: explanations live behind the mark, structured, and no small
 * print ever sits inline on a screen.
 */
data class Tip(val lead: String, val bullets: List<String> = emptyList())

/**
 * The house info affordance: anything that needs explaining lives behind this, never in
 * small print. The glyph is drawn rather than imported, as everywhere in the portfolio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTip(tip: Tip, target: androidx.compose.ui.unit.Dp = 40.dp) {
  val state = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()
  val play = rememberTick()
  val tint = Limen.colors.inkDim
  val text = tip.lead
  TooltipBox(
    positionProvider =
      TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = {
      PlainTooltip {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
          Text(tip.lead, style = MaterialTheme.typography.bodyMedium)
          if (tip.bullets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            tip.bullets.forEachIndexed { i, b ->
              Row {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(b, style = MaterialTheme.typography.bodyMedium)
              }
              if (i < tip.bullets.size - 1) Spacer(Modifier.height(5.dp))
            }
          }
        }
      }
    },
    state = state,
  ) {
    // Forty dp of target around a sixteen dp mark: the padding is transparent, so the
    // panel looks the same and a thumb can still hit it.
    Box(
      Modifier
        .size(target)
        .clip(CircleShape)
        .clickable {
          play(Tick.Key)
          scope.launch { state.show() }
        }
        .semantics { contentDescription = text },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(16.dp)) {
        val r = size.minDimension / 2f
        val hair = 1.4f * density
        drawCircle(tint, radius = r - hair / 2, style = Stroke(width = hair))
        drawCircle(tint, radius = r * 0.12f, center = Offset(center.x, center.y - r * 0.44f))
        drawLine(
          tint,
          Offset(center.x, center.y - r * 0.08f),
          Offset(center.x, center.y + r * 0.46f),
          strokeWidth = hair * 1.5f,
          cap = StrokeCap.Round,
        )
      }
    }
  }
}

/**
 * What a key just opened. When a control reveals fields, the fields wash once with a
 * neutral highlight and a marker runs down their left edge, then both settle: the eye
 * follows the change to where it landed instead of hunting for what moved.
 *
 * Deliberately ONE treatment rather than a colour per option. In this instrument colour
 * is a statement, and the green means signed; painting the bank fields blue and the
 * insurance fields amber would say those states differ in kind, which they do not. The
 * highlight says "this is new", the label says what it is, and the pill above stays lit
 * to say which choice put it there.
 *
 * [key] is what the reveal depends on: pass the chosen value, and every change of it
 * plays the wash again.
 */
@Composable
fun Revealed(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  // First composition included: a field that appears with the screen is new to that eye too.
  val progress = remember(key) { Animatable(1f) }
  LaunchedEffect(key) {
    progress.animateTo(0f, animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing))
  }
  val wash = Limen.colors.ink.copy(alpha = 0.09f * progress.value)
  val edge = Limen.colors.ink.copy(alpha = 0.55f * progress.value)
  Box(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(wash)
      .drawBehind {
        // The marker: full height at the start, retracting upward as the wash fades.
        val h = size.height * progress.value
        if (h > 0f) {
          drawLine(
            edge,
            Offset(1.2f * density, 0f),
            Offset(1.2f * density, h),
            strokeWidth = 2.4f * density,
            cap = StrokeCap.Round,
          )
        }
      }
      .padding(horizontal = 6.dp, vertical = 4.dp),
  ) {
    content()
  }
}

/** A raised panel with its stamped label, the container every control row sits in. */
@Composable
fun Panel(label: String, info: Tip? = null, content: @Composable () -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .background(Limen.colors.plateRaised, RoundedCornerShape(10.dp))
      .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(10.dp))
      .padding(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      if (info != null) {
        Spacer(Modifier.width(2.dp))
        InfoTip(info)
      }
    }
    Spacer(Modifier.height(8.dp))
    content()
  }
}

/**
 * The pressable surface every key and chip sits on: a softly embossed plate that lifts a
 * hair off the paper, presses down under the finger, and springs back. The emboss is a
 * light inner edge along the top and a shade along the bottom, no gradient anywhere; the
 * press is a scale and the shadow settling, which is what a real key does.
 */
@Composable
fun KeySurface(
  modifier: Modifier = Modifier,
  active: Boolean = false,
  enabled: Boolean = true,
  shape: RoundedCornerShape = RoundedCornerShape(12.dp),
  tick: Tick = Tick.Key,
  onPress: () -> Unit,
  content: @Composable () -> Unit,
) {
  val play = rememberTick()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // Stiff and barely bouncy on purpose. The earlier spring took about a third of a second
  // to settle, and a key still moving when the screen has already changed reads as the app
  // being slow to answer rather than as the key being springy.
  val scale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.955f else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
    label = "keyScale",
  )
  val lift by animateDpAsState(
    targetValue = if (pressed && enabled) 0.dp else 2.dp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
    label = "keyLift",
  )
  val corner = 12f
  Box(
    modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (enabled || active) 1f else 0.45f
      }
      .shadow(lift, shape, clip = false)
      .clip(shape)
      .background(if (active) Limen.colors.ink else Limen.colors.plateRaised)
      .border(1.dp, if (active) Limen.colors.ink else Limen.colors.plateEdge, shape)
      .drawBehind {
        // The emboss: a light edge where the light lands, a shade where it leaves.
        val inset = corner * density
        val hi = if (active) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f)
        val lo = if (active) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.07f)
        drawLine(hi, Offset(inset, 1.2f * density), Offset(size.width - inset, 1.2f * density), 2f * density, StrokeCap.Round)
        drawLine(lo, Offset(inset, size.height - 1.2f * density), Offset(size.width - inset, size.height - 1.2f * density), 2f * density, StrokeCap.Round)
      }
      .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
        play(tick)
        onPress()
      },
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** A transport-sized key. Active means engaged: it fills with ink, never with the seal green. */
@Composable
fun BigKey(
  label: String,
  active: Boolean,
  modifier: Modifier = Modifier,
  tick: Tick = Tick.Key,
  enabled: Boolean = true,
  onPress: () -> Unit,
) {
  KeySurface(
    modifier,
    active = active,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    tick = tick,
    onPress = onPress,
  ) {
    Text(
      label,
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 17.sp,
      letterSpacing = 0.8.sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
      color = if (active) Limen.colors.onInk else Limen.colors.ink,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
    )
  }
}

/** The transport keys' smaller sibling: same plate, room for three in a row. */
@Composable
fun SmallKey(
  label: String,
  modifier: Modifier = Modifier,
  tick: Tick = Tick.Key,
  enabled: Boolean = true,
  onPress: () -> Unit,
) {
  KeySurface(modifier, enabled = enabled, shape = RoundedCornerShape(11.dp), tick = tick, onPress = onPress) {
    Text(
      label,
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      letterSpacing = 0.4.sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
      color = Limen.colors.ink,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
    )
  }
}

/** One small choice: the pills, chips and state markers all press like this. */
@Composable
fun Chip(
  label: String,
  active: Boolean = false,
  modifier: Modifier = Modifier,
  dim: Boolean = true,
  tick: Tick = Tick.Key,
  enabled: Boolean = true,
  onPress: () -> Unit,
) {
  KeySurface(
    modifier,
    active = active,
    enabled = enabled,
    shape = RoundedCornerShape(9.dp),
    tick = tick,
    onPress = onPress,
  ) {
    Text(
      label,
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      letterSpacing = 0.3.sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
      color = when {
        active -> Limen.colors.onInk
        dim -> Limen.colors.inkDim
        else -> Limen.colors.ink
      },
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
    )
  }
}

/**
 * The language menu: one compact key showing the current language, the five spelled out
 * behind it. Each name is written in its own language, so it never needs translating and
 * is findable by someone lost in the wrong one.
 */
@Composable
fun LangMenu(current: Lang, onSelect: (Lang) -> Unit) {
  var open by remember { androidx.compose.runtime.mutableStateOf(false) }
  val names = mapOf(
    Lang.En to "English",
    Lang.Fr to "Français",
    Lang.De to "Deutsch",
    Lang.It to "Italiano",
    Lang.Rm to "Rumantsch",
  )
  Box {
    KeySurface(shape = RoundedCornerShape(9.dp), onPress = { open = true }) {
      Text(
        "${current.name.uppercase()} ▾",
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        color = Limen.colors.ink,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      )
    }
    androidx.compose.material3.DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      containerColor = Limen.colors.plateRaised,
    ) {
      Lang.entries.forEach { l ->
        androidx.compose.material3.DropdownMenuItem(
          text = {
            Text(
              names.getValue(l),
              fontFamily = FontFamily.SansSerif,
              fontWeight = if (l == current) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 15.sp,
              color = Limen.colors.ink,
            )
          },
          trailingIcon = if (l == current) {
            { Text("✓", fontSize = 14.sp, color = Limen.colors.ink) }
          } else null,
          onClick = {
            onSelect(l)
            open = false
          },
        )
      }
    }
  }
}

/**
 * The voice that walks the user through a step: one readable sentence at the top, full
 * body size, never fine print. This is instruction, not decoration, so it gets room.
 */
@Composable
fun StepLead(text: String) {
  Row(
    Modifier
      .fillMaxWidth()
      .background(Limen.colors.plateRaised, RoundedCornerShape(10.dp))
      .border(1.dp, Limen.colors.plateEdge, RoundedCornerShape(10.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.bodyLarge,
      color = Limen.colors.ink,
    )
  }
}

/** A row of options as pills, the house selector: languages, protocol styles, deposit kinds. */
@Composable
fun <T> PillRow(
  options: List<Pair<T, String>>,
  current: T,
  dense: Boolean = false,
  onSelect: (T) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 6.dp),
    verticalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 6.dp),
  ) {
    options.forEach { (value, label) ->
      Chip(label, active = value == current, enabled = value != current) { onSelect(value) }
    }
  }
}

/** The house text field: a stamped label over a plain line of ink on the raised plate. */
@Composable
fun Field(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  singleLine: Boolean = true,
  hint: String? = null,
  onChange: (String) -> Unit,
) {
  androidx.compose.material3.OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = modifier.fillMaxWidth(),
    singleLine = singleLine,
    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    placeholder = hint?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim) } },
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Limen.colors.ink),
    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
      focusedBorderColor = Limen.colors.ink,
      unfocusedBorderColor = Limen.colors.plateEdge,
      focusedLabelColor = Limen.colors.ink,
      unfocusedLabelColor = Limen.colors.inkDim,
      cursorColor = Limen.colors.ink,
      focusedContainerColor = Limen.colors.plateRaised,
      unfocusedContainerColor = Limen.colors.plateRaised,
    ),
  )
}

/** A count with its two keys: keys are counted, never typed, at the door. */
@Composable
fun Stepper(count: Int, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    KeySurface(
      enabled = count > 0,
      shape = RoundedCornerShape(9.dp),
      onPress = { if (count > 0) onChange(count - 1) },
    ) {
      Text(
        "−",
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = Limen.colors.ink,
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
      )
    }
    Text(
      count.toString(),
      fontFamily = FontFamily.Monospace,
      fontSize = 17.sp,
      color = Limen.colors.ink,
      modifier = Modifier.padding(horizontal = 14.dp),
    )
    KeySurface(shape = RoundedCornerShape(9.dp), onPress = { onChange(count + 1) }) {
      Text(
        "+",
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = Limen.colors.ink,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      )
    }
  }
}

/** A key that stays down. Ink when engaged, as every engaged control in this app is. */
@Composable
fun Toggle(
  label: String,
  on: Boolean,
  modifier: Modifier = Modifier,
  dense: Boolean = false,
  onPress: () -> Unit,
) {
  KeySurface(modifier, active = on, shape = RoundedCornerShape(9.dp), onPress = onPress) {
    Text(
      label,
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      letterSpacing = 0.3.sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
      color = if (on) Limen.colors.onInk else Limen.colors.inkDim,
      modifier = Modifier.padding(horizontal = if (dense) 10.dp else 13.dp, vertical = 9.dp),
    )
  }
}
