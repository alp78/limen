package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The plate sizes: up to the full width inside the card's frame, 1020 units, or 1100 units
 * high, decoded at four pixels per three units, which is 200 dpi. The iPhone suite asserts
 * the same table, so both apps print and decode the same sizes.
 */
class AnnexPlateTest {

  private data class Row(val width: Int, val height: Int, val boxWidth: Double, val boxHeight: Double, val pixelWidth: Int, val pixelHeight: Int)

  private val table = listOf(
    Row(4032, 3024, 1020.0, 765.0, 1360, 1020),
    Row(3024, 4032, 825.0, 1100.0, 1100, 1467),
    Row(6000, 4000, 1020.0, 680.0, 1360, 907),
    Row(1920, 1080, 1020.0, 573.8, 1360, 765),
    Row(4000, 1000, 1020.0, 255.0, 1360, 340),
    Row(800, 600, 1020.0, 765.0, 800, 600),
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
