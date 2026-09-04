package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
