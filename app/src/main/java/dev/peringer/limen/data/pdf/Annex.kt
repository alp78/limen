package dev.peringer.limen.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.peringer.limen.data.*
import dev.peringer.limen.data.pdf.PdfTokens.BADGE_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.BOTTOM
import dev.peringer.limen.data.pdf.PdfTokens.FRAME_PAD
import dev.peringer.limen.data.pdf.PdfTokens.HEADER_BOTTOM
import dev.peringer.limen.data.pdf.PdfTokens.MARGIN
import dev.peringer.limen.data.pdf.PdfTokens.PANEL_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.THUMB_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.W
import dev.peringer.limen.photo.PhotoDecode
import dev.peringer.limen.ui.*
import java.io.File

/**
 * The photo annex, organised the way a handover is walked: a contents list, then one
 * section per room, each opening on a new page under the room's name, then the supplied
 * objects, the meters and anything left. Every photograph sits in a panel with its legend,
 * which repeats the room, so a page met on its own still says where it belongs and what it
 * shows: the finding's number and words as the body prints them, the surface, who is held
 * to it, and when the photograph was taken. No hash: it tells the people at a handover
 * nothing.
 *
 * The photographs are large, up to the full width or 1100 units high, 17 cm wide or 19 cm
 * high, at 200 dpi as JPEG. Each is written once as a JPEG, which the HTML copy embeds as
 * it is and [PdfPlates] puts into the PDF in place of a marked stand-in, so that PdfDocument
 * never holds the bitmap and a record of any size exports.
 *
 * The counting pass decodes nothing, but it reads every photograph's size from its header
 * and lays out every legend, so both passes break the pages in the same places.
 */
internal fun Sheet.annex(p: Protocol, photoDir: File, measuring: Boolean) {
  val plates = Report.plates(p)
  if (plates.isEmpty()) return
  require(plates.all { PhotoDecode.readable(File(photoDir, it.file)) }) { "A referenced photograph cannot be rendered" }
  val sections = AnnexContent.sections(p, s)
  newPage()
  heading(s.pdfDefectAnnex, pill = Pill("${s.photos}: ${plates.size}", PillTone.Dim))
  if (sections.size > 1) {
    // The contents: each section with its photographs and, once counted, its first page.
    // The section marks follow at once, so the anchors are the next marks' in order.
    val next = marks.size
    val entries = sections.mapIndexed { i, section -> Contents.Entry(section.title, 0, "${s.photos}: ${section.cards.size}", anchor(next + i)) }
    block(Contents(entries, sections.map { annexPages[it.key] }))
    gap(16f)
  }
  sections.forEachIndexed { i, section ->
    if (sections.size > 1 || i > 0) newPage()
    heading(section.title, level = 1, pill = Pill("${s.photos}: ${section.cards.size}", PillTone.Dim), key = section.key)
    compose(section.cards.map { layout(it, photoDir) }, photoDir, measuring)
  }
}

private const val LEGEND_GAP = 30f
private const val LEGEND_MIN = 300f
private const val CARD_GAP = 40f
private const val BADGE = 32f

/**
 * Sets a section's cards on its pages: as many as fit on each, the first under the section's
 * title, and a page that continues the section with its cards centred on it, so that a
 * single photograph does not hang from the top of an empty page.
 */
private fun Sheet.compose(cards: List<CardLayout>, photoDir: File, measuring: Boolean) {
  var i = 0
  var underTitle = true
  while (i < cards.size) {
    if (!cards[i].fits) {
      if (!underTitle) newPage()
      drawRunOn(cards[i], photoDir, measuring)
      underTitle = false
      i++
      continue
    }
    var used = 0f
    var j = i
    while (j < cards.size && cards[j].fits && y + used + cards[j].height <= BOTTOM) {
      used += cards[j].height + CARD_GAP
      j++
    }
    if (j == i) {
      newPage()
      underTitle = false
      continue
    }
    if (!underTitle) y += (BOTTOM - y - (used - CARD_GAP)) / 2f
    for (k in i until j) {
      drawCard(cards[k], y, photoDir, measuring)
      y += cards[k].height + CARD_GAP
    }
    i = j
    underTitle = false
    if (i < cards.size) newPage()
  }
}

/** A legend line: its text, paint, offset from the legend's left edge, and baseline from its top. */
private class LegendLine(val text: String, val paint: Paint, val x: Float, val baseline: Float)

/** A card's legend laid out at a width: its lines, the finding's badge if any, and its height. */
private class Legend(val lines: List<LegendLine>, val badge: RectF?, val number: String?, val height: Float)

/**
 * The legend. Wide, under a photograph, it has two columns: where and what on the left,
 * which photograph and when on the right, set flush right against the frame and as wide
 * as its longest line, so a file name is never cut. Narrow, beside a tall photograph, the
 * two stack.
 */
private fun Sheet.legend(card: AnnexContent.Card, width: Float): Legend {
  val split = width >= 800f
  val metaWidth = if (split) minOf(width * 0.45f, card.meta.maxOf { paints.small.measureText(it) } + 2f) else width
  val mainWidth = if (split) width - metaWidth - 40f else width
  val lines = mutableListOf<LegendLine>()
  var y = 0f
  fun add(text: String, paint: Paint, x: Float, lineWidth: Float, flushRight: Boolean = false) {
    TextMeasure.wrapLines(text, paint, lineWidth).forEach { line ->
      y += paint.textSize
      lines += LegendLine(line, paint, if (flushRight) width - paint.measureText(line) else x, y)
      y += 8f
    }
  }
  add(card.where.uppercase(), paints.labelSmall, 0f, mainWidth)
  y += 10f
  // The finding's number in a navy badge, its words beside it in bold: the way it reads in the body.
  val number = card.number?.toString()
  val badgeWidth = number?.let { paints.badge.measureText(it) + 24f } ?: 0f
  val indent = if (number != null) badgeWidth + 14f else 0f
  val titleTop = lines.size
  add(card.title, paints.strong, indent, mainWidth - indent)
  val badge = number?.let {
    val baseline = lines[titleTop].baseline
    RectF(0f, baseline - paints.strong.textSize - 2f, badgeWidth, baseline - paints.strong.textSize - 2f + BADGE)
  }
  if (card.context.isNotEmpty()) y += 4f
  card.context.forEach { add(it, paints.body, 0f, mainWidth) }
  val main = y
  y = if (split) 0f else y + 10f
  card.meta.forEach { add(it, paints.small, 0f, metaWidth, flushRight = split) }
  return Legend(lines, badge, number, maxOf(main, y) - 8f)
}

/** Draws [legend] with its top left corner at [left], [top]. */
private fun Sheet.drawLegend(legend: Legend, left: Float, top: Float) {
  legend.badge?.let { badge ->
    val box = RectF(left + badge.left, top + badge.top, left + badge.right, top + badge.bottom)
    canvas.drawRoundRect(box, BADGE_RADIUS, BADGE_RADIUS, paints.navyFill)
    val number = legend.number.orEmpty()
    canvas.drawText(number, box.centerX() - paints.badge.measureText(number) / 2f, box.top + paints.badge.baseline(BADGE), paints.badge)
  }
  legend.lines.forEach { canvas.drawText(it.text, left + it.x, top + it.baseline, it.paint) }
}

/** A card measured before anything is drawn, so a section's pages can be composed first. */
private class CardLayout(val card: AnnexContent.Card, val fit: AnnexPlate.Fit, val legend: Legend, val beside: Boolean, val height: Float, val fits: Boolean)

/**
 * The photograph centred with its legend below, or beside it when a tall photograph leaves
 * at least [LEGEND_MIN] there. Read from the file's header, never decoded, so the counting
 * pass measures what the drawing pass draws.
 */
private fun Sheet.layout(card: AnnexContent.Card, photoDir: File): CardLayout {
  val fit = PhotoDecode.uprightSize(File(photoDir, card.file))?.let { (width, height) -> AnnexPlate.fit(width, height) }
  require(fit != null) { "Photograph cannot be rendered: ${card.file}" }
  val inner = W - 2 * MARGIN - 2 * FRAME_PAD
  val besideWidth = inner - fit.boxWidth - LEGEND_GAP
  var beside = besideWidth >= LEGEND_MIN
  var legend = legend(card, if (beside) besideWidth else inner)
  if (beside && legend.height > fit.boxHeight + 200f) {
    beside = false
    legend = legend(card, inner)
  }
  val body = if (beside) maxOf(fit.boxHeight, legend.height) else fit.boxHeight + 28f + legend.height
  val height = 2 * FRAME_PAD + body
  return CardLayout(card, fit, legend, beside, height, height <= BOTTOM - HEADER_BOTTOM)
}

/** One card, its top at [top]: the panel, the photograph in rounded corners under a navy border, the legend. */
private fun Sheet.drawCard(layout: CardLayout, top: Float, photoDir: File, measuring: Boolean) {
  val inner = W - 2 * MARGIN - 2 * FRAME_PAD
  val fit = layout.fit
  val left = if (layout.beside) MARGIN + FRAME_PAD else MARGIN + FRAME_PAD + (inner - fit.boxWidth) / 2f
  val photo = RectF(left, top + FRAME_PAD, left + fit.boxWidth, top + FRAME_PAD + fit.boxHeight)
  frame(top, layout.height, Tone.Raised, PANEL_RADIUS)
  plate(layout.card, File(photoDir, layout.card.file), fit, photo, measuring)
  if (layout.beside) drawLegend(layout.legend, photo.right + LEGEND_GAP, photo.top)
  else {
    canvas.drawLine(MARGIN + FRAME_PAD, photo.bottom + 14f, W - MARGIN - FRAME_PAD, photo.bottom + 14f, paints.rule)
    drawLegend(layout.legend, MARGIN + FRAME_PAD, photo.bottom + 28f)
  }
}

/** A card whose legend is longer than a page: the framed photograph, then the legend running on. */
private fun Sheet.drawRunOn(layout: CardLayout, photoDir: File, measuring: Boolean) {
  val inner = W - 2 * MARGIN - 2 * FRAME_PAD
  val fit = layout.fit
  ensure(2 * FRAME_PAD + fit.boxHeight)
  val top = y
  val left = MARGIN + FRAME_PAD + (inner - fit.boxWidth) / 2f
  val photo = RectF(left, top + FRAME_PAD, left + fit.boxWidth, top + FRAME_PAD + fit.boxHeight)
  frame(top, 2 * FRAME_PAD + fit.boxHeight, Tone.Raised, PANEL_RADIUS)
  plate(layout.card, File(photoDir, layout.card.file), fit, photo, measuring)
  y = photo.bottom + FRAME_PAD + 16f
  val flowing = legend(layout.card, 600f)
  flowing.lines.forEach { line ->
    ensure(line.paint.textSize + 10f)
    canvas.drawText(line.text, MARGIN + FRAME_PAD + line.x, y + line.paint.textSize, line.paint)
    y += line.paint.textSize + 8f
  }
  y += CARD_GAP
}

/**
 * The photograph in its box, clipped to the app's thumbnail corners under a navy border.
 * The clip is a path in the page's content stream; the stand-in underneath stays the plain
 * image object [PdfPlates.swap] looks for.
 */
private fun Sheet.plate(card: AnnexContent.Card, file: File, fit: AnnexPlate.Fit, box: RectF, measuring: Boolean) {
  if (!measuring) {
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(box, THUMB_RADIUS, THUMB_RADIUS, Path.Direction.CW) })
    photo(card, file, fit, box)
    canvas.restore()
  }
  val border = RectF(box).apply { inset(PdfTokens.BORDER / 2f, PdfTokens.BORDER / 2f) }
  canvas.drawRoundRect(border, THUMB_RADIUS, THUMB_RADIUS, paints.navyStroke)
}

/**
 * The photograph itself. For the export it becomes a JPEG in the spool and a stand-in on
 * the page; for the on-screen preview, a light JPEG at half the size in the HTML only.
 */
private fun Sheet.photo(card: AnnexContent.Card, file: File, fit: AnnexPlate.Fit, box: RectF) {
  val spool = spool
  val scale = if (spool == null) 2 else 1
  val bmp = PhotoDecode.uprightAtSize(file, maxOf(1, fit.pixelWidth / scale), maxOf(1, fit.pixelHeight / scale))
  require(bmp != null) { "Photograph cannot be rendered: ${card.file}" }
  val picture = onPaper(bmp)
  if (spool == null) html.jpeg(picture, card) else {
    val jpeg = File(spool, "plate-${plates.size}.jpg")
    jpeg.outputStream().buffered().use { check(picture.compress(Bitmap.CompressFormat.JPEG, AnnexPlate.JPEG_QUALITY, it)) }
    val standIn = standIn(plates.size)
    plates += PdfPlates.Plate(jpeg, picture.width, picture.height)
    canvas.drawBitmap(standIn, null, box, null)
    standIn.recycle()
    html.plate(jpeg, card)
  }
  picture.recycle()
}

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
