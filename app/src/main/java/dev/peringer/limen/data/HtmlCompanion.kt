package dev.peringer.limen.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Built by the PDF's own drawing pass: identical content selection and reading order. */
internal class HtmlCompanion(language: String, title: String) {
  private val body = StringBuilder("<!doctype html><html lang=\"${escape(language)}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>${escape(title)}</title><style>body{font-family:sans-serif;line-height:1.6;max-width:65ch;margin:auto;padding:1rem;color:#15202d;background:white;overflow-wrap:anywhere}h1,h2{line-height:1.3}h2{border-bottom:1px solid #aaa;padding-top:1rem}dt{font-weight:bold}dd{margin:0 0 .7rem}img{max-width:100%;height:auto}figure{margin:1rem 0}p{white-space:pre-wrap}</style></head><body><main><h1>${escape(title)}</h1>")
  fun heading(text: String) { body.append("<h2>").append(escape(text)).append("</h2>") }
  fun text(text: String) { body.append("<p>").append(escape(text)).append("</p>") }
  fun fields(pairs: List<Pair<String, String>>) {
    body.append("<dl>")
    pairs.forEach { (name, value) -> body.append("<dt>").append(escape(name)).append("</dt><dd>").append(escape(value)).append("</dd>") }
    body.append("</dl>")
  }
  fun image(bitmap: Bitmap, caption: String) {
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    body.append("<figure><img alt=\"").append(escape(caption)).append("\" src=\"data:image/png;base64,")
      .append(android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP))
      .append("\"><figcaption>").append(escape(caption)).append("</figcaption></figure>")
  }
  fun finish(): String = body.toString() + "</main></body></html>"
  companion object {
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
  }
}
