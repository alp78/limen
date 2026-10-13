package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalOverlayTest {

  private fun protocol(
    canton: SwissCanton,
    date: String = "2026-08-18",
    kind: Kind = Kind.Entry,
    depositKind: DepositKind = DepositKind.None,
  ) = Protocol(
    id = "p-${canton.code}-$date-$kind",
    kind = kind,
    createdEpochMs = 1L,
    objectCanton = canton,
    handoverDate = date,
    contract = Contract(conclusionDate = date),
    deposit = Deposit(kind = depositKind),
  )

  @Test
  fun initialRentFormUsesOnlyTheDated2026OfficialSnapshot() {
    listOf(
      SwissCanton.BE,
      SwissCanton.BS,
      SwissCanton.FR,
      SwissCanton.GE,
      SwissCanton.LU,
      SwissCanton.ZG,
      SwissCanton.ZH,
    ).forEach { canton ->
      assertEquals(InitialRentFormRule.Required, protocol(canton).resolvedLegalOverlay()?.initialRentFormRule)
    }
    listOf(SwissCanton.NE, SwissCanton.VD).forEach { canton ->
      assertEquals(
        InitialRentFormRule.ConditionalLocal,
        protocol(canton).resolvedLegalOverlay()?.initialRentFormRule,
      )
    }
    assertEquals(InitialRentFormRule.NotRequired, protocol(SwissCanton.AG).resolvedLegalOverlay()?.initialRentFormRule)
    assertEquals(
      InitialRentFormRule.VerifyCurrent,
      protocol(SwissCanton.ZH, date = "2027-01-01").resolvedLegalOverlay()?.initialRentFormRule,
    )
  }

  @Test
  fun initialRentFormUsesLeaseConclusionDateNotTheLaterHandover() {
    val handoverIn2027 = protocol(SwissCanton.ZH, date = "2027-01-08").copy(
      contract = Contract(conclusionDate = "2026-12-14"),
    )
    assertEquals(
      InitialRentFormRule.Required,
      handoverIn2027.resolvedLegalOverlay()?.initialRentFormRule,
    )

    val unknownConclusion = handoverIn2027.copy(contract = Contract())
    assertEquals(
      InitialRentFormRule.VerifyCurrent,
      unknownConclusion.resolvedLegalOverlay()?.initialRentFormRule,
    )
  }

  @Test
  fun vaudEntryAndExitReceiveDifferentChecksWithoutForkingTheFlow() {
    val entry = protocol(SwissCanton.VD).resolvedLegalOverlay()!!
    assertEquals(
      listOf(
        LegalRequirement.LeaseAndLocalTiming,
        LegalRequirement.InitialRentForm,
        LegalRequirement.TenantLiabilityInsurance,
        LegalRequirement.LandlordLiabilityInsurance,
      ),
      entry.requirements,
    )

    val withDeposit = protocol(
      SwissCanton.VD,
      depositKind = DepositKind.BankAccount,
    ).resolvedLegalOverlay()!!
    assertTrue(LegalRequirement.DepositProof in withDeposit.requirements)

    val exit = protocol(SwissCanton.VD, kind = Kind.Exit).resolvedLegalOverlay()!!
    assertEquals(
      listOf(LegalRequirement.LeaseAndLocalTiming, LegalRequirement.VaudExitReadiness),
      exit.requirements,
    )
  }

  @Test
  fun missingAndExceptionalOutcomesStayRecordableButNeedAnExplanation() {
    val overlay = protocol(SwissCanton.AG, kind = Kind.Exit).resolvedLegalOverlay()!!
    assertFalse(overlay.isComplete(LegalOverlayRecord()))
    val disputed = LegalOverlayRecord().withStatus(
      LegalRequirement.LeaseAndLocalTiming,
      LegalCheckStatus.MissingOrDisputed,
    )
    assertFalse(overlay.isComplete(disputed))
    assertFalse(overlay.isComplete(disputed.copy(note = "An unrelated general note.")))
    assertTrue(overlay.isComplete(disputed.copy(notes = mapOf(LegalRequirement.LeaseAndLocalTiming to "Lease gives a different appointment."))))
    assertTrue(
      overlay.isComplete(
        LegalOverlayRecord().withStatus(
          LegalRequirement.LeaseAndLocalTiming,
          LegalCheckStatus.Confirmed,
        )
      )
    )
  }
}
