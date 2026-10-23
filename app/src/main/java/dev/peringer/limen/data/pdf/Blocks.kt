package dev.peringer.limen.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import dev.peringer.limen.data.HtmlCompanion
import dev.peringer.limen.data.pdf.PdfTokens.BADGE_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.CHIP_GAP
import dev.peringer.limen.data.pdf.PdfTokens.CHIP_RADIUS
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_GAP

/** One atomic row of a panel, [height] tall, drawn with its top left corner at (left, top). */
internal class Row(val height: Float, val keepWithNext: Boolean = false, val draw: (Canvas, Float, Float) -> Unit)

/**
 * A unit of content that lays itself out as rows at a given width and writes itself to
 * the HTML copy once. The content is built once and both outputs read it, so the preview
 * on the screen and the paper cannot say different things.
 */
internal interface Block {
  fun rows(p: Paints, width: Float): List<Row>
  fun html(h: HtmlCompanion)
  /** Called once the block's panel has been drawn, for a block holding a bitmap. */
  fun release() {}
}

internal object TextMeasure {
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

  fun ellipsize(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var t = text
    while (t.isNotEmpty() && paint.measureText("$t…") > width) t = t.dropLast(1)
    return "$t…"
  }
}

/** Wrapped text, one row per line. */
internal class Text(val text: String, val style: Style = Style.Body, val indent: Float = 0f, val keepWithNext: Boolean = false) : Block {
  override fun rows(p: Paints, width: Float): List<Row> {
    val paint = p.of(style)
    val line = p.lineOf(style)
    return TextMeasure.wrapLines(text, paint, width - indent).map { words ->
      Row(line, keepWithNext) { c, l, t -> c.drawText(words, l + indent, t + paint.baseline(line), paint) }
    }
  }
  override fun html(h: HtmlCompanion) = h.text(text)
}

/** One line that never wraps, cut with an ellipsis: one row high whatever its words, in both passes. */
internal class Line(val text: String, val style: Style = Style.Small, val indent: Float = 0f) : Block {
  override fun rows(p: Paints, width: Float): List<Row> {
    val paint = p.of(style)
    val line = p.lineOf(style)
    val words = TextMeasure.ellipsize(text, paint, width - indent)
    return listOf(Row(line) { c, l, t -> c.drawText(words, l + indent, t + paint.baseline(line), paint) })
  }
  override fun html(h: HtmlCompanion) = h.text(text)
}

internal class Gap(val height: Float) : Block {
  override fun rows(p: Paints, width: Float) = listOf(Row(height) { _, _, _ -> })
  override fun html(h: HtmlCompanion) = Unit
}

/**
 * Labels over values in [columns] equal columns, the way the app's fields sit on a panel.
 * Each grid row is one atomic row; a value too long for a page paginates as plain rows.
 */
internal class Fields(val pairs: List<Pair<String, String>>, val columns: Int = pairs.size) : Block {
  override fun rows(p: Paints, width: Float): List<Row> {
    val label = p.labelSmall
    val value = p.bodyLarge
    val labelLine = p.lineOf(Style.LabelSmall)
    val valueLine = p.lineOf(Style.BodyLarge)
    val colWidth = width / columns
    return pairs.chunked(columns).flatMap { chunk ->
      val cols = chunk.map { (heading, entry) ->
        TextMeasure.wrapLines(heading, label, colWidth - 18f) to TextMeasure.wrapLines(entry.ifBlank { "–" }, value, colWidth - 18f)
      }
      val labelLines = cols.maxOf { it.first.size }
      val valueLines = cols.maxOf { it.second.size }
      val height = labelLines * labelLine + valueLines * valueLine + LABEL_GAP
      if (height > PdfTokens.BOTTOM - PdfTokens.HEADER_BOTTOM - 200f) {
        // An unusually long legal name or address paginates rather than running off the paper.
        return@flatMap chunk.flatMap { (heading, entry) ->
          Text(heading, Style.LabelSmall, keepWithNext = true).rows(p, width) + Text(entry.ifBlank { "–" }, Style.BodyLarge).rows(p, width)
        }
      }
      listOf(Row(height) { c, l, t ->
        cols.forEachIndexed { i, (headings, entries) ->
          val x = l + i * colWidth
          headings.forEachIndexed { n, words -> c.drawText(words, x, t + n * labelLine + label.baseline(labelLine), label) }
          val valueTop = t + labelLines * labelLine
          entries.forEachIndexed { n, words -> c.drawText(words, x, valueTop + n * valueLine + value.baseline(valueLine), value) }
        }
      })
    }
  }
  override fun html(h: HtmlCompanion) = h.fields(pairs)
}

/** [html] is what the accessible copy says when the page's wording means nothing there, such as a page number. */
internal class Chip(val text: String, val active: Boolean = false, val html: String = text)

/** A wrapping row of chips, the app's pill row: one row per wrapped line. */
internal class Chips(val chips: List<Chip>, val indent: Float = 0f, val keepWithNext: Boolean = false) : Block {
  private class Placed(val chip: Chip, val x: Float, val width: Float)

  override fun rows(p: Paints, width: Float): List<Row> {
    if (chips.isEmpty()) return emptyList()
    val avail = width - indent
    val lines = mutableListOf<MutableList<Placed>>()
    var x = 0f
    chips.forEach { chip ->
      val text = TextMeasure.ellipsize(chip.text, p.chip, avail - 2 * PAD)
      val w = p.chip.measureText(text) + 2 * PAD
      if (lines.isEmpty() || (x + w > avail && lines.last().isNotEmpty())) {
        lines.add(mutableListOf())
        x = 0f
      }
      lines.last() += Placed(Chip(text, chip.active), x, w)
      x += w + CHIP_GAP
    }
    return lines.map { line ->
      Row(HEIGHT + CHIP_GAP, keepWithNext) { c, l, t -> line.forEach { draw(c, p, it.chip, l + indent + it.x, t, it.width) } }
    }
  }

  private fun draw(c: Canvas, p: Paints, chip: Chip, x: Float, top: Float, w: Float) {
    val box = RectF(x, top, x + w, top + HEIGHT)
    if (chip.active) c.drawRoundRect(box, CHIP_RADIUS, CHIP_RADIUS, p.navyFill)
    else {
      c.drawRoundRect(box, CHIP_RADIUS, CHIP_RADIUS, p.inputFill)
      box.inset(0.5f, 0.5f)
      c.drawRoundRect(box, CHIP_RADIUS, CHIP_RADIUS, p.edgeStroke)
    }
    val paint = if (chip.active) p.chipActive else p.chip
    c.drawText(chip.text, x + PAD, top + paint.baseline(HEIGHT), paint)
  }

  override fun html(h: HtmlCompanion) = h.chips(chips.map { it.html to it.active })

  companion object {
    const val HEIGHT = 34f
    const val PAD = 14f
  }
}

/**
 * One numbered finding: the number in a navy badge beside where it was found, then its
 * words, its chips, the notes hanging on it and each participant's position.
 */
internal class Finding(
  val number: Int,
  val where: String,
  val text: String,
  val chips: List<Chip>,
  val notes: List<String>,
  val positions: List<String>,
) : Block {
  override fun rows(p: Paints, width: Float): List<Row> = buildList {
    val numberText = number.toString()
    val badgeWidth = p.badge.measureText(numberText) + 24f
    val indent = maxOf(INDENT, badgeWidth + 14f)
    val whereText = TextMeasure.ellipsize(where.uppercase(), p.labelSmall, width - indent)
    add(Row(BADGE + 6f, keepWithNext = true) { c, l, t ->
      c.drawRoundRect(RectF(l, t, l + badgeWidth, t + BADGE), BADGE_RADIUS, BADGE_RADIUS, p.navyFill)
      c.drawText(numberText, l + (badgeWidth - p.badge.measureText(numberText)) / 2f, t + p.badge.baseline(BADGE), p.badge)
      if (whereText.isNotBlank()) c.drawText(whereText, l + indent, t + p.labelSmall.baseline(BADGE), p.labelSmall)
    })
    // Every row holds on to the next, so a finding that fits a page moves to it whole and
    // a cost note never opens a page on its own; only the closing gap lets a page end.
    fun kept(rows: List<Row>) = rows.map { Row(it.height, keepWithNext = true, it.draw) }
    addAll(kept(Text(text.ifBlank { "–" }, Style.Body, indent).rows(p, width)))
    addAll(kept(Chips(chips, indent).rows(p, width)))
    notes.forEach { addAll(kept(Text(it, Style.Small, indent).rows(p, width))) }
    positions.forEach { addAll(kept(Text(it, Style.Body, indent).rows(p, width))) }
    add(Row(LABEL_GAP) { _, _, _ -> })
  }

  override fun html(h: HtmlCompanion) = h.finding(number, where) {
    h.text(text.ifBlank { "–" })
    if (chips.isNotEmpty()) h.chips(chips.map { it.text to it.active })
    notes.forEach(h::text)
    positions.forEach(h::text)
  }

  companion object {
    const val INDENT = 48f
    const val BADGE = 32f
  }
}

/** The exhaustive item grid older records were signed with: two columns, the state at the right of each. */
internal class ItemGrid(val lines: List<Pair<String, String>>) : Block {
  override fun rows(p: Paints, width: Float): List<Row> {
    val colWidth = (width - 40f) / 2f
    val stateWidth = 140f
    val line = p.lineOf(Style.Body)
    val rowsPerCol = (lines.size + 1) / 2
    return (0 until rowsPerCol).map { i ->
      Row(line) { c, l, t ->
        listOf(0, 1).forEach { col ->
          val idx = i + col * rowsPerCol
          if (idx < lines.size) {
            val (name, state) = lines[idx]
            val x = l + col * (colWidth + 40f)
            c.drawText(TextMeasure.ellipsize(name, p.body, colWidth - stateWidth - 20f), x, t + p.body.baseline(line), p.body)
            val stateText = TextMeasure.ellipsize(state, p.bodyMedium, stateWidth)
            c.drawText(stateText, x + colWidth - p.bodyMedium.measureText(stateText), t + p.bodyMedium.baseline(line), p.bodyMedium)
          }
        }
      }
    }
  }
  override fun html(h: HtmlCompanion) = h.fields(lines)
}

/**
 * A signature: the ink over a rule, or the note that stands for it, then its caption rows.
 * The bitmap is drawn straight onto the page and PdfDocument embeds it itself.
 */
internal class Ink(val bitmap: Bitmap?, val alt: String, val note: String?, val caption: List<Block>) : Block {
  override fun rows(p: Paints, width: Float): List<Row> = buildList {
    when {
      note != null -> addAll(Text(note, Style.Body, keepWithNext = true).rows(p, width))
      bitmap != null -> {
        val scale = minOf(MAX_WIDTH / bitmap.width, MAX_HEIGHT / bitmap.height, 1f)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        add(Row(h + 18f, keepWithNext = true) { c, l, t ->
          c.drawBitmap(bitmap, null, RectF(l, t, l + w, t + h), p.bitmap)
          c.drawLine(l, t + h + 8f, l + MAX_WIDTH, t + h + 8f, p.rule)
        })
      }
      else -> add(Row(18f, keepWithNext = true) { c, l, t -> c.drawLine(l, t + 8f, l + MAX_WIDTH, t + 8f, p.rule) })
    }
    caption.forEach { addAll(it.rows(p, width)) }
    add(Row(14f) { _, _, _ -> })
  }

  override fun html(h: HtmlCompanion) {
    if (bitmap != null && note == null) h.image(bitmap, alt)
    note?.let(h::text)
    caption.forEach { it.html(h) }
  }

  override fun release() {
    bitmap?.recycle()
  }

  companion object {
    const val MAX_WIDTH = 420f
    const val MAX_HEIGHT = 130f
  }
}

/**
 * A contents list: each entry's title, an optional detail and its page, the page column
 * of fixed width so the counting pass, which knows no pages yet, lays the rows out as the
 * drawing pass does.
 */
internal class Contents(val entries: List<Entry>, val pages: List<Int?>) : Block {
  class Entry(val title: String, val level: Int, val detail: String? = null, val anchor: String? = null)

  override fun rows(p: Paints, width: Float): List<Row> = entries.mapIndexed { i, entry ->
    val paint = if (entry.level == 0) p.body else p.small
    val line = p.lineOf(if (entry.level == 0) Style.Body else Style.Small)
    val indent = entry.level * 32f
    val pageText = pages.getOrNull(i)?.toString().orEmpty()
    Row(line) { c, l, t ->
      val base = t + paint.baseline(line)
      val right = l + width
      c.drawText(pageText, right - paint.measureText(pageText), base, paint)
      var titleMax = width - indent - PAGE_COLUMN
      entry.detail?.let { detail ->
        val detailWidth = p.small.measureText(detail)
        c.drawText(detail, right - PAGE_COLUMN - detailWidth, t + p.small.baseline(line), p.small)
        titleMax -= detailWidth + 40f
      }
      c.drawText(TextMeasure.ellipsize(entry.title, paint, titleMax), l + indent, base, paint)
    }
  }

  override fun html(h: HtmlCompanion) = h.contents(entries.map { HtmlCompanion.ContentsEntry(it.title, it.level, it.detail, it.anchor) })

  companion object {
    const val PAGE_COLUMN = 100f
  }
}
