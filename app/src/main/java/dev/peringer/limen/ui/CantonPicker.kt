package dev.peringer.limen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.theme.Limen

private const val CANTON_COLUMNS = 4

/** The object canton, with the complete flag grid one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantonPicker(s: Str, selected: SwissCanton?, onSelect: (SwissCanton) -> Unit) {
  var open by rememberSaveable { mutableStateOf(false) }
  val locked = LocalRecordLocked.current

  KeySurface(
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.selectCanton },
    enabled = !locked,
    shape = RoundedCornerShape(10.dp),
    onPress = { open = true },
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      CantonFlag(selected, contentDescription = null, size = 28.dp)
      Column(Modifier.weight(1f)) {
        Text(s.cantonLabel, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
        Text(
          selected?.let(s::canton) ?: s.selectCanton,
          style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Limen.colors.inkDim)
    }
  }

  if (open && !locked) {
    ModalBottomSheet(
      onDismissRequest = { open = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = Limen.colors.plateRaised,
    ) {
      CantonGrid(s, selected) { canton ->
        onSelect(canton)
        open = false
      }
    }
  }
}

@Composable
private fun CantonGrid(s: Str, selected: SwissCanton?, onSelect: (SwissCanton) -> Unit) {
  Column(
    Modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp)
      .padding(bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      s.selectCanton,
      style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
      color = Limen.colors.ink,
    )
    SwissCanton.entries.chunked(CANTON_COLUMNS).forEach { cantons ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cantons.forEach { canton ->
          CantonTile(
            s = s,
            canton = canton,
            selected = canton == selected,
            modifier = Modifier.weight(1f),
            onPress = { onSelect(canton) },
          )
        }
        repeat(CANTON_COLUMNS - cantons.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun CantonTile(
  s: Str,
  canton: SwissCanton,
  selected: Boolean,
  modifier: Modifier,
  onPress: () -> Unit,
) {
  KeySurface(
    modifier = modifier
      .height(64.dp)
      .semantics { contentDescription = s.canton(canton) },
    active = selected,
    shape = RoundedCornerShape(12.dp),
    onPress = onPress,
  ) {
    Column(
      Modifier.fillMaxWidth().padding(vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      CantonFlag(canton, contentDescription = null, modifier = Modifier.size(26.dp), size = 26.dp)
      Text(
        canton.code,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (selected) Limen.colors.onInk else Limen.colors.ink,
      )
    }
  }
}
