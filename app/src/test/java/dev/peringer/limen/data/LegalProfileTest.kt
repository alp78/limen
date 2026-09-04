package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalProfileTest {

  private fun protocol(canton: SwissCanton?, date: String, incorporated: Boolean = false) =
    Protocol(
      id = "p1",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      objectCanton = canton,
      handoverDate = date,
      romandFrameworkInLease = incorporated,
    )

  @Test
  fun vaudRulvSelectsThePreviousAndCurrentBindingPeriods() {
    assertEquals(LegalProfile.FederalAndLease, protocol(SwissCanton.VD, "2020-06-30").legalProfile())
    assertEquals(LegalProfile.VaudRulv2020, protocol(SwissCanton.VD, "2020-07-01").legalProfile())
    assertEquals(LegalProfile.VaudRulv2020, protocol(SwissCanton.VD, "2026-06-30").legalProfile())
    assertEquals(LegalProfile.VaudRulv2026, protocol(SwissCanton.VD, "2026-07-01").legalProfile())
    assertEquals(LegalProfile.VaudRulv2026, protocol(SwissCanton.VD, "2032-06-30").legalProfile())
    assertEquals(LegalProfile.FederalAndLease, protocol(SwissCanton.VD, "2032-07-01").legalProfile())
  }

  @Test
  fun romandFrameworkIsContractualOutsideThatRule() {
    assertTrue(SwissCanton.JU.supportsRomandFramework())
    assertFalse(SwissCanton.ZH.supportsRomandFramework())
    assertEquals(
      LegalProfile.RomandContractual,
      protocol(SwissCanton.JU, "2026-08-18", incorporated = true).legalProfile(),
    )
    assertEquals(
      LegalProfile.FederalAndLease,
      protocol(SwissCanton.JU, "2026-08-18").legalProfile(),
    )
  }
}
