package dev.peringer.limen.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.imageio.ImageIO
import kotlin.math.abs

class PdfPlatesTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun latin(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

  private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater().apply { setInput(data); finish() }
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    return out.toByteArray()
  }

  private fun image(number: Int, data: ByteArray, flate: Boolean): ByteArray {
    val body = if (flate) deflate(data) else data
    val head = "$number 0 obj\n<</Type /XObject\n/Subtype /Image\n/Width 4\n/Height 4\n/ColorSpace [/ICCBased 9 0 R]\n/BitsPerComponent 8\n" +
      (if (flate) "/Filter /FlateDecode\n" else "") + "/Length ${body.size}>> stream\n"
    return head.toByteArray(Charsets.ISO_8859_1) + body + "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)
  }

  /** The shape PdfDocument writes: a classic table with one subsection, objects out of numeric order. */
  private fun skiaShaped(): ByteArray {
    val objects = linkedMapOf(
      1 to "1 0 obj\n<</Producer (Skia/PDF m137)>>\nendobj\n".toByteArray(Charsets.ISO_8859_1),
      4 to image(4, PdfPlates.marker(1), flate = true),
      3 to image(3, ByteArray(48) { it.toByte() }, flate = true),
      2 to image(2, PdfPlates.marker(0), flate = false),
      5 to "5 0 obj\n<</Type /Catalog>>\nendobj\n".toByteArray(Charsets.ISO_8859_1),
    )
    val out = ByteArrayOutputStream()
    out.write("%PDF-1.4\n%Óëéá\n".toByteArray(Charsets.ISO_8859_1))
    val offsets = mutableMapOf<Int, Int>()
    objects.forEach { (number, bytes) -> offsets[number] = out.size(); out.write(bytes) }
    val xref = out.size()
    out.write(("xref\n0 6\n0000000000 65535 f \n" + (1..5).joinToString("") { "%010d 00000 n \n".format(offsets.getValue(it)) } +
      "trailer\n<</Size 6\n/Root 5 0 R\n/Info 1 0 R>>\nstartxref\n$xref\n%%EOF\n").toByteArray(Charsets.ISO_8859_1))
    return out.toByteArray()
  }

  /** Noisy, so it compresses like a photograph, and written as 8-bit RGB the way Android writes an opaque bitmap. */
  private fun png(name: String, width: Int, height: Int, seed: Long, type: Int = BufferedImage.TYPE_INT_RGB): Pair<File, IntArray> {
    val random = java.util.Random(seed)
    val pixels = IntArray(width * height) { random.nextInt() and 0xFFFFFF }
    val file = tmp.newFile(name)
    ImageIO.write(BufferedImage(width, height, type).apply { setRGB(0, 0, width, height, pixels, 0, width) }, "png", file)
    return file to pixels
  }

  private fun swapped(plates: List<PdfPlates.Plate>) = ByteArrayOutputStream().also { PdfPlates.swap(skiaShaped(), plates, it) }.toByteArray()

  /** What /Predictor 15 asks a reader to do: inflate, then undo each row's PNG filter. */
  private fun decode(stream: ByteArray, width: Int, height: Int): IntArray {
    val stride = width * 3
    val raw = ByteArray((stride + 1) * height)
    Inflater().run { setInput(stream); var n = 0; while (n < raw.size && !finished()) n += inflate(raw, n, raw.size - n); end() }
    fun paeth(a: Int, b: Int, c: Int): Int { val p = a + b - c; val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c); return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
    val previous = ByteArray(stride)
    val current = ByteArray(stride)
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val row = y * (stride + 1)
      for (i in 0 until stride) {
        val x = raw[row + 1 + i].toInt() and 0xFF
        val a = if (i >= 3) current[i - 3].toInt() and 0xFF else 0
        val b = previous[i].toInt() and 0xFF
        val c = if (i >= 3) previous[i - 3].toInt() and 0xFF else 0
        current[i] = when (raw[row].toInt()) {
          0 -> x; 1 -> x + a; 2 -> x + b; 3 -> x + (a + b) / 2; 4 -> x + paeth(a, b, c)
          else -> error("Unknown PNG filter")
        }.toByte()
      }
      for (px in 0 until width) {
        pixels[y * width + px] = ((current[px * 3].toInt() and 0xFF) shl 16) or ((current[px * 3 + 1].toInt() and 0xFF) shl 8) or (current[px * 3 + 2].toInt() and 0xFF)
      }
      current.copyInto(previous)
    }
    return pixels
  }

  @Test fun everyStandInBecomesItsPlatePixelForPixelAndEveryOffsetStillPointsAtItsObject() {
    val (smallFile, small) = png("small.png", 41, 29, 1L)
    // Large enough that the PNG writer splits its pixel data over several IDAT chunks.
    val (largeFile, large) = png("large.png", 300, 200, 2L)
    assertTrue(latin(largeFile.readBytes()).split("IDAT").size > 2)
    val text = latin(swapped(listOf(PdfPlates.Plate(smallFile), PdfPlates.Plate(largeFile))))

    val xrefAt = Regex("""startxref\n(\d+)\n%%EOF\n$""").find(text)!!.groupValues[1].toInt()
    assertTrue(text.startsWith("xref\n0 6\n", xrefAt))
    val entries = Regex("""(\d{10}) (\d{5}) ([nf]) \n""").findAll(text, xrefAt).toList()
    assertEquals(6, entries.size)
    entries.drop(1).forEachIndexed { i, entry -> assertTrue("object ${i + 1}", text.startsWith("${i + 1} 0 obj\n", entry.groupValues[1].toInt())) }
    fun objectAt(number: Int): String {
      val at = entries[number].groupValues[1].toInt()
      return text.substring(at, text.indexOf("\nendstream\nendobj\n", at).takeIf { it >= 0 } ?: text.indexOf("endobj\n", at))
    }
    // Stand-in 0 is object 2, stand-in 1 is object 4.
    listOf(Triple(2, 41 to 29, small), Triple(4, 300 to 200, large)).forEach { (number, size, pixels) ->
      val (width, height) = size
      val body = objectAt(number)
      assertTrue(body, body.contains("/Width $width\n/Height $height\n/ColorSpace [/ICCBased 9 0 R]\n/BitsPerComponent 8\n"))
      assertTrue(body, body.contains("/Filter /FlateDecode\n/DecodeParms <</Predictor 15 /Colors 3 /BitsPerComponent 8 /Columns $width>>\n"))
      val length = Regex("""/Length (\d+)>> stream\n""").find(body)!!
      val stream = body.substring(length.range.last + 1).toByteArray(Charsets.ISO_8859_1)
      assertEquals(length.groupValues[1].toInt(), stream.size)
      assertArrayEquals(pixels, decode(stream, width, height))
    }
    val unrelated = entries[3].groupValues[1].toInt()
    assertEquals(latin(image(3, ByteArray(48) { it.toByte() }, flate = true)), text.substring(unrelated, text.indexOf("endobj\n", unrelated) + 7))
    assertTrue(text.contains("trailer\n<</Size 6\n/Root 5 0 R\n/Info 1 0 R>>\nstartxref\n"))
  }

  @Test fun aPlateWithoutItsStandInFailsTheExportRatherThanDropAPhotograph() {
    val plates = listOf(1L, 2L, 3L).map { PdfPlates.Plate(png("p$it.png", 8, 8, it).first) }
    assertThrows(IllegalStateException::class.java) { swapped(plates) }
  }

  @Test fun aPlateThatIsNotEightBitRgbIsRefusedRatherThanPrintedWrong() {
    val alpha = png("alpha.png", 8, 8, 4L, BufferedImage.TYPE_INT_ARGB).first
    val plain = png("plain.png", 8, 8, 5L).first
    assertThrows(IllegalArgumentException::class.java) { swapped(listOf(alpha, plain).map { PdfPlates.Plate(it) }) }
  }

  @Test fun aDocumentWithoutPlatesIsWrittenAsItCame() {
    val pdf = skiaShaped()
    assertArrayEquals(pdf, ByteArrayOutputStream().also { PdfPlates.swap(pdf, emptyList(), it) }.toByteArray())
  }

  @Test fun markersDifferByIndexAndFillTheStandIn() {
    assertEquals(PdfPlates.SIDE * PdfPlates.SIDE * 3, PdfPlates.marker(0).size)
    assertTrue(!PdfPlates.marker(0).contentEquals(PdfPlates.marker(1)))
    assertArrayEquals(PdfPlates.marker(7), PdfPlates.marker(7))
  }
}
