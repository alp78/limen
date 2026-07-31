package dev.verso.limen.sense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ClocksTest {

  @Test
  fun weekendDoesNotCount() {
    // Keys back on a Friday: the two safe working days are Monday and Tuesday.
    val friday = LocalDate.of(2026, 7, 31)
    assertEquals(LocalDate.of(2026, 8, 4), Clocks.exitNoticeSafeBy(friday))
    assertEquals(LocalDate.of(2026, 8, 5), Clocks.exitNoticeLatest(friday))
  }

  @Test
  fun midweekRunsStraightThrough() {
    val monday = LocalDate.of(2026, 8, 3)
    assertEquals(LocalDate.of(2026, 8, 5), Clocks.exitNoticeSafeBy(monday))
  }

  @Test
  fun holidaysAreSkippedWhenGiven() {
    // Keys back Thursday July 30, Friday July 31 is a holiday (as in Romandie 2026):
    // the two working days are Monday and Tuesday.
    val thursday = LocalDate.of(2026, 7, 30)
    val holidays = setOf(LocalDate.of(2026, 7, 31))
    assertEquals(LocalDate.of(2026, 8, 4), Clocks.exitNoticeSafeBy(thursday, holidays))
  }

  @Test
  fun returnDueShiftsOffTheWeekend() {
    // A lease ending Saturday is returnable the next working day.
    val saturday = LocalDate.of(2026, 8, 1)
    assertEquals(LocalDate.of(2026, 8, 3), Clocks.returnDue(saturday))
    // A weekday stays put.
    val wednesday = LocalDate.of(2026, 8, 5)
    assertEquals(wednesday, Clocks.returnDue(wednesday))
  }

  @Test
  fun entryListRunsTenCalendarDays() {
    assertEquals(LocalDate.of(2026, 8, 10), Clocks.entryListBy(LocalDate.of(2026, 7, 31)))
  }

  @Test
  fun depositBackstopIsOneYear() {
    assertEquals(LocalDate.of(2027, 9, 30), Clocks.depositFreeAfter(LocalDate.of(2026, 9, 30)))
  }

  @Test
  fun workingDayPredicate() {
    assertTrue(Clocks.isWorkingDay(LocalDate.of(2026, 8, 3)))
    assertFalse(Clocks.isWorkingDay(LocalDate.of(2026, 8, 2)))
    assertFalse(
      Clocks.isWorkingDay(LocalDate.of(2026, 8, 3), setOf(LocalDate.of(2026, 8, 3)))
    )
  }
}
