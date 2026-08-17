package dev.verso.limen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.verso.limen.ui.Str
import java.io.File

/**
 * The protocol as an A4 document, drawn with the platform's own PdfDocument: no library,
 * no fonts to embed, black ink on white paper. The layout follows the paper canon it will
 * be compared against at a conciliation hearing: header, parties, the money and meter
 * rubrics this handover actually carries, the supplied objects, the findings numbered by
 * room with the explicit charge column, the TWO separate signature blocks, the deposit
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

  fun export(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
  ): ExportedFile {
    require(p.isFinalized()) { "Only finalized protocols can be exported" }
    check(ProtocolIntegrity.matches(p, photoDir, signatureDir)) { "Finalized evidence does not match its fingerprint" }
    val reference = reference(p)
    // Twice: the first pass only counts the sheets so the second can number them "3 / 7".
    // A form that names its own total is a form nobody can quietly take a page out of,
    // which is the tamper evidence paper has always used and people actually check.
    val counted = build(context, p, s, photoDir, signatureDir, reference, 0)
    val total = try { counted.pages.size } finally { counted.close() }

    val doc = build(context, p, s, photoDir, signatureDir, reference, total)
    try {
      val kind = if (p.kind == Kind.Entry) s.entry else s.exit
      val name = "limen-${kind.lowercase().replace(' ', '-')}-${exportStamp()}.pdf"
      return writeToDownloads(context, name, "application/pdf") { out -> doc.writeTo(out) }
    } finally {
      doc.close()
    }
  }

  /** One rendering pass. [total] is 0 while counting, and the real page count afterwards. */
  private fun build(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
    reference: String,
    total: Int,
  ): PdfDocument {
    val doc = PdfDocument()
    val sheet = Sheet(doc, s, reference, total)
    val cards = p.dossierCards()
    title(sheet, p, s, context)
    header(sheet, p, s)
    integrity(sheet, p, s)
    parties(sheet, p, s)
    if (DossierCard.Contract in cards) contract(sheet, p, s)
    if (DossierCard.Deposit in cards) deposit(sheet, p, s)
    if (DossierCard.Meters in cards) meters(sheet, p, s)
    if (DossierCard.Keys in cards) keys(sheet, p, s)
    rooms(sheet, p, s)
    objects(sheet, p, s)
    findings(sheet, p, s)
    if (DossierCard.Notes in cards) remarks(sheet, p, s)
    signatures(sheet, p, s, signatureDir)
    release(sheet, p, s)
    annex(sheet, p, s, photoDir, measuring = total == 0)
    sheet.close()
    return doc
  }

  /** The filing reference is a readable prefix of the frozen evidence fingerprint. */
  private fun reference(p: Protocol): String = "LIMEN-${p.evidenceFingerprint.take(16)}"

  // ── The sheet: cursor, page breaks, footer ───────────────────────────────────────────

  private class Sheet(val doc: PdfDocument, val s: Str, val reference: String, val total: Int) {
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
    val body = Paint(ink).apply { typeface = Typeface.SANS_SERIF; textSize = 21f }
    val small = Paint(ink).apply { typeface = Typeface.SANS_SERIF; textSize = 17f; color = Color.rgb(90, 90, 90) }
    val rule = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1.5f }

    var pageNo = 0
    var page: PdfDocument.Page? = null
    var canvas: Canvas = Canvas()
    var y = 0f

    fun newPage() {
      finish()
      pageNo++
      page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
      canvas = page!!.canvas
      canvas.drawColor(Color.WHITE)
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
      ensure(60f)
      gap(16f)
      canvas.drawText(text.uppercase(), MARGIN, y + section.textSize, section)
      y += section.textSize + 8f
      canvas.drawLine(MARGIN, y, W - MARGIN, y, rule)
      gap(10f)
    }

    fun labeled(labelText: String, valueText: String, x: Float, width: Float) {
      canvas.drawText(ellipsize(labelText, label, width), x, y + label.textSize, label)
      canvas.drawText(
        ellipsize(valueText.ifBlank { "–" }, value, width),
        x,
        y + label.textSize + value.textSize + 6f,
        value,
      )
    }

    fun labeledRow(pairs: List<Pair<String, String>>) {
      ensure(64f)
      val width = (W - 2 * MARGIN) / pairs.size
      pairs.forEachIndexed { i, (l, v) -> labeled(l, v, MARGIN + i * width, width - 18f) }
      y += label.textSize + value.textSize + 22f
    }

    fun wrapped(text: String, paint: Paint, x: Float = MARGIN, width: Float = W - 2 * MARGIN) {
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
      BitmapFactory.decodeResource(context.resources, dev.verso.limen.R.drawable.limen_mark)
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
    sheet.labeledRow(
      listOf(
        s.objectDetail to p.objectDetail,
        s.handoverDate to listOf(SwissDate.display(p.handoverDate), p.handoverTime)
          .filter { it.isNotBlank() }.joinToString(" "),
      )
    )
    if (p.lastProtocolDate.isNotBlank()) {
      sheet.labeledRow(
        listOf("${s.protocolKind} (${s.entry.lowercase()})" to SwissDate.display(p.lastProtocolDate))
      )
    }
  }

  /** Identifies the immutable record and, when applicable, the record it corrects. */
  private fun integrity(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.pdfFinalized)
    sheet.wrapped("${s.fingerprint}: ${p.evidenceFingerprint}", sheet.small)
    if (p.amendedFromId.isNotBlank()) {
      sheet.wrapped("${s.pdfAmends}: ${p.amendedFromId}", sheet.small)
    }
  }

  private fun parties(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.parties)
    p.parties.forEach { party ->
      val detail = buildList {
        if (party.representedBy.isNotBlank()) add("${s.representedBy}: ${party.representedBy}")
        if (party.phone.isNotBlank()) add(party.phone)
        if (party.address.isNotBlank()) add("${s.newAddress}: ${party.address}")
      }.joinToString(" · ")
      sheet.ensure(52f)
      sheet.canvas.drawText(s.role(party.role), MARGIN, sheet.y + sheet.label.textSize, sheet.label)
      sheet.canvas.drawText(
        sheet.ellipsize(party.name.ifBlank { "–" }, sheet.value, 640f),
        MARGIN + 330f,
        sheet.y + sheet.label.textSize + 2f,
        sheet.value,
      )
      sheet.y += sheet.label.textSize + 10f
      if (detail.isNotBlank()) {
        sheet.canvas.drawText(sheet.ellipsize(detail, sheet.small, W - 2 * MARGIN - 330f), MARGIN + 330f, sheet.y + sheet.small.textSize, sheet.small)
        sheet.y += sheet.small.textSize + 10f
      }
    }

    // Who compiled the record, with the contact the session was opened under. This was
    // collected and validated at the start of every session and then printed nowhere, which
    // made the whole identity form a set of fields that changed no document.
    val by = p.sessionIdentity
    val contact = buildList {
      by.fullName.takeIf { it.isNotBlank() }?.let(::add)
      by.phone.takeIf { it.isNotBlank() }?.let(::add)
      listOf(by.addressLine, by.placeLine).filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    if (contact.isNotEmpty()) {
      sheet.gap(4f)
      sheet.wrapped("${s.compiledBy}: ${contact.joinToString(" · ")}", sheet.small)
    }
  }

  private fun deposit(sheet: Sheet, p: Protocol, s: Str) {
    if (p.deposit.kind == DepositKind.None) return
    sheet.sectionTitle(s.deposit)
    when (p.deposit.kind) {
      DepositKind.Insurance -> sheet.labeledRow(
        listOf(
          s.depositInsurance to p.deposit.provider,
          s.depositPolicy to p.deposit.policyNo,
          s.depositAmount to p.deposit.amountChf,
        )
      )
      DepositKind.BankAccount -> sheet.labeledRow(
        listOf(
          s.depositBank to p.deposit.bank,
          s.depositAccount to p.deposit.account,
          s.depositAmount to p.deposit.amountChf,
        )
      )
      DepositKind.None -> Unit
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
    if (p.objects.isEmpty()) {
      legacyExtras(sheet, p, s)
      return
    }
    sheet.sectionTitle(s.objects)
    p.objects
      .groupBy { it.group to it.groupLabel }
      .forEach { (heading, rows) ->
        val (group, groupLabel) = heading
        sheet.ensure(50f)
        sheet.wrapped(groupLabel.ifBlank { s.objectGroup(group) }.uppercase(), sheet.label)
        rows.forEach { entry ->
          val detail = buildList {
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
    sheet.wrapped(
      p.rooms.joinToString(" · ") { it.name.ifBlank { s.room(it.kind) } },
      sheet.body,
    )
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
   * A room with nothing recorded in it prints nothing at all. It would be easy to give it
   * a heading and a dash, and it would be a lie: the rooms are seeded, not visited, so a
   * dash under BALCONY would state that a balcony was inspected and found in order in a
   * flat that has no balcony. Silence claims nothing. The rooms themselves are named once
   * in [rooms], which is a fact the record does hold.
   */
  private fun findings(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle("${s.defects} (${p.defects.size})")
    if (p.defects.isEmpty()) sheet.wrapped("–", sheet.body)
    p.rooms.forEach { room ->
      val found = Report.roomFindings(p, room.id)
      if (found.isEmpty()) return@forEach
      sheet.ensure(60f)
      sheet.wrapped(room.name.ifBlank { s.room(room.kind) }.uppercase(), sheet.label)
      found.forEach { finding(sheet, p, s, it.defect, it.number) }
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
    if (p.remarks.isNotBlank()) {
      sheet.sectionTitle(s.remarks)
      sheet.wrapped(p.remarks, sheet.body)
    }
    if (p.chargesStatementBy.isNotBlank()) {
      sheet.gap(8f)
      sheet.wrapped("${s.chargesBy} ${SwissDate.display(p.chargesStatementBy)}", sheet.body)
    }
  }

  private fun signatures(sheet: Sheet, p: Protocol, s: Str, signatureDir: File) {
    // Plane 1: the record. Always printed, with its meaning above the ink.
    sheet.sectionTitle(s.signRecordTitle)
    // A handover one party did not attend is a statement about these signatures, so it is
    // printed here and unconditionally. It used to live under the remarks section, which
    // meant it vanished from the paper whenever that optional component was not added.
    if (p.oneSided) {
      sheet.wrapped(s.oneSided.uppercase(), sheet.section)
      sheet.wrapped(s.infoOneSided, sheet.small)
      sheet.gap(8f)
    }
    sheet.wrapped(s.signRecordMeaning, sheet.small)
    sheet.gap(10f)
    drawPlane(sheet, p, s, signatureDir, Plane.Record)

    // Plane 2: the debt. Printed only when someone engaged with it; its warning always.
    val costs = p.signatures.filter { it.plane == Plane.Costs && p.isCurrent(it) }
    if (costs.isNotEmpty()) {
      sheet.sectionTitle(s.signCostsTitle)
      sheet.wrapped(s.signCostsWarning, sheet.small)
      sheet.gap(10f)
      drawPlane(sheet, p, s, signatureDir, Plane.Costs)
    }

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
      if (sig.refused) {
        sheet.canvas.drawText(s.refusedNote.uppercase(), x, sheet.y + sheet.value.textSize, sheet.value)
        sheet.y += sheet.value.textSize + 6f
      } else {
        val file = File(dir, sig.file)
        val bmp = if (sig.file.isNotBlank() && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        if (bmp != null) {
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
      }.joinToString(" · ")
      sheet.wrapped(caption, sheet.small)
      sheet.gap(14f)
    }
  }

  private fun release(sheet: Sheet, p: Protocol, s: Str) {
    if (p.kind != Kind.Exit || p.deposit.kind == DepositKind.None) return
    sheet.sectionTitle(s.releaseTitle)
    when (p.deposit.kind) {
      DepositKind.BankAccount -> sheet.wrapped(s.releaseBank, sheet.body)
      DepositKind.Insurance -> {
        sheet.wrapped(s.releaseInsurance, sheet.body)
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
    }
  }


  /**
   * A plate's caption. A finding is cited by the number it prints under in [findings],
   * which is what makes the annex checkable against the body; an object is cited by name,
   * because an object has no number to cite.
   */
  private fun Report.Plate.caption(s: Str): String = when (val o = owner) {
    is Report.PlateOwner.Finding -> "${s.defects} ${o.number}"
    is Report.PlateOwner.Supplied -> o.entry.displayName(s)
  }

  private fun annex(sheet: Sheet, p: Protocol, s: Str, photoDir: File, measuring: Boolean) {
    val plates = Report.plates(p)
    if (plates.isEmpty()) return
    sheet.newPage()
    sheet.sectionTitle(s.pdfDefectAnnex)
    val cellW = (W - 2 * MARGIN - 40f) / 2f
    val cellH = 420f
    var col = 0
    plates.forEach { plate ->
      val file = File(photoDir, plate.file)
      // The counting pass reads the dimensions only: same page breaks, no decoding.
      val bmp = if (measuring) null else decodeDown(file, cellW.toInt())
      if (if (measuring) readable(file) else bmp != null) {
        if (col == 0) sheet.ensure(cellH + 50f)
        val x = MARGIN + col * (cellW + 40f)
        if (bmp != null) {
          val scale = minOf(cellW / bmp.width, (cellH - 40f) / bmp.height)
          val w = bmp.width * scale
          val h = bmp.height * scale
          val paint = Paint(Paint.FILTER_BITMAP_FLAG)
          sheet.canvas.drawBitmap(bmp, null, android.graphics.RectF(x, sheet.y, x + w, sheet.y + h), paint)
          val caption = buildString {
            append(plate.caption(s))
            if (plate.sha256.isNotBlank()) append(" · ").append(plate.sha256.take(12))
          }
          sheet.canvas.drawText(
            sheet.ellipsize(caption, sheet.small, cellW),
            x,
            sheet.y + h + sheet.small.textSize + 6f,
            sheet.small,
          )
          bmp.recycle()
        }
        col++
        if (col == 2) {
          col = 0
          sheet.y += cellH + 20f
        }
      }
    }
    if (col != 0) sheet.y += cellH + 20f
  }

  /** Whether a photo file would decode at all: the bounds pass, for counting pages. */
  private fun readable(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
  }

  /** EXIF-blind fast decode at roughly [targetWidth]; the camera writes upright JPEGs here. */
  private fun decodeDown(file: File, targetWidth: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    val sample = (bounds.outWidth / targetWidth).coerceAtLeast(1)
    val bmp = BitmapFactory.decodeFile(
      file.absolutePath,
      BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    // The one camera reality PdfDocument does not forgive: sideways EXIF rotation.
    val exif = runCatching { androidx.exifinterface.media.ExifInterface(file.absolutePath) }.getOrNull()
    val rotation = when (exif?.getAttributeInt(
      androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
      androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
    )) {
      androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
    if (rotation == 0f) return bmp
    val m = android.graphics.Matrix().apply { postRotate(rotation) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    if (rotated != bmp) bmp.recycle()
    return rotated
  }
}
