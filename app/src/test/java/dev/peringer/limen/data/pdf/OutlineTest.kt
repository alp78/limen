package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.Lang
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.costSignatureTitle
import dev.peringer.limen.ui.understoodLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class OutlineTest {

  private val s = Str(Lang.Fr)

  private fun evidence(file: String) = PhotoEvidence(file, sha256 = "AB", capturedEpochMs = 1761030000000L, source = PhotoSource.Camera)

  /** Every kind of section at once: dossier cards, rooms, objects, strays, extras, a costs plane, receipts, a deposit to release. */
  private val full = Protocol(
    id = "outline", kind = Kind.Exit, createdEpochMs = 1L,
    address = "Rue du Lac 3", place = "1000 Lausanne", objectCanton = SwissCanton.VD,
    controllerIdentity = ControllerIdentity(organisation = "Régie Test"),
    dossier = listOf(DossierCard.Contract, DossierCard.Deposit, DossierCard.Meters, DossierCard.Keys, DossierCard.Notes), dossierChosen = true,
    deposit = Deposit(DepositKind.BankAccount, amountChf = "3000"),
    meters = Meters(electricity1 = "12345"),
    keys = listOf(KeyCount(KeyKind.Apartment, count = 2)),
    rooms = listOf(Room("kitchen", RoomKind.Kitchen, inspected = true), Room("bath", RoomKind.Bathroom, name = "Salle d’eau")),
    defects = listOf(
      Defect("d1", "bath", ItemKey.Walls, "Joint noirci", photos = listOf("d1-a.jpg"), photoEvidence = listOf(evidence("d1-a.jpg"))),
      Defect("d2", objectId = "dishwasher", text = "Panier cassé"),
      Defect("d3", "gone", text = "Pièce supprimée"),
      Defect("d4", objectId = "vanished", text = "Objet disparu"),
    ),
    objects = listOf(ObjectEntry("dishwasher", ObjectGroup.Appliance, catalogue = "Dishwasher", roomId = "kitchen")),
    remarks = "Remarque",
    signatures = listOf(Signature(Role.Tenant, "T", Plane.Costs, contentRevision = 0)),
    deliveryReceipts = listOf(DeliveryReceipt(Role.Tenant, method = DeliveryMethod.InPerson)),
    details = RecordDetails(understoodLanguage = "Français", roomPhotos = mapOf("kitchen" to listOf(evidence("k.jpg")))),
  )

  @Test fun thePlanFollowsThePaperCanonWithRoomsAndAnnexSectionsBeneathTheirHeadings() {
    val titles = Outline.plan(full, s).map { it.title to it.level }
    val expected = listOf(
      s.controllerTitle to 0, s.contract to 0, s.deposit to 0, s.meters to 0, s.keysLabel to 0,
      s.stepInspection to 0, "Cuisine" to 1, "Salle d’eau" to 1,
      s.objects to 0, s.defects to 0,
      s.understoodLanguage to 0,
      s.remarks to 0, s.signRecordTitle to 0, s.costSignatureTitle to 0, s.copyDelivery to 0, s.releaseTitle to 0,
      s.pdfDefectAnnex to 0, "Cuisine" to 1, "Salle d’eau" to 1,
    )
    assertEquals(expected, titles)
  }

  @Test fun aBareRecordPlansOnlyTheSignatures() {
    val bare = Protocol(id = "bare", kind = Kind.Entry, createdEpochMs = 1L)
    assertEquals(listOf(Outline.Entry(s.signRecordTitle, 0)), Outline.plan(bare, s))
  }

  @Test fun strayFindingsAreThoseWithoutARoomOrAListedObjectInRecordOrder() {
    assertEquals(listOf(3, 4), Outline.strays(full).map { it.number })
  }

  @Test fun aRoomWithoutANameIsPlannedUnderItsKind() {
    assertEquals("Cuisine", Outline.roomName(Room("k", RoomKind.Kitchen), s))
    assertEquals("Atelier", Outline.roomName(Room("k", RoomKind.Kitchen, name = "Atelier"), s))
  }
}
