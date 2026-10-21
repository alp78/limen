package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*

/**
 * One numbered finding as a block: where it was found, its words, the facts hanging on it
 * as chips, the charge first and in the active colour because Art. 267a OR makes it the
 * column a court reads, and each participant's position.
 */
internal fun Sheet.findingBlock(p: Protocol, numbered: Report.Numbered): Finding {
  val d = numbered.defect
  val where = buildList {
    if (d.objectId.isNotBlank()) p.objects.firstOrNull { it.id == d.objectId }?.let { add(it.displayName(s)) }
    p.defectItemLabel(d, s)?.let(::add)
  }.joinToString(", ")
  val chips = buildList {
    if (d.chargedToTenant) add(Chip(s.chargedToTenant, active = true))
    when (d.fixBy) {
      FixBy.Tenant -> add(Chip("${s.fixByLabel}: ${s.role(Role.Tenant)}"))
      FixBy.Landlord -> add(Chip("${s.fixByLabel}: ${s.role(Role.Landlord)}"))
      FixBy.Open -> Unit
    }
    if (d.fixDeadline.isNotBlank()) add(Chip("${s.fixDeadline} ${SwissDate.display(d.fixDeadline)}"))
    if (d.photos.isNotEmpty()) add(Chip("${s.photos}: ${d.photos.size}"))
    if (d.tenantResponse != TenantResponse.NotStated) add(Chip("${s.tenantResponse}: ${s.tenantResponse(d.tenantResponse)}"))
  }
  val notes = buildList {
    if (d.costNote.isNotBlank()) add("${s.costNote}: ${d.costNote}")
    if (d.reservation.isNotBlank()) add("${s.reservationLabel}: ${d.reservation}")
    if (d.tenantComment.isNotBlank()) add("${s.tenantComment}: ${d.tenantComment}")
  }
  val positions = d.participantPositions.map { (id, position) ->
    val name = p.parties.firstOrNull { it.participantKey() == id }?.name ?: id
    "$name: ${s.tenantResponse(position.response)}\n${position.comment}"
  }
  return Finding(numbered.number, where, d.text, chips, notes, positions)
}

/**
 * Findings with no panel to sit in: a finding whose room is gone, or one on an object the
 * record no longer lists. Either would otherwise vanish from the paper while still counting
 * in the record and carrying a caption in the annex.
 */
internal fun Sheet.strayFindings(p: Protocol) {
  val strays = Outline.strays(p)
  if (strays.isEmpty()) return
  panel(Panel(s.defects, strays.map { findingBlock(p, it) }))
}
