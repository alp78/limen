package dev.peringer.limen.data.pdf

import android.graphics.RectF
import dev.peringer.limen.data.*
import dev.peringer.limen.data.pdf.PdfTokens.MARGIN
import dev.peringer.limen.data.pdf.PdfTokens.ON_NAVY_DIM
import dev.peringer.limen.data.pdf.PdfTokens.PAPER
import dev.peringer.limen.data.pdf.PdfTokens.W
import dev.peringer.limen.ui.*

/**
 * Page 1: the landing's navy field with the seal, the name and what this record is, then
 * the facts a reader wants first, the property, the parties, the record's status, and the
 * contents with each section's page. [pages] are the counting pass's, or null while counting.
 */
internal fun Sheet.cover(p: Protocol, planned: List<Outline.Entry>, pages: List<Int>?) {
  newPage(cover = true)
  val kind = if (p.kind == Kind.Entry) s.entry else s.exit
  val moment = listOf(SwissDate.display(p.handoverDate), p.handoverTime).filter { it.isNotBlank() }.joinToString(" ")
  val canton = p.objectCanton?.let { s.canton(it) }.orEmpty()

  canvas.drawRoundRect(RectF(MARGIN, MARGIN, W - MARGIN, BANNER_BOTTOM), Sheet.BANNER_RADIUS, Sheet.BANNER_RADIUS, paints.navyFill)
  logo?.let { canvas.drawBitmap(it, null, RectF(138f, 146f, 266f, 274f), paints.bitmap) }
  canvas.drawText("LIMEN", 306f, 216f, paints.display)
  canvas.drawText(s.protocolKind.uppercase(), 306f, 252f, paints.labelLarge.tinted(ON_NAVY_DIM))
  pill(Pill(kind, PillTone.Paper), W - MARGIN - 48f, 146f)
  val address = paints.title.tinted(PAPER)
  val dim = paints.bodyLarge.tinted(ON_NAVY_DIM)
  val paper = paints.bodyLarge.tinted(PAPER)
  val rightX = W - MARGIN - 48f
  val momentWidth = paper.measureText(moment)
  val cantonWidth = dim.measureText(canton)
  val leftMax = rightX - maxOf(momentWidth, cantonWidth) - 40f - 138f
  canvas.drawText(TextMeasure.ellipsize(p.address, address, leftMax), 138f, 400f, address)
  canvas.drawText(TextMeasure.ellipsize(p.place, dim, leftMax), 138f, 440f, dim)
  canvas.drawText(moment, rightX - momentWidth, 400f, paper)
  canvas.drawText(canton, rightX - cantonWidth, 440f, dim)
  html.banner("Limen", listOf("${s.protocolKind}: $kind", p.address, p.place, moment, canton))
  y = BANNER_BOTTOM + 40f

  panel(Panel(s.objectLabel, property(p, moment), inContents = false))
  panel(Panel(s.parties, parties(p), inContents = false))
  val status = if (p.isFinalized()) Pill(s.pdfFinalized, PillTone.Seal) else Pill(s.previewDraft, PillTone.Dim)
  panel(Panel(s.stepRecord, status(p), pill = status, inContents = false))
  val entries = planned.mapIndexed { i, entry -> Contents.Entry(entry.title, entry.level, anchor = anchor(i)) }
  panel(Panel(s.contents, listOf(Contents(entries, pages ?: List(planned.size) { null })), inContents = false))
  // The body starts on a fresh page when the cover held everything; a contents that ran on
  // to a second page is followed by the body on that page rather than a blank one.
  if (pageNo == 1) newPage()
}

private const val BANNER_BOTTOM = 510f

private fun Sheet.property(p: Protocol, moment: String): List<Block> = buildList {
  val first = mutableListOf(s.address to p.address, s.place to p.place)
  p.objectCanton?.let {
    first += s.cantonLabel to s.canton(it)
    first += s.legalBasis to s.legalProfile(p.legalProfile())
  }
  add(Fields(first, 2))
  val second = mutableListOf(s.objectDetail to p.objectDetail, s.handoverDate to moment)
  if (p.lastProtocolDate.isNotBlank()) second += s.lastProtocol to SwissDate.display(p.lastProtocolDate)
  add(Fields(second, 2))
}

private fun Sheet.parties(p: Protocol): List<Block> = buildList {
  p.parties.forEach { party ->
    add(Fields(listOf(s.role(party.role) to party.name), 1))
    if (party.representedBy.isNotBlank()) add(Text("${s.representedBy}: ${party.representedBy}", Style.Small))
    if (party.agency.isNotBlank()) add(Text("${s.actorAgency}: ${party.agency}", Style.Small))
    if (party.authority.isNotBlank()) add(Text("${s.authority}: ${party.authority}", Style.Small))
    if (party.phone.isNotBlank()) add(Text(party.phone, Style.Small))
    if (party.email.isNotBlank()) add(Text(party.email, Style.Small))
    if (party.address.isNotBlank()) add(Text("${s.newAddress}: ${party.address}", Style.Small))
  }
  // Who compiled the record, with the contact the session was opened under. This was
  // collected and validated at the start of every session and then printed nowhere, which
  // made the whole identity form a set of fields that changed no document.
  val by = p.sessionIdentity
  val contact = buildList {
    // An agency session names the firm first and the person present as its representative.
    if (by.company.isNotBlank()) {
      add(by.company)
      by.fullName.takeIf { it.isNotBlank() }?.let { add("${s.representedBy}: $it") }
    } else {
      by.fullName.takeIf { it.isNotBlank() }?.let(::add)
    }
    by.phone.takeIf { it.isNotBlank() }?.let(::add)
    listOf(by.addressLine, by.placeLine).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
  }
  if (contact.isNotEmpty()) {
    add(Gap(PdfTokens.LABEL_GAP))
    val side = p.sessionRole?.let { " (${s.role(it)})" } ?: ""
    add(Text("${s.compiledBy}$side", Style.LabelSmall, keepWithNext = true))
    contact.forEach { add(Text(it, Style.Small)) }
  }
}

/** The immutable record's reference and fingerprint and, when applicable, the record it corrects. */
private fun Sheet.status(p: Protocol): List<Block> = buildList {
  add(Fields(listOf("Limen" to reference), 1))
  if (p.evidenceFingerprint.isNotBlank()) add(Text("${s.fingerprint}: ${p.evidenceFingerprint}", Style.Small))
  if (p.amendedFromId.isNotBlank()) {
    // By the reference the amended document actually prints on itself. The raw store id
    // ("p1758912345678") appears on no PDF anywhere, so it identified nothing to a reader.
    val amended = p.amendedFromFingerprint.takeIf { it.isNotBlank() }?.let { "LIMEN-${it.take(16)}" } ?: p.amendedFromId
    add(Text("${s.pdfAmends}: $amended", Style.Small))
  }
}
