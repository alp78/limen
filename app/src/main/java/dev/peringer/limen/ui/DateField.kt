package dev.peringer.limen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.SwissDate
import dev.peringer.limen.theme.Limen
import java.time.LocalDate

/** A day the picker returns, counted from the epoch it hands back in UTC milliseconds. */
private const val DAY_MS = 86_400_000L

/**
 * A date, typed or picked, never merely typed at.
 *
 * Both roads exist because both are used: a person reading a tenancy agreement aloud types
 * the date faster than they can reach it on a grid, and a person standing in a hallway
 * working out which Tuesday the keys go back wants the grid. What neither road allows any
 * more is a date that is not one. The keyboard is masked to DD/MM/YYYY, the value only
 * reaches the record once it names a real day, and 31/02 is marked rather than stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
  label: String,
  stored: String,
  s: Str,
  modifier: Modifier = Modifier,
  onChange: (String) -> Unit,
) {
  val locked = LocalRecordLocked.current
  // Keyed on the stored value: an accepted date, the picker, and a record opened fresh all
  // arrive through it, while a half-typed date leaves it alone and so survives typing.
  val text = SwissDate.display(stored)
  var lastValid by androidx.compose.runtime.saveable.rememberSaveable(label) { mutableStateOf(stored.takeIf { SwissDate.parse(it) != null }.orEmpty()) }
  androidx.compose.runtime.LaunchedEffect(stored) { if (SwissDate.parse(stored) != null) lastValid = stored }
  var picking by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  val digits = text.filter(Char::isDigit).length
  // A date is wrong when it is complete and impossible, and equally wrong when the field has
  // been left half typed: "31/08/26" masks to a complete-looking Swiss date, stores nothing
  // and used to carry no error mark at all, so a repair deadline silently never reached the
  // record. Only flagged once the field is left, so it does not shout at the first digit.
  val impossible = when {
    digits == 8 -> SwissDate.toStored(text) == null
    else -> digits in 1..7 && !focused
  }
  val calendar: (@Composable () -> Unit)? = if (locked) null else {
    { IconKey(ButtonGlyph.Calendar, s.pickDate, Modifier.padding(end = 4.dp)) { picking = true } }
  }

  Field(
    label = label,
    value = text,
    modifier = modifier,
    hint = s.dateFormat,
    keyboardType = KeyboardType.Number,
    isError = impossible,
    trailing = calendar,
    onFocusChanged = { focused = it },
  ) { raw ->
    val text = SwissDate.mask(raw)
    val iso = SwissDate.toStored(text)
    // Retain an unfinished/invalid edit in the draft; never silently export the old date.
    onChange(iso ?: text)
  }
  if (!locked && impossible && lastValid.isNotBlank()) IconKey(ButtonGlyph.Undo, s.words("Restore last valid date", "Rétablir la dernière date valide", "Letztes gültiges Datum übernehmen", "Ripristina l’ultima data valida", "Restabilir l’ultima data valaivla")) { onChange(lastValid) }

  if (picking) {
    val start = SwissDate.parse(stored) ?: LocalDate.now()
    val state = rememberDatePickerState(initialSelectedDateMillis = start.toEpochDay() * DAY_MS)
    DatePickerDialog(
      onDismissRequest = { picking = false },
      confirmButton = {
        SmallKey(s.done) {
          state.selectedDateMillis?.let { ms ->
            onChange(LocalDate.ofEpochDay(Math.floorDiv(ms, DAY_MS)).toString())
          }
          picking = false
        }
      },
      dismissButton = { SmallKey(s.cancel) { picking = false } },
      colors = DatePickerDefaults.colors(containerColor = Limen.colors.plateRaised),
    ) {
      DatePicker(
        state = state,
        colors = DatePickerDefaults.colors(
          containerColor = Limen.colors.plateRaised,
          titleContentColor = Limen.colors.inkDim,
          headlineContentColor = Limen.colors.ink,
          weekdayContentColor = Limen.colors.inkDim,
          dayContentColor = Limen.colors.ink,
          selectedDayContainerColor = Limen.colors.action,
          selectedDayContentColor = Limen.colors.onInk,
          todayContentColor = Limen.colors.actionOutline,
          todayDateBorderColor = Limen.colors.actionOutline,
        ),
      )
    }
  }
}

/** The handover's hour, on the same contract as [DateField]: masked to HH:MM, or dialled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
  label: String,
  stored: String,
  s: Str,
  modifier: Modifier = Modifier,
  onChange: (String) -> Unit,
) {
  val locked = LocalRecordLocked.current
  val text = stored
  var lastValid by androidx.compose.runtime.saveable.rememberSaveable(label) { mutableStateOf(stored.takeIf { SwissDate.toStoredTime(it) != null }.orEmpty()) }
  androidx.compose.runtime.LaunchedEffect(stored) { if (SwissDate.toStoredTime(stored) != null) lastValid = stored }
  var picking by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  val digits = text.filter(Char::isDigit).length
  val impossible = when {
    digits == 4 -> SwissDate.toStoredTime(text) == null
    else -> digits in 1..3 && !focused
  }
  val dial: (@Composable () -> Unit)? = if (locked) null else {
    { IconKey(ButtonGlyph.Clock, s.pickTime, Modifier.padding(end = 4.dp)) { picking = true } }
  }

  Field(
    label = label,
    value = text,
    modifier = modifier,
    hint = s.timeFormat,
    keyboardType = KeyboardType.Number,
    isError = impossible,
    trailing = dial,
    onFocusChanged = { focused = it },
  ) { raw ->
    val text = SwissDate.maskTime(raw)
    val value = SwissDate.toStoredTime(text)
    onChange(value ?: text)
  }
  if (!locked && impossible && lastValid.isNotBlank()) IconKey(ButtonGlyph.Undo, s.words("Restore last valid time", "Rétablir la dernière heure valide", "Letzte gültige Uhrzeit übernehmen", "Ripristina l’ultima ora valida", "Restabilir l’ultima ura valaivla")) { onChange(lastValid) }

  if (picking) {
    val existing = stored.split(':')
    val state = rememberTimePickerState(
      initialHour = existing.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12,
      initialMinute = existing.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
      is24Hour = true,
    )
    AlertDialog(
      onDismissRequest = { picking = false },
      containerColor = Limen.colors.plateRaised,
      title = null,
      text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          TimePicker(state = state)
        }
      },
      confirmButton = {
        SmallKey(s.done) {
          // Locale.ROOT: the stored time is data, and "%02d" under an Arabic or Bengali
          // handset locale writes digits the parser then refuses to read back.
          onChange(String.format(java.util.Locale.ROOT, "%02d:%02d", state.hour, state.minute))
          picking = false
        }
      },
      dismissButton = { SmallKey(s.cancel) { picking = false } },
    )
  }
}

/** Two fields that belong side by side wherever a handover states when it happened. */
@Composable
fun DateAndTimeRow(
  dateLabel: String,
  date: String,
  timeLabel: String,
  time: String,
  s: Str,
  onDate: (String) -> Unit,
  onTime: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    DateField(dateLabel, date, s, Modifier.fillMaxWidth(), onDate)
    TimeField(timeLabel, time, s, Modifier.fillMaxWidth(), onTime)
  }
}
