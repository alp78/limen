package dev.verso.limen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.theme.Limen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew

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

/** A raised panel with its stamped label; findings can use the warning treatment. */
@Composable
fun Panel(
  label: String,
  info: Tip? = null,
  warning: Boolean = false,
  content: @Composable () -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .background(if (warning) Limen.colors.warning else Limen.colors.plateRaised, RoundedCornerShape(12.dp))
      .border(
        1.5.dp,
        if (warning) Limen.colors.warningOutline else Limen.colors.actionOutline,
        RoundedCornerShape(12.dp),
      )
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

/**
 * The shared action surface. The visual depth is deliberately part of the control itself:
 * a lighter top, the palette colour through its face, and a darker lower edge. On touch the
 * face travels down into that edge, making the pressed state legible without changing Limen's
 * approved palette.
 */
@Composable
fun KeySurface(
  modifier: Modifier = Modifier,
  active: Boolean = false,
  enabled: Boolean = true,
  shape: RoundedCornerShape = RoundedCornerShape(12.dp),
  fillColor: Color? = null,
  outlineColor: Color? = null,
  tick: Tick = Tick.Key,
  onPress: () -> Unit,
  content: @Composable () -> Unit,
) {
  val play = rememberTick()
  val fill = when {
    !enabled && !active -> Limen.colors.disabled
    fillColor != null -> fillColor
    active -> Limen.colors.action
    else -> Limen.colors.input
  }
  val outline = when {
    !enabled && !active -> Limen.colors.plateEdge
    outlineColor != null -> outlineColor
    active -> Limen.colors.action
    else -> Limen.colors.actionOutline
  }
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val depth = if (enabled) 3.dp else 2.dp
  val faceOffset by animateDpAsState(
    targetValue = if (pressed && enabled) depth else 0.dp,
    animationSpec = tween(durationMillis = 90),
    label = "keyFaceOffset",
  )
  val topLight = fill.lifted(if (Limen.colors.isDark) 0.18f else 0.30f)
  val lowerTone = fill.shaded(if (Limen.colors.isDark) 0.18f else 0.12f)
  val edge = fill.shaded(if (enabled) 0.16f else 0.08f)
  val faceOutline = if (
    !Limen.colors.isDark && !active && fillColor == null && outlineColor == null
  ) {
    Limen.colors.plateEdge
  } else {
    outline
  }
  val face = remember(topLight, fill, lowerTone) {
    Brush.verticalGradient(
      0.0f to topLight,
      0.36f to fill,
      1.0f to lowerTone,
    )
  }

  Layout(
    content = content,
    modifier = modifier
      .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
      .clip(shape)
      .background(edge, shape)
      .drawBehind {
        val faceHeight = size.height - depth.toPx()
        val faceOffsetPx = faceOffset.toPx()
        if (faceHeight <= 0f) return@drawBehind
        val faceSize = Size(size.width, faceHeight)
        val radius = shape.topStart.toPx(faceSize, this).coerceAtMost(faceHeight / 2f)
        val corner = CornerRadius(radius, radius)
        withTransform({ translate(top = faceOffsetPx) }) {
          drawRoundRect(brush = face, size = faceSize, cornerRadius = corner)
          drawRoundRect(
            color = faceOutline,
            size = faceSize,
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx()),
          )
        }
      }
      .border(1.dp, edge, shape)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
      ) {
        play(tick)
        onPress()
      },
  ) { measurables, constraints ->
    val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(contentConstraints) }
    val contentWidth = placeables.maxOfOrNull { it.width } ?: 0
    val contentHeight = placeables.maxOfOrNull { it.height } ?: 0
    val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
    val depthPx = depth.roundToPx()
    val height = (contentHeight + depthPx).coerceIn(constraints.minHeight, constraints.maxHeight)
    val faceHeight = (height - depthPx).coerceAtLeast(0)
    val faceOffsetPx = faceOffset.roundToPx()
    layout(width, height) {
      placeables.forEach { placeable ->
        placeable.placeRelative(
          x = (width - placeable.width) / 2,
          y = faceOffsetPx + (faceHeight - placeable.height) / 2,
        )
      }
    }
  }
}

private fun Color.lifted(amount: Float): Color = Color.White.copy(alpha = amount).compositeOver(this)

private fun Color.shaded(amount: Float): Color = Color.Black.copy(alpha = amount).compositeOver(this)

/** Universal controls use the familiar Material icon instead of repeating a text label. */
enum class ButtonGlyph {
  Add, Remove, Delete, Edit, Camera, Gallery, Back, Next, Confirm, Cancel, Clear, Share, Open,
}

fun ButtonGlyph.vector(): ImageVector = when (this) {
  ButtonGlyph.Add -> Icons.Outlined.Add
  ButtonGlyph.Remove -> Icons.Outlined.Remove
  ButtonGlyph.Delete -> Icons.Outlined.Delete
  ButtonGlyph.Edit -> Icons.Outlined.Edit
  ButtonGlyph.Camera -> Icons.Outlined.PhotoCamera
  ButtonGlyph.Gallery -> Icons.Outlined.PhotoLibrary
  ButtonGlyph.Back -> Icons.AutoMirrored.Outlined.ArrowBack
  ButtonGlyph.Next -> Icons.AutoMirrored.Outlined.ArrowForward
  ButtonGlyph.Confirm -> Icons.Outlined.Check
  ButtonGlyph.Cancel -> Icons.Outlined.Close
  ButtonGlyph.Clear -> Icons.Outlined.Refresh
  ButtonGlyph.Share -> Icons.Outlined.Share
  ButtonGlyph.Open -> Icons.AutoMirrored.Outlined.OpenInNew
}

@Composable
fun IconKey(
  glyph: ButtonGlyph,
  contentDescription: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
  fillColor: Color? = null,
  outlineColor: Color? = null,
  tint: Color? = null,
  tick: Tick = Tick.Key,
  onPress: () -> Unit,
) {
  val resolvedFill = if (destructive) Limen.colors.danger else fillColor
  val resolvedOutline = if (destructive) Limen.colors.dangerOutline else outlineColor
  val resolvedTint = if (destructive) Limen.colors.dangerOutline else tint
  KeySurface(
    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).then(modifier),
    enabled = enabled,
    shape = RoundedCornerShape(12.dp),
    fillColor = resolvedFill,
    outlineColor = resolvedOutline,
    tick = tick,
    onPress = onPress,
  ) {
    Icon(
      imageVector = glyph.vector(),
      contentDescription = contentDescription,
      modifier = Modifier.size(20.dp),
      tint = if (enabled) resolvedTint ?: Limen.colors.ink else Limen.colors.disabledInk,
    )
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
      color = when {
        active -> Limen.colors.onInk
        !enabled -> Limen.colors.disabledInk
        else -> Limen.colors.ink
      },
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
  destructive: Boolean = false,
  onPress: () -> Unit,
) {
  val destructiveFill = if (destructive) {
    if (active) Limen.colors.dangerOutline else Limen.colors.danger
  } else null
  KeySurface(
    modifier,
    active = active && !destructive,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    fillColor = destructiveFill,
    outlineColor = if (destructive) Limen.colors.dangerOutline else null,
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
        destructive && active -> Limen.colors.onInk
        destructive -> Limen.colors.dangerOutline
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
    IconKey(
      glyph = ButtonGlyph.Remove,
      contentDescription = "Decrease",
      enabled = count > 0,
      onPress = { if (count > 0) onChange(count - 1) },
    )
    Text(
      count.toString(),
      fontFamily = FontFamily.Monospace,
      fontSize = 17.sp,
      color = Limen.colors.ink,
      modifier = Modifier.padding(horizontal = 14.dp),
    )
    IconKey(ButtonGlyph.Add, "Increase") { onChange(count + 1) }
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
