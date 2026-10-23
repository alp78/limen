package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun fullProtocol(id: String = "p1") = Protocol(
    id = id,
    kind = Kind.Exit,
    style = GradeStyle.Grades,
    address = "Rue du Simplon 12",
    place = "1006 Lausanne",
    objectDetail = "3e étage gauche",
    handoverDate = "2026-09-30",
    handoverTime = "10:00",
    createdEpochMs = 1_000_000L,
    parties = listOf(
      Party(Role.Tenant, "A. Locataire", address = "Nouvelle adresse 1, 1200 Genève"),
      Party(Role.Landlord, "Régie Exemple SA", representedBy = "B. Gérant"),
      Party(Role.Witness, "C. Témoin"),
    ),
    deposit = Deposit(
      kind = DepositKind.Insurance,
      amountChf = "4500",
      provider = "SwissCaution",
      policyNo = "SC-123456",
    ),
    meters = Meters(electricity1 = "48214.7", water = "1210.3"),
    keys = listOf(
      KeyCount(KeyKind.Apartment, count = 3),
      KeyCount(KeyKind.Mailbox, count = 1),
      KeyCount(KeyKind.Other, label = "Buanderie", count = 1),
    ),
    selfMadeKeys = true,
    rooms = listOf(
      Room(
        id = "r1",
        kind = RoomKind.Kitchen,
        items = Templates.itemsFor(RoomKind.Kitchen).map { RoomItem(it, grade = Grade.Ok) },
        keysCount = 1,
        freshPaintedWalls = true,
      ),
      Room(id = "r2", kind = RoomKind.Room, name = "Chambre 2"),
    ),
    objects = listOf(
      ObjectEntry(
        id = "o1",
        group = ObjectGroup.Appliance,
        catalogue = Appliance.Fridge.name,
        roomId = "r1",
        note = "Bosch, no de série 4471",
        photos = listOf("o1-1.jpg"),
        photoEvidence = listOf(PhotoEvidence("o1-1.jpg", sha256 = "AB", source = PhotoSource.Camera)),
      ),
      ObjectEntry(id = "o2", group = ObjectGroup.Other, groupLabel = "Jardin", label = "Tondeuse"),
    ),
    contract = Contract(
      monthlyRentChf = "1890",
      serviceChargesChf = "220",
      startDate = "2019-04-01",
      endDate = "2026-09-30",
      noticeDate = "2026-06-28",
    ),
    dossier = listOf(DossierCard.Contract, DossierCard.Deposit, DossierCard.Meters, DossierCard.Keys),
    defects = listOf(
      Defect(
        id = "d1",
        roomId = "r1",
        item = ItemKey.Worktop,
        text = "Brûlure ronde d'env. 2 cm près de la cuisinière",
        photos = listOf("d1-1.jpg", "d1-2.jpg"),
        chargedToTenant = true,
        fixBy = FixBy.Landlord,
        costNote = "Sous réserve d'un devis",
      ),
      Defect(
        id = "d2",
        objectId = "o1",
        item = ItemKey.Custom,
        itemLabel = "Joint de porte",
        text = "Joint fendu sur 4 cm",
      ),
    ),
    remarks = "Signature du locataire: ne vaut pas reconnaissance de dette.",
    chargesStatementBy = "2027-03-31",
    signatures = listOf(
      Signature(Role.Tenant, "A. Locataire", Plane.Record, file = "tenant-record.png", epochMs = 5L),
      Signature(Role.Landlord, "B. Gérant", Plane.Record, file = "landlord-record.png", epochMs = 6L),
      Signature(Role.Tenant, "A. Locataire", Plane.Costs, refused = true, reservation = "Conteste le montant"),
    ),
  )

  @Test
  fun roundTripKeepsEverything() {
    val store = Store(tmp.root)
    val p = fullProtocol()
    store.save(p)
    assertEquals(p, store.load("p1"))
  }

  @Test
  fun listSortsNewestFirst() {
    val store = Store(tmp.root)
    store.save(fullProtocol("old").copy(createdEpochMs = 1))
    store.save(fullProtocol("new").copy(createdEpochMs = 2))
    assertEquals(listOf("new", "old"), store.list().map { it.id })
  }

  @Test
  fun unknownFieldsFromTheFutureAreSkipped() {
    val store = Store(tmp.root)
    store.save(fullProtocol())
    val f = File(File(tmp.root, "protocols"), "p1.json")
    // A version-2 writer adds a field this reader has never heard of.
    f.writeText(f.readText().replaceFirst("{", "{\n  \"fromTheFuture\": {\"x\": 1},"))
    assertNotNull(store.load("p1"))
  }

  @Test
  fun corruptFileIsRefusedQuietlyAndDoesNotPoisonTheList() {
    val store = Store(tmp.root)
    store.save(fullProtocol())
    File(File(tmp.root, "protocols"), "broken.json").writeText("{ not json")
    assertEquals(1, store.list().size)
    assertNull(store.load("broken"))
  }

  @Test
  fun deleteTakesPhotosAndInkWithIt() {
    val store = Store(tmp.root)
    store.save(fullProtocol())
    File(store.photoDir("p1"), "d1-1.jpg").writeText("x")
    File(store.signatureDir("p1"), "tenant-record.png").writeText("x")
    store.delete("p1")
    assertNull(store.load("p1"))
    assertTrue(store.photoDir("p1").listFiles().isNullOrEmpty())
    assertTrue(store.signatureDir("p1").listFiles().isNullOrEmpty())
  }

  @Test
  fun signedMeansRecordPlaneAndRefusalDoesNotCount() {
    val p = fullProtocol()
    assertTrue(p.isSigned())
    val refusedOnly = p.copy(
      signatures = listOf(Signature(Role.Tenant, plane = Plane.Record, refused = true))
    )
    assertTrue(!refusedOnly.isSigned())
  }

  @Test
  fun aRecoveryThatCannotBeWrittenLeavesTheUnreadableRecordListedForAnotherTry() {
    val store = Store(tmp.root)
    val protocols = File(tmp.root, "protocols")
    File(protocols, "p1.json").writeText("{ not json")
    // A last-good copy that save refuses to file under any id stands in for a write that fails.
    File(protocols, "p1.json.last-good").writeText(
      kotlinx.serialization.json.Json.encodeToString(Protocol.serializer(), fullProtocol("not an id")))
    assertThrows(IllegalArgumentException::class.java) { store.recover("p1") }
    assertEquals("{ not json", File(protocols, "p1.json").readText())
    assertEquals(listOf(Store.UnreadableRecord("p1", true)), store.catalogue().unreadable)
  }

  @Test
  fun sweepingTheStoreRemovesWhatARestoreOrAnAmendmentKilledPartWayLeftBehind() {
    val root = tmp.newFolder("killed-restore")
    File(root, ".archive-import-123/photos/p1").mkdirs()
    File(root, ".archive-import-123/photos/p1/wall.jpg").writeText("staged")
    File(root, "archive-decrypt-456.zip").writeText("decrypted")
    File(root, "photos/.amend-x").mkdirs()
    File(root, "photos/.amend-x/wall.jpg").writeText("half copied")
    File(root, "photos/p1").mkdirs()
    File(root, "photos/p1/wall.jpg").writeText("kept")
    Store(root).sweepStale()
    assertTrue(!File(root, ".archive-import-123").exists())
    assertTrue(!File(root, "archive-decrypt-456.zip").exists())
    assertTrue(!File(root, "photos/.amend-x").exists())
    assertEquals("kept", File(root, "photos/p1/wall.jpg").readText())
  }

  @Test
  fun aSecondSweepOnTheSameRootLeavesARunningRestoresStageAlone() {
    val root = tmp.newFolder("running-restore")
    Store(root).sweepStale()
    File(root, ".archive-import-789").mkdirs()
    Store(root).sweepStale()
    assertTrue(File(root, ".archive-import-789").exists())
  }

  @Test
  fun openingTheStoreDeletesNothingUntilItIsSweptOffTheMainThread() {
    val root = tmp.newFolder("opened-only")
    File(root, ".archive-import-123").mkdirs()
    Store(root)
    assertTrue(File(root, ".archive-import-123").exists())
  }

  private fun parentWithAnOrphan(store: Store): Protocol {
    val parent = Protocol(
      id = "p1",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg"))),
      details = RecordDetails(
        roomPhotos = mapOf("r1" to listOf(PhotoEvidence("overview.jpg"))),
        readings = listOf(MeterReading("m1", photos = listOf(PhotoEvidence("meter.jpg")))),
        attachments = listOf(SupportingDocument("a1", file = "lease.pdf")),
      ),
    )
    listOf("wall.jpg", "overview.jpg", "meter.jpg", "lease.pdf", "removed.jpg").forEach {
      File(store.photoDir("p1"), it).writeText("bytes of $it")
    }
    return parent
  }

  @Test
  fun anAmendmentCarriesOnlyTheFilesItsDraftRefersTo() {
    val store = Store(tmp.root)
    val parent = parentWithAnOrphan(store)

    store.saveAmendment(parent, parent.copy(id = "p2", amendedFromId = "p1"))

    assertEquals(
      setOf("wall.jpg", "overview.jpg", "meter.jpg", "lease.pdf"),
      store.photoDir("p2").listFiles().orEmpty().map { it.name }.toSet(),
    )
    assertEquals("p2", store.load("p2")?.id)
    assertTrue(!File(tmp.root, "photos/.amend-p2").exists())
    // Removed evidence stays with the record it was removed from.
    assertTrue(File(store.photoDir("p1"), "removed.jpg").isFile)
  }

  @Test
  fun anAmendmentWhoseCopyFailsLeavesNeitherFolderNorRecord() {
    val store = Store(tmp.root)
    val parent = parentWithAnOrphan(store)
    // A file already in the draft's stage makes the copy refuse, as a full disk would part way.
    File(tmp.root, "photos/.amend-p2").apply { mkdirs() }.resolve("meter.jpg").writeText("in the way")

    assertThrows(FileAlreadyExistsException::class.java) {
      store.saveAmendment(parent, parent.copy(id = "p2", amendedFromId = "p1"))
    }

    assertTrue(!File(tmp.root, "photos/.amend-p2").exists())
    assertTrue(!File(tmp.root, "photos/p2").exists())
    assertNull(store.load("p2"))
    assertTrue(File(store.photoDir("p1"), "meter.jpg").isFile)
  }

  @Test
  fun aReferencedNameThatLeavesTheFolderIsNotCopiedIntoAnAmendment() {
    val store = Store(tmp.root)
    File(store.photoDir("p1").parentFile, "secret.txt").writeText("not evidence")
    val parent = Protocol(
      id = "p1", kind = Kind.Entry, createdEpochMs = 1L,
      defects = listOf(Defect(id = "d1", photos = listOf("../secret.txt"))),
    )

    store.saveAmendment(parent, parent.copy(id = "p2"))

    assertTrue(store.photoDir("p2").listFiles().isNullOrEmpty())
  }
}
