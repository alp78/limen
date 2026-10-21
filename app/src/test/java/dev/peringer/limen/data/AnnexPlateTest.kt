package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The table the iPhone suite asserts as well: both apps must print and decode the same sizes. */
class AnnexPlateTest {

  private data class Row(val width: Int, val height: Int, val boxWidth: Double, val boxHeight: Double, val pixelWidth: Int, val pixelHeight: Int)

  private val table = listOf(
    Row(4032, 3024, 706.7, 530.0, 1413, 1060),
    Row(3024, 4032, 397.5, 530.0, 795, 1060),
    Row(6000, 4000, 795.0, 530.0, 1590, 1060),
    Row(1920, 1080, 942.2, 530.0, 1884, 1060),
    Row(4000, 1000, 1060.0, 265.0, 2120, 530),
    Row(800, 600, 706.7, 530.0, 800, 600),
  )

  @Test fun everyRowOfTheSharedTableHoldsLongEdgeExactAndShortEdgeWithinOnePixel() {
    table.forEach { row ->
      val fit = AnnexPlate.fit(row.width, row.height)
      val label = "${row.width} × ${row.height}"
      assertEquals("$label box width", row.boxWidth, fit.boxWidth.toDouble(), 0.05)
      assertEquals("$label box height", row.boxHeight, fit.boxHeight.toDouble(), 0.05)
      val landscape = row.width >= row.height
      val (long, short) = if (landscape) fit.pixelWidth to fit.pixelHeight else fit.pixelHeight to fit.pixelWidth
      val (expectedLong, expectedShort) = if (landscape) row.pixelWidth to row.pixelHeight else row.pixelHeight to row.pixelWidth
      assertEquals("$label long edge", expectedLong, long)
      assertTrue("$label short edge $short against $expectedShort", abs(short - expectedShort) <= 1)
    }
  }

  @Test fun aPictureSmallerThanItsBoxIsNeverEnlarged() {
    val fit = AnnexPlate.fit(640, 480)
    assertEquals(640, fit.pixelWidth)
    assertEquals(480, fit.pixelHeight)
  }
}
