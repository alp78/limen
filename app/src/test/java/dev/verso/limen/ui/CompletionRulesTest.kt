package dev.verso.limen.ui

import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.Deposit
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.KeyCount
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.KeyTransferStatus
import dev.verso.limen.data.Kind
import dev.verso.limen.data.ObjectEntry
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Party
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.data.Room
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.Signature
import dev.verso.limen.data.SwissCanton
import dev.verso.limen.data.UnilateralReason
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
        )
      ).isEmpty()
    )
  }
}
