package dev.peringer.limen.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.Inflater

/**
 * The annex photographs inside the PDF, as JPEG at their plate size.
 *
 * PdfDocument cannot be handed a plate as it is. It rasterized a Pixel capture's bitmap into
 * a 72 dpi page image, it stores even a plain bitmap losslessly, which made thirty
 * photographs 137 MB, and it keeps every bitmap in memory until the whole document is
 * written. So the annex draws a small stand-in for each plate, a [SIDE] × [SIDE] image whose
 * pixels spell its [marker], and once PdfDocument has written the file, [swap] puts each
 * plate's JPEG in place of its stand-in's image object and rewrites the cross-reference
 * table. The page paints the new image into the same box, because a PDF image is always
 * drawn into a unit square the page scales: its pixel size lives in the image object alone.
 *
 * A PDF reads a JPEG file unchanged under /DCTDecode, so nothing is re-encoded and the PDF
 * and the HTML copy, which embeds the same file, hold the same photograph.
 *
 * Plain files and bytes, no Android, so the rewrite is tested on the JVM.
 */
internal object PdfPlates {

  const val SIDE = 4

  /** A plate as the RGB JPEG the drawing pass wrote for it, [width] × [height] pixels. */
  class Plate(val jpeg: File, val width: Int, val height: Int)

  private val SIGNATURE = "LIMEN-PLATE:".toByteArray(Charsets.ISO_8859_1)

  /** The RGB bytes of stand-in [index]: a signature no photograph, logo or signature carries, then the index. */
  fun marker(index: Int): ByteArray = ByteArray(SIDE * SIDE * 3).also { bytes ->
    SIGNATURE.copyInto(bytes)
    for (i in 0 until 4) bytes[SIGNATURE.size + i] = (index ushr (24 - 8 * i)).toByte()
    for (i in SIGNATURE.size + 4 until bytes.size) bytes[i] = (i * 37 + 11).toByte()
  }

  /**
   * [pdf] as PdfDocument wrote it, to [out], with stand-in `i` replaced by `plates[i]`. A plate
   * without its stand-in fails the export: a document missing one photograph must never be
   * handed over as complete.
   */
  fun swap(pdf: ByteArray, plates: List<Plate>, out: OutputStream) {
    if (plates.isEmpty()) { out.write(pdf); return }
    // Every regular expression here runs on a small slice. Android matches in native code
    // and copies the whole input into each matcher, and Kotlin opens one per match: reading
    // the table with a regex over the document cost 220 MB for a 0.5 MB file.
    val text = String(pdf, Charsets.ISO_8859_1)
    val startxref = text.lastIndexOf("startxref")
    check(startxref >= 0) { "The PDF has no startxref" }
    val xrefAt = Regex("""startxref\s+(\d+)\s+%%EOF\s*""").matchEntire(text.substring(startxref))
      ?.groupValues?.get(1)?.toInt() ?: error("The PDF does not end in startxref")
    // PdfDocument writes one table in one section and no object streams; anything else is
    // refused rather than guessed at.
    val section = Regex("""\Axref\s+(\d+) (\d+)\s+""").find(text.substring(xrefAt, minOf(text.length, xrefAt + 64)))
      ?: error("The PDF has no cross-reference table where startxref points")
    check(section.groupValues[1] == "0") { "The cross-reference table does not start at object 0" }
    val count = section.groupValues[2].toInt()
    // Each entry is exactly 20 bytes: offset, generation, kind and a two-byte line end.
    val first = xrefAt + section.range.last + 1
    val entries = List(count) { i ->
      val at = first + 20 * i
      check(at + 20 <= startxref && text[at + 10] == ' ' && text[at + 16] == ' ' && text[at + 17] in "nf") { "Cross-reference entry $i is malformed" }
      Entry(text.substring(at, at + 10).toLong(), text.substring(at + 11, at + 16), text[at + 17])
    }
    val trailerAt = first + 20 * count
    check(text.startsWith("trailer", trailerAt)) { "The PDF has more than one cross-reference section" }
    val trailer = text.substring(trailerAt, startxref)

    val objects = entries.withIndex().filter { it.value.kind == 'n' }
      .map { it.index to it.value.offset.toInt() }
      .sortedBy { it.second }
    val counted = Counting(out)
    counted.write(pdf, 0, objects.first().second)
    val moved = HashMap<Int, Long>()
    val placed = BooleanArray(plates.size)
    objects.forEachIndexed { i, (number, start) ->
      val end = objects.getOrNull(i + 1)?.second ?: xrefAt
      moved[number] = counted.count
      // A stand-in's dictionary is a few lines long; the window keeps the match off font files.
      val standIn = standIn(pdf, text.substring(start, minOf(end, start + 1024)), start, end)
      if (standIn == null) {
        counted.write(pdf, start, end - start)
      } else {
        val (index, dictionary) = standIn
        check(index in plates.indices && !placed[index]) { "Annex stand-in $index has no plate, or appears twice" }
        placed[index] = true
        replace(counted, number, dictionary, plates[index])
      }
    }
    check(placed.all { it }) { "An annex plate has no stand-in in the written PDF" }

    val xref = counted.count
    val table = StringBuilder("xref\n0 $count\n")
    entries.forEachIndexed { number, entry ->
      val at = if (entry.kind == 'n') moved.getValue(number) else entry.offset
      table.append("%010d %s %s \n".format(at, entry.generation, entry.kind))
    }
    table.append(trailer).append("startxref\n").append(xref).append("\n%%EOF\n")
    counted.write(table.toString().toByteArray(Charsets.ISO_8859_1))
    counted.flush()
  }

  private class Entry(val offset: Long, val generation: String, val kind: Char)

  private class Counting(private val out: OutputStream) : OutputStream() {
    var count = 0L
    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    override fun flush() = out.flush()
  }

  private val OBJECT = Regex("""\A(\d+) 0 obj\s*<<(.*?)>>\s*stream\r?\n""", RegexOption.DOT_MATCHES_ALL)

  /** The stand-in's index and its dictionary when the object at [start], up to [end], is one. */
  private fun standIn(pdf: ByteArray, window: String, start: Int, end: Int): Pair<Int, String>? {
    val head = OBJECT.find(window) ?: return null
    val dictionary = head.groupValues[2]
    if (!Regex("""/Subtype\s*/Image\b""").containsMatchIn(dictionary)) return null
    if (Regex("""/Width\s+(\d+)""").find(dictionary)?.groupValues?.get(1) != "$SIDE") return null
    if (Regex("""/Height\s+(\d+)""").find(dictionary)?.groupValues?.get(1) != "$SIDE") return null
    val length = Regex("""/Length\s+(\d+)(?!\s+\d+\s+R)""").find(dictionary)?.groupValues?.get(1)?.toInt() ?: return null
    val data = start + head.range.last + 1
    if (data + length > end) return null
    val stream = pdf.copyOfRange(data, data + length)
    val pixels = if (dictionary.contains("/FlateDecode")) inflate(stream) ?: return null else stream
    if (pixels.size != SIDE * SIDE * 3 || !pixels.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)) return null
    var index = 0
    for (i in 0 until 4) index = (index shl 8) or (pixels[SIGNATURE.size + i].toInt() and 0xFF)
    return if (pixels.contentEquals(marker(index))) index to dictionary else null
  }

  private fun replace(out: OutputStream, number: Int, dictionary: String, plate: Plate) {
    // The stand-in's colour space is the one PdfDocument gave an opaque bitmap, an sRGB
    // profile with three components, which is what a JPEG of the same bitmap holds.
    val colorSpace = Regex("""/ColorSpace\s*(\[[^\]]*]|/\w+|\d+ \d+ R)""").find(dictionary)?.groupValues?.get(1) ?: "/DeviceRGB"
    val head = "$number 0 obj\n<</Type /XObject\n/Subtype /Image\n/Width ${plate.width}\n/Height ${plate.height}\n" +
      "/ColorSpace $colorSpace\n/BitsPerComponent 8\n/Filter /DCTDecode\n/Length ${plate.jpeg.length()}>> stream\n"
    out.write(head.toByteArray(Charsets.ISO_8859_1))
    plate.jpeg.inputStream().use { it.copyTo(out, 64 * 1024) }
    out.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
  }

  private fun inflate(data: ByteArray): ByteArray? = runCatching {
    val inflater = Inflater().apply { setInput(data) }
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(256)
    while (!inflater.finished() && out.size() <= SIDE * SIDE * 3) {
      val n = inflater.inflate(buffer)
      if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
      out.write(buffer, 0, n)
    }
    inflater.end()
    out.toByteArray()
  }.getOrNull()
}
