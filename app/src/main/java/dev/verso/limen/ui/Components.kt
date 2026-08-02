package dev.verso.limen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.geometry.Offset
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
  val tint = Limen.colors.actionOutline
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
  val wash = Limen.colors.actionOutline.copy(alpha = 0.08f * progress.value)
  val edge = Limen.colors.actionOutline.copy(alpha = 0.60f * progress.value)
  Box(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
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
      .background(Limen.colors.plateRaised, RoundedCornerShape(12.dp))
      .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(12.dp))
      .padding(16.dp)
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

/** The shared outlined control surface used by every actionable element. */
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
  val scale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.98f else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
    label = "keyScale",
  )
  val fill = when {
    !enabled && !active -> Limen.colors.disabled
    active -> Limen.colors.action
    else -> Limen.colors.input
  }
  val outline = when {
    !enabled && !active -> Limen.colors.plateEdge
    active -> Limen.colors.action
    else -> Limen.colors.actionOutline
  }
  Box(
    modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (enabled || active) 1f else 0.45f
      }
      .clip(shape)
      .background(fill)
      .border(1.5.dp, outline, shape)
      .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
        play(tick)
        onPress()
      },
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** A transport-sized primary action. */
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
    shape = RoundedCornerShape(12.dp),
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

/** The transport keys' smaller sibling, outlined for secondary actions. */
@Composable
fun SmallKey(
  label: String,
  modifier: Modifier = Modifier,
  tick: Tick = Tick.Key,
  enabled: Boolean = true,
  onPress: () -> Unit,
) {
  KeySurface(modifier, enabled = enabled, shape = RoundedCornerShape(10.dp), tick = tick, onPress = onPress) {
    Text(
      label,
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      letterSpacing = 0.4.sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
      color = if (enabled) Limen.colors.ink else Limen.colors.disabledInk,
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
    shape = RoundedCornerShape(10.dp),
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
        !enabled -> Limen.colors.disabledInk
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
      .background(Limen.colors.plateRaised, RoundedCornerShape(12.dp))
      .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(12.dp))
      .padding(horizontal = 16.dp, vertical = 14.dp),
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
    shape = RoundedCornerShape(10.dp),
    singleLine = singleLine,
    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    placeholder = hint?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim) } },
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Limen.colors.ink),
    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
      focusedBorderColor = Limen.colors.actionOutline,
      unfocusedBorderColor = Limen.colors.actionOutline,
      focusedLabelColor = Limen.colors.actionOutline,
      unfocusedLabelColor = Limen.colors.inkDim,
      cursorColor = Limen.colors.actionOutline,
      focusedContainerColor = Limen.colors.input,
      unfocusedContainerColor = Limen.colors.input,
      disabledContainerColor = Limen.colors.disabled,
      disabledTextColor = Limen.colors.disabledInk,
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

/** A labelled switch for persistent on/off choices. */
@Composable
fun Toggle(
  label: String,
  on: Boolean,
  modifier: Modifier = Modifier,
  dense: Boolean = false,
  onPress: () -> Unit,
) {
  KeySurface(modifier, shape = RoundedCornerShape(10.dp), onPress = onPress) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = if (dense) 10.dp else 13.dp, vertical = if (dense) 7.dp else 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
        maxLines = 1,
        color = Limen.colors.ink,
        modifier = Modifier.weight(1f),
      )
      Box(
        Modifier
          .width(42.dp)
          .height(24.dp)
          .clip(CircleShape)
          .background(if (on) Limen.colors.action else Limen.colors.plateEdge)
          .padding(3.dp),
      ) {
        Box(
          Modifier
            .size(18.dp)
            .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
            .clip(CircleShape)
            .background(if (on) Limen.colors.onInk else Limen.colors.inkDim),
        )
      }
    }
  }
}
