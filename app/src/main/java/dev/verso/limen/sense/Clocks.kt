package dev.verso.limen.sense

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The legal clocks of the handover, as pure date arithmetic so they can be tested against
 * a calendar on the JVM.
 *
 * The law says "immediately". BGer 4A_388/2013 reports a usual 2 to 3 working-day
 * practice, another view extending to one week, and makes the concrete circumstances
 * decisive. Ten calendar days is a frequent contractual move-in period, not a uniform
 * federal deadline. The deposit's backstop is Art. 257e's one year.
 */
object Clocks {

  fun isWorkingDay(d: LocalDate, holidays: Set<LocalDate> = emptySet()): Boolean =
    d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in holidays

  /** The next working day at or after [d]. */
  fun nextWorkingDay(d: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate {
    var x = d
    while (!isWorkingDay(x, holidays)) x = x.plusDays(1)
    return x
  }

  /** [n] working days after [d], exclusive of [d] itself. */
  fun addWorkingDays(d: LocalDate, n: Int, holidays: Set<LocalDate> = emptySet()): LocalDate {
    var x = d
    var left = n
    while (left > 0) {
      x = x.plusDays(1)
      if (isWorkingDay(x, holidays)) left--
    }
    return x
  }

  /**
   * A conservative planning target: the second working day after return. It is not a
   * statutory deadline and does not replace the duty to act immediately.
   */
  fun exitNoticeSafeBy(keysReturned: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
    addWorkingDays(keysReturned, 2, holidays)

  /** The three-working-day practice reference, not a guaranteed legal outer edge. */
  fun exitNoticeLatest(keysReturned: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
    addWorkingDays(keysReturned, 3, holidays)

  /** A common contractual ten-day period, shown only as a planning aid. */
  fun entryListBy(moveIn: LocalDate): LocalDate = moveIn.plusDays(10)

  /**
   * Art. 257e para 3: if the landlord has asserted no claim legally within one year of
   * the end of the tenancy, the bank pays the deposit back on the tenant's demand alone.
   */
  fun depositFreeAfter(tenancyEnd: LocalDate): LocalDate = tenancyEnd.plusYears(1)

  /**
   * Art. 78 CO planning aid: shift a Sunday or supplied recognized holiday, but not an
   * ordinary Saturday. The lease or another agreement can provide otherwise.
   */
  fun returnDue(leaseEnd: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate {
    var date = leaseEnd
    while (date.dayOfWeek == DayOfWeek.SUNDAY || date in holidays) date = date.plusDays(1)
    return date
  }
}
