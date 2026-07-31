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
 * The journal on paper: every chained entry of every dossier, in order, with its author,
 * its moment, its summary and its state fingerprint, plus the verification verdict of
 * each chain. This is the page a conciliation authority reads to see who did what and
 * when, and whether anyone's copy has been touched since.
 */
object AuditExport {

  private const val W = 1240
  private const val H = 1754
  private const val MARGIN = 90f
  private const val FOOT = 60f

  /** One chain to print: its heading and its events, verified against itself. */
  data class Chain(val title: String, val events: List<LedgerEvent>, val brokenAt: Int?)

  fun export(context: Context, s: Str, chains: List<Chain>): ExportedFile {
    val doc = PdfDocument()
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 40f; color = Color.BLACK
    }
    val section = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 26f; color = Color.BLACK
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.SANS_SERIF; textSize = 17f; color = Color.rgb(90, 90, 90)
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.SANS_SERIF; textSize = 20f; color = Color.BLACK
    }
    val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.MONOSPACE; textSize = 18f; color = Color.BLACK
    }
    val rule = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1.5f }
    val stampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

    var pageNo = 0
    var page: PdfDocument.Page? = null
    var canvas = Canvas()
    var y = 0f

    fun finish() {
      page?.let { pg ->
        canvas.drawLine(MARGIN, H - FOOT - 14f, W - MARGIN, H - FOOT - 14f, rule)
        canvas.drawText("Limen", MARGIN, H - FOOT + 8f, small)
        val t = "${s.pdfPage} $pageNo"
        canvas.drawText(t, W - MARGIN - small.measureText(t), H - FOOT + 8f, small)
        doc.finishPage(pg)
        page = null
      }
    }

    fun newPage() {
      finish()
      pageNo++
      page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
      canvas = page!!.canvas
      canvas.drawColor(Color.WHITE)
      y = MARGIN
    }

    fun ensure(need: Float) {
      if (page == null || y + need > H - FOOT - 30f) newPage()
    }

    newPage()
    canvas.drawText(s.auditJournal.uppercase(), MARGIN, y + title.textSize, title)
    y += title.textSize + 12f
    canvas.drawLine(MARGIN, y, W - MARGIN, y, Paint().apply { color = Color.BLACK; strokeWidth = 3f })
    y += 24f

    chains.forEach { chain ->
      ensure(80f)
      y += 10f
      canvas.drawText(chain.title.uppercase(), MARGIN, y + section.textSize, section)
      y += section.textSize + 6f
      val verdict =
        if (chain.brokenAt == null) "${s.chainOk} · ${chain.events.size} ${s.entries}"
        else "${s.chainBrokenAt} ${chain.brokenAt}"
      canvas.drawText(verdict, MARGIN, y + small.textSize, small)
      y += small.textSize + 10f
      canvas.drawLine(MARGIN, y, W - MARGIN, y, rule)
      y += 12f

      chain.events.forEach { e ->
        ensure(56f)
        canvas.drawText(stampFmt.format(Date(e.epochMs)), MARGIN, y + mono.textSize, mono)
        canvas.drawText(
          "${s.role(e.actorRole)}${if (e.actorName.isNotBlank()) " · ${e.actorName}" else ""}",
          MARGIN + 260f,
          y + body.textSize,
          body,
        )
        y += body.textSize + 4f
        val line = "${e.kind.name} · ${e.summary}".take(110)
        canvas.drawText(line, MARGIN + 260f, y + body.textSize, body)
        val fp = Seal.fingerprint(e.hash())
        canvas.drawText(fp, W - MARGIN - mono.measureText(fp), y + mono.textSize, mono)
        y += body.textSize + 12f
      }
    }

    finish()
    try {
      return writeToDownloads(context, "limen-audit-${exportStamp()}.pdf", "application/pdf") {
        doc.writeTo(it)
      }
    } finally {
      doc.close()
    }
  }
}
