package dev.peringer.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.sense.LifeKey
import dev.peringer.limen.sense.Lifespans
import dev.peringer.limen.sense.Zeitwert
import dev.peringer.limen.theme.Limen
import java.util.Locale

/**
 * The Zeitwert calculation belongs to a specific charged defect, not the home screen. Pick
 * the item, state its age and repair cost, then use the tenant share in that defect's record.
 */
@Composable
fun WearAssessment(s: Str, modifier: Modifier = Modifier) {
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var lifespan by rememberSaveable { mutableStateOf("8") }
  var age by rememberSaveable { mutableStateOf("") }
  var cost by rememberSaveable { mutableStateOf("") }
  var showTable by rememberSaveable { mutableStateOf(false) }
  val play = rememberTick()

  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Panel(label = s.faceValue, info = s.tipZeitwert()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // The picked thing, or the way into the table.
          val pickedKey = picked?.let { name -> LifeKey.entries.firstOrNull { it.name == name } }
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Limen.colors.input)
              .border(1.5.dp, Limen.colors.actionOutline, RoundedCornerShape(10.dp))
              .clickable {
                play(Tick.Key)
                showTable = !showTable
              }
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              pickedKey?.let { s.life(it) } ?: s.customLifespan,
              style = MaterialTheme.typography.bodyMedium,
              color = Limen.colors.ink,
              modifier = Modifier.weight(1f),
            )
            Text(if (showTable) "▴" else "▾", color = Limen.colors.inkDim)
          }
          if (showTable) {
            Revealed(showTable) {
            LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
              items(Lifespans.table) { row ->
                Row(
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                      play(Tick.Key)
                      picked = row.key.name
                      lifespan = row.years.toInt().toString()
                      showTable = false
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                  Text(
                    s.life(row.key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Limen.colors.ink,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    "${row.years.toInt()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Limen.colors.inkDim,
                  )
                }
              }
            }
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(s.lifespanYears, lifespan, Modifier.weight(1f)) { v ->
              lifespan = v.take(5)
              picked = null
            }
            Field(s.ageYears, age, Modifier.weight(1f)) { v -> age = v.take(5) }
          }
          Field(
            s.repairCost,
            cost,
            keyboardType = KeyboardType.Decimal,
            isError = cost.isNotBlank() && swissNumber(cost) == null,
          ) { v -> cost = v.take(12) }
        }
      }

      // The answer.
      val lifespanV = swissNumber(lifespan) ?: 0.0
      val ageV = swissNumber(age)
      val costV = swissNumber(cost)
      if (lifespanV > 0 && ageV != null) {
        val fraction = Zeitwert.remainingFraction(lifespanV, ageV)
        Panel(label = s.tenantShare) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val share = costV?.let { Zeitwert.tenantShare(it, lifespanV, ageV) }
            val zero = (share != null && share == 0.0) || (share == null && fraction == 0.0)
            Text(
              share?.let { "CHF " + String.format(Locale.ROOT, "%.0f", it) }
                ?: String.format(Locale.ROOT, "%.0f%%", fraction * 100),
              fontFamily = FontFamily.Monospace,
              fontSize = 54.sp,
              color = if (zero) Limen.colors.seal else Limen.colors.ink,
            )
            Text(
              "${s.remainingValue}: " + String.format(Locale.ROOT, "%.0f%%", fraction * 100),
              style = MaterialTheme.typography.labelLarge,
              color = Limen.colors.inkDim,
            )
          }
        }
      }
  }
}

/** The Swiss thousands separator, straight and typographic alike. */
private const val GROUPING_MARKS = "'’"

/**
 * A figure as a person actually types it here: "1'200.50", "1 200,50", "1200.5".
 *
 * The apostrophe is the Swiss thousands separator and toDoubleOrNull refuses it, so a cost
 * entered the normal way parsed as null. The headline then silently changed unit from francs
 * to a percentage, with nothing on the field to say the figure had been ignored.
 */
private fun swissNumber(typed: String): Double? = typed
  .filterNot { it in GROUPING_MARKS || it.isWhitespace() }
  .replace(',', '.')
  .toDoubleOrNull()
