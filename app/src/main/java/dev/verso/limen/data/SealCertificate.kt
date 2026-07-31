package dev.verso.limen.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.verso.limen.ui.Str
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One page that names everything the seal knows: both digests, both keys, both moments.
 * It travels beside the protocol PDF; whoever holds the pair can have any tool recompute
 * the PDF's SHA-256 and see that nothing moved since the door.
 */
object SealCertificate {

  private const val W = 1240
  private const val H = 1754
  private const val MARGIN = 90f

  fun export(context: Context, p: Protocol, s: Str): ExportedFile {
    val seal = p.seal ?: throw IllegalStateException("no seal to certify")
    val doc = PdfDocument()
    try {
      val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
      val c = page.canvas
      c.drawColor(Color.WHITE)
      val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 40f; color = Color.BLACK
      }
      val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 20f; color = Color.rgb(90, 90, 90)
      }
      val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textSize = 22f; color = Color.BLACK
      }
      val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 21f; color = Color.BLACK
      }
      val rule = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1.5f }
      val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
      var y = MARGIN

      c.drawText(s.sealCertificateKey, MARGIN, y + title.textSize, title)
      y += title.textSize + 12f
      c.drawLine(MARGIN, y, W - MARGIN, y, Paint().apply { color = Color.BLACK; strokeWidth = 3f })
      y += 30f

      fun row(l: String, v: String) {
        c.drawText(l, MARGIN, y + label.textSize, label)
        y += label.textSize + 6f
        wrap(v, value, W - 2 * MARGIN).forEach { line ->
          c.drawText(line, MARGIN, y + value.textSize, value)
          y += value.textSize + 6f
        }
        y += 14f
      }

      val kind = if (p.kind == Kind.Entry) s.entry else s.exit
      row(s.objectLabel, listOf(p.address, p.place, p.objectDetail).filter { it.isNotBlank() }.joinToString(", "))
      row(s.protocolKind, "$kind · ${p.handoverDate}")
      c.drawLine(MARGIN, y, W - MARGIN, y, rule)
      y += 24f

      row(s.contentFingerprint, Seal.grouped(seal.contentHash))
      row(s.pdfFingerprint, Seal.grouped(seal.pdfHash))
      row(
        s.deviceSeal,
        "${Seal.keyFingerprint(seal.publicKey)} · ${stamp.format(Date(seal.epochMs))}",
      )
      p.counterSeal?.let { cs ->
        row(
          s.counterSealLabel,
          "${cs.name} · ${s.role(cs.role)} · ${Seal.keyFingerprint(cs.publicKey)} · ${stamp.format(Date(cs.epochMs))}",
        )
      }

      c.drawLine(MARGIN, y, W - MARGIN, y, rule)
      y += 20f
      wrap(s.pdfSealNote, body, W - 2 * MARGIN).forEach { line ->
        c.drawText(line, MARGIN, y + body.textSize, body)
        y += body.textSize + 7f
      }

      doc.finishPage(page)
      return writeToDownloads(context, "limen-seal-${exportStamp()}.pdf", "application/pdf") {
        doc.writeTo(it)
      }
    } finally {
      doc.close()
    }
  }

  private fun wrap(text: String, paint: Paint, width: Float): List<String> {
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
