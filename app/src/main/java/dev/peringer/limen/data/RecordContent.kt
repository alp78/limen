package dev.peringer.limen.data

import dev.peringer.limen.sense.LifeKey
import dev.peringer.limen.ui.*

/** Supplemental sections shared by the preview, PDF and accessible companion. */
internal fun extraContent(p: Protocol, s: Str): List<Pair<String, List<String>>> = buildList {
  val d = p.details
  fun section(label: String, values: List<String>) { if (values.isNotEmpty()) add(label to values) }
  section(s.actualTransfer, listOfNotNull(
    d.actualTransferDate.takeIf { it.isNotBlank() }?.let { SwissDate.display(it) + " " + d.actualTransferTime },
    // Only an entry has the field to show or clear it, so an exit seeded before 1.2.7 with the entry's clause prints without it.
    d.notificationClause.takeIf { p.kind == Kind.Entry && it.isNotBlank() }?.let { "${s.notificationClause}: $it\n${d.notificationDays}" },
  ))
  section(s.understoodLanguage, listOf(d.understoodLanguage).filter(String::isNotBlank))
  val cards = p.dossierCards()
  section(s.notInspectedReasons, d.scopeNotes.mapNotNull { (id, reason) ->
    // A reason prints only while the thing it excuses is still missing. Its field hides itself
    // when the item is inspected, a room exists or the card is removed, and hiding does not
    // clear the text, so printing the whole map left lines nobody could see or delete.
    val room = p.rooms.firstOrNull { it.id == id }
    val entry = p.objects.firstOrNull { it.id == id }
    val label = when {
      room != null -> room.takeIf { !it.inspected }?.let { it.name.ifBlank { s.room(it.kind) } }
      entry != null -> entry.takeIf { !it.inspected }?.displayName(s)
      id == "Inspection" -> s.stepInspection.takeIf { p.rooms.isEmpty() }
      // The Keys reason stands in for a removed Keys card, so it is the one that needs the card absent.
      else -> DossierCard.entries.firstOrNull { it.name == id }
        ?.takeIf { (it in cards) != (it == DossierCard.Keys) }?.let(s::dossierCard)
    }
    label?.let { "$it: $reason" }
  })
  section(s.meters, d.readings.map { r ->
    "${r.label}\n${s.meterIdentifier}: ${r.identifier}\n${s.reading}: ${r.value} ${r.unit}\n" +
      "${SwissDate.display(r.date)} ${r.time}\n${r.note}"
  })
  section(s.accessDevices, d.accessDevices.map { a ->
    "${a.label}\n${a.identifier}\n${s.expected}: ${a.expected}\n${s.transferred}: ${a.transferred}\n${a.note}"
  })
  section(s.attachments, d.attachments.map { a ->
    "${s.documentKind(a.kind)}: ${a.title}\n${a.externalReference}\n${a.file}\n${a.bytes} B"
  })
  // No list of photographs here: each one carries its file name and capture time in the
  // annex, in its frame, beside the picture it describes.
  section(s.followUps, d.followUps.map { f ->
    "${f.description}\n${s.discoveredOn}: ${SwissDate.display(f.discoveredDate)}\n" +
      "${s.notifiedOn}: ${SwissDate.display(f.notifiedDate)}\n${f.recipient}\n${f.method}\n" +
      "${s.repairDeadline}: ${SwissDate.display(f.deadline)}\n${if (f.done) s.done else s.toVerify}"
  })
  section(s.faceValue, d.estimates.map { (id, e) ->
    val number = p.defects.indexOfFirst { it.id == id } + 1
    // The row is stored by its enum name; the table's own wording is what the screen shows for it.
    val basis = if (e.basis.isBlank()) s.customLifespan else LifeKey.entries.firstOrNull { it.name == e.basis }?.let(s::life) ?: e.basis
    "${s.defects} $number: $basis\n${s.repairCost}: ${e.replacementChf}\n${s.ageYears}: ${e.ageYears}\n${s.lifespanYears}: ${e.lifeYears}\n${e.date}\n${e.source}\n${s.estimateNotice}"
  })
  section(s.reservationLabel, p.parties.mapNotNull { party ->
    val note = d.reservations[party.participantKey()] ?: p.pendingRecordReservations[party.role]
      ?: p.signatureFor(party)?.reservation
    note?.takeIf { it.isNotBlank() }?.let { "${party.signerName()} (${s.role(party.role)}): $it" }
  })
  section(s.legalOverlayTitle, buildList {
    // Their inputs exist for NE and VD alone, and a canton change leaves the values behind.
    if (p.objectCanton == SwissCanton.NE && d.municipality.isNotBlank()) add("${s.municipality}: ${d.municipality}\n${s.habitableRooms}: ${d.habitableRooms}")
    if (p.objectCanton == SwissCanton.VD && d.district.isNotBlank()) add("${s.district}: ${d.district}")
    if (p.legalOverlay.checks.containsKey(LegalRequirement.CantonalDepositHandling)) add(s.depositLocal(p.objectCanton))
    (d.legalSnapshot ?: if (!p.isFinalized()) p.freezeLegalRules() else null)?.let { snapshot -> add(snapshot.ruleSetId + "\n" + snapshot.checkedOn); addAll(snapshot.sources) }
  })
}

fun Str.documentKind(kind: DocumentKind): String = when (kind) {
  DocumentKind.Lease -> contract
  DocumentKind.RentForm -> initialRentForm
  DocumentKind.Mandate -> authority
  DocumentKind.PriorReport -> lastProtocol
  DocumentKind.Deposit -> deposit
  DocumentKind.Delivery -> copyDelivery
  DocumentKind.Other -> words("Other document", "Autre document", "Andere Beilage", "Altro documento", "Auter document")
}
