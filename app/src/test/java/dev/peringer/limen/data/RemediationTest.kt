package dev.peringer.limen.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*

class RemediationTest {
  @get:Rule val tmp = TemporaryFolder()
  private fun p(canton: SwissCanton = SwissCanton.AG) = Protocol(id = "p1", kind = Kind.Entry,
    createdEpochMs = 1, objectCanton = canton, handoverDate = "2026-10-13", contract = Contract(conclusionDate = "2026-10-01"))

  @Test fun cantonMatrixAndLocalCategories() {
    val full = setOf(SwissCanton.BE, SwissCanton.BS, SwissCanton.FR, SwissCanton.GE, SwissCanton.LU, SwissCanton.ZG, SwissCanton.ZH)
    SwissCanton.entries.forEach { canton -> assertEquals(canton.name,
      if (canton in full) InitialRentFormRule.Required else if (canton in setOf(SwissCanton.NE, SwissCanton.VD)) InitialRentFormRule.ConditionalLocal else InitialRentFormRule.NotRequired,
      p(canton).resolvedLegalOverlay()!!.initialRentFormRule) }
    assertEquals(InitialRentFormRule.Required, neInitialRentRule("Laténa", "4.5"))
    assertEquals(InitialRentFormRule.NotRequired, neInitialRentRule("Laténa", "3.5"))
    assertEquals(InitialRentFormRule.Required, neInitialRentRule("La Grande Béroche", "3"))
    assertEquals(InitialRentFormRule.NotRequired, neInitialRentRule("La Grande Béroche", "2"))
    assertEquals(InitialRentFormRule.ConditionalLocal, neInitialRentRule("Misspelt locality", "4"))
    assertEquals(InitialRentFormRule.ConditionalLocal, neInitialRentRule("Neuchâtel", "NaN"))
    assertEquals(InitialRentFormRule.NotRequired, p(SwissCanton.VD).copy(details = RecordDetails(district = "Aigle")).resolvedLegalOverlay()!!.initialRentFormRule)
    assertEquals(InitialRentFormRule.Required, p(SwissCanton.VD).copy(details = RecordDetails(district = "Lausanne")).resolvedLegalOverlay()!!.initialRentFormRule)
    assertEquals(InitialRentFormRule.VerifyCurrent, p().copy(contract = Contract(conclusionDate = "2027-01-01")).resolvedLegalOverlay()!!.initialRentFormRule)
  }

  @Test fun depositRulesAndFrozenSnapshot() {
    listOf(SwissCanton.FR, SwissCanton.GE, SwissCanton.TI).forEach { canton ->
      assertTrue(LegalRequirement.CantonalDepositHandling in p(canton).copy(deposit = Deposit(DepositKind.BankAccount)).resolvedLegalOverlay()!!.requirements)
    }
    val base = p(SwissCanton.VD).copy(details = RecordDetails(district = "Aigle"))
    val snapshot = base.freezeLegalRules()!!
    val historic = base.copy(details = base.details.copy(district = "Lausanne", legalSnapshot = snapshot))
    assertEquals(InitialRentFormRule.NotRequired, historic.resolvedLegalOverlay()!!.initialRentFormRule)
    assertTrue(snapshot.sources.any { it.contains("vd.ch") })
    val old = p(SwissCanton.FR).copy(finalizedEpochMs = 1, evidenceFingerprint = "legacy",
      deposit = Deposit(DepositKind.BankAccount), legalOverlay = LegalOverlayRecord(ruleSetId = "ch-tenancy-2026.1"))
    assertEquals("ch-tenancy-2026.1", old.resolvedLegalOverlay()!!.ruleSetId)
    assertFalse(LegalRequirement.CantonalDepositHandling in old.resolvedLegalOverlay()!!.requirements)
    val changes = base.invalidatedLegalRequirements(base.copy(deposit = Deposit(DepositKind.BankAccount)))
    assertTrue(LegalRequirement.DepositProof in changes)
    assertFalse(LegalRequirement.InitialRentForm in changes)
    assertEquals(setOf(LegalRequirement.InitialRentForm), base.invalidatedLegalRequirements(base.copy(details = base.details.copy(habitableRooms = "4"))))
  }

  @Test fun storedRecordPinsDefaultsTheFingerprintCovers() {
    // The canonical form hashes defaults while the store omits them, so a default left out of
    // the file would be re-read as whatever a later release declares, and fail the fingerprint.
    val root = tmp.newFolder()
    Store(root).save(p().copy(details = RecordDetails(legalSnapshot = p().freezeLegalRules(),
      attachments = listOf(SupportingDocument(id = "a1")), estimates = mapOf("d1" to ReplacementEstimate()))))
    val stored = File(root, "protocols/p1.json").readText()
    listOf("\"checkedOn\"", "\"mime\"", "\"source\"").forEach { assertTrue(it, it in stored) }
  }

  @Test fun staleSaveCannotReplaceLatestAndCorruptCopyIsRecoverable() {
    val root = tmp.newFolder(); val store = Store(root)
    val first = p().copy(address = "first", contentRevision = 1, saveSequence = 1)
    val latest = first.copy(address = "latest", contentRevision = 2, saveSequence = 2)
    store.save(first); store.save(latest); store.save(first)
    assertEquals("latest", store.load("p1")!!.address)
    File(root, "protocols/p1.json").writeText("broken")
    assertEquals(listOf(Store.UnreadableRecord("p1", true)), store.catalogue().unreadable)
    assertTrue(store.recover("p1"))
    assertEquals("first", store.load("p1")!!.address)
    assertTrue(File(root, "protocols").listFiles()!!.any { it.extension == "quarantine" && it.readText() == "broken" })
  }

  @Test fun scopedArchiveRepairsOnlyCorruptRecordsAndPreservesDamagedFiles() {
    val root = tmp.newFolder(); val store = Store(root)
    store.save(p()); store.save(p().copy(id = "p2"))
    File(store.photoDir("p1"), "orphan.jpg").writeText("private discarded evidence")
    val out = ByteArrayOutputStream(); store.exportArchive(charArrayOf(), out, setOf("p1"))
    val otherRoot = tmp.newFolder(); val other = Store(otherRoot)
    other.save(p()); File(otherRoot, "protocols/p1.json").writeText("corrupt original")
    assertEquals(1, other.importArchive(charArrayOf(), ByteArrayInputStream(out.toByteArray())).repairable)
    assertEquals(1, other.importArchive(charArrayOf(), ByteArrayInputStream(out.toByteArray()), repairCorrupt = true).repaired)
    assertNull(other.load("p2"))
    assertFalse(File(other.photoDir("p1"), "orphan.jpg").exists())
    assertTrue(File(otherRoot, "recovery").walkTopDown().any { it.isFile && it.readText() == "corrupt original" })
    assertEquals(1, other.importArchive(charArrayOf(), ByteArrayInputStream(out.toByteArray()), repairCorrupt = true).skippedExisting)
  }

  @Test fun metadataAndExportsDoNotChangeSignedEvidence() {
    val photos = tmp.newFolder(); val ink = tmp.newFolder()
    val base = p().copy(finalizedEpochMs = 1)
    val before = ProtocolIntegrity.fingerprint(base, photos, ink)
    val after = base.copy(deliveryRevision = 2, saveSequence = 100,
      exportHistory = listOf(ExportCopy("content://test", "record.pdf", 10, "Fr", 1, 100)))
    assertEquals(before, ProtocolIntegrity.fingerprint(after, photos, ink))
    assertNotEquals(before, ProtocolIntegrity.fingerprint(base.copy(details = RecordDetails(actualTransferDate = "2026-10-14")), photos, ink))
  }

  @Test fun coTenantsHaveIndependentOutcomesAndDelivery() {
    val one = Party(Role.Tenant, "A", id = "a")
    val two = Party(Role.Tenant, "B", id = "b")
    val owner = Party(Role.Landlord, "Owner", id = "owner", representedBy = "Agent", agency = "Agency", authority = "Written mandate")
    val record = p().copy(parties = listOf(one, two, owner),
      signatures = listOf(Signature(Role.Tenant, plane = Plane.Record, participantId = "a", refused = true)),
      deliveryReceipts = listOf(DeliveryReceipt(Role.Tenant, participantId = "a"), DeliveryReceipt(Role.Landlord, participantId = "owner")))
    assertNotNull(record.signatureFor(one)); assertNull(record.signatureFor(two))
    assertEquals("Agent", owner.signerName())
    assertFalse(record.deliveryComplete())
    assertTrue(record.copy(deliveryReceipts = record.deliveryReceipts + DeliveryReceipt(Role.Tenant, participantId = "b")).deliveryComplete())
    val exitParties = record.partiesForExit(Role.Tenant, SessionIdentity(firstName = "A", street = "New street", city = "Bern"))
    assertEquals(listOf("A", "B", "Owner"), exitParties.map { it.name })
    assertTrue(exitParties.first().address.contains("New street"))
    assertEquals("", exitParties[1].address)
    val agencyExit = record.partiesForExit(Role.Landlord, SessionIdentity(firstName = "New", lastName = "Agent", company = "Agency"))
    assertEquals("Owner", agencyExit.last().name)
    assertEquals("New Agent", agencyExit.last().representedBy)
    assertEquals("", agencyExit.last().authority)
  }
}
