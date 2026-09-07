package dev.peringer.limen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.theme.Limen
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
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
 * True while a finalized record is on screen. Everything in it can be walked into and read;
 * nothing in it can be changed, because its evidence fingerprint was computed over exactly
 * what it holds now. The way to change a signed record is an amendment, which is a new draft.
 *
 * Held as a local rather than passed down: every field on every screen has to honour it, and
 * a parameter that forty call sites can forget is a parameter that forty call sites will.
 */
val LocalRecordLocked = staticCompositionLocalOf { false }

/**
 * The house info affordance: anything that needs explaining lives behind this, never in
 * small print. The mark comes from the icon set, like every other control in the app.
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
      Icon(
        Icons.Outlined.Info,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp),
      )
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
  role: Role = Role.Button,
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
    // Neutral keys stay neutral in both schemes. Using the action outline here was the
    // source of the stray light-blue lower rule on dark secondary buttons.
    else -> Limen.colors.plateEdge
  }
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  // A smaller lower edge in night mode keeps the visible face the same weight as an
  // active blue key, without the light lower rule that made neutral keys look shorter.
  val depth = when {
    !enabled -> 2.dp
    Limen.colors.isDark -> 2.dp
    else -> 3.dp
  }
  val faceOffset by animateDpAsState(
    targetValue = if (pressed && enabled) depth else 0.dp,
    animationSpec = tween(durationMillis = 90),
    label = "keyFaceOffset",
  )
  val topLight = fill.lifted(if (Limen.colors.isDark) 0.18f else 0.30f)
  val lowerTone = fill.shaded(if (Limen.colors.isDark) 0.18f else 0.12f)
  val edge = fill.shaded(if (enabled) 0.16f else 0.08f)
  // A full stroke includes a horizontal rule at the lower face edge. On dark fills it
  // read as a stray blue or white line, so the night treatment relies on the tonal edge.
  val drawFaceOutline = !Limen.colors.isDark
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
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
          if (drawFaceOutline) {
            drawRoundRect(
              color = outline,
              size = faceSize,
              cornerRadius = corner,
              style = Stroke(width = 1.dp.toPx()),
            )
          }
        }
      }
      .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        role = role,
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
  Calendar, Clock,
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
  ButtonGlyph.Calendar -> Icons.Outlined.CalendarMonth
  ButtonGlyph.Clock -> Icons.Outlined.Schedule
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
  // A destructive key rejects by default. Leaving this to each call site meant six of the
  // twelve delete and clear keys felt like an ordinary press, which is the one thing
  // Tick.Reject exists to prevent. Pass it explicitly to override, as the armed two-stage
  // delete on the home screen does.
  tick: Tick = if (destructive) Tick.Reject else Tick.Key,
  onPress: () -> Unit,
) {
  val resolvedFill = if (destructive) Limen.colors.danger else fillColor
  val resolvedOutline = if (destructive) Limen.colors.dangerOutline else outlineColor
  val resolvedTint = if (destructive) Limen.colors.dangerOutline else tint
  KeySurface(
    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).then(modifier),
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
    active = active && enabled,
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
        active && enabled -> Limen.colors.onInk
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
  selectionState: Boolean? = null,
  onPress: () -> Unit,
) {
  val destructiveFill = if (destructive) {
    if (active) Limen.colors.dangerOutline else Limen.colors.danger
  } else null
  KeySurface(
    if (selectionState == null) modifier else modifier.semantics { selected = selectionState },
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
  val play = rememberTick()
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
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = Limen.colors.ink, modifier = Modifier.size(16.dp)) }
          } else null,
          onClick = {
            play(Tick.Key)
            onSelect(l)
            open = false
          },
        )
      }
    }
  }
}

/** A row of options as pills, the house selector: languages, protocol styles, deposit kinds. */
@Composable
fun <T> PillRow(
  options: List<Pair<T, String>>,
  current: T,
  dense: Boolean = false,
  enabled: Boolean = true,
  onSelect: (T) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 6.dp),
    verticalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 6.dp),
  ) {
    options.forEach { (value, label) ->
      Chip(
        label,
        active = value == current,
        enabled = enabled && value != current,
        selectionState = value == current,
      ) { onSelect(value) }
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
  keyboardType: KeyboardType = KeyboardType.Text,
  isError: Boolean = false,
  trailing: @Composable (() -> Unit)? = null,
  onFocusChanged: ((Boolean) -> Unit)? = null,
  editableWhenLocked: Boolean = false,
  onChange: (String) -> Unit,
) {
  androidx.compose.material3.OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = modifier.fillMaxWidth().onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
    shape = RoundedCornerShape(10.dp),
    singleLine = singleLine,
    isError = isError,
    // A finalized record is readable everywhere and writable nowhere, and the keyboard
    // never opening says so better than a keystroke that silently does nothing.
    readOnly = LocalRecordLocked.current && !editableWhenLocked,
    trailingIcon = trailing,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
fun Stepper(count: Int, s: Str, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconKey(
      glyph = ButtonGlyph.Remove,
      contentDescription = s.decrease,
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
    IconKey(ButtonGlyph.Add, s.increase) { onChange(count + 1) }
  }
}

/** A labelled switch for persistent on/off choices. */
@Composable
fun Toggle(
  label: String,
  on: Boolean,
  modifier: Modifier = Modifier,
  dense: Boolean = false,
  enabled: Boolean = true,
  onPress: () -> Unit,
) {
  KeySurface(
    modifier.semantics { toggleableState = ToggleableState(on) },
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    role = Role.Switch,
    // Pressing a switch that is on turns it off, so the weight names the state it lands in.
    tick = if (on) Tick.ToggleOff else Tick.ToggleOn,
    onPress = onPress,
  ) {
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
