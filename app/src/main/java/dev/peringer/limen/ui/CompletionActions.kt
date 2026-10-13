package dev.peringer.limen.ui

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
    FinalizationIssue.Dossier -> buildList {
      listOf(s.leaseConcludedOn to p.contract.conclusionDate, s.tenancyStart to p.contract.startDate,
        s.tenancyEnd to p.contract.endDate, s.noticeDate to p.contract.noticeDate, s.chargesBy to p.chargesStatementBy).forEach { (name, date) ->
        if (date.isNotBlank() && SwissDate.parse(date) == null) add(name)
      }
      p.defects.filter { it.fixDeadline.isNotBlank() && SwissDate.parse(it.fixDeadline) == null }.forEach { add("${s.fixDeadline}: ${it.text}") }
      if (p.deposit.kind != DepositKind.None) add(s.deposit)
      if (DossierCard.Meters in p.dossierCards()) add(s.meters)
      if (p.details.attachments.isNotEmpty()) add(s.attachments)
    }.joinToString()
    else -> ""
  }
  SmallKey(s.finalizationIssue(issue) + if (details.isBlank()) "" else " · $details", Modifier.fillMaxWidth()) {
    vm.navigateIssue(issue)
  }
}
