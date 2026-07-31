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
 * be compared against at a conciliation hearing: header, parties, deposit, meters, keys,
 * rooms, the numbered defect ledger with the explicit charge column, the TWO separate
 * signature blocks, the deposit release instruction, and the photo annex.
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

  /** The export plus the digest of its own bytes, the thing the device seal signs. */
  data class Result(val file: ExportedFile, val pdfSha256: String)

  fun export(
    context: Context,
    p: Protocol,
    s: Str,
    photoDir: File,
    signatureDir: File,
    contentHash: String = "",
  ): Result {
    val doc = PdfDocument()
    val sheet = Sheet(doc, s)
    try {
      title(sheet, p, s)
      header(sheet, p, s)
      parties(sheet, p, s)
      deposit(sheet, p, s)
      metersAndKeys(sheet, p, s)
      rooms(sheet, p, s)
      defects(sheet, p, s)
      remarks(sheet, p, s)
      signatures(sheet, p, s, signatureDir)
      release(sheet, p, s)
      sealBlock(sheet, s, contentHash)
      annex(sheet, p, s, photoDir)
      sheet.close()
      val kind = if (p.kind == Kind.Entry) s.entry else s.exit
      val name = "limen-${kind.lowercase().replace(' ', '-')}-${exportStamp()}.pdf"
      // The PDF is hashed while it streams out, so the seal signs exactly what was written.
      val digest = java.security.MessageDigest.getInstance("SHA-256")
      val file = writeToDownloads(context, name, "application/pdf") { out ->
        doc.writeTo(java.security.DigestOutputStream(out, digest))
      }
      return Result(file, digest.digest().joinToString("") { "%02x".format(it) })
    } finally {
      doc.close()
    }
  }

  // ── The sheet: cursor, page breaks, footer ───────────────────────────────────────────

  private class Sheet(val doc: PdfDocument, val s: Str) {
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
      canvas.drawText("Limen", MARGIN, H - FOOT + 8f, small)
      val pageText = "${s.pdfPage} $pageNo"
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

  private fun title(sheet: Sheet, p: Protocol, s: Str) {
    sheet.newPage()
    val kind = if (p.kind == Kind.Entry) s.entry else s.exit
    sheet.canvas.drawText(
      "${s.protocolKind.uppercase()} · ${kind.uppercase()}",
      MARGIN,
      sheet.y + sheet.titlePaint.textSize,
      sheet.titlePaint,
    )
    sheet.y += sheet.titlePaint.textSize + 12f
    sheet.canvas.drawLine(MARGIN, sheet.y, W - MARGIN, sheet.y, sheet.ink.apply { strokeWidth = 3f })
    sheet.gap(8f)
  }

  private fun header(sheet: Sheet, p: Protocol, s: Str) {
    sheet.labeledRow(listOf(s.address to p.address, s.place to p.place))
    sheet.labeledRow(
      listOf(
        s.objectDetail to p.objectDetail,
        s.handoverDate to listOf(p.handoverDate, p.handoverTime).filter { it.isNotBlank() }.joinToString(" "),
      )
    )
    if (p.lastProtocolDate.isNotBlank()) {
      sheet.labeledRow(listOf("${s.protocolKind} (${s.entry.lowercase()})" to p.lastProtocolDate))
    }
  }

  private fun parties(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.parties)
    p.parties.forEach { party ->
      val detail = buildList {
        if (party.representedBy.isNotBlank()) add("${s.representedBy}: ${party.representedBy}")
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

  private fun metersAndKeys(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle(s.meters)
    sheet.labeledRow(
      listOf(s.meterElec1 to p.meters.electricity1, s.meterElec2 to p.meters.electricity2, s.meterGas to p.meters.gas)
    )
    sheet.labeledRow(
      listOf(s.meterWater to p.meters.water, s.meterHotWater to p.meters.hotWater, s.meterOil to p.meters.heatingOil)
    )
    sheet.sectionTitle(s.keysLabel)
    val keyText = p.keys.filter { it.count > 0 }.joinToString("   ") { k ->
      val label = if (k.kind == KeyKind.Other && k.label.isNotBlank()) k.label else s.keyKind(k.kind)
      "$label: ${k.count}"
    }
    sheet.wrapped(keyText.ifBlank { "–" }, sheet.value)
    val roomKeys = p.rooms.filter { it.keysCount > 0 }
    if (roomKeys.isNotEmpty()) {
      sheet.wrapped(
        roomKeys.joinToString("   ") { r -> "${r.name.ifBlank { s.room(r.kind) }}: ${r.keysCount}" },
        sheet.value,
      )
    }
    if (p.selfMadeKeys) sheet.wrapped(s.selfMadeKeys, sheet.body)
  }

  private fun rooms(sheet: Sheet, p: Protocol, s: Str) {
    val defectMarks = p.defects.groupBy { it.roomId }
    p.rooms.forEach { room ->
      sheet.sectionTitle(room.name.ifBlank { s.room(room.kind) })
      val flags = buildList {
        if (room.freshPaintedWalls) add(s.freshWalls)
        if (room.freshPaintedWoodwork) add(s.freshWood)
        if (room.newFloor) add(s.newFloor)
      }
      if (flags.isNotEmpty()) sheet.wrapped(flags.joinToString(" · "), sheet.small)

      // Two columns of item lines: the form look, at half the paper.
      val colWidth = (W - 2 * MARGIN - 40f) / 2f
      val lines = room.items.map { item ->
        val name =
          if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key)
        val defectNos = p.defects.withIndex()
          .filter { (_, d) -> d.roomId == room.id && d.item == item.key }
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
            sheet.canvas.drawText(sheet.ellipsize(name, sheet.body, colWidth - 130f), x, sheet.y + sheet.body.textSize, sheet.body)
            sheet.canvas.drawText(state, x + colWidth - 110f, sheet.y + sheet.body.textSize, sheet.value)
          }
        }
        sheet.y += sheet.body.textSize + 8f
        i++
      }
      if (room.keysCount > 0) {
        sheet.wrapped("${s.roomKeys}: ${room.keysCount}", sheet.small)
      }
    }
  }

  private fun defects(sheet: Sheet, p: Protocol, s: Str) {
    sheet.sectionTitle("${s.defects} (${p.defects.size})")
    if (p.defects.isEmpty()) {
      sheet.wrapped("–", sheet.body)
      return
    }
    p.defects.forEachIndexed { i, d ->
      val room = p.rooms.firstOrNull { it.id == d.roomId }
      sheet.ensure(80f)
      val where = buildList {
        room?.let { add(it.name.ifBlank { s.room(it.kind) }) }
        d.item?.let { add(s.item(it)) }
      }.joinToString(" · ")
      sheet.canvas.drawText("${i + 1}.", MARGIN, sheet.y + sheet.value.textSize, sheet.value)
      sheet.canvas.drawText(where, MARGIN + 60f, sheet.y + sheet.small.textSize + 2f, sheet.small)
      sheet.y += sheet.small.textSize + 8f
      sheet.wrapped(d.text.ifBlank { "–" }, sheet.body, MARGIN + 60f, W - 2 * MARGIN - 60f)
      val meta = buildList {
        if (d.chargedToTenant) add(s.chargedToTenant.uppercase())
        when (d.fixBy) {
          FixBy.Tenant -> add("${s.fixByLabel}: ${s.role(Role.Tenant)}")
          FixBy.Landlord -> add("${s.fixByLabel}: ${s.role(Role.Landlord)}")
          FixBy.Open -> Unit
        }
        if (d.fixDeadline.isNotBlank()) add("${s.fixDeadline} ${d.fixDeadline}")
        if (d.costNote.isNotBlank()) add("${s.costNote}: ${d.costNote}")
        if (d.photos.isNotEmpty()) add("${s.photos}: ${d.photos.size}")
      }
      if (meta.isNotEmpty()) {
        sheet.wrapped(meta.joinToString(" · "), sheet.small, MARGIN + 60f, W - 2 * MARGIN - 60f)
      }
      if (d.reservation.isNotBlank()) {
        sheet.wrapped("${s.reservationLabel}: ${d.reservation}", sheet.small, MARGIN + 60f, W - 2 * MARGIN - 60f)
      }
      sheet.gap(8f)
    }
  }

  private fun remarks(sheet: Sheet, p: Protocol, s: Str) {
    if (p.remarks.isNotBlank()) {
      sheet.sectionTitle(s.remarks)
      sheet.wrapped(p.remarks, sheet.body)
    }
    if (p.chargesStatementBy.isNotBlank()) {
      sheet.gap(8f)
      sheet.wrapped("${s.chargesBy} ${p.chargesStatementBy}", sheet.body)
    }
    if (p.oneSided) {
      sheet.gap(8f)
      sheet.wrapped(s.oneSided.uppercase(), sheet.section)
      sheet.wrapped(s.infoOneSided, sheet.small)
    }
  }

  private fun signatures(sheet: Sheet, p: Protocol, s: Str, signatureDir: File) {
    // Plane 1: the record. Always printed, with its meaning above the ink.
    sheet.sectionTitle(s.signRecordTitle)
    sheet.wrapped(s.signRecordMeaning, sheet.small)
    sheet.gap(10f)
    drawPlane(sheet, p, s, signatureDir, Plane.Record)

    // Plane 2: the debt. Printed only when someone engaged with it; its warning always.
    val costs = p.signatures.filter { it.plane == Plane.Costs }
    if (costs.isNotEmpty()) {
      sheet.sectionTitle(s.signCostsTitle)
      sheet.wrapped(s.signCostsWarning, sheet.small)
      sheet.gap(10f)
      drawPlane(sheet, p, s, signatureDir, Plane.Costs)
    }
  }

  private fun drawPlane(sheet: Sheet, p: Protocol, s: Str, dir: File, plane: Plane) {
    val sigs = p.signatures.filter { it.plane == plane }
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

  /** The document names its own data: the fingerprint printed where a stamp would sit. */
  private fun sealBlock(sheet: Sheet, s: Str, contentHash: String) {
    if (contentHash.isBlank()) return
    sheet.sectionTitle(s.sealTitle)
    sheet.ensure(60f)
    sheet.canvas.drawText(s.contentFingerprint, MARGIN, sheet.y + sheet.label.textSize, sheet.label)
    sheet.y += sheet.label.textSize + 8f
    sheet.wrapped(Seal.grouped(contentHash), sheet.value)
    sheet.gap(6f)
    sheet.wrapped(s.pdfSealNote, sheet.small)
  }

  private fun annex(sheet: Sheet, p: Protocol, s: Str, photoDir: File) {
    val withPhotos = p.defects.withIndex().filter { it.value.photos.isNotEmpty() }
    if (withPhotos.isEmpty()) return
    sheet.newPage()
    sheet.sectionTitle(s.pdfDefectAnnex)
    val cellW = (W - 2 * MARGIN - 40f) / 2f
    val cellH = 420f
    var col = 0
    withPhotos.forEach { (index, d) ->
      d.photos.forEach { name ->
        val bmp = decodeDown(File(photoDir, name), cellW.toInt())
        if (bmp != null) {
          if (col == 0) sheet.ensure(cellH + 50f)
          val x = MARGIN + col * (cellW + 40f)
          val scale = minOf(cellW / bmp.width, (cellH - 40f) / bmp.height)
          val w = bmp.width * scale
          val h = bmp.height * scale
          val paint = Paint(Paint.FILTER_BITMAP_FLAG)
          sheet.canvas.drawBitmap(bmp, null, android.graphics.RectF(x, sheet.y, x + w, sheet.y + h), paint)
          sheet.canvas.drawText("${s.defects} ${index + 1}", x, sheet.y + h + sheet.small.textSize + 6f, sheet.small)
          bmp.recycle()
          col++
          if (col == 2) {
            col = 0
            sheet.y += cellH + 20f
          }
        }
      }
    }
    if (col != 0) sheet.y += cellH + 20f
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
