package dev.peringer.limen.ui

import dev.peringer.limen.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InputValidationTest {
  @get:Rule val tmp = TemporaryFolder()
  private fun issues(p: Protocol) = protocolCompletionIssues(p, listOf(Role.Tenant, Role.Landlord), tmp.root, tmp.root)
  private val base get() = Protocol(id = "date-case", kind = Kind.Entry, createdEpochMs = 1,
    handoverDate = "2026-10-13", handoverTime = "12:00")

  @Test fun invalidDraftDatesAndTimesCannotPassTheGate() {
    assertTrue(FinalizationIssue.HandoverDate in issues(base.copy(handoverDate = "31/02/2026")))
    assertTrue(FinalizationIssue.HandoverTime in issues(base.copy(handoverTime = "25:90")))
    assertTrue(FinalizationIssue.Dossier in issues(base.copy(contract = Contract(startDate = "12/0"))))
    assertFalse(FinalizationIssue.HandoverDate in issues(base))
  }

  @Test fun aHalfTypedPreviousProtocolDateCannotPassTheGate() {
    // The field keeps unfinished text verbatim and the cover prints it, so it is gated like every other date.
    assertTrue(FinalizationIssue.Dossier in issues(base.copy(lastProtocolDate = "31/02/2026")))
    assertEquals("object", incompleteDossierTarget(base.copy(lastProtocolDate = "12/0")))
    assertFalse(FinalizationIssue.Dossier in issues(base.copy(lastProtocolDate = "2026-09-01")))
  }

  @Test fun partialInspectionAndNoDepositAreHonestOutcomes() {
    val p = base.copy(rooms = listOf(Room("cellar", RoomKind.Cellar)),
      dossier = listOf(DossierCard.Deposit), dossierChosen = true)
    assertTrue(FinalizationIssue.Rooms in issues(p))
    assertFalse(FinalizationIssue.Rooms in issues(p.copy(details = RecordDetails(scopeNotes = mapOf("cellar" to "Locked; no access available")))))
    assertFalse(FinalizationIssue.Dossier in issues(p))
    val unknown = p.copy(deposit = Deposit(DepositKind.Unknown))
    assertTrue(FinalizationIssue.Dossier in issues(unknown))
    val explained = unknown.copy(details = RecordDetails(scopeNotes = mapOf("Deposit" to "Certificate not available at the handover")))
    assertFalse(FinalizationIssue.Dossier in issues(explained))
  }

  @Test fun financialInputsRejectNonFiniteAndNegativeValues() {
    listOf("NaN", "Infinity", "-1", "1e309").forEach { assertNull(nonNegativeNumber(it)) }
    assertEquals(1200.5, nonNegativeNumber("1’200,50")!!, 0.0001)
    assertTrue(FinalizationIssue.Dossier in issues(base.copy(contract = Contract(monthlyRentChf = "NaN"))))
    assertThrows(IllegalArgumentException::class.java) { dev.peringer.limen.sense.Zeitwert.remainingReplacementValue(Double.POSITIVE_INFINITY, 10.0, 2.0) }
  }

  @Test fun swissWrittenAmountsPassAndAnInvalidDepositPointsAtTheDeposit() {
    listOf("2'400.-", "CHF 2’400.–", "2400.- CHF", "Fr. 2400", "2400.").forEach { assertEquals(it, 2400.0, nonNegativeNumber(it)!!, 0.0001) }
    listOf("abc", ".-", "CHF", "-2400").forEach { assertNull(it, nonNegativeNumber(it)) }
    assertFalse(FinalizationIssue.Dossier in issues(base.copy(contract = Contract(monthlyRentChf = "CHF 1'850.-"))))
    val deposit = base.copy(dossier = listOf(DossierCard.Deposit), dossierChosen = true,
      deposit = Deposit(DepositKind.BankAccount, amountChf = "two months", bank = "BCV", account = "CH93 0076 2011 6238 5295 7"))
    assertTrue(FinalizationIssue.Dossier in issues(deposit))
    assertEquals("card-Deposit", incompleteDossierTarget(deposit))
  }

  @Test fun quickDraftAcceptsSingleLetterNameAndOptionalContact() {
    assertTrue(SessionIdentity(firstName = "Ö").hasRequiredFields())
    assertTrue(SessionIdentity(firstName = "Li", addressCountry = "IE", usesSwissAddress = false).hasRequiredFields())
    assertFalse(SessionIdentity().hasRequiredFields())
    assertFalse(SessionIdentity(firstName = "Test", phone = "invalid").hasRequiredFields())
  }
}
