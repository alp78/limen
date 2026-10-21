package dev.peringer.limen.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Base64OutputStream
import java.io.File
import java.io.OutputStream

/**
 * Built by the PDF's own drawing pass: identical content selection and reading order, in
 * the app's colours and shapes, so the accessible copy and the screen's preview look like
 * the paper and the screen at once.
 *
 * Written straight to [sink] as the pages are drawn and never held whole. Built in memory, a
 * record of thirty photographs once exhausted the heap and failed the PDF with it, and a
 * record holds any number of photographs.
 */
internal class HtmlCompanion(language: String, title: String, private val sink: OutputStream) {
  init {
    put("<!doctype html><html lang=\"${escape(language)}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>${escape(title)}</title><style>$STYLE</style></head><body><main>")
  }
  private fun put(text: String) = sink.write(text.toByteArray(Charsets.UTF_8))
  private fun id(anchor: String?) = anchor?.let { " id=\"${escape(it)}\"" }.orEmpty()

  /** The cover's banner: the name, then one line each for what the record is and where. */
  fun banner(name: String, lines: List<String>) {
    put("<header class=\"banner\"><h1>${escape(name)}</h1>")
    lines.filter(String::isNotBlank).forEach { put("<p>${escape(it)}</p>") }
    put("</header>")
  }
  fun heading(text: String, anchor: String? = null) = put("<h2${id(anchor)}>${escape(text)}</h2>")
  /** A section of the photo annex: a room, the objects, the meters. */
  fun subheading(text: String, anchor: String? = null) = put("<h3${id(anchor)}>${escape(text)}</h3>")
  fun text(text: String) = put("<p>${escape(text)}</p>")
  fun fields(pairs: List<Pair<String, String>>) {
    put("<dl class=\"grid\">")
    pairs.forEach { (name, value) -> put("<div><dt>${escape(name)}</dt><dd>${escape(value.ifBlank { "–" })}</dd></div>") }
    put("</dl>")
  }
  /** A panel: its title with its pill, then whatever [body] writes. */
  fun panel(title: String, tone: String, pill: String?, pillTone: String?, anchor: String?, body: () -> Unit) {
    val pillHtml = pill?.let { "<span class=\"pill ${escape(pillTone.orEmpty())}\">${escape(it)}</span>" }.orEmpty()
    put("<section class=\"panel ${escape(tone)}\"${id(anchor)}><h2>${escape(title)}$pillHtml</h2>")
    body()
    put("</section>")
  }
  fun chips(items: List<Pair<String, Boolean>>) {
    put("<ul class=\"chips\">")
    items.forEach { (text, active) -> put("<li class=\"chip${if (active) " active" else ""}\">${escape(text)}</li>") }
    put("</ul>")
  }
  fun finding(number: Int, where: String, body: () -> Unit) {
    put("<article class=\"finding\"><p class=\"where\"><span class=\"badge\">$number</span>${escape(where)}</p>")
    body()
    put("</article>")
  }
  class ContentsEntry(val title: String, val level: Int, val detail: String?, val anchor: String?)
  fun contents(entries: List<ContentsEntry>) {
    put("<nav class=\"contents\"><ul>")
    entries.forEach { e ->
      val title = e.anchor?.let { "<a href=\"#${escape(it)}\">${escape(e.title)}</a>" } ?: escape(e.title)
      put("<li class=\"l${e.level}\">$title${e.detail?.let { " <small>${escape(it)}</small>" }.orEmpty()}</li>")
    }
    put("</ul></nav>")
  }
  fun list(items: List<String>) {
    put("<ul class=\"meta\">")
    items.filter(String::isNotBlank).forEach { put("<li>${escape(it)}</li>") }
    put("</ul>")
  }
  /** A signature, which carries transparency and so goes as PNG; anything opaque goes as JPEG. */
  fun image(bitmap: Bitmap, caption: String) {
    val png = bitmap.hasAlpha()
    figure(if (png) "png" else "jpeg", caption, "<p>${escape(caption)}</p>") {
      check(bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 85, it))
    }
  }
  /** An annex plate in the exported copy: the very JPEG the PDF carries, with its legend. */
  fun plate(file: File, card: AnnexContent.Card) = figure("jpeg", alt(card), legend(card)) { out -> file.inputStream().use { it.copyTo(out) } }
  /** An annex plate in the on-screen preview, which is not the evidence and stays light. */
  fun jpeg(bitmap: Bitmap, card: AnnexContent.Card) = figure("jpeg", alt(card), legend(card)) {
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
  }
  private fun alt(card: AnnexContent.Card) = "${card.where}, ${title(card)}"
  private fun title(card: AnnexContent.Card) = card.number?.let { "$it. ${card.title}" } ?: card.title
  private fun legend(card: AnnexContent.Card) = buildString {
    append("<p><small>").append(escape(card.where)).append("</small><br><strong>").append(escape(title(card))).append("</strong>")
    card.context.forEach { append("<br>").append(escape(it)) }
    append("</p><ul class=\"meta\">")
    card.meta.forEach { append("<li>").append(escape(it)).append("</li>") }
    append("</ul>")
  }
  private fun figure(type: String, alt: String, captionHtml: String, encode: (OutputStream) -> Unit) {
    put("<figure><img alt=\"${escape(alt)}\" src=\"data:image/$type;base64,")
    Base64OutputStream(sink, Base64.NO_WRAP or Base64.NO_CLOSE).use { encode(it) }
    put("\"><figcaption>$captionHtml</figcaption></figure>")
  }
  fun finish() = put("</main></body></html>")

  companion object {
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    /** The Day palette's hex values, the same ones the page is drawn in. */
    private const val STYLE =
      "body{font-family:Roboto,'Segoe UI',system-ui,sans-serif;color:#1A1D23;background:#F6F7F9;line-height:1.5;margin:0;padding:1rem;overflow-wrap:anywhere}" +
        "main{max-width:52rem;margin:auto}" +
        ".banner{background:#233350;color:#fff;border-radius:14px;padding:1.5rem;margin-bottom:1rem}.banner h1{margin:0 0 .5rem;letter-spacing:-.02em}.banner p{margin:.2rem 0;color:#E5E7EA}" +
        "h2{font-size:1.3rem;border-bottom:1.5px solid #233350;padding-bottom:.3rem;margin:1.5rem 0 .8rem}" +
        "h3{font-size:1.1rem;border-bottom:1.5px solid #233350;padding-bottom:.3rem;margin:1.5rem 0 .8rem}" +
        ".panel{background:#E7E9EE;border:1.5px solid #233350;border-radius:12px;padding:1rem;margin:.7rem 0}.panel.warning{background:#FCEBD2;border-color:#835000}" +
        ".panel>h2{font-size:.75rem;font-weight:500;letter-spacing:.02em;text-transform:uppercase;color:#5B6472;border:0;padding:0;margin:0 0 .6rem;display:flex;justify-content:space-between;align-items:center;gap:.5rem}" +
        ".pill{font-size:.75rem;font-weight:700;border-radius:6px;padding:.15rem .6rem;text-transform:uppercase;white-space:nowrap}.pill.seal{background:#233350;color:#fff}.pill.warning{background:#FCEBD2;color:#835000;border:1.5px solid #835000}.pill.dim{background:#EFF1F4;color:#5B6472}.pill.paper{background:#fff;color:#233350}" +
        "dl.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(12rem,1fr));gap:.6rem;margin:.4rem 0}dt{font-size:.75rem;font-weight:500;color:#5B6472}dd{margin:0}" +
        ".chips{list-style:none;display:flex;flex-wrap:wrap;gap:.4rem;padding:0;margin:.4rem 0}.chip{background:#EFF1F4;border:1px solid #D7DAE0;border-radius:10px;padding:.15rem .6rem;font-size:.8rem}.chip.active{background:#233350;color:#fff;border-color:#233350}" +
        ".finding{margin:.8rem 0;padding-left:2.6rem;position:relative}.finding .where{font-size:.75rem;text-transform:uppercase;color:#5B6472;margin:0 0 .2rem}.badge{position:absolute;left:0;top:0;min-width:1.4rem;padding:0 .3rem;background:#233350;color:#fff;border-radius:8px;text-align:center;font-weight:700;font-size:.85rem;line-height:1.5rem}" +
        "figure{background:#E7E9EE;border:1.5px solid #233350;border-radius:12px;padding:1rem;margin:1rem 0}img{border-radius:8px;border:1.5px solid #233350;max-width:100%;height:auto;display:block;box-sizing:border-box}figcaption{margin-top:.6rem}figcaption small,ul.meta{color:#5B6472;font-size:.85rem}ul.meta{padding-left:1.2rem;margin:.3rem 0}" +
        "nav.contents ul{list-style:none;padding:0;margin:0}nav.contents li{margin:.15rem 0}nav.contents li.l1{padding-left:1.4rem;color:#5B6472;font-size:.9rem}nav.contents a{color:#233350}" +
        "p{white-space:pre-wrap;margin:.3rem 0}"
  }
}
