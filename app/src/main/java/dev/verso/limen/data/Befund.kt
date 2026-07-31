package dev.verso.limen.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.verso.limen.ui.Str

/**
 * The dispute letter: a one-page request to a neutral official to come and record the
 * facts, with the protocol and its photo annex named as the factual base. In Zurich this
 * is the Gemeindeammann's amtlicher Befund; everywhere else the same letter serves the
 * court's precautionary taking of evidence under Art. 182 CPC. The user prints it, signs
 * it by hand and mails it: officials still live on paper, and that is fine.
 */
object Befund {

  private const val W = 1240
  private const val H = 1754
  private const val MARGIN = 110f

  fun export(context: Context, p: Protocol, s: Str, commune: String): ExportedFile {
    val doc = PdfDocument()
    try {
      val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
      val c = page.canvas
      c.drawColor(Color.WHITE)
      val subject = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 26f; color = Color.BLACK
      }
      val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 22f; color = Color.BLACK
      }
      val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 19f; color = Color.rgb(90, 90, 90)
      }
      val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textSize = 21f; color = Color.BLACK
      }
      val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 17f; color = Color.rgb(90, 90, 90)
      }
      val rule = Paint().apply { color = Color.rgb(60, 60, 60); strokeWidth = 1.5f }
      var y = MARGIN

      fun paragraph(text: String, paint: Paint, gapAfter: Float = 18f) {
        wrap(text, paint, W - 2 * MARGIN).forEach { line ->
          c.drawText(line, MARGIN, y + paint.textSize, paint)
          y += paint.textSize + 8f
        }
        y += gapAfter
      }

      // The addressee, where a window envelope expects it.
      paragraph(commune.ifBlank { "…" }, body, 40f)

      paragraph(s.befundSubject, subject, 26f)
      paragraph(s.befundBody, body, 26f)

      fun fact(l: String, v: String) {
        c.drawText(l, MARGIN, y + label.textSize, label)
        c.drawText(v.ifBlank { "–" }, MARGIN + 360f, y + label.textSize + 1f, value)
        y += label.textSize + 14f
      }
      fact(s.objectLabel, listOf(p.address, p.place, p.objectDetail).filter { it.isNotBlank() }.joinToString(", "))
      fact(s.handoverDate, p.handoverDate)
      p.parties.filter { it.name.isNotBlank() }.forEach { fact(s.role(it.role), it.name) }
      p.seal?.let {
        y += 6f
        c.drawText(s.contentFingerprint, MARGIN, y + label.textSize, label)
        y += label.textSize + 8f
        wrap(Seal.grouped(it.contentHash), value, W - 2 * MARGIN).forEach { line ->
          c.drawText(line, MARGIN, y + value.textSize, value)
          y += value.textSize + 6f
        }
      }
      y += 22f

      fact(s.befundDesiredDate, "")
      c.drawLine(MARGIN + 360f, y - 6f, MARGIN + 760f, y - 6f, rule)
      y += 30f

      paragraph(s.befundLegal, small, 40f)

      c.drawText(s.placeDateSignature, MARGIN, y + label.textSize, label)
      y += label.textSize + 60f
      c.drawLine(MARGIN, y, MARGIN + 520f, y, rule)

      doc.finishPage(page)
      return writeToDownloads(context, "limen-befund-${exportStamp()}.pdf", "application/pdf") {
        doc.writeTo(it)
      }
    } finally {
      doc.close()
    }
  }

  private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val out = mutableListOf<String>()
    text.split('\n').forEach { paragraphText ->
      var line = ""
      paragraphText.split(' ').forEach { word ->
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
