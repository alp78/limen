package dev.peringer.limen.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PngWriterTest {

  private fun roundTrip(width: Int, height: Int, seed: Long): Pair<IntArray, ByteArray> {
    val random = java.util.Random(seed)
    val pixels = IntArray(width * height) { (0xFF shl 24) or (random.nextInt() and 0xFFFFFF) }
    val png = ByteArrayOutputStream().also { out ->
      PngWriter.write(out, width, height) { y, row -> pixels.copyInto(row, 0, y * width, y * width + width) }
    }.toByteArray()
    return pixels to png
  }

  @Test fun everyPixelComesBackExactlyAsWritten() {
    listOf(Triple(1, 1, 1L), Triple(37, 23, 2L), Triple(300, 200, 3L)).forEach { (width, height, seed) ->
      val (pixels, png) = roundTrip(width, height, seed)
      val image = ImageIO.read(ByteArrayInputStream(png))
      assertEquals(width, image.width)
      assertEquals(height, image.height)
      val back = image.getRGB(0, 0, width, height, null, 0, width)
      assertArrayEquals("$width × $height", pixels, back)
    }
  }

  @Test fun theHeaderIsEightBitRgbWithoutInterlacing() {
    val png = roundTrip(5, 4, 4L).second
    // IHDR data: width, height, then bit depth, colour type, compression, filter, interlace.
    assertArrayEquals(byteArrayOf(0, 0, 0, 5, 0, 0, 0, 4, 8, 2, 0, 0, 0), png.copyOfRange(16, 29))
  }

  @Test fun aLargeImageSpreadsItsPixelsOverSeveralChunks() {
    val png = roundTrip(600, 500, 5L).second
    assertTrue(String(png, Charsets.ISO_8859_1).split("IDAT").size > 2)
  }
}
