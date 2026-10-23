package dev.peringer.limen.ui

import dev.peringer.limen.data.Contract
import dev.peringer.limen.data.Defect
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.Deposit
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.FollowUp
import dev.peringer.limen.data.KeyCount
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalOverlayRecord
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.LEGAL_RULE_SET_ID
import dev.peringer.limen.data.MeterReading
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Party
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.RecordDetails
import dev.peringer.limen.data.ReplacementEstimate
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.Room
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.Signature
import dev.peringer.limen.data.SupportingDocument
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
      notes = mapOf(LegalRequirement.InitialRentForm to "Official form reviewed against the lease"),
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
  fun removingTheKeysCardRequiresAnExplicitScopeReason() {
    // The rule used to be the one component check that was not gated on the card. Removing
    // Keys took the only control that can record an outcome off the screen and left the
    // record permanently unfinalizable, with nothing anywhere to answer the complaint.
    val withoutKeys = complete(Kind.Entry, Role.Tenant).copy(
      dossier = emptyList(),
      dossierChosen = true,
      keys = emptyList(),
      keyTransfer = KeyTransferStatus.NotRecorded,
    )
    assertTrue(FinalizationIssue.Keys in issues(withoutKeys))
    assertTrue(issues(withoutKeys.copy(details = dev.peringer.limen.data.RecordDetails(scopeNotes = mapOf("Keys" to "Keys returned separately to the agency")))).isEmpty())
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

  /** Every section the dossier chips cover, answered and accepted by the gate. */
  private fun everySectionAnswered(): Protocol {
    val base = complete(Kind.Entry, Role.Tenant)
    return base.copy(
      dossier = DossierCard.entries.toList(),
      dossierChosen = true,
      contract = Contract(monthlyRentChf = "1850", conclusionDate = "2026-08-01"),
      deposit = Deposit(DepositKind.BankAccount, amountChf = "3700", bank = "BCV", account = "CH93 0076 2011 6238 5295 7"),
      remarks = "Keys handed over at the door",
      legalOverlay = base.legalOverlay.withStatus(LegalRequirement.DepositProof, LegalCheckStatus.Confirmed),
      details = RecordDetails(
        actualTransferDate = "2026-08-18",
        actualTransferTime = "10:30",
        readings = listOf(MeterReading("m1", label = "Electricity", identifier = "E-1", value = "4821", unit = "kWh", date = "2026-08-18", time = "10:05")),
        attachments = listOf(SupportingDocument("a1", title = "Lease", externalReference = "Folder 3")),
        followUps = listOf(FollowUp("f1", description = "Stain returns after cleaning")),
      ),
    )
  }

  private fun chips(p: Protocol): Map<String, Boolean> = mapOf(
    "object" to objectRecorded(p),
    "parties" to partiesRecorded(p),
    "transfer" to transferRecorded(p),
    "attachments" to attachmentsRecorded(p),
    "followUps" to followUpsRecorded(p),
  ) + DossierCard.entries.associate { it.name to dossierCardRecorded(p, it) }

  @Test
  fun everySectionChipTicksOnARecordTheGateAccepts() {
    // Meters here are readings only, and the legacy six dials stay empty: the card's only way in.
    val full = everySectionAnswered()
    assertTrue(issues(full).isEmpty())
    assertTrue(chips(full).toString(), chips(full).values.all { it })
  }

  @Test
  fun aChipDoesNotTickWhereTheGateRejectsItsSection() {
    val full = everySectionAnswered()
    listOf(
      "parties" to full.copy(parties = full.parties + Party(Role.Witness, id = "w1")),
      "parties" to full.copy(parties = listOf(Party(Role.Tenant, "Tenant", representedBy = "Agent"), full.parties[1])),
      "object" to full.copy(handoverTime = "14:3"),
      "object" to full.copy(lastProtocolDate = "31/02/2026"),
      "Contract" to full.copy(contract = full.contract.copy(monthlyRentChf = "abc")),
      "Deposit" to full.copy(deposit = full.deposit.copy(amountChf = "3k")),
      "Meters" to full.copy(details = full.details.copy(readings = full.details.readings.map { it.copy(time = "25:99") })),
      "Notes" to full.copy(chargesStatementBy = "31/02/2026"),
      "transfer" to full.copy(details = full.details.copy(actualTransferTime = "25:99")),
      "attachments" to full.copy(details = full.details.copy(attachments = listOf(SupportingDocument("a2")))),
      "followUps" to full.copy(details = full.details.copy(followUps = listOf(FollowUp("f2")))),
    ).forEach { (section, broken) ->
      assertEquals(section, false, chips(broken)[section])
      assertTrue(section, issues(broken).isNotEmpty())
    }
  }

  @Test
  fun aContractAnsweredOnlyByItsReasonTicksAndPasses() {
    val full = everySectionAnswered()
    val reasonOnly = full.copy(
      contract = Contract(),
      details = full.details.copy(scopeNotes = mapOf("Contract" to "Lease not available at the handover")),
    )
    assertTrue(dossierCardRecorded(reasonOnly, DossierCard.Contract))
    assertTrue(issues(reasonOnly).isEmpty())
    assertEquals(false, dossierCardRecorded(full.copy(contract = Contract()), DossierCard.Contract))
  }

  @Test
  fun aDepositAmountLeftBehindByTheKindPillDoesNotBlockFinalization() {
    // The amount field exists only for a bank account and an insurance, so text kept after the
    // pill moved to None is a value nobody can reach to correct.
    val full = everySectionAnswered()
    val none = full.copy(deposit = Deposit(DepositKind.None, amountChf = "two months"))
    assertTrue(issues(none).isEmpty())
    assertEquals("", depositAmountShown(none))
    val bank = none.copy(deposit = none.deposit.copy(kind = DepositKind.BankAccount, bank = "BCV", account = "CH93 0076 2011 6238 5295 7"))
    assertTrue(FinalizationIssue.Dossier in issues(bank))
    assertEquals("card-Deposit", incompleteDossierTarget(bank))
  }

  @Test
  fun theDossierMessageNamesOnlyWhatTheGateRejected() {
    val s = Str(Lang.En)
    val full = everySectionAnswered()
    assertEquals(emptyList<String>(), dossierCauses(full, s))
    assertEquals(listOf(s.monthlyRent), dossierCauses(full.copy(contract = full.contract.copy(monthlyRentChf = "1.200,50")), s))
    assertEquals(listOf(s.depositAmount), dossierCauses(full.copy(deposit = full.deposit.copy(amountChf = "3k")), s))
    assertEquals(listOf(s.lastProtocol), dossierCauses(full.copy(lastProtocolDate = "12/0"), s))
    assertEquals(listOf(s.contract), dossierCauses(full.copy(contract = Contract()), s))
    assertEquals(listOf(s.meters), dossierCauses(full.copy(details = full.details.copy(readings = listOf(MeterReading("m2")))), s))
    // A wear estimate with its cost left blank is the case the editor itself does not flag.
    val worn = full.copy(
      defects = listOf(Defect("d1", roomId = "r1", text = "Scratched floor", chargedToTenant = true)),
      details = full.details.copy(estimates = mapOf("d1" to ReplacementEstimate(ageYears = "3", lifeYears = "8"))),
    )
    assertEquals(listOf("${s.faceValue}: Scratched floor"), dossierCauses(worn, s))
  }

  @Test
  fun aTimeThatIsNotValidIsNamedBesideTheHandoverTimeMessage() {
    val s = Str(Lang.En)
    val full = everySectionAnswered()
    assertTrue(timeCauses(full, s).isEmpty())
    val bad = full.copy(details = full.details.copy(
      actualTransferTime = "25:99",
      readings = full.details.readings.map { it.copy(time = "9") },
    ))
    assertTrue(FinalizationIssue.HandoverTime in issues(bad))
    assertEquals(listOf(s.actualTransfer, "${s.meters}: Electricity"), timeCauses(bad, s))
  }
}
