package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwissDateTest {

  @Test
  fun storedIsIsoAndShownIsSwiss() {
    assertEquals("17/08/2026", SwissDate.display("2026-08-17"))
    assertEquals("2026-08-17", SwissDate.toStored("17/08/2026"))
  }

  @Test
  fun aDayThatDoesNotExistIsNotADate() {
    assertNull(SwissDate.toStored("31/02/2026"))
    assertNull(SwissDate.toStored("32/01/2026"))
    assertNull(SwissDate.toStored("01/13/2026"))
  }

  @Test
  fun theLeapDayIsADateEveryFourthYear() {
    assertEquals("2028-02-29", SwissDate.toStored("29/02/2028"))
    assertNull(SwissDate.toStored("29/02/2027"))
  }

  @Test
  fun anIncompleteDateIsNotYetAnAnswer() {
    assertNull(SwissDate.toStored(""))
    assertNull(SwissDate.toStored("17/0"))
    assertNull(SwissDate.toStored("17/08/202"))
  }

  @Test
  fun theMaskPutsTheSeparatorsBackWhereverTypingStarted() {
    assertEquals("17/08/2026", SwissDate.mask("17082026"))
    assertEquals("17/08/2026", SwissDate.mask("17/08/2026"))
    assertEquals("17/0", SwissDate.mask("170"))
    // The separator someone reaches for on a Swiss keyboard, corrected in place.
    assertEquals("17/08/2026", SwissDate.mask("17.08.2026"))
    // Positional, never reinterpreting: what it holds is always day, month, year.
    assertEquals("20/26/0817", SwissDate.mask("2026-08-17"))
  }

  @Test
  fun whatWasNeverADatePassesThroughInsteadOfBeingLost() {
    // Older drafts let any text into these fields. Showing it back is the only honest
    // option; blanking it would delete what somebody wrote.
    assertEquals("end of the month", SwissDate.display("end of the month"))
    assertEquals("", SwissDate.display(""))
  }

  @Test
  fun timeIsTwentyFourHourAndChecked() {
    assertEquals("08:30", SwissDate.toStoredTime("0830"))
    assertEquals("23:59", SwissDate.toStoredTime("23:59"))
    assertNull(SwissDate.toStoredTime("24:00"))
    assertNull(SwissDate.toStoredTime("12:60"))
    assertNull(SwissDate.toStoredTime("8:3"))
    assertEquals("08:3", SwissDate.maskTime("083"))
  }
}
