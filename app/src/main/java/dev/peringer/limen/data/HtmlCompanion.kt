package dev.peringer.limen.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Base64OutputStream
import java.io.File
import java.io.OutputStream

/**
 * Built by the PDF's own drawing pass: identical content selection and reading order.
 *
 * Written straight to [sink] as the pages are drawn and never held whole. Built in memory, a
 * record of thirty photographs once exhausted the heap and failed the PDF with it, and a
 * record holds any number of photographs.
 */
internal class HtmlCompanion(language: String, title: String, private val sink: OutputStream) {
  init {
    put("<!doctype html><html lang=\"${escape(language)}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>${escape(title)}</title><style>body{font-family:sans-serif;line-height:1.6;padding:1rem;color:#15202d;background:white;overflow-wrap:anywhere}h1,h2,h3{line-height:1.3}h2{border-bottom:1px solid #aaa;padding-top:1rem}h3{border-bottom:2px solid #15202d;padding-top:1.5rem;text-transform:uppercase;letter-spacing:.05em}dt{font-weight:bold}dd{margin:0 0 .7rem}img{max-width:100%;height:auto;display:block}figure{margin:1rem 0;padding:.75rem;border:1px solid #c8c8c8;border-radius:6px}figcaption{margin-top:.5rem}figcaption small{color:#555}p{white-space:pre-wrap}</style></head><body><main><h1>${escape(title)}</h1>")
  }
  private fun put(text: String) = sink.write(text.toByteArray(Charsets.UTF_8))
  fun heading(text: String) = put("<h2>${escape(text)}</h2>")
  /** A section of the photo annex: a room, the objects, the meters. */
  fun subheading(text: String) = put("<h3>${escape(text)}</h3>")
  fun text(text: String) = put("<p>${escape(text)}</p>")
  fun fields(pairs: List<Pair<String, String>>) {
    put("<dl>")
    pairs.forEach { (name, value) -> put("<dt>${escape(name)}</dt><dd>${escape(value)}</dd>") }
    put("</dl>")
  }
  /** A signature, which carries transparency and so goes as PNG; anything opaque goes as JPEG. */
  fun image(bitmap: Bitmap, caption: String) {
    val png = bitmap.hasAlpha()
    figure(if (png) "png" else "jpeg", caption, escape(caption)) {
      check(bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 85, it))
    }
  }
  /** An annex plate in the exported copy: the very JPEG the PDF carries, with its legend. */
  fun plate(file: File, card: AnnexContent.Card) = figure("jpeg", alt(card), legend(card)) { out -> file.inputStream().use { it.copyTo(out) } }
  /** An annex plate in the on-screen preview, which is not the evidence and stays light. */
  fun jpeg(bitmap: Bitmap, card: AnnexContent.Card) = figure("jpeg", alt(card), legend(card)) {
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
  }
  private fun alt(card: AnnexContent.Card) = "${card.where} · ${title(card)}"
  private fun title(card: AnnexContent.Card) = card.number?.let { "$it. ${card.title}" } ?: card.title
  private fun legend(card: AnnexContent.Card) = buildString {
    append("<small>").append(escape(card.where)).append("</small><br><strong>").append(escape(title(card))).append("</strong>")
    card.context.forEach { append("<br>").append(escape(it)) }
    append("<br><small>").append(escape(card.meta.joinToString(" · "))).append("</small>")
  }
  private fun figure(type: String, alt: String, captionHtml: String, encode: (OutputStream) -> Unit) {
    put("<figure><img alt=\"${escape(alt)}\" src=\"data:image/$type;base64,")
    Base64OutputStream(sink, Base64.NO_WRAP or Base64.NO_CLOSE).use { encode(it) }
    put("\"><figcaption>$captionHtml</figcaption></figure>")
  }
  fun finish() = put("</main></body></html>")
  companion object {
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
  }
}
