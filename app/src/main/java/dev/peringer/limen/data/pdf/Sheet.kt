package dev.peringer.limen.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import dev.peringer.limen.data.HtmlCompanion
import dev.peringer.limen.data.PdfPlates
import dev.peringer.limen.data.pdf.PdfTokens.BOTTOM
import dev.peringer.limen.data.pdf.PdfTokens.DOOR_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.FOOT
import dev.peringer.limen.data.pdf.PdfTokens.H
import dev.peringer.limen.data.pdf.PdfTokens.HEADER_BOTTOM
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_GAP
import dev.peringer.limen.data.pdf.PdfTokens.MARGIN
import dev.peringer.limen.data.pdf.PdfTokens.PANEL_GAP
import dev.peringer.limen.data.pdf.PdfTokens.PANEL_PAD
import dev.peringer.limen.data.pdf.PdfTokens.PANEL_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.PILL_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.W
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.continued
import java.io.File
import java.io.OutputStream

/** A panel's look: the app's raised panel, or its amber warning panel. */
internal enum class Tone { Raised, Warning }

/** Seal: navy, white lettering. Warning: amber. Dim: the input grey. Paper: white on the navy banner. */
internal enum class PillTone { Seal, Warning, Dim, Paper }

internal class Pill(val text: String, val tone: PillTone)

/**
 * The app's panel on paper: a title in small capitals, a pill at the right, blocks under
 * them. [inContents] lists it on the cover at [level]; [key] names it to the annex.
 */
internal class Panel(
  val title: String,
  val blocks: List<Block>,
  val pill: Pill? = null,
  val tone: Tone = Tone.Raised,
  val inContents: Boolean = true,
  val level: Int = 0,
  val key: String? = null,
)

/** Where a section opened: its title, its page, its depth in the contents, and the key the annex knows it by. */
internal data class Mark(val title: String, val page: Int, val level: Int, val key: String? = null)

/**
 * The pages: cursor, running header, footer, and the one way a section draws anything,
 * which is a [panel] or a [heading]. Every page after the cover carries the record's kind,
 * its address and its reference, so a sheet separated from the rest still says what it
 * belongs to; the footer's "n / N" is the tamper evidence paper has always used.
 */
internal class Sheet(
  private val doc: PdfDocument,
  val s: Str,
  val reference: String,
  private val kindLine: String,
  private val addressLine: String,
  private val total: Int,
  html: OutputStream,
  val spool: File?,
  val logo: Bitmap?,
  /** The page each annex section opened on in the counting pass, by section key; empty while counting. */
  val annexPages: Map<String, Int>,
) {
  val paints = Paints()
  val html = HtmlCompanion(s.lang.name.lowercase(), "Limen $reference", html)
  /** The annex photographs as JPEG files, in the order their stand-ins were drawn. */
  val plates = mutableListOf<PdfPlates.Plate>()
  val marks = mutableListOf<Mark>()
  var pageNo = 0
    private set
  private var page: PdfDocument.Page? = null
  var canvas: Canvas = Canvas()
    private set
  var y = 0f
  val onPage get() = page != null
  /** The width inside a panel. */
  val inner = W - 2 * MARGIN - 2 * PANEL_PAD

  fun newPage(cover: Boolean = false) {
    finish()
    pageNo++
    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
    canvas = page!!.canvas
    canvas.drawColor(PdfTokens.PAPER)
    canvas.scale(595f / W, 842f / H)
    if (cover) {
      y = MARGIN
    } else {
      runningHeader()
      y = HEADER_BOTTOM
    }
  }

  private fun runningHeader() {
    logo?.let { canvas.drawBitmap(it, null, RectF(MARGIN, 48f, MARGIN + 36f, 84f), paints.bitmap) }
    canvas.drawText(kindLine.uppercase(), MARGIN + 50f, 76f, paints.labelSmall)
    val address = TextMeasure.ellipsize(addressLine, paints.small, 700f)
    canvas.drawText(address, W - MARGIN - paints.small.measureText(address), 62f, paints.small)
    canvas.drawText(reference, W - MARGIN - paints.small.measureText(reference), 88f, paints.small)
    canvas.drawLine(MARGIN, 102f, W - MARGIN, 102f, paints.rule)
  }

  fun finish() {
    val pg = page ?: return
    canvas.drawLine(MARGIN, H - FOOT - 14f, W - MARGIN, H - FOOT - 14f, paints.rule)
    canvas.drawText(reference, MARGIN, H - FOOT + 8f, paints.small)
    val pageText = if (total > 0) "${s.pdfPage} $pageNo / $total" else "${s.pdfPage} $pageNo"
    canvas.drawText(pageText, W - MARGIN - paints.small.measureText(pageText), H - FOOT + 8f, paints.small)
    doc.finishPage(pg)
    page = null
  }

  fun close() = finish()

  /** Guarantees [need] vertical units, breaking the page when they are not there. */
  fun ensure(need: Float) {
    if (page == null || y + need > BOTTOM) newPage()
  }

  fun gap(v: Float) {
    y += v
  }

  /** Records where a section opened and returns the anchor its HTML carries. */
  fun mark(title: String, level: Int, key: String? = null): String {
    marks += Mark(title, pageNo, level, key)
    return anchor(marks.size - 1)
  }

  fun anchor(index: Int) = "c$index"

  /** Draws [pill] with its right edge at [right], and returns its width. */
  fun pill(pill: Pill, right: Float, top: Float): Float {
    val text = pill.text.uppercase()
    val paint = when (pill.tone) {
      PillTone.Seal -> paints.pillSeal
      PillTone.Warning -> paints.pillWarning
      PillTone.Dim -> paints.pillDim
      PillTone.Paper -> paints.pillPaper
    }
    val width = paint.measureText(text) + 2 * PILL_PAD
    val box = RectF(right - width, top, right, top + PILL_HEIGHT)
    when (pill.tone) {
      PillTone.Seal -> canvas.drawRoundRect(box, PILL_RADIUS, PILL_RADIUS, paints.navyFill)
      PillTone.Warning -> {
        canvas.drawRoundRect(box, PILL_RADIUS, PILL_RADIUS, paints.warningFill)
        box.inset(PdfTokens.BORDER / 2f, PdfTokens.BORDER / 2f)
        canvas.drawRoundRect(box, PILL_RADIUS, PILL_RADIUS, paints.warningStroke)
      }
      PillTone.Dim -> canvas.drawRoundRect(box, PILL_RADIUS, PILL_RADIUS, paints.inputFill)
      PillTone.Paper -> canvas.drawRoundRect(box, PILL_RADIUS, PILL_RADIUS, paints.paperFill)
    }
    canvas.drawText(text, right - width + PILL_PAD, top + paint.baseline(PILL_HEIGHT), paint)
    return width
  }

  /** A section heading over a navy rule, the hub door's title: for a group of panels. */
  fun heading(title: String, level: Int = 0, pill: Pill? = null, key: String? = null) {
    ensure(HEADING_NEED)
    html.heading(title, mark(title, level, key))
    val line = paints.lineOf(Style.Title)
    var max = W - 2 * MARGIN
    pill?.let { max -= pill(it, W - MARGIN, y + (line - PILL_HEIGHT) / 2f) + 20f }
    canvas.drawText(TextMeasure.ellipsize(title, paints.title, max), MARGIN, y + paints.title.baseline(line), paints.title)
    y += line + 10f
    canvas.drawLine(MARGIN, y, W - MARGIN, y, paints.navyRule)
    y += 24f
  }

  /**
   * Draws [panel], whole when it fits the space left, otherwise in closed segments, each
   * a complete panel with the title repeated and a "continued" pill, so no page ends on an
   * open shape. The HTML is written once, before the first segment.
   */
  fun panel(panel: Panel) {
    val rows = panel.blocks.flatMap { it.rows(paints, inner) }
    val chrome = 2 * PANEL_PAD + PILL_HEIGHT + LABEL_GAP
    val available = if (page == null) -1f else BOTTOM - y - chrome
    val full = BOTTOM - HEADER_BOTTOM - chrome
    val segments = Paginator.segments(rows.map { Slot(it.height, it.keepWithNext) }, available, full)
    val anchor = if (panel.inContents) anchor(marks.size) else null
    html.panel(panel.title, panel.tone.name.lowercase(), panel.pill?.text, panel.pill?.tone?.name?.lowercase(), anchor) {
      panel.blocks.forEach { it.html(html) }
    }
    segments.forEach { segment ->
      if (segment.newPageBefore || page == null) newPage()
      val height = chrome + rows.subList(segment.from, segment.to).fold(0f) { acc, row -> acc + row.height }
      frame(y, height, panel.tone, PANEL_RADIUS)
      if (!segment.continued && panel.inContents) mark(panel.title, panel.level, panel.key)
      val left = MARGIN + PANEL_PAD
      val top = y + PANEL_PAD
      val pill = if (segment.continued) Pill(s.continued, PillTone.Dim) else panel.pill
      var titleMax = inner
      pill?.let { titleMax -= pill(it, W - MARGIN - PANEL_PAD, top) + 20f }
      // A room's panel is titled like a hub door, in the bold label; a component's like a panel.
      val titlePaint = if (panel.level > 0) paints.labelLarge else paints.labelSmall
      canvas.drawText(TextMeasure.ellipsize(panel.title.uppercase(), titlePaint, titleMax), left, top + titlePaint.baseline(PILL_HEIGHT), titlePaint)
      var rowTop = top + PILL_HEIGHT + LABEL_GAP
      for (i in segment.from until segment.to) {
        rows[i].draw(canvas, left, rowTop)
        rowTop += rows[i].height
      }
      y += height + PANEL_GAP
    }
    panel.blocks.forEach { it.release() }
  }

  /** The panel's shape: the fill, then the border inset by half its width so it stays inside the margin. */
  fun frame(top: Float, height: Float, tone: Tone, radius: Float, left: Float = MARGIN, right: Float = W - MARGIN) {
    val box = RectF(left, top, right, top + height)
    canvas.drawRoundRect(box, radius, radius, if (tone == Tone.Warning) paints.warningFill else paints.panelFill)
    box.inset(PdfTokens.BORDER / 2f, PdfTokens.BORDER / 2f)
    canvas.drawRoundRect(box, radius, radius, if (tone == Tone.Warning) paints.warningStroke else paints.navyStroke)
  }

  /** A block outside any panel, split row by row across pages. */
  fun block(block: Block) {
    block.html(html)
    block.rows(paints, W - 2 * MARGIN).forEach { row ->
      ensure(row.height)
      row.draw(canvas, MARGIN, y)
      y += row.height
    }
    block.release()
  }

  companion object {
    const val PILL_HEIGHT = 34f
    const val PILL_PAD = 16f
    /** Room for a heading and the first rows under it, so a heading never ends a page. */
    const val HEADING_NEED = 260f
    val BANNER_RADIUS = DOOR_RADIUS
  }
}
