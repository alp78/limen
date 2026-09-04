package dev.peringer.limen.ui

import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.Deposit
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.KeyCount
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalOverlayRecord
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.LEGAL_RULE_SET_ID
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Party
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.Room
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.Signature
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.data.UnilateralReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompletionRulesTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun complete(kind: Kind, role: Role, company: String = "") = Protocol(
    id = "p-${kind.name}-${role.name}-$company",
    kind = kind,
    createdEpochMs = 1L,
    sessionRole = role,
    sessionName = "Starter",
    address = "Rue Test 1",
    place = "1000 Lausanne",
    objectCanton = SwissCanton.VD,
    handoverDate = "2026-08-18",
    handoverTime = "10:00",
    legalOverlay = LegalOverlayRecord(
      checks = if (kind == Kind.Entry) {
        mapOf(
          LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed,
          LegalRequirement.InitialRentForm to LegalCheckStatus.Confirmed,
          LegalRequirement.TenantLiabilityInsurance to LegalCheckStatus.Confirmed,
          LegalRequirement.LandlordLiabilityInsurance to LegalCheckStatus.Confirmed,
        )
      } else {
        mapOf(
          LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed,
          LegalRequirement.VaudExitReadiness to LegalCheckStatus.Confirmed,
        )
      },
      ruleSetId = LEGAL_RULE_SET_ID,
    ),
    parties = listOf(Party(Role.Tenant, "Tenant"), Party(Role.Landlord, company.ifBlank { "Landlord" })),
    rooms = listOf(Room("r1", RoomKind.Room, inspected = true)),
    keys = listOf(KeyCount(KeyKind.Apartment, count = 2)),
    dossier = listOf(DossierCard.Keys),
    keyTransfer = KeyTransferStatus.AllTransferred,
    signatures = listOf(
      Signature(Role.Tenant, plane = Plane.Record, refused = true),
      Signature(Role.Landlord, plane = Plane.Record, refused = true),
    ),
  )

  private fun issues(record: Protocol): List<FinalizationIssue> = protocolCompletionIssues(
    record,
    requiredRecordRoles(record, record.sessionRole),
    tmp.newFolder("photos-${System.nanoTime()}"),
    tmp.newFolder("ink-${System.nanoTime()}"),
  )

  @Test
  fun bothHandoffsWorkForTenantPrivateLandlordAndAgency() {
    Kind.entries.forEach { kind ->
      assertTrue("tenant $kind", issues(complete(kind, Role.Tenant)).isEmpty())
      assertTrue("private landlord $kind", issues(complete(kind, Role.Landlord)).isEmpty())
      assertTrue("agency $kind", issues(complete(kind, Role.Landlord, "Example Agency SA")).isEmpty())
    }
  }

  @Test
  fun unilateralRecordRequiresItsReasonAndOnlyThePresentSideOutcome() {
    val base = complete(Kind.Exit, Role.Tenant).copy(
      oneSided = true,
      signatures = listOf(Signature(Role.Tenant, plane = Plane.Record, refused = true)),
    )
    assertEquals(listOf(Role.Tenant), requiredRecordRoles(base, null))
    assertTrue(FinalizationIssue.UnilateralReason in issues(base))
    assertTrue(issues(base.copy(unilateralReason = UnilateralReason.OtherPartyAbsent)).isEmpty())
    assertTrue(
      FinalizationIssue.RecordOutcomes in issues(
        base.copy(
          sessionRole = null,
          unilateralReason = UnilateralReason.OtherPartyAbsent,
          signatures = emptyList(),
        )
      )
    )
  }

  @Test
  fun uncheckedScopeAndInconsistentKeysBlockFinalization() {
    val base = complete(Kind.Entry, Role.Tenant)
    val incomplete = base.copy(
      rooms = base.rooms.map { it.copy(inspected = false) },
      objects = listOf(ObjectEntry("o1", ObjectGroup.Appliance, inspected = false)),
      keys = base.keys.map { it.copy(count = 0) },
    )

    val found = issues(incomplete)
    assertTrue(FinalizationIssue.Rooms in found)
    assertTrue(FinalizationIssue.Objects in found)
    assertTrue(FinalizationIssue.Keys in found)
  }

  @Test
  fun aSelectedDepositNeedsItsIdentifyingDetails() {
    val base = complete(Kind.Entry, Role.Tenant)
    val incomplete = base.copy(
      dossier = base.dossier + DossierCard.Deposit,
      deposit = Deposit(kind = DepositKind.BankAccount, amountChf = "3000"),
    )

    assertTrue(FinalizationIssue.Dossier in issues(incomplete))
    assertTrue(
      issues(
        incomplete.copy(
          deposit = incomplete.deposit.copy(bank = "Bank", account = "Account"),
          legalOverlay = incomplete.legalOverlay.withStatus(
            LegalRequirement.DepositProof,
            LegalCheckStatus.Confirmed,
          ),
        )
      ).isEmpty()
    )
  }

  @Test
  fun applicableOverlayChecksBlockFinalizationUntilTheyAreRecorded() {
    val base = complete(Kind.Exit, Role.Tenant)
    assertTrue(FinalizationIssue.LegalOverlay in issues(base.copy(legalOverlay = LegalOverlayRecord())))
    assertTrue(issues(base).isEmpty())
  }

  @Test
  fun removingTheKeysCardRemovesTheKeyRuleWithIt() {
    // The rule used to be the one component check that was not gated on the card. Removing
    // Keys took the only control that can record an outcome off the screen and left the
    // record permanently unfinalizable, with nothing anywhere to answer the complaint.
    val withoutKeys = complete(Kind.Entry, Role.Tenant).copy(
      dossier = emptyList(),
      dossierChosen = true,
      keys = emptyList(),
      keyTransfer = KeyTransferStatus.NotRecorded,
    )
    assertTrue(issues(withoutKeys).isEmpty())
  }

  @Test
  fun theKeysCardStillDemandsAnOutcomeWhileItIsOnTheRecord() {
    val unanswered = complete(Kind.Entry, Role.Tenant).copy(keyTransfer = KeyTransferStatus.NotRecorded)
    assertEquals(listOf(FinalizationIssue.Keys), issues(unanswered))
  }

  @Test
  fun theKeysChipAgreesWithTheGate() {
    // A ticked chip beside a blocked FINALIZE is the failure this pairing exists to stop.
    val noCountEntered = complete(Kind.Entry, Role.Tenant).copy(keys = emptyList())
    assertEquals(false, keyOutcomeRecorded(noCountEntered))
    assertEquals(listOf(FinalizationIssue.Keys), issues(noCountEntered))
  }

  @Test
  fun anEmptyInspectionAsksForARoomRatherThanForConfirmation() {
    val noRooms = complete(Kind.Entry, Role.Tenant).copy(rooms = emptyList())
    assertTrue(FinalizationIssue.NoRooms in issues(noRooms))
    assertTrue(FinalizationIssue.Rooms !in issues(noRooms))
  }

  @Test
  fun aOneSidedRecordWithNoKnownSideFallsBackToBothSignatures() {
    // With no side known the required list used to come back empty, and the gate then
    // demanded an outcome for a row the Sign step never drew: unfinalizable forever.
    val sideless = complete(Kind.Exit, Role.Tenant).copy(
      oneSided = true,
      unilateralReason = UnilateralReason.OtherPartyAbsent,
      sessionRole = null,
    )
    assertEquals(
      listOf(Role.Tenant, Role.Landlord),
      requiredRecordRoles(sideless, null),
    )
    assertTrue(issues(sideless).isEmpty())
  }
}
