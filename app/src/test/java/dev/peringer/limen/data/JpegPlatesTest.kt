package dev.peringer.limen.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class JpegPlatesTest {

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
      4 to image(4, JpegPlates.marker(1), flate = true),
      3 to image(3, ByteArray(48) { it.toByte() }, flate = true),
      2 to image(2, JpegPlates.marker(0), flate = false),
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

  private val first = JpegPlates.Plate(byteArrayOf(-1, -40, -1, 0x11, 0x22, -1, -39), 795, 1060)
  private val second = JpegPlates.Plate(byteArrayOf(-1, -40, -1, 0x33, -1, -39), 1413, 1060)

  @Test fun everyPlaceholderBecomesItsPlateAndEveryOffsetStillPointsAtItsObject() {
    val swapped = JpegPlates.swap(skiaShaped(), listOf(first, second))
    val text = latin(swapped)
    val xrefAt = Regex("""startxref\n(\d+)\n%%EOF\n$""").find(text)!!.groupValues[1].toInt()
    assertTrue(text.startsWith("xref\n0 6\n", xrefAt))
    val entries = Regex("""(\d{10}) (\d{5}) ([nf]) \n""").findAll(text, xrefAt).toList()
    assertEquals(6, entries.size)
    entries.drop(1).forEachIndexed { i, entry ->
      assertTrue("object ${i + 1}", text.startsWith("${i + 1} 0 obj\n", entry.groupValues[1].toInt()))
    }
    fun objectAt(number: Int): String {
      val at = entries[number].groupValues[1].toInt()
      return text.substring(at, text.indexOf("endobj\n", at))
    }
    // Placeholder 0 is object 2, placeholder 1 is object 4.
    listOf(2 to first, 4 to second).forEach { (number, plate) ->
      val body = objectAt(number)
      assertTrue(body, body.contains("/Width ${plate.width}\n/Height ${plate.height}\n"))
      assertTrue(body, body.contains("/ColorSpace [/ICCBased 9 0 R]\n"))
      assertTrue(body, body.contains("/Filter /DCTDecode\n/Length ${plate.jpeg.size}>> stream\n"))
      assertTrue(body, body.contains(latin(plate.jpeg) + "\nendstream\n"))
    }
    assertEquals(latin(image(3, ByteArray(48) { it.toByte() }, flate = true)), objectAt(3) + "endobj\n")
    assertTrue(text.contains("trailer\n<</Size 6\n/Root 5 0 R\n/Info 1 0 R>>\nstartxref\n"))
  }

  @Test fun aPlateWithoutItsPlaceholderFailsTheExportRatherThanDropAPhotograph() {
    val third = JpegPlates.Plate(byteArrayOf(1), 10, 10)
    assertThrows(IllegalStateException::class.java) { JpegPlates.swap(skiaShaped(), listOf(first, second, third)) }
  }

  @Test fun aDocumentWithoutPlatesIsLeftAsWritten() {
    val pdf = skiaShaped()
    assertSame(pdf, JpegPlates.swap(pdf, emptyList()))
  }

  @Test fun markersDifferByIndexAndFillTheStandIn() {
    assertEquals(JpegPlates.SIDE * JpegPlates.SIDE * 3, JpegPlates.marker(0).size)
    assertTrue(!JpegPlates.marker(0).contentEquals(JpegPlates.marker(1)))
    assertArrayEquals(JpegPlates.marker(7), JpegPlates.marker(7))
  }
}
