package dev.verso.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.sense.LifeKey
import dev.verso.limen.sense.Lifespans
import dev.verso.limen.sense.Zeitwert
import dev.verso.limen.theme.Limen
import java.util.Locale

/**
 * The Zeitwert face: the arithmetic every deposit dispute turns on, on one screen. Pick a
 * thing or type a lifespan, say how old it is and what the repair costs, and read off the
 * tenant's share. The iron rules stand under the number, and the share wears the seal
 * green only when it is ZERO, because that is the settled outcome the table exists for.
 */
@Composable
fun ValueScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  var lifespan by rememberSaveable { mutableStateOf("8") }
  var age by rememberSaveable { mutableStateOf("") }
  var cost by rememberSaveable { mutableStateOf("") }
  var showTable by rememberSaveable { mutableStateOf(false) }
  val play = rememberTick()

  Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    AppHeader(vm, s)
    Spacer(Modifier.height(12.dp))

    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        LimenMark(size = 76.dp)
      }
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
          Field(s.repairCost, cost) { v -> cost = v.take(9) }
        }
      }

      // The answer.
      val lifespanV = lifespan.replace(',', '.').toDoubleOrNull() ?: 0.0
      val ageV = age.replace(',', '.').toDoubleOrNull()
      val costV = cost.replace(',', '.').toDoubleOrNull()
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

      Spacer(Modifier.height(12.dp))
    }
  }
}
