package dev.peringer.limen.data.pdf

import android.graphics.Paint
import android.graphics.Typeface
import dev.peringer.limen.data.pdf.PdfTokens.BODY
import dev.peringer.limen.data.pdf.PdfTokens.BODY_LARGE
import dev.peringer.limen.data.pdf.PdfTokens.BODY_LARGE_LINE
import dev.peringer.limen.data.pdf.PdfTokens.BODY_LINE
import dev.peringer.limen.data.pdf.PdfTokens.BORDER
import dev.peringer.limen.data.pdf.PdfTokens.DISPLAY
import dev.peringer.limen.data.pdf.PdfTokens.DISPLAY_TRACK
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_LARGE
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_LARGE_TRACK
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_SMALL
import dev.peringer.limen.data.pdf.PdfTokens.LABEL_SMALL_TRACK
import dev.peringer.limen.data.pdf.PdfTokens.LINE
import dev.peringer.limen.data.pdf.PdfTokens.PAPER
import dev.peringer.limen.data.pdf.PdfTokens.TITLE

/** The text styles a block names, the theme's six and the three the screens set inline. */
internal enum class Style { Display, Title, BodyLarge, Body, BodyMedium, Strong, LabelLarge, LabelSmall, Small }

/**
 * One set of paints per drawing pass: three weights of the system sans-serif, which is the
 * family the app renders, and the flat fills and strokes of its panels. PdfDocument subsets
 * each face into the file. No monospace: the screens use it for a readout or two, the
 * paper for nothing.
 */
internal class Paints {
  private val regular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
  private val medium: Typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
  private val bold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

  private fun text(size: Float, face: Typeface, color: Int, track: Float = 0f) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color; letterSpacing = track }
  private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
  private fun stroke(color: Int, width: Float) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

  val display = text(DISPLAY, bold, PAPER, DISPLAY_TRACK)
  val title = text(TITLE, bold, PdfTokens.ink)
  val bodyLarge = text(BODY_LARGE, regular, PdfTokens.ink)
  val body = text(BODY, regular, PdfTokens.ink)
  val bodyMedium = text(BODY, medium, PdfTokens.ink)
  /** A finding's words in the annex legend, as the body sets them in bold. */
  val strong = text(BODY_LARGE, bold, PdfTokens.ink)
  val labelLarge = text(LABEL_LARGE, bold, PdfTokens.ink, LABEL_LARGE_TRACK)
  val labelSmall = text(LABEL_SMALL, medium, PdfTokens.inkDim, LABEL_SMALL_TRACK)
  val small = text(LABEL_SMALL, regular, PdfTokens.inkDim)
  /** The finding number inside its navy badge. */
  val badge = text(LABEL_LARGE, bold, PAPER)
  val chip = text(LABEL_SMALL, medium, PdfTokens.ink, 0.3f / 12f)
  val chipActive = text(LABEL_SMALL, medium, PAPER, 0.3f / 12f)
  val pillSeal = text(LABEL_LARGE, bold, PAPER, LABEL_LARGE_TRACK)
  val pillWarning = text(LABEL_LARGE, bold, PdfTokens.warningOutline, LABEL_LARGE_TRACK)
  val pillDim = text(LABEL_LARGE, bold, PdfTokens.inkDim, LABEL_LARGE_TRACK)
  val pillPaper = text(LABEL_LARGE, bold, PdfTokens.navy, LABEL_LARGE_TRACK)

  val paperFill = fill(PAPER)
  val panelFill = fill(PdfTokens.panel)
  val warningFill = fill(PdfTokens.warning)
  val inputFill = fill(PdfTokens.input)
  val navyFill = fill(PdfTokens.navy)
  val navyStroke = stroke(PdfTokens.navy, BORDER)
  val warningStroke = stroke(PdfTokens.warningOutline, BORDER)
  val edgeStroke = stroke(PdfTokens.edge, 1f)
  val rule = Paint().apply { color = PdfTokens.edge; strokeWidth = 1.5f }
  val navyRule = Paint().apply { color = PdfTokens.navy; strokeWidth = BORDER }
  val bitmap = Paint(Paint.FILTER_BITMAP_FLAG)

  fun of(style: Style): Paint = when (style) {
    Style.Display -> display
    Style.Title -> title
    Style.BodyLarge -> bodyLarge
    Style.Body -> body
    Style.BodyMedium -> bodyMedium
    Style.Strong -> strong
    Style.LabelLarge -> labelLarge
    Style.LabelSmall -> labelSmall
    Style.Small -> small
  }

  /** The theme's line height where it sets one, else the face's natural line. */
  fun lineOf(style: Style): Float = when (style) {
    Style.Body, Style.BodyMedium -> BODY_LINE
    Style.BodyLarge, Style.Strong -> BODY_LARGE_LINE
    else -> of(style).textSize * LINE
  }
}

internal fun Paint.tinted(color: Int): Paint = Paint(this).apply { this.color = color }

/** The baseline that centres this face in a line [line] tall, the same in both passes. */
internal fun Paint.baseline(line: Float): Float {
  val metrics = fontMetrics
  return (line - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
}
