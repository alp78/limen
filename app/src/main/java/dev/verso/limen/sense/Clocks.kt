package dev.verso.limen.sense

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The legal clocks of the handover, as pure date arithmetic so they can be tested against
 * a calendar on the JVM.
 *
 * The law says "immediately"; the case law (BGer 4A_388/2013) says that means 2 to 3
 * working days for the landlord's defect notice, Saturdays, Sundays and holidays not
 * counting. The tenant's move-in defect list runs on ~10 calendar days of practice; the
 * deposit's backstop is Art. 257e's one year. None of this is legal advice and every
 * face that shows these numbers says where they come from.
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
   * The landlord's defect notice should be POSTED by this date: the second working day
   * after the keys came back. Courts have accepted three; one week is already late, so
   * the instrument shows the safe edge, not the cliff.
   */
  fun exitNoticeSafeBy(keysReturned: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
    addWorkingDays(keysReturned, 2, holidays)

  /** The strict edge the case law still tolerates. */
  fun exitNoticeLatest(keysReturned: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
    addWorkingDays(keysReturned, 3, holidays)

  /** The incoming tenant's own defect list, on the customary ten days of practice. */
  fun entryListBy(moveIn: LocalDate): LocalDate = moveIn.plusDays(10)

  /**
   * Art. 257e para 3: if the landlord has asserted no claim legally within one year of
   * the end of the tenancy, the bank pays the deposit back on the tenant's demand alone.
   */
  fun depositFreeAfter(tenancyEnd: LocalDate): LocalDate = tenancyEnd.plusYears(1)

  /**
   * When the flat is actually due back: the lease's last day, shifted to the next working
   * day when it falls on a weekend or holiday.
   */
  fun returnDue(leaseEnd: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
    nextWorkingDay(leaseEnd, holidays)
}
