package dev.peringer.limen.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorTest {

  private fun rows(vararg heights: Float) = heights.map { Slot(it) }

  @Test fun aPanelThatFitsIsOneSegmentOnTheCurrentPage() {
    val segments = Paginator.segments(rows(30f, 30f, 30f), available = 100f, full = 500f)
    assertEquals(listOf(Segment(0, 3, newPageBefore = false, continued = false)), segments)
  }

  @Test fun rowsFillThePageGreedilyAndTheRestContinuesOnTheNext() {
    val segments = Paginator.segments(rows(30f, 30f, 30f, 30f, 30f), available = 95f, full = 500f)
    assertEquals(
      listOf(Segment(0, 3, newPageBefore = false, continued = false), Segment(3, 5, newPageBefore = true, continued = true)),
      segments,
    )
  }

  @Test fun aPanelThatWouldOpenWithOneRowMovesWholeToAFreshPage() {
    val segments = Paginator.segments(rows(30f, 30f, 30f), available = 40f, full = 500f)
    assertEquals(listOf(Segment(0, 3, newPageBefore = true, continued = false)), segments)
  }

  @Test fun aOneRowPanelOpensWithItsOneRowWhereItFits() {
    val segments = Paginator.segments(rows(30f), available = 40f, full = 500f)
    assertEquals(listOf(Segment(0, 1, newPageBefore = false, continued = false)), segments)
  }

  @Test fun aRowKeptWithItsSuccessorNeverEndsASegment() {
    val list = listOf(Slot(30f), Slot(30f, keepWithNext = true), Slot(30f), Slot(30f))
    val segments = Paginator.segments(list, available = 65f, full = 65f)
    assertEquals(
      listOf(
        Segment(0, 1, newPageBefore = false, continued = false),
        Segment(1, 3, newPageBefore = true, continued = true),
        Segment(3, 4, newPageBefore = true, continued = true),
      ),
      segments,
    )
  }

  @Test fun aPanelLeftWithOneRowAfterItsKeptRowsMoveOpensOnAFreshPageInstead() {
    val list = listOf(Slot(30f), Slot(30f, keepWithNext = true), Slot(30f), Slot(30f))
    assertEquals(listOf(Segment(0, 4, newPageBefore = true, continued = false)), Paginator.segments(list, available = 65f, full = 500f))
  }

  @Test fun aKeptRowStillOpensASegmentWhenNothingElseFitsOnAFreshPage() {
    val list = listOf(Slot(30f, keepWithNext = true), Slot(600f), Slot(30f))
    val segments = Paginator.segments(list, available = 500f, full = 500f)
    assertEquals(
      listOf(
        Segment(0, 1, newPageBefore = false, continued = false),
        Segment(1, 2, newPageBefore = true, continued = true),
        Segment(2, 3, newPageBefore = true, continued = true),
      ),
      segments,
    )
  }

  @Test fun aKeptGroupTallerThanAPageFillsEachPageInsteadOfLeavingOneRowOnEach() {
    val list = List(6) { Slot(30f, keepWithNext = true) } + Slot(10f)
    val segments = Paginator.segments(list, available = 100f, full = 100f)
    assertEquals(
      listOf(Segment(0, 3, newPageBefore = false, continued = false), Segment(3, 7, newPageBefore = true, continued = true)),
      segments,
    )
  }

  @Test fun aKeptGroupTallerThanAFreshPageOpensOnAFreshPageAndThenFillsThem() {
    val list = List(6) { Slot(30f, keepWithNext = true) } + Slot(10f)
    val segments = Paginator.segments(list, available = 50f, full = 100f)
    assertEquals(
      listOf(Segment(0, 3, newPageBefore = true, continued = false), Segment(3, 7, newPageBefore = true, continued = true)),
      segments,
    )
  }

  @Test fun aPanelTallerThanTwoPagesGivesThreeSegmentsTheLaterOnesContinued() {
    val segments = Paginator.segments(rows(200f, 200f, 200f, 200f, 200f), available = 500f, full = 500f)
    assertEquals(
      listOf(
        Segment(0, 2, newPageBefore = false, continued = false),
        Segment(2, 4, newPageBefore = true, continued = true),
        Segment(4, 5, newPageBefore = true, continued = true),
      ),
      segments,
    )
  }

  @Test fun aRowTallerThanAPageIsPlacedAloneOnAFreshPage() {
    // Only one row would open the panel on the current page, so it opens on a fresh one.
    val segments = Paginator.segments(rows(30f, 900f, 30f), available = 200f, full = 500f)
    assertEquals(
      listOf(
        Segment(0, 1, newPageBefore = true, continued = false),
        Segment(1, 2, newPageBefore = true, continued = true),
        Segment(2, 3, newPageBefore = true, continued = true),
      ),
      segments,
    )
  }

  @Test fun anOpeningIsTheSmallestSpaceWhereThePanelStartsOnTheCurrentPage() {
    val list = listOf(Slot(38f, keepWithNext = true), Slot(67f, keepWithNext = true), Slot(44f, keepWithNext = true), Slot(13f), Slot(33f))
    val need = Paginator.opening(list, full = 1000f)
    assertEquals(162f, need, 0f)
    assertFalse(Paginator.segments(list, need, 1000f).first().newPageBefore)
    assertTrue(Paginator.segments(list, need - 1f, 1000f).first().newPageBefore)
  }

  @Test fun anOpeningForARowTallerThanAPageIsAtLeastAPage() {
    assertTrue(Paginator.opening(listOf(Slot(900f)), full = 500f) >= 500f)
  }

  @Test fun noRowsStillGivesOneSegmentSoAnEmptyPanelDraws() {
    assertEquals(listOf(Segment(0, 0, newPageBefore = false, continued = false)), Paginator.segments(emptyList(), 100f, 500f))
    assertEquals(listOf(Segment(0, 0, newPageBefore = true, continued = false)), Paginator.segments(emptyList(), -5f, 500f))
  }

  @Test fun theSameHeightsBreakInTheSamePlacesTwice() {
    val list = rows(37f, 120f, 44f, 44f, 300f, 12f, 12f, 500f, 60f)
    assertEquals(Paginator.segments(list, 333f, 1400f), Paginator.segments(list, 333f, 1400f))
  }
}
