package dev.peringer.limen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.*
import dev.peringer.limen.photo.PhotoDecode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The protocol as an A4 document, drawn with the platform's own PdfDocument: no library,
 * no fonts to embed, black ink on white paper. The layout follows the paper canon it will
 * be compared against at a conciliation hearing: header, parties, the money and meter
 * rubrics this handover actually carries, the supplied objects, the findings numbered by
 * room with the explicit charge column, the record signatures, the deposit
 * release instruction, and the photo annex.
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

  private const val W = 1240
  private const val H = 1754
  private const val MARGIN = 90f
  private const val FOOT = 60f
  /**
   * An annex card: the frame's padding, which with the margins leaves [AnnexPlate.MAX_WIDTH]
   * for the photograph, the gap to a legend set beside it, the narrowest legend set beside
   * rather than below, and the space between cards.
   */
  private const val FRAME_PAD = 20f
  private const val LEGEND_GAP = 30f
  private const val LEGEND_MIN = 300f
  private const val CARD_GAP = 40f

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
    // Twice: the first pass only counts the sheets so the second can number them "3 / 7".
    // A form that names its own total is a form nobody can quietly take a page out of,
    // which is the tamper evidence paper has always used and people actually check.
    // The counting pass also learns where each annex section starts, for the annex contents.
    var sections = emptyList<Int>()
    val counted = build(context, p, s, photoDir, signatureDir, reference, 0, Discard, onSections = { sections = it })
    val total = try { counted.pages.size } finally { counted.close() }

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
      var plates = emptyList<PdfPlates.Plate>()
      val doc = companion.outputStream().buffered().use { html ->
        build(context, p, s, photoDir, signatureDir, reference, total, html, spool, sections) { plates = it }
      }
      val written = try { java.io.ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray() } finally { doc.close() }
      original.outputStream().buffered().use { PdfPlates.swap(written, plates, it) }
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
  }

  /** The record as the on-screen preview shows it; its plates are light JPEG copies, not the evidence. */
  fun preview(context: Context, p: Protocol, s: Str, photos: File, ink: File): String {
    dev.peringer.limen.photo.EvidenceValidation.requireReadable(p, photos, ink)
    val html = java.io.ByteArrayOutputStream()
    build(context, p, s, photos, ink, if (p.isFinalized()) reference(p) else s.previewDraft, 1, html).close()
    return html.toString(Charsets.UTF_8.name())
  }

  /** The counting pass draws an HTML copy nobody reads. */
  private object Discard : java.io.OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
  }

  /**
   * One rendering pass. [total] is 0 while counting, and the real page count afterwards. The
   * HTML copy is written to [html] as the pages are drawn. With a [spool], each annex plate
   * is kept there as a JPEG and handed to [onPlates]; without one, the plates are
   * light JPEG copies for the screen.
   */
  private fun build(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
    reference: String,
    total: Int,
    html: java.io.OutputStream,
    spool: File? = null,
    contents: List<Int> = emptyList(),
    onSections: ((List<Int>) -> Unit)? = null,
    onPlates: ((List<PdfPlates.Plate>) -> Unit)? = null,
  ): PdfDocument {
    val doc = PdfDocument()
    val sheet = Sheet(doc, s, reference, total, html, spool, contents)
    val cards = p.dossierCards()
    title(sheet, p, s, context)
    header(sheet, p, s)
    integrity(sheet, p, s)
    legalOverlay(sheet, p, s)
    parties(sheet, p, s)
    dataController(sheet, p, s)
    if (DossierCard.Contract in cards) contract(sheet, p, s)
    if (DossierCard.Deposit in cards) deposit(sheet, p, s)
    if (DossierCard.Meters in cards && !p.meters.isBlank()) meters(sheet, p, s)
    if (DossierCard.Keys in cards) keys(sheet, p, s)
    rooms(sheet, p, s)
    objects(sheet, p, s)
    findings(sheet, p, s)
    extraContent(p, s).forEach { (heading, lines) ->
      sheet.sectionTitle(heading)
      lines.forEach { sheet.wrapped(it, sheet.body) }
    }
    if (DossierCard.Notes in cards) remarks(sheet, p, s)
    signatures(sheet, p, s, signatureDir)
    delivery(sheet, p, s)
    release(sheet, p, s)
    annex(sheet, p, s, photoDir, measuring = total == 0)
    sheet.close()
    sheet.html.finish()
    onSections?.invoke(sheet.sectionStarts)
    onPlates?.invoke(sheet.plates)
    return doc
  }

  /** The filing reference is a readable prefix of the frozen evidence fingerprint. */
  private fun reference(p: Protocol): String = "LIMEN-${p.evidenceFingerprint.take(16)}"

  // ── The sheet: cursor, page breaks, footer ───────────────────────────────────────────

  private class Sheet(
    val doc: PdfDocument,
    val s: Str,
    val reference: String,
    val total: Int,
    html: java.io.OutputStream,
    val spool: File?,
    /** The page each annex section starts on, known from the counting pass, for its contents. */
    val contents: List<Int>,
  ) {
    val html = HtmlCompanion(s.lang.name.lowercase(), "Limen · $reference", html)
    /** The annex photographs as JPEG files, in the order their stand-ins were drawn. */
    val plates = mutableListOf<PdfPlates.Plate>()
    val sectionStarts = mutableListOf<Int>()
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    val titlePaint = Paint(ink).apply {
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 40f
    }
    val section = Paint(ink).apply {
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 26f
    }
    val label = Paint(ink).apply {
      typeface = Typeface.SANS_SERIF; textSize = 20f; color = Color.rgb(90, 90, 90)
    }
    val value = Paint(ink).apply { typeface = Typeface.MONOSPACE; textSize = 22f }
    val body = Paint(ink).apply { typeface = Typeface.SANS_SERIF; textSize = 24f }
    val small = Paint(ink).apply { typeface = Typeface.SANS_SERIF; textSize = 21f; color = Color.rgb(70, 70, 70) }
    val rule = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1.5f }
    // The photo annex: a room's title over a heavy rule, then framed cards with a bold legend.
    val roomTitle = Paint(ink).apply {
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 36f; letterSpacing = 0.04f
    }
    val heavyRule = Paint().apply { color = Color.rgb(21, 32, 45); strokeWidth = 4f }
    val strong = Paint(ink).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 27f }
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(185, 185, 185) }
    val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.rgb(215, 215, 215) }
    val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 32, 45) }
    val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 23f; color = Color.WHITE
    }

    var pageNo = 0
    var page: PdfDocument.Page? = null
    var canvas: Canvas = Canvas()
    var y = 0f

    fun newPage() {
      finish()
      pageNo++
      page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
      canvas = page!!.canvas
      canvas.drawColor(Color.WHITE)
      canvas.scale(595f / W, 842f / H)
      y = MARGIN
    }

    fun finish() {
      val pg = page ?: return
      // The footer: the instrument's name and the page, quietly.
      canvas.drawLine(MARGIN, H - FOOT - 14f, W - MARGIN, H - FOOT - 14f, rule)
      canvas.drawText("Limen · $reference", MARGIN, H - FOOT + 8f, small)
      val pageText = if (total > 0) "${s.pdfPage} $pageNo / $total" else "${s.pdfPage} $pageNo"
      canvas.drawText(pageText, W - MARGIN - small.measureText(pageText), H - FOOT + 8f, small)
      doc.finishPage(pg)
      page = null
    }

    fun close() = finish()

    /** Guarantees [need] vertical pixels, breaking the page when they are not there. */
    fun ensure(need: Float) {
      if (page == null || y + need > H - FOOT - 30f) newPage()
    }

    fun gap(v: Float) {
      y += v
    }

    fun sectionTitle(text: String) {
      html.heading(text)
      ensure(60f)
      gap(16f)
      canvas.drawText(text.uppercase(), MARGIN, y + section.textSize, section)
      y += section.textSize + 8f
      canvas.drawLine(MARGIN, y, W - MARGIN, y, rule)
      gap(10f)
    }

    fun labeledRow(pairs: List<Pair<String, String>>) {
      val width = (W - 2 * MARGIN) / pairs.size
      val columns = pairs.map { (heading, entry) ->
        wrapLines(heading, label, width - 18f) to
          wrapLines(entry.ifBlank { "–" }, value, width - 18f)
      }
      val labelLines = columns.maxOf { it.first.size }
      val valueLines = columns.maxOf { it.second.size }
      val labelStep = label.textSize + 5f
      val valueStep = value.textSize + 6f
      val need = labelLines * labelStep + valueLines * valueStep + 14f
      if (need > H - FOOT - MARGIN - 30f) {
        // An unusually long legal name/address must paginate rather than run off paper.
        pairs.forEach { (heading, entry) ->
          wrapped(heading, label)
          wrapped(entry.ifBlank { "–" }, value)
        }
        return
      }
      html.fields(pairs)
      ensure(need)
      columns.forEachIndexed { index, (headings, values) ->
        val x = MARGIN + index * width
        headings.forEachIndexed { line, text ->
          canvas.drawText(text, x, y + label.textSize + line * labelStep, label)
        }
        val valueTop = y + labelLines * labelStep
        values.forEachIndexed { line, text ->
          canvas.drawText(text, x, valueTop + value.textSize + line * valueStep, value)
        }
      }
      y += need
    }

    fun wrapped(text: String, paint: Paint, x: Float = MARGIN, width: Float = W - 2 * MARGIN, companion: Boolean = true) {
      if (companion) html.text(text)
      wrapLines(text, paint, width).forEach { line ->
        ensure(paint.textSize + 10f)
        canvas.drawText(line, x, y + paint.textSize, paint)
        y += paint.textSize + 7f
      }
    }

    fun ellipsize(text: String, paint: Paint, width: Float): String {
      if (paint.measureText(text) <= width) return text
      var t = text
      while (t.isNotEmpty() && paint.measureText("$t…") > width) t = t.dropLast(1)
      return "$t…"
    }

    fun wrapLines(text: String, paint: Paint, width: Float): List<String> {
      val out = mutableListOf<String>()
      text.split('\n').forEach { paragraph ->
        var line = ""
        paragraph.split(' ').forEach { word ->
          if (paint.measureText(word) > width) {
            if (line.isNotEmpty()) {
              out.add(line)
              line = ""
            }
            val chunks = mutableListOf<String>()
            var chunk = ""
            word.forEach { character ->
              val probe = chunk + character
              if (chunk.isNotEmpty() && paint.measureText(probe) > width) {
                chunks.add(chunk)
                chunk = character.toString()
              } else {
                chunk = probe
              }
            }
            if (chunk.isNotEmpty()) chunks.add(chunk)
            out.addAll(chunks.dropLast(1))
            line = chunks.lastOrNull().orEmpty()
            return@forEach
          }
          val probe = if (line.isEmpty()) word else "$line $word"
          if (paint.measureText(probe) <= width) line = probe
          else {
            if (line.isNotEmpty()) out.add(line)
            line = word
          }
        }
        out.add(line)
      }
      return out
    }
  }

  // ── Sections ─────────────────────────────────────────────────────────────────────────

  private fun title(sheet: Sheet, p: Protocol, s: Str, context: Context) {
    sheet.newPage()
    // The badge, top right of the first page. A protocol that ends in a conciliation hearing
    // should look like it came from somewhere: the same seal as the launcher and the header,
    // drawn from the same resource so the three can never drift apart.
    val badge = 64f
    runCatching {
      BitmapFactory.decodeResource(context.resources, dev.peringer.limen.R.drawable.limen_mark)
    }.getOrNull()?.let { bmp ->
      val paint = Paint(Paint.FILTER_BITMAP_FLAG)
      sheet.canvas.drawBitmap(
        bmp,
        null,
        android.graphics.RectF(W - MARGIN - badge, sheet.y, W - MARGIN, sheet.y + badge),
        paint,
      )
      bmp.recycle()
    }
    val kind = if (p.kind == Kind.Entry) s.entry else s.exit
    sheet.canvas.drawText(
      "${s.protocolKind.uppercase()} · ${kind.uppercase()}",
      MARGIN,
      sheet.y + sheet.titlePaint.textSize,
      sheet.titlePaint,
    )
    // The rule clears the badge as well as the text, whichever is taller.
    sheet.y = maxOf(sheet.y + sheet.titlePaint.textSize + 12f, sheet.y + badge + 10f)
    sheet.canvas.drawLine(MARGIN, sheet.y, W - MARGIN, sheet.y, sheet.ink.apply { strokeWidth = 3f })
    sheet.gap(8f)
  }

  private fun header(sheet: Sheet, p: Protocol, s: Str) {
    sheet.labeledRow(listOf(s.address to p.address, s.place to p.place))
    if (p.objectCanton != null) {
      sheet.labeledRow(
        listOf(
          s.cantonLabel to s.canton(p.objectCanton),
          s.legalBasis to s.legalProfile(p.legalProfile()),
        )
      )
    }
    sheet.labeledRow(
      listOf(
        s.objectDetail to p.objectDetail,
        s.handoverDate to listOf(SwissDate.display(p.handoverDate), p.handoverTime)
          .filter { it.isNotBlank() }.joinToString(" "),
      )
    )
    if (p.lastProtocolDate.isNotBlank()) {
      sheet.labeledRow(
        listOf(s.lastProtocol to SwissDate.display(p.lastProtocolDate))
      )
    }
  }

  /** Identifies the immutable record and, when applicable, the record it corrects. */
  private fun integrity(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(if (p.isFinalized()) s.pdfFinalized else s.previewDraft)
    sheet.wrapped("${s.fingerprint}: ${p.evidenceFingerprint}", sheet.small)
    if (p.amendedFromId.isNotBlank()) {
      // By the reference the amended document actually prints on itself. The raw store id
      // ("p1758912345678") appears on no PDF anywhere, so it identified nothing to a reader.
      // By the reference the amended document actually prints on itself. The raw store id
      // ("p1758912345678") appears on no PDF anywhere, so it identified nothing to a reader.
      val amended = p.amendedFromFingerprint
        .takeIf { it.isNotBlank() }
        ?.let { "LIMEN-${it.take(16)}" }
        ?: p.amendedFromId
      sheet.wrapped("${s.pdfAmends}: $amended", sheet.small)
    }
  }

  /** The dated canton checks actually recorded, never inferred into an older frozen PDF. */
  private fun legalOverlay(sheet: Sheet, p: Protocol, s: Str) {
    if (p.legalOverlay == LegalOverlayRecord()) return
    val overlay = p.resolvedLegalOverlay() ?: return
    sheet.sectionTitle(s.legalOverlayTitle)
    sheet.wrapped(
      "${s.canton(overlay.canton)} · ${s.legalProfile(overlay.profile)}",
      sheet.body,
    )
    if (p.kind == Kind.Entry) {
      sheet.wrapped(
        "${s.initialRentForm}: ${s.initialRentFormRule(overlay.initialRentFormRule)}",
        sheet.small,
      )
    }
    // Only the requirements this record still resolves. Printed from the raw answer map, a
    // deposit set back to None left "Deposit proof: Confirmed" on a record stating there is
    // no deposit, which neither the panel nor the completeness gate would ever have shown.
    overlay.requirements.sortedBy { it.ordinal }.forEach { requirement ->
      val status = p.legalOverlay.status(requirement) ?: return@forEach
      sheet.wrapped(
        "${s.legalRequirement(requirement)}: ${s.legalCheckStatus(status)}",
        sheet.body,
      )
      p.legalOverlay.notes[requirement]?.takeIf(String::isNotBlank)?.let { sheet.wrapped(it, sheet.small) }
    }
    if (p.legalOverlay.note.isNotBlank()) {
      sheet.wrapped("${s.legalOverlayNote}: ${p.legalOverlay.note}", sheet.small)
    }
    val ruleSet = p.legalOverlay.ruleSetId.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    sheet.wrapped("${s.legalSnapshotFor(overlay.canton)}$ruleSet", sheet.small)
  }

  private fun parties(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.parties)
    p.parties.forEach { party ->
      val detail = buildList {
        if (party.representedBy.isNotBlank()) add("${s.representedBy}: ${party.representedBy}")
        if (party.agency.isNotBlank()) add("${s.actorAgency}: ${party.agency}")
        if (party.authority.isNotBlank()) add("${s.authority}: ${party.authority}")
        if (party.phone.isNotBlank()) add(party.phone)
        if (party.email.isNotBlank()) add(party.email)
        if (party.address.isNotBlank()) add("${s.newAddress}: ${party.address}")
      }.joinToString(" · ")
      sheet.labeledRow(listOf(s.role(party.role) to party.name))
      if (detail.isNotBlank()) {
        sheet.wrapped(detail, sheet.small, MARGIN + 30f, W - 2 * MARGIN - 30f)
      }
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
      listOf(by.addressLine, by.placeLine).filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    if (contact.isNotEmpty()) {
      sheet.gap(4f)
      val side = p.sessionRole?.let { " (${s.role(it)})" } ?: ""
      sheet.wrapped("${s.compiledBy}$side: ${contact.joinToString(" · ")}", sheet.small)
    }
  }

  private fun dataController(sheet: Sheet, p: Protocol, s: Str) {
    val controller = p.controllerIdentity
    if (controller.isBlank()) return
    sheet.sectionTitle(s.controllerTitle)
    if (controller.organisation.isNotBlank()) sheet.wrapped(controller.organisation, sheet.value)
    val contact = buildList {
      controller.contactName.takeIf { it.isNotBlank() }?.let(::add)
      controller.email.takeIf { it.isNotBlank() }?.let(::add)
      controller.phone.takeIf { it.isNotBlank() }?.let(::add)
      listOf(
        controller.street,
        listOf(controller.postalCode, controller.city).filter { it.isNotBlank() }.joinToString(" "),
      ).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    if (contact.isNotEmpty()) sheet.wrapped(contact.joinToString(" · "), sheet.body)
  }

  private fun deposit(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.deposit)
    if (p.deposit.kind == DepositKind.None) { sheet.wrapped(s.depositKind(DepositKind.None), sheet.body); return }
    when (p.deposit.kind) {
      DepositKind.Insurance -> sheet.labeledRow(
        listOf(
          s.depositProvider to p.deposit.provider,
          s.depositPolicy to p.deposit.policyNo,
          s.depositAmount to p.deposit.amountChf,
        )
      )
      DepositKind.BankAccount -> sheet.labeledRow(
        listOf(
          s.depositBankName to p.deposit.bank,
          s.depositAccount to p.deposit.account,
          s.depositAmount to p.deposit.amountChf,
        )
      )
      DepositKind.None -> Unit
      DepositKind.Unknown -> sheet.wrapped(s.toVerify, sheet.body)
    }
  }

  private fun contract(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.contract)
    sheet.labeledRow(
      listOf(
        s.monthlyRent to p.contract.monthlyRentChf,
        s.serviceCharges to p.contract.serviceChargesChf,
        s.noticeDate to SwissDate.display(p.contract.noticeDate),
      )
    )
    sheet.labeledRow(
      listOf(
        s.leaseConcludedOn to SwissDate.display(p.contract.conclusionDate),
        s.tenancyStart to SwissDate.display(p.contract.startDate),
        s.tenancyEnd to SwissDate.display(p.contract.endDate),
      )
    )
  }

  private fun meters(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.meters)
    sheet.labeledRow(
      listOf(s.meterElec1 to p.meters.electricity1, s.meterElec2 to p.meters.electricity2, s.meterGas to p.meters.gas)
    )
    sheet.labeledRow(
      listOf(s.meterWater to p.meters.water, s.meterHotWater to p.meters.hotWater, s.meterOil to p.meters.heatingOil)
    )
  }

  private fun keys(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.keysLabel)
    sheet.wrapped("${s.keyTransferLabel}: ${s.keyTransfer(p.keyTransfer)}", sheet.body)
    if (p.keyTransferNote.isNotBlank()) sheet.wrapped(p.keyTransferNote, sheet.small)
    if (p.keyTransfer == KeyTransferStatus.NoPhysicalKeys) return
    val keyText = p.keys.filter { it.count > 0 }.joinToString("   ") { k ->
      val label = if (k.kind == KeyKind.Other && k.label.isNotBlank()) k.label else s.keyKind(k.kind)
      "$label: ${k.count}"
    }
    sheet.wrapped(keyText.ifBlank { "–" }, sheet.value)
    // Older records counted a room's own door keys inside the room. Those still print,
    // once, here rather than twice as they used to.
    val roomKeys = p.rooms.filter { it.keysCount > 0 }
    if (roomKeys.isNotEmpty()) {
      sheet.wrapped(
        roomKeys.joinToString("   ") { r -> "${r.name.ifBlank { s.room(r.kind) }}: ${r.keysCount}" },
        sheet.value,
      )
    }
    if (p.selfMadeKeys) sheet.wrapped(s.selfMadeKeys, sheet.body)
  }

  /** Everything supplied with the flat, one line each, grouped the way it was entered. */
  private fun objects(sheet: Sheet, p: Protocol, s: Str) {
    // Both, when a record carries both. Returning after the legacy block only while objects
    // was empty meant adding one row to a seeded exit dropped the whole inherited inventory.
    legacyExtras(sheet, p, s)
    if (p.objects.isEmpty()) return
    sheet.sectionTitle(s.objects)
    p.objects
      .groupBy { it.group to it.groupLabel }
      .forEach { (heading, rows) ->
        val (group, groupLabel) = heading
        sheet.ensure(50f)
        sheet.wrapped(groupLabel.ifBlank { s.objectGroup(group) }.uppercase(), sheet.label)
        rows.forEach { entry ->
          val detail = buildList {
            add(if (entry.inspected) s.objectInspected else s.notInspected)
            p.rooms.firstOrNull { it.id == entry.roomId }
              ?.let { add(it.name.ifBlank { s.room(it.kind) }) }
            if (entry.note.isNotBlank()) add(entry.note)
            if (entry.photos.isNotEmpty()) add("${s.photos}: ${entry.photos.size}")
          }.joinToString(" · ")
          sheet.wrapped(
            entry.displayName(s) + if (detail.isNotBlank()) "   $detail" else "",
            sheet.body,
            MARGIN + 30f,
            W - 2 * MARGIN - 30f,
          )
        }
        sheet.gap(6f)
      }
  }

  /** The set-of-toggles inventory older records carry, printed the way they were signed. */
  private fun legacyExtras(sheet: Sheet, p: Protocol, s: Str) {
    val appliances = p.extras.appliances.sortedBy { it.ordinal }.map(s::appliance) + p.extras.customAppliances
    val furniture = p.extras.furniture.sortedBy { it.ordinal }.map(s::furniture) + p.extras.customFurniture
    if (appliances.isEmpty() && furniture.isEmpty()) return
    sheet.sectionTitle(s.objects)
    if (appliances.isNotEmpty()) sheet.wrapped("${s.appliances}: ${appliances.joinToString(" · ")}", sheet.body)
    if (furniture.isNotEmpty()) sheet.wrapped("${s.furniture}: ${furniture.joinToString(" · ")}", sheet.body)
  }

  /** The rooms the record covers, named once, so the reader knows the shape of the flat. */
  private fun rooms(sheet: Sheet, p: Protocol, s: Str) {
    if (p.rooms.isEmpty()) return
    sheet.sectionTitle(s.stepInspection)
    p.rooms.forEach { room ->
      val status = if (room.inspected) s.roomInspected else s.notInspected
      sheet.wrapped("${room.name.ifBlank { s.room(room.kind) }} · $status", sheet.body)
    }
    p.rooms.filter { it.hasLegacyGrid() }.forEach { room ->
      sheet.ensure(60f)
      sheet.wrapped(room.name.ifBlank { s.room(room.kind) }.uppercase(), sheet.label)
      legacyGrid(sheet, p, s, room)
    }
  }

  /**
   * The findings, by room and then by object.
   *
   * Each is numbered by its position in the whole record rather than within its room, so
   * the number under a photograph in the annex names exactly one finding on exactly one
   * page.
   *
   * A room explicitly confirmed as inspected prints even when it has no finding. Legacy
   * seeded rooms remain labelled not inspected, so the PDF never turns their silence into
   * a claim that somebody checked them.
   */
  private fun findings(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle("${s.defects} (${p.defects.size})")
    if (p.defects.isEmpty()) sheet.wrapped("–", sheet.body)
    p.rooms.forEach { room ->
      val found = Report.roomFindings(p, room.id)
      if (found.isEmpty() && !room.inspected) return@forEach
      sheet.ensure(60f)
      sheet.wrapped(room.name.ifBlank { s.room(room.kind) }.uppercase(), sheet.label)
      if (found.isEmpty()) sheet.wrapped(s.inspectedNoDefects, sheet.body)
      else found.forEach { finding(sheet, p, s, it.defect, it.number) }
      sheet.gap(6f)
    }

    val onObjects = Report.objectFindings(p)
    if (onObjects.isNotEmpty()) {
      sheet.ensure(60f)
      sheet.wrapped(s.objects.uppercase(), sheet.label)
      onObjects.forEach { finding(sheet, p, s, it.defect, it.number) }
    }

    // A finding whose room is gone would otherwise vanish from the paper while still
    // counting in the heading and carrying a caption in the annex.
    val orphaned = Report.orphanedFindings(p)
    if (orphaned.isNotEmpty()) {
      sheet.ensure(60f)
      sheet.wrapped(s.defects.uppercase(), sheet.label)
      orphaned.forEach { finding(sheet, p, s, it.defect, it.number) }
    }
  }

  /** One numbered finding: what it is about, what it says, and everything hanging on it. */
  private fun finding(sheet: Sheet, p: Protocol, s: Str, d: Defect, number: Int) {
    sheet.html.text("${s.defects} $number · ${p.defectPlace(d, s)} · ${p.defectItemLabel(d, s).orEmpty()}")
    sheet.ensure(80f)
    sheet.canvas.drawText("$number.", MARGIN, sheet.y + sheet.value.textSize, sheet.value)
    val where = buildList {
      if (d.objectId.isNotBlank()) {
        p.objects.firstOrNull { it.id == d.objectId }?.let { add(it.displayName(s)) }
      }
      defectItemLabel(p, d, s)?.let(::add)
    }.joinToString(" · ")
    if (where.isNotBlank()) {
      sheet.canvas.drawText(
        sheet.ellipsize(where, sheet.small, W - 2 * MARGIN - 60f),
        MARGIN + 60f,
        sheet.y + sheet.small.textSize + 2f,
        sheet.small,
      )
      sheet.y += sheet.small.textSize + 8f
    }
    sheet.wrapped(d.text.ifBlank { "–" }, sheet.body, MARGIN + 60f, W - 2 * MARGIN - 60f)
    val meta = buildList {
      if (d.chargedToTenant) add(s.chargedToTenant.uppercase())
      when (d.fixBy) {
        FixBy.Tenant -> add("${s.fixByLabel}: ${s.role(Role.Tenant)}")
        FixBy.Landlord -> add("${s.fixByLabel}: ${s.role(Role.Landlord)}")
        FixBy.Open -> Unit
      }
      if (d.fixDeadline.isNotBlank()) add("${s.fixDeadline} ${SwissDate.display(d.fixDeadline)}")
      if (d.costNote.isNotBlank()) add("${s.costNote}: ${d.costNote}")
      if (d.photos.isNotEmpty()) add("${s.photos}: ${d.photos.size}")
      if (d.tenantResponse != TenantResponse.NotStated) {
        add("${s.tenantResponse}: ${s.tenantResponse(d.tenantResponse)}")
      }
    }
    if (meta.isNotEmpty()) {
      sheet.wrapped(meta.joinToString(" · "), sheet.small, MARGIN + 60f, W - 2 * MARGIN - 60f)
    }
    if (d.reservation.isNotBlank()) {
      sheet.wrapped("${s.reservationLabel}: ${d.reservation}", sheet.small, MARGIN + 60f, W - 2 * MARGIN - 60f)
    }
    if (d.tenantComment.isNotBlank()) {
      sheet.wrapped("${s.tenantComment}: ${d.tenantComment}", sheet.small, MARGIN + 60f, W - 2 * MARGIN - 60f)
    }
    d.participantPositions.forEach { (id, position) ->
      val name = p.parties.firstOrNull { it.participantKey() == id }?.name ?: id
      sheet.wrapped("$name · ${s.tenantResponse(position.response)}\n${position.comment}", sheet.body)
    }
    sheet.gap(8f)
  }

  /**
   * The exhaustive item grid, printed only for a record that was signed with one.
   *
   * Two columns of item lines: the form look, at half the paper. The state is ellipsized
   * into its own gutter, because "In Ordnung" in the right-hand column used to be drawn
   * straight through the margin.
   */
  private fun legacyGrid(sheet: Sheet, p: Protocol, s: Str, room: Room) {
    val colWidth = (W - 2 * MARGIN - 40f) / 2f
    val stateWidth = 140f
    val lines = room.items.map { item ->
      val name =
        if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key)
      val defectNos = p.defects.withIndex()
        .filter { (_, d) ->
          d.roomId == room.id && d.item == item.key &&
            (d.itemId.isBlank() || item.itemId.isBlank() || d.itemId == item.itemId)
        }
        .map { (i, _) -> i + 1 }
      val state = when {
        defectNos.isNotEmpty() -> "! " + defectNos.joinToString(",")
        item.grade == Grade.NotChecked -> ""
        p.style == GradeStyle.Binary -> s.grade(Grade.Ok)
        else -> s.gradeShort(item.grade)
      }
      name to state
    }
    val rowsPerCol = (lines.size + 1) / 2
    sheet.html.fields(lines)
    var i = 0
    while (i < rowsPerCol) {
      sheet.ensure(30f)
      listOf(0, 1).forEach { col ->
        val idx = i + col * rowsPerCol
        if (idx < lines.size) {
          val (name, state) = lines[idx]
          val x = MARGIN + col * (colWidth + 40f)
          sheet.canvas.drawText(
            sheet.ellipsize(name, sheet.body, colWidth - stateWidth - 20f),
            x,
            sheet.y + sheet.body.textSize,
            sheet.body,
          )
          sheet.canvas.drawText(
            sheet.ellipsize(state, sheet.value, stateWidth),
            x + colWidth - stateWidth,
            sheet.y + sheet.body.textSize,
            sheet.value,
          )
        }
      }
      sheet.y += sheet.body.textSize + 8f
      i++
    }
  }

  /**
   * The item a finding names, or null when it is about the room or the object as a whole.
   * A finding written by this version carries its own label; older ones kept it on the
   * room's item row, so those are still resolved through the row.
   */
  private fun defectItemLabel(p: Protocol, defect: Defect, s: Str): String? {
    val key = defect.item ?: return null
    if (key != ItemKey.Custom) return s.item(key)
    if (defect.itemLabel.isNotBlank()) return defect.itemLabel
    val item = p.rooms.firstOrNull { it.id == defect.roomId }?.items?.firstOrNull {
      it.key == key &&
        (defect.itemId.isBlank() || it.itemId.isBlank() || defect.itemId == it.itemId)
    }
    return item?.label?.takeIf { it.isNotBlank() } ?: s.item(key)
  }

  /** What an object row is called: its catalogue entry when it has one, else what was typed. */
  private fun ObjectEntry.displayName(s: Str): String = when (group) {
    ObjectGroup.Appliance ->
      Appliance.entries.firstOrNull { it.name == catalogue }?.let(s::appliance) ?: label
    ObjectGroup.Furniture ->
      Furniture.entries.firstOrNull { it.name == catalogue }?.let(s::furniture) ?: label
    ObjectGroup.Other -> label
  }

  /** The Notes component: free remarks and the service-charge date, when it is on the record. */
  private fun remarks(sheet: Sheet, p: Protocol, s: Str) {
    if (p.remarks.isBlank() && p.chargesStatementBy.isBlank()) return
    // Hoisted above both branches: opened only inside the remarks branch, a service-charge
    // date with no remarks printed under the DEFECTS heading, reading as the last finding.
    sheet.sectionTitle(s.remarks)
    if (p.remarks.isNotBlank()) sheet.wrapped(p.remarks, sheet.body)
    if (p.chargesStatementBy.isNotBlank()) {
      if (p.remarks.isNotBlank()) sheet.gap(8f)
      sheet.wrapped("${s.chargesBy} ${SwissDate.display(p.chargesStatementBy)}", sheet.body)
    }
  }

  private fun signatures(sheet: Sheet, p: Protocol, s: Str, signatureDir: File) {
    // The record signature is always printed, with its meaning above the ink.
    sheet.sectionTitle(s.signRecordTitle)
    // A handover one party did not attend is a statement about these signatures, so it is
    // printed here and unconditionally. It used to live under the remarks section, which
    // meant it vanished from the paper whenever that optional component was not added.
    if (p.oneSided) {
      sheet.wrapped(s.oneSided.uppercase(), sheet.section)
      p.unilateralReason?.let { reason ->
        sheet.wrapped("${s.unilateralReasonLabel}: ${s.unilateralReason(reason)}", sheet.body)
      }
      if (p.unilateralNote.isNotBlank()) sheet.wrapped(p.unilateralNote, sheet.small)
      sheet.wrapped(s.infoOneSided, sheet.small)
      sheet.gap(8f)
    }
    sheet.wrapped(s.signRecordMeaning, sheet.small)
    sheet.gap(10f)
    drawPlane(sheet, p, s, signatureDir, Plane.Record)
    if (p.signatures.any { it.plane == Plane.Costs }) {
      sheet.sectionTitle(s.words("Historical cost signature", "Signature historique relative aux frais", "Historische Kostenunterschrift", "Firma storica relativa ai costi", "Suttascripziun istorica davart ils custs"))
      drawPlane(sheet, p, s, signatureDir, Plane.Costs)
    }
    sheet.wrapped(s.inkEvidenceNotice, sheet.small)

    // The Swiss ritual, said on the document itself: two identical papers, one per party.
    // In Vaud the RULV require exactly that, and a tenant who reads it here knows to leave
    // the flat holding one.
    sheet.gap(6f)
    sheet.wrapped(s.pdfTwoCopies, sheet.small)
  }

  private fun drawPlane(sheet: Sheet, p: Protocol, s: Str, dir: File, plane: Plane) {
    val sigs = p.signatures.filter { it.plane == plane && p.isCurrent(it) }
    if (sigs.isEmpty()) {
      sheet.wrapped(s.notSigned, sheet.body)
      return
    }
    sigs.forEach { sig ->
      sheet.ensure(190f)
      val x = MARGIN
      if (sig.refused || sig.outcome.isNotBlank()) {
        sheet.wrapped(when (sig.outcome) { "absent" -> s.absentOutcome; "unable" -> s.unableToDraw; else -> s.refusedNote }, sheet.body)
      } else {
        val file = File(dir, sig.file)
        val bmp = if (sig.file.isNotBlank() && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        require(sig.file.isBlank() || bmp != null) { "Signature cannot be rendered: ${sig.file}" }
        if (bmp != null) {
          sheet.html.image(bmp, "${s.signRecordTitle}: ${sig.name}")
          val maxW = 420f
          val maxH = 130f
          val scale = minOf(maxW / bmp.width, maxH / bmp.height, 1f)
          val w = bmp.width * scale
          val h = bmp.height * scale
          val paint = Paint(Paint.FILTER_BITMAP_FLAG)
          sheet.canvas.drawBitmap(bmp, null, android.graphics.RectF(x, sheet.y, x + w, sheet.y + h), paint)
          sheet.y += h + 4f
          bmp.recycle()
        }
      }
      sheet.canvas.drawLine(x, sheet.y + 4f, x + 420f, sheet.y + 4f, sheet.rule)
      sheet.y += 10f
      val caption = buildList {
        add(s.role(sig.role))
        if (sig.name.isNotBlank()) add(sig.name)
        if (sig.reservation.isNotBlank()) add("${s.reservationLabel}: ${sig.reservation}")
        if (sig.epochMs > 0L) add("${s.capturedOn}: ${formatMoment(sig.epochMs)}")
        add("${s.revisionLabel}: ${sig.contentRevision}")
      }.joinToString(" · ")
      sheet.wrapped(caption, sheet.small)
      sheet.gap(14f)
    }
  }

  private fun formatMoment(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
      .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

  private fun delivery(sheet: Sheet, p: Protocol, s: Str) {
    if (p.deliveryReceipts.isEmpty()) return
    sheet.sectionTitle(s.copyDelivery)
    sheet.wrapped(s.deliverySelfDeclared, sheet.small)
    p.deliveryReceipts.sortedBy { it.role.ordinal }.forEach { receipt ->
      val detail = buildList {
        add(s.role(receipt.role))
        p.parties.firstOrNull { it.participantKey() == receipt.participantId || receipt.participantId.isBlank() && it.id.isBlank() && it.role == receipt.role }?.let { add(it.name) }
        add(s.deliveryMethod(receipt.method))
        if (receipt.epochMs > 0L) add(formatMoment(receipt.epochMs))
        if (receipt.note.isNotBlank()) add(receipt.note)
      }.joinToString(" · ")
      sheet.wrapped(detail, sheet.body)
    }
  }

  private fun release(sheet: Sheet, p: Protocol, s: Str) {
    if (p.kind != Kind.Exit || p.deposit.kind == DepositKind.None) return
    val releaseText = when (p.deposit.kind) {
      DepositKind.BankAccount -> s.releaseBank
      DepositKind.Insurance -> s.releaseInsurance
      DepositKind.None -> ""
              DepositKind.Unknown -> s.toVerify
    }
    var need = 70f + sheet.wrapLines(releaseText, sheet.body, W - 2 * MARGIN)
      .size * (sheet.body.textSize + 7f)
    if (p.deposit.kind == DepositKind.Insurance && p.deposit.provider.isNotBlank()) {
      val provider = "${s.depositProvider}: ${p.deposit.provider}" +
        (if (p.deposit.policyNo.isNotBlank()) " · ${s.depositPolicy}: ${p.deposit.policyNo}" else "")
      need += 12f + sheet.wrapLines(provider, sheet.value, W - 2 * MARGIN)
        .size * (sheet.value.textSize + 7f)
    }
    sheet.ensure(need)
    sheet.sectionTitle(s.releaseTitle)
    when (p.deposit.kind) {
      DepositKind.BankAccount -> sheet.wrapped(releaseText, sheet.body)
      DepositKind.Insurance -> {
        sheet.wrapped(releaseText, sheet.body)
        if (p.deposit.provider.isNotBlank()) {
          sheet.gap(6f)
          sheet.wrapped(
            "${s.depositProvider}: ${p.deposit.provider}" +
              (if (p.deposit.policyNo.isNotBlank()) " · ${s.depositPolicy}: ${p.deposit.policyNo}" else ""),
            sheet.value,
          )
        }
      }
      DepositKind.None -> Unit
      DepositKind.Unknown -> sheet.wrapped(s.toVerify, sheet.body)
    }
  }


  /**
   * The photo annex, organised the way a handover is walked: a contents page, then one
   * section per room, each opening on a new page under the room's title, then the supplied
   * objects, the meters and anything left. Every photograph sits in a frame with its legend,
   * which repeats the room, so a page met on its own still says where it belongs and what it
   * shows: the finding's number and words as the body prints them, the surface, who is held
   * to it, and when the photograph was taken. No hash: it tells the people at a handover
   * nothing.
   *
   * The photographs are large, up to the full width or 1100 units high, 17 cm wide or 19 cm high,
   * at 200 dpi as JPEG, which keeps thirty of them to a PDF of 7.7 MB. Each is written once
   * as a JPEG, which the HTML copy embeds as it is and [PdfPlates] puts into the PDF in place
   * of a marked stand-in, so that PdfDocument never holds the bitmap and a record of any size
   * exports.
   *
   * The counting pass decodes nothing, but it reads every photograph's size from its header
   * and lays out every legend, so both passes break the pages in the same places.
   */
  private fun annex(sheet: Sheet, p: Protocol, s: Str, photoDir: File, measuring: Boolean) {
    val plates = Report.plates(p)
    if (plates.isEmpty()) return
    require(plates.all { readable(File(photoDir, it.file)) }) { "A referenced photograph cannot be rendered" }
    val sections = AnnexContent.sections(p, s)
    sheet.newPage()
    sheet.sectionTitle(s.pdfDefectAnnex)
    if (sections.size > 1) {
      // The contents: each section with its photographs and, once counted, its first page.
      sections.forEachIndexed { i, section ->
        val count = "${s.photos}: ${section.cards.size}"
        val page = sheet.contents.getOrNull(i)?.let { "${s.pdfPage} $it" }.orEmpty()
        sheet.html.text(listOf(section.title, count, page).filter(String::isNotBlank).joinToString(" · "))
        sheet.ensure(sheet.body.textSize + 18f)
        val baseline = sheet.y + sheet.body.textSize
        sheet.canvas.drawText(sheet.ellipsize(section.title, sheet.body, 640f), MARGIN, baseline, sheet.body)
        sheet.canvas.drawText(count, MARGIN + 680f, baseline, sheet.small)
        sheet.canvas.drawText(page, W - MARGIN - sheet.small.measureText(page), baseline, sheet.small)
        sheet.y += sheet.body.textSize + 10f
        sheet.canvas.drawLine(MARGIN, sheet.y, W - MARGIN, sheet.y, sheet.rule)
        sheet.y += 8f
      }
    }
    sections.forEachIndexed { i, section ->
      if (sections.size > 1 || i > 0) sheet.newPage()
      sheet.sectionStarts += sheet.pageNo
      sectionHeader(sheet, section, s)
      compose(sheet, section.cards.map { layout(sheet, it, photoDir) }, photoDir, measuring)
    }
  }

  /**
   * Sets a section's cards on its pages: as many as fit on each, the first under the section's
   * title, and a page that continues the section with its cards centred on it, so that a
   * single photograph does not hang from the top of an empty page.
   */
  private fun compose(sheet: Sheet, cards: List<CardLayout>, photoDir: File, measuring: Boolean) {
    val bottom = H - FOOT - 30f
    var i = 0
    var underTitle = true
    while (i < cards.size) {
      if (!cards[i].fits) {
        if (!underTitle) sheet.newPage()
        drawRunOn(sheet, cards[i], photoDir, measuring)
        underTitle = false
        i++
        continue
      }
      var used = 0f
      var j = i
      while (j < cards.size && cards[j].fits && sheet.y + used + cards[j].height <= bottom) {
        used += cards[j].height + CARD_GAP
        j++
      }
      if (j == i) {
        sheet.newPage()
        underTitle = false
        continue
      }
      if (!underTitle) sheet.y += (bottom - sheet.y - (used - CARD_GAP)) / 2f
      for (k in i until j) {
        drawCard(sheet, cards[k], sheet.y, photoDir, measuring)
        sheet.y += cards[k].height + CARD_GAP
      }
      i = j
      underTitle = false
      if (i < cards.size) sheet.newPage()
    }
  }

  /** A section's title band: the room in capitals over a heavy rule, its photo count at the right. */
  private fun sectionHeader(sheet: Sheet, section: AnnexContent.Section, s: Str) {
    sheet.html.subheading(section.title)
    val baseline = sheet.y + sheet.roomTitle.textSize
    val count = "${s.photos}: ${section.cards.size}"
    val countWidth = sheet.small.measureText(count)
    sheet.canvas.drawText(sheet.ellipsize(section.title.uppercase(), sheet.roomTitle, W - 2 * MARGIN - countWidth - 30f), MARGIN, baseline, sheet.roomTitle)
    sheet.canvas.drawText(count, W - MARGIN - countWidth, baseline, sheet.small)
    sheet.y = baseline + 16f
    sheet.canvas.drawLine(MARGIN, sheet.y, W - MARGIN, sheet.y, sheet.heavyRule)
    sheet.y += 30f
  }

  /** A legend line: its text, paint, offset from the legend's left edge, and baseline from its top. */
  private class LegendLine(val text: String, val paint: Paint, val x: Float, val baseline: Float)

  /** A card's legend laid out at [width]: its lines, the finding's badge if any, and its height. */
  private class Legend(val lines: List<LegendLine>, val badge: android.graphics.RectF?, val number: String?, val height: Float)

  /**
   * The legend. Wide, under a photograph, it has two columns: where and what on the left,
   * which photograph and when on the right, set flush right against the frame and as wide
   * as its longest line, so a file name is never cut. Narrow, beside a tall photograph, the
   * two stack.
   */
  private fun legend(sheet: Sheet, card: AnnexContent.Card, width: Float): Legend {
    val split = width >= 800f
    val metaWidth = if (split) minOf(width * 0.45f, card.meta.maxOf { sheet.small.measureText(it) } + 2f) else width
    val mainWidth = if (split) width - metaWidth - 40f else width
    val lines = mutableListOf<LegendLine>()
    var y = 0f
    fun add(text: String, paint: Paint, x: Float, lineWidth: Float, flushRight: Boolean = false) {
      sheet.wrapLines(text, paint, lineWidth).forEach { line ->
        y += paint.textSize
        lines += LegendLine(line, paint, if (flushRight) width - paint.measureText(line) else x, y)
        y += 8f
      }
    }
    add(card.where.uppercase(), sheet.label, 0f, mainWidth)
    y += 10f
    // The finding's number in a dark badge, its words beside it in bold: the way it reads in the body.
    val number = card.number?.toString()
    val badgeWidth = number?.let { sheet.badgeText.measureText(it) + 24f } ?: 0f
    val indent = if (number != null) badgeWidth + 14f else 0f
    val titleTop = lines.size
    add(card.title, sheet.strong, indent, mainWidth - indent)
    val badge = number?.let {
      val baseline = lines[titleTop].baseline
      android.graphics.RectF(0f, baseline - sheet.strong.textSize - 3f, badgeWidth, baseline + 9f)
    }
    if (card.context.isNotEmpty()) y += 4f
    card.context.forEach { add(it, sheet.body, 0f, mainWidth) }
    val main = y
    y = if (split) 0f else y + 10f
    card.meta.forEach { add(it, sheet.small, 0f, metaWidth, flushRight = split) }
    return Legend(lines, badge, number, maxOf(main, y) - 8f)
  }

  /** Draws [legend] with its top left corner at [left], [top]. */
  private fun drawLegend(sheet: Sheet, legend: Legend, left: Float, top: Float) {
    legend.badge?.let { badge ->
      val box = android.graphics.RectF(left + badge.left, top + badge.top, left + badge.right, top + badge.bottom)
      sheet.canvas.drawRoundRect(box, 8f, 8f, sheet.badgeFill)
      val number = legend.number.orEmpty()
      sheet.canvas.drawText(number, box.centerX() - sheet.badgeText.measureText(number) / 2f, box.bottom - 11f, sheet.badgeText)
    }
    legend.lines.forEach { sheet.canvas.drawText(it.text, left + it.x, top + it.baseline, it.paint) }
  }

  /** A card measured before anything is drawn, so a section's pages can be composed first. */
  private class CardLayout(val card: AnnexContent.Card, val fit: AnnexPlate.Fit, val legend: Legend, val beside: Boolean, val height: Float, val fits: Boolean)

  /**
   * The photograph centred with its legend below, or beside it when a tall photograph leaves
   * at least [LEGEND_MIN] there. Read from the file's header, never decoded, so the counting
   * pass measures what the drawing pass draws.
   */
  private fun layout(sheet: Sheet, card: AnnexContent.Card, photoDir: File): CardLayout {
    val fit = PhotoDecode.uprightSize(File(photoDir, card.file))?.let { (width, height) -> AnnexPlate.fit(width, height) }
    require(fit != null) { "Photograph cannot be rendered: ${card.file}" }
    val inner = W - 2 * MARGIN - 2 * FRAME_PAD
    val besideWidth = inner - fit.boxWidth - LEGEND_GAP
    var beside = besideWidth >= LEGEND_MIN
    var legend = legend(sheet, card, if (beside) besideWidth else inner)
    if (beside && legend.height > fit.boxHeight + 200f) {
      beside = false
      legend = legend(sheet, card, inner)
    }
    val body = if (beside) maxOf(fit.boxHeight, legend.height) else fit.boxHeight + 28f + legend.height
    val height = 2 * FRAME_PAD + body
    return CardLayout(card, fit, legend, beside, height, height <= H - FOOT - 30f - MARGIN)
  }

  /** One framed card, its top at [top]. */
  private fun drawCard(sheet: Sheet, layout: CardLayout, top: Float, photoDir: File, measuring: Boolean) {
    val inner = W - 2 * MARGIN - 2 * FRAME_PAD
    val fit = layout.fit
    val left = if (layout.beside) MARGIN + FRAME_PAD else MARGIN + FRAME_PAD + (inner - fit.boxWidth) / 2f
    val photo = android.graphics.RectF(left, top + FRAME_PAD, left + fit.boxWidth, top + FRAME_PAD + fit.boxHeight)
    sheet.canvas.drawRoundRect(android.graphics.RectF(MARGIN, top, W - MARGIN, top + layout.height), 12f, 12f, sheet.frame)
    if (!measuring) photo(sheet, layout.card, File(photoDir, layout.card.file), fit, photo)
    sheet.canvas.drawRect(photo, sheet.hairline)
    if (layout.beside) drawLegend(sheet, layout.legend, photo.right + LEGEND_GAP, photo.top)
    else {
      sheet.canvas.drawLine(MARGIN + FRAME_PAD, photo.bottom + 14f, W - MARGIN - FRAME_PAD, photo.bottom + 14f, sheet.rule)
      drawLegend(sheet, layout.legend, MARGIN + FRAME_PAD, photo.bottom + 28f)
    }
  }

  /** A card whose legend is longer than a page: the framed photograph, then the legend running on. */
  private fun drawRunOn(sheet: Sheet, layout: CardLayout, photoDir: File, measuring: Boolean) {
    val inner = W - 2 * MARGIN - 2 * FRAME_PAD
    val fit = layout.fit
    sheet.ensure(2 * FRAME_PAD + fit.boxHeight)
    val top = sheet.y
    val left = MARGIN + FRAME_PAD + (inner - fit.boxWidth) / 2f
    val photo = android.graphics.RectF(left, top + FRAME_PAD, left + fit.boxWidth, top + FRAME_PAD + fit.boxHeight)
    sheet.canvas.drawRoundRect(android.graphics.RectF(MARGIN, top, W - MARGIN, photo.bottom + FRAME_PAD), 12f, 12f, sheet.frame)
    if (!measuring) photo(sheet, layout.card, File(photoDir, layout.card.file), fit, photo)
    sheet.canvas.drawRect(photo, sheet.hairline)
    sheet.y = photo.bottom + FRAME_PAD + 16f
    val flowing = legend(sheet, layout.card, 600f)
    flowing.lines.forEach { line ->
      sheet.ensure(line.paint.textSize + 10f)
      sheet.canvas.drawText(line.text, MARGIN + FRAME_PAD + line.x, sheet.y + line.paint.textSize, line.paint)
      sheet.y += line.paint.textSize + 8f
    }
    sheet.y += CARD_GAP
  }

  /**
   * The photograph itself. For the export it becomes a JPEG in the spool and a stand-in on
   * the page; for the on-screen preview, a light JPEG at half the size in the HTML only.
   */
  private fun photo(sheet: Sheet, card: AnnexContent.Card, file: File, fit: AnnexPlate.Fit, box: android.graphics.RectF) {
    val spool = sheet.spool
    val scale = if (spool == null) 2 else 1
    val bmp = PhotoDecode.uprightAtSize(file, maxOf(1, fit.pixelWidth / scale), maxOf(1, fit.pixelHeight / scale))
    require(bmp != null) { "Photograph cannot be rendered: ${card.file}" }
    val picture = onPaper(bmp)
    if (spool == null) sheet.html.jpeg(picture, card) else {
      val jpeg = File(spool, "plate-${sheet.plates.size}.jpg")
      jpeg.outputStream().buffered().use { check(picture.compress(Bitmap.CompressFormat.JPEG, AnnexPlate.JPEG_QUALITY, it)) }
      val standIn = standIn(sheet.plates.size)
      sheet.plates += PdfPlates.Plate(jpeg, picture.width, picture.height)
      sheet.canvas.drawBitmap(standIn, null, box, null)
      standIn.recycle()
      sheet.html.plate(jpeg, card)
    }
    picture.recycle()
  }

  /** Whether a photo file would decode at all: the bounds pass, for counting pages. */
  /**
   * Whether a plate decodes, which the counting pass and the rendering pass must answer
   * identically or the printed "page N of M" lies about a document already signed.
   */
  private fun readable(file: File): Boolean = PhotoDecode.readable(file)

  /**
   * The plate as paper shows it: a picture with transparency, a screenshot picked from the
   * gallery, is laid on white. A JPEG carries three colour channels and no alpha, and
   * Android would encode a transparent pixel as black.
   */
  private fun onPaper(bitmap: Bitmap): Bitmap {
    if (!bitmap.hasAlpha()) return bitmap
    val paper = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    paper.eraseColor(Color.WHITE)
    Canvas(paper).drawBitmap(bitmap, 0f, 0f, null)
    paper.setHasAlpha(false)
    bitmap.recycle()
    return paper
  }

  /** Plate [index]'s stand-in: opaque, so PdfDocument embeds its pixels exactly as set. */
  private fun standIn(index: Int): Bitmap {
    val rgb = PdfPlates.marker(index)
    val side = PdfPlates.SIDE
    val pixels = IntArray(side * side) { i -> Color.rgb(rgb[3 * i].toInt() and 0xFF, rgb[3 * i + 1].toInt() and 0xFF, rgb[3 * i + 2].toInt() and 0xFF) }
    return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, side, 0, 0, side, side)
      setHasAlpha(false)
    }
  }
}
