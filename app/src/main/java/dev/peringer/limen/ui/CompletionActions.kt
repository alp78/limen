package dev.peringer.limen.ui

import dev.peringer.limen.theme.Limen
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import dev.peringer.limen.data.*

@Composable
internal fun CompletionAction(vm: LimenViewModel, s: Str, p: Protocol, issue: FinalizationIssue) {
  val details = when (issue) {
    FinalizationIssue.Rooms -> p.rooms.filter { !it.inspected && p.details.scopeNotes[it.id].isNullOrBlank() }.joinToString { it.name.ifBlank { s.room(it.kind) } }
    FinalizationIssue.Objects -> p.objects.filter { !it.inspected && p.details.scopeNotes[it.id].isNullOrBlank() }.joinToString { it.displayName(s) }
    FinalizationIssue.Parties -> p.parties.filter { it.name.isBlank() || it.representedBy.isNotBlank() && it.authority.isBlank() }.joinToString { s.role(it.role) + " " + it.name }
    FinalizationIssue.Dossier -> dossierCauses(p, s).joinToString()
    FinalizationIssue.HandoverTime -> timeCauses(p, s).joinToString()
    else -> ""
  }
  Text(
    s.finalizationIssue(issue) + if (details.isBlank()) "" else " · $details",
    style = MaterialTheme.typography.bodyMedium,
    color = Limen.colors.ink,
    modifier = Modifier.fillMaxWidth().clickable { vm.navigateIssue(issue) }.padding(vertical = 6.dp),
  )
}
