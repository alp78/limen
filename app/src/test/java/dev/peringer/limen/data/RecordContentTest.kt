package dev.peringer.limen.data

import dev.peringer.limen.data.pdf.Chip
import dev.peringer.limen.data.pdf.Chips
import dev.peringer.limen.data.pdf.participantPositionLines
import dev.peringer.limen.sense.LifeKey
import dev.peringer.limen.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * What the supplemental sections of the exported record print. The paper states what is true
 * of the record as it stands, so a value whose input has gone from the screen must not stay
 * on it, and a label is always the document's own language.
 */
class RecordContentTest {

  private val en = Str(Lang.En)

  /** Every component removed but Keys, so no stand-in reason for it is expected on the paper. */
  private val draft = Protocol(
    id = "rc", kind = Kind.Exit, createdEpochMs = 1L,
    dossier = listOf(DossierCard.Keys), dossierChosen = true,
  )

  private fun withNotes(p: Protocol, vararg notes: Pair<String, String>) =
    p.copy(details = p.details.copy(scopeNotes = mapOf(*notes)))

  private fun reasons(p: Protocol, s: Str = en): List<String> =
    extraContent(p, s).firstOrNull { it.first == s.notInspectedReasons }?.second.orEmpty()

  private fun legal(p: Protocol): String =
    extraContent(p, en).firstOrNull { it.first == en.legalOverlayTitle }?.second.orEmpty().joinToString("\n")

  @Test
  fun aRoomReasonPrintsOnlyWhileTheRoomIsNotInspected() {
    val p = withNotes(
      draft.copy(rooms = listOf(Room("k", RoomKind.Kitchen), Room("b", RoomKind.Bathroom, inspected = true))),
      "k" to "No access to the kitchen", "b" to "Tenant refused entry",
    )
    assertEquals(listOf("${en.room(RoomKind.Kitchen)}: No access to the kitchen"), reasons(p))
  }

  @Test
  fun aSuppliedItemReasonPrintsOnlyWhileTheItemIsNotInspected() {
    val p = withNotes(
      draft.copy(objects = listOf(
        ObjectEntry("o1", ObjectGroup.Other, label = "Garden shed"),
        ObjectEntry("o2", ObjectGroup.Other, label = "Cellar shelving", inspected = true),
      )),
      "o1" to "Locked", "o2" to "Key missing",
    )
    assertEquals(listOf("Garden shed: Locked"), reasons(p))
  }

  @Test
  fun aReasonForAComponentPrintsOnlyWhileThatComponentIsOnTheRecord() {
    val notes = arrayOf("Contract" to "Lease not at hand", "Deposit" to "No certificate", "Meters" to "Dials sealed")
    val withDeposit = withNotes(draft.copy(dossier = listOf(DossierCard.Deposit, DossierCard.Keys)), *notes)
    assertEquals(listOf("${en.dossierCard(DossierCard.Deposit)}: No certificate"), reasons(withDeposit))
    val withoutDeposit = withNotes(draft.copy(dossier = listOf(DossierCard.Contract, DossierCard.Meters, DossierCard.Keys)), *notes)
    assertEquals(
      listOf("${en.dossierCard(DossierCard.Contract)}: Lease not at hand", "${en.dossierCard(DossierCard.Meters)}: Dials sealed"),
      reasons(withoutDeposit),
    )
  }

  @Test
  fun theKeysReasonStandsInForARemovedKeysCardAndHidesWhenTheCardIsBack() {
    val note = "Keys" to "Handed over separately"
    val removed = withNotes(draft.copy(dossier = emptyList()), note)
    assertEquals(listOf("${en.dossierCard(DossierCard.Keys)}: Handed over separately"), reasons(removed))
    assertTrue(reasons(withNotes(draft, note)).isEmpty())
  }

  @Test
  fun theInspectionReasonPrintsInTheDocumentLanguageAndOnlyForARecordWithoutRooms() {
    val de = Str(Lang.De)
    val noRooms = withNotes(draft, "Inspection" to "No access to the flat")
    assertEquals(listOf("${de.stepInspection}: No access to the flat"), reasons(noRooms, de))
    assertFalse(reasons(noRooms, de).single().contains("Inspection"))
    assertTrue(reasons(noRooms.copy(rooms = listOf(Room("k", RoomKind.Kitchen, inspected = true))), de).isEmpty())
  }

  @Test
  fun aReasonKeyThatMatchesNothingOnTheRecordIsNotPrintedAsAKey() {
    val p = withNotes(draft, "cellar" to "Locked")
    assertTrue(reasons(p).isEmpty())
    assertTrue(extraContent(p, en).none { it.first == en.notInspectedReasons })
  }

  @Test
  fun theWearEstimateNamesItsLifespanRowInTheDocumentLanguage() {
    val fr = Str(Lang.Fr)
    fun line(basis: String): String {
      val estimate = ReplacementEstimate(replacementChf = "400", ageYears = "3", lifeYears = "8", basis = basis)
      val p = draft.copy(defects = listOf(Defect("d1")), details = RecordDetails(estimates = mapOf("d1" to estimate)))
      return extraContent(p, fr).first { it.first == fr.faceValue }.second.single()
    }
    val picked = line(LifeKey.DispersionPaint.name)
    assertTrue(picked.startsWith("${fr.defects} 1: ${fr.life(LifeKey.DispersionPaint)}\n"))
    assertFalse(picked.contains(LifeKey.DispersionPaint.name))
    assertTrue(line("").startsWith("${fr.defects} 1: ${fr.customLifespan}\n"))
    assertTrue(line("Older wording").startsWith("${fr.defects} 1: Older wording\n"))
  }

  @Test
  fun theNeuchatelFactsPrintOnlyForNeuchatelAndTheDistrictOnlyForVaud() {
    val facts = RecordDetails(municipality = "Neuchâtel", habitableRooms = "4", district = "Lausanne")
    val ne = legal(draft.copy(objectCanton = SwissCanton.NE, details = facts))
    assertTrue(ne.contains("Neuchâtel"))
    assertFalse(ne.contains("Lausanne"))
    val vd = legal(draft.copy(objectCanton = SwissCanton.VD, details = facts))
    assertTrue(vd.contains("Lausanne"))
    assertFalse(vd.contains("Neuchâtel"))
    val ge = legal(draft.copy(objectCanton = SwissCanton.GE, details = facts))
    assertFalse(ge.contains("Neuchâtel"))
    assertFalse(ge.contains("Lausanne"))
  }

  @Test
  fun theFollowUpDeadlineUsesThePrintedLabelNotTheInputPrompt() {
    val p = draft.copy(details = RecordDetails(followUps = listOf(FollowUp("f1", description = "Mould", deadline = "2026-03-12"))))
    val text = extraContent(p, en).first { it.first == en.followUps }.second.single()
    assertTrue(text.contains("${en.repairDeadline}: 12/03/2026"))
    assertFalse(text.contains("(date)"))
  }

  @Test
  fun thePrintedLabelsCarryNoInstructionToThePersonTyping() {
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      assertTrue("$lang repair deadline", s.repairDeadline.isNotBlank() && !s.repairDeadline.contains("("))
      assertTrue("$lang reasons heading", s.notInspectedReasons.isNotBlank() && !s.notInspectedReasons.contains(":"))
      assertTrue("$lang meter identifier", s.meterIdentifier.isNotBlank() && !s.meterIdentifier.contains("("))
    }
    assertEquals("Repair deadline", en.repairDeadline)
  }

  @Test
  fun theRomanshDepositReleaseNamesTheLandlordAndWhenTheYearStarts() {
    val rm = Str(Lang.Rm).releaseBank
    assertTrue(rm.contains("Sche il locatur"))
    assertTrue(rm.contains("suenter la fin da la locaziun"))
    assertTrue(rm.contains("da la banca"))
  }

  @Test
  fun aForeignCountryIsNamedInTheLanguageOfTheDocument() {
    val berlin = SessionIdentity(postalCode = "10115", city = "Berlin", addressCountry = "DE", usesSwissAddress = false)
    assertEquals("10115 Berlin Deutschland", berlin.placeLine(Locale.GERMAN))
    assertEquals("10115 Berlin Allemagne", berlin.placeLine(Locale.FRENCH))
    val zurich = SessionIdentity(postalCode = "8001", city = "Zürich")
    assertEquals("8001 Zürich", zurich.placeLine(Locale.FRENCH))
  }

  private fun tenant(id: String, name: String) = Party(Role.Tenant, name, id = id)

  private val positions = mapOf(
    "t1" to TenantPosition(TenantResponse.Agrees),
    "t2" to TenantPosition(TenantResponse.Disputes, "Not caused by me"),
    "gone" to TenantPosition(TenantResponse.Disputes, "Left behind"),
  )

  @Test
  fun eachTenantStillOnTheRecordPrintsItsOwnPositionAndARemovedOneNeverPrintsUnderItsId() {
    val p = draft.copy(parties = listOf(tenant("t1", "Anna"), tenant("t2", "Beat"), Party(Role.Landlord, "Lena")))
    val lines = participantPositionLines(p, Defect("d1", participantPositions = positions), en)
    assertEquals(
      listOf("Anna: ${en.tenantResponse(TenantResponse.Agrees)}\n", "Beat: ${en.tenantResponse(TenantResponse.Disputes)}\nNot caused by me"),
      lines,
    )
    assertTrue(lines.none { it.contains("gone") || it.contains("Left behind") })
  }

  @Test
  fun aLoneTenantsPositionsStayOffThePaperBecauseTheEditorHidesThem() {
    val p = draft.copy(parties = listOf(tenant("t1", "Anna"), Party(Role.Landlord, "Lena")))
    assertTrue(participantPositionLines(p, Defect("d1", participantPositions = positions), en).isEmpty())
  }

  @Test
  fun theOriginalTenantsPositionPrintsUnderTheirNameNotTheLegacyKey() {
    val first = Party(Role.Tenant, "Anna")
    val p = draft.copy(parties = listOf(first, tenant("t2", "Beat")))
    val defect = Defect("d1", participantPositions = mapOf(first.participantKey() to TenantPosition(TenantResponse.NormalWear)))
    assertEquals(listOf("Anna: ${en.tenantResponse(TenantResponse.NormalWear)}\n"), participantPositionLines(p, defect, en))
  }

  @Test
  fun theNotificationClausePrintsForAnEntryOnlyBecauseOnlyAnEntryEditsIt() {
    val details = RecordDetails(notificationClause = "Art. 12 du bail", notificationDays = "30")
    fun transfer(p: Protocol) = extraContent(p, en).firstOrNull { it.first == en.actualTransfer }?.second.orEmpty().joinToString("\n")
    assertTrue(transfer(draft.copy(kind = Kind.Entry, details = details)).contains("Art. 12 du bail"))
    assertEquals("", transfer(draft.copy(kind = Kind.Exit, details = details)))
  }

  @Test
  fun aChipSpeaksTheSameWordsInHtmlUnlessItIsGivenOthers() {
    assertEquals("Photo annex", Chip("Photo annex").html)
    assertEquals("Photo annex", Chip("Photo annex: Page 12", html = "Photo annex").html)
  }

  /** Rooms.kt, which sets the annex chip, needs a PdfDocument and cannot run on the JVM; this drives the block it fills. */
  @Test
  fun theHtmlCopyOfAChipRowCarriesNoPageNumberIntoAFileWithoutPages() {
    val out = java.io.ByteArrayOutputStream()
    Chips(listOf(Chip("Photos: 2"), Chip("Photo annex: Page 12", html = "Photo annex"))).html(HtmlCompanion("en", "t", out))
    val html = out.toString(Charsets.UTF_8.name())
    assertTrue(html.contains("<li class=\"chip\">Photos: 2</li>"))
    assertTrue(html.contains("<li class=\"chip\">Photo annex</li>"))
    assertFalse(html.contains("Page 12"))
  }
}
