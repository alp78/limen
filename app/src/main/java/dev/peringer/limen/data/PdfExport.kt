package dev.peringer.limen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import dev.peringer.limen.data.pdf.Mark
import dev.peringer.limen.data.pdf.Outline
import dev.peringer.limen.data.pdf.Sheet
import dev.peringer.limen.data.pdf.annex
import dev.peringer.limen.data.pdf.contract
import dev.peringer.limen.data.pdf.cover
import dev.peringer.limen.data.pdf.dataController
import dev.peringer.limen.data.pdf.delivery
import dev.peringer.limen.data.pdf.deposit
import dev.peringer.limen.data.pdf.extras
import dev.peringer.limen.data.pdf.keys
import dev.peringer.limen.data.pdf.legalOverlay
import dev.peringer.limen.data.pdf.meters
import dev.peringer.limen.data.pdf.objects
import dev.peringer.limen.data.pdf.release
import dev.peringer.limen.data.pdf.remarks
import dev.peringer.limen.data.pdf.rooms
import dev.peringer.limen.data.pdf.signatures
import dev.peringer.limen.data.pdf.strayFindings
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.previewDraft
import java.io.File

/**
 * The protocol as an A4 document, drawn with the platform's own PdfDocument in the app's
 * own style: the Day palette, the panels with their rounded corners and navy border, the
 * system sans-serif, every size derived from the screen's tokens. No library and no font
 * file to embed. The pieces live in [dev.peringer.limen.data.pdf]: the tokens, the page
 * engine, the blocks, and one file per section.
 *
 * The order follows the paper canon the record will be compared against at a conciliation
 * hearing: the parties, the money and meter rubrics this handover actually carries, the
 * rooms with their findings numbered across the record and the charge column explicit,
 * the supplied objects, the record signatures, the deposit release instruction, and the
 * photo annex. A cover opens it with the facts a reader wants first and the contents.
 *
 * The paper prints what was recorded and nothing else. Undamaged surfaces do not appear,
 * because a page of "in order" lines beside three real findings buries the three; and a
 * dossier rubric nobody filled in does not appear either, because an empty field on a
 * signed document reads as an unanswered question. Records written before this, which do
 * carry an exhaustive item grid, still print theirs: those grades are part of what the
 * parties put their names to.
 *
 * The language of the document is the [Str] handed in, independent of the UI language:
 * a Lausanne regie hands a German-speaking tenant a French protocol or the reverse, and
 * the choice belongs to the moment of export.
 */
object PdfExport {

  fun export(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
  ): ExportedFile {
    require(p.isFinalized()) { "Only finalized protocols can be exported" }
    check(ProtocolIntegrity.matches(p, photoDir, signatureDir)) { "Finalized evidence does not match its fingerprint" }
    dev.peringer.limen.photo.EvidenceValidation.requireReadable(p, photoDir, signatureDir)
    val reference = reference(p)
    val planned = Outline.plan(p, s)
    val logo = logo(context)
    try {
      // Twice: the first pass only counts the sheets so the second can number them "3 / 7".
      // A form that names its own total is a form nobody can quietly take a page out of,
      // which is the tamper evidence paper has always used and people actually check.
      // The counting pass also learns the page each section opens on, for the contents.
      val counted = build(context, p, s, photoDir, signatureDir, reference, planned, 0, Discard, null, null, emptyMap(), logo)
      val total = try { counted.doc.pages.size } finally { counted.doc.close() }
      check(counted.marks.map { Outline.Entry(it.title, it.level) } == planned) { "The sections drawn are not the sections planned" }
      val pages = counted.marks.map { it.page }
      val annexPages = counted.marks.filter { it.key != null }.associate { it.key!! to it.page }

      val kind = if (p.kind == Kind.Entry) s.entry else s.exit
      val property = (p.address + "-" + p.place).replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").take(65)
      val name = java.text.Normalizer.normalize("limen-$property-${kind.lowercase()}-${s.lang.name.lowercase()}-${reference}-d${p.deliveryRevision}-${exportStamp()}.pdf", java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^A-Za-z0-9._-]"), "-")
      val retained = File(context.filesDir, "exports/${p.id}").apply { mkdirs() }
      val original = File(retained, name)
      val companion = File(retained, name.removeSuffix(".pdf") + ".html")
      // The plates go through files, never memory: a record holds any number of photographs.
      val spool = File(context.cacheDir, "annex-${System.nanoTime()}").apply { mkdirs() }
      try {
        val drawn = companion.outputStream().buffered().use { html ->
          build(context, p, s, photoDir, signatureDir, reference, planned, total, html, spool, pages, annexPages, logo)
        }
        // The contents names pages; a drawing pass that broke them elsewhere would print a
        // wrong page under a signed document, so it is refused rather than handed over.
        check(drawn.marks == counted.marks) { "The drawing pass paginated differently from the counting pass" }
        val written = try { java.io.ByteArrayOutputStream().also { drawn.doc.writeTo(it) }.toByteArray() } finally { drawn.doc.close() }
        original.outputStream().buffered().use { PdfPlates.swap(written, drawn.plates, it) }
        val out = writeToDownloads(context, name, "application/pdf") { out -> original.inputStream().use { it.copyTo(out) } }
        val html = writeToDownloads(context, companion.name, "text/html") { out -> companion.inputStream().use { it.copyTo(out) } }
        return out.copy(companionUri = html.uri, sha256 = ProtocolIntegrity.fileHash(original))
      } catch (failure: Throwable) {
        original.delete()
        companion.delete()
        throw failure
      } finally {
        spool.deleteRecursively()
      }
    } finally {
      logo?.recycle()
    }
  }

  /** The record as the on-screen preview shows it; its plates are light JPEG copies, not the evidence. */
  fun preview(context: Context, p: Protocol, s: Str, photos: File, ink: File): String {
    dev.peringer.limen.photo.EvidenceValidation.requireReadable(p, photos, ink)
    val html = java.io.ByteArrayOutputStream()
    val logo = logo(context)
    try {
      val reference = if (p.isFinalized()) reference(p) else s.previewDraft
      build(context, p, s, photos, ink, reference, Outline.plan(p, s), 1, html, null, null, emptyMap(), logo).doc.close()
    } finally {
      logo?.recycle()
    }
    return html.toString(Charsets.UTF_8.name())
  }

  /** The counting pass draws an HTML copy nobody reads. */
  private object Discard : java.io.OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
  }

  private class Pass(val doc: PdfDocument, val marks: List<Mark>, val plates: List<PdfPlates.Plate>)

  /**
   * The seal, decoded once per pass and drawn on every page from the same bitmap, which
   * PdfDocument then embeds once. The same resource as the launcher and the header, so
   * the three can never drift apart. Decoded at half size: the 512-pixel mark prints 22 mm
   * wide on the cover, where 256 pixels are 300 dpi, and at full size it was 374 KB of a
   * 500 KB two-page record.
   */
  private fun logo(context: Context): Bitmap? = runCatching {
    BitmapFactory.decodeResource(context.resources, dev.peringer.limen.R.drawable.limen_mark, BitmapFactory.Options().apply { inSampleSize = 2 })
  }.getOrNull()

  /**
   * One rendering pass. [total] is 0 while counting, and the real page count afterwards.
   * [pages] and [annexPages] are what the counting pass learned, null and empty before it.
   * The HTML copy is written to [html] as the pages are drawn. With a [spool], each annex
   * plate is kept there as a JPEG; without one, the plates are light JPEG copies for the
   * screen.
   */
  private fun build(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
    reference: String,
    planned: List<Outline.Entry>,
    total: Int,
    html: java.io.OutputStream,
    spool: File?,
    pages: List<Int>?,
    annexPages: Map<String, Int>,
    logo: Bitmap?,
  ): Pass {
    val doc = PdfDocument()
    val kind = if (p.kind == Kind.Entry) s.entry else s.exit
    val address = listOf(p.address, p.place).filter { it.isNotBlank() }.joinToString(", ")
    val sheet = Sheet(doc, s, reference, "${s.protocolKind}: $kind", address, total, html, spool, logo, annexPages)
    val cards = p.dossierCards()
    sheet.cover(p, planned, pages)
    if (Outline.legalOverlay(p)) sheet.legalOverlay(p)
    if (Outline.controller(p)) sheet.dataController(p)
    if (DossierCard.Contract in cards) sheet.contract(p)
    if (DossierCard.Deposit in cards) sheet.deposit(p)
    if (Outline.meters(p)) sheet.meters(p)
    if (DossierCard.Keys in cards) sheet.keys(p)
    sheet.rooms(p)
    sheet.objects(p)
    sheet.strayFindings(p)
    sheet.extras(p)
    sheet.remarks(p)
    sheet.signatures(p, signatureDir)
    sheet.delivery(p)
    sheet.release(p)
    sheet.annex(p, photoDir, measuring = total == 0)
    sheet.close()
    sheet.html.finish()
    return Pass(doc, sheet.marks.toList(), sheet.plates.toList())
  }

  /** The filing reference is a readable prefix of the frozen evidence fingerprint. */
  private fun reference(p: Protocol): String = "LIMEN-${p.evidenceFingerprint.take(16)}"
}
