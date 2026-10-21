package dev.peringer.limen.data

import dev.peringer.limen.ui.Lang
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.overviewPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnexContentTest {

  private val s = Str(Lang.Fr)
  private val taken = 1761030000000L // 21.10.2025 09:00 in Zurich

  private fun evidence(file: String) = PhotoEvidence(file, sha256 = "AB", capturedEpochMs = taken, source = PhotoSource.Camera)

  /** Every kind of plate, deliberately out of walkthrough order in the record itself. */
  private val record = Protocol(
    id = "annex", kind = Kind.Exit, createdEpochMs = 1L,
    rooms = listOf(Room("kitchen", RoomKind.Kitchen), Room("bath", RoomKind.Bathroom, name = "Salle d’eau")),
    defects = listOf(
      Defect("d1", "bath", ItemKey.Walls, "Joint noirci", photos = listOf("d1-a.jpg"), photoEvidence = listOf(evidence("d1-a.jpg")), chargedToTenant = true),
      Defect("d2", "kitchen", ItemKey.Floor, "Rayure", photos = listOf("d2-a.jpg", "d2-b.jpg"), photoEvidence = listOf(evidence("d2-a.jpg"), evidence("d2-b.jpg"))),
      Defect("d3", objectId = "dishwasher", text = "Panier cassé", photos = listOf("d3-a.jpg")),
      Defect("d4", "gone", text = "Pièce supprimée", photos = listOf("d4-a.jpg")),
      Defect("d5", objectId = "lamp", text = "Abat-jour fendu", photos = listOf("d5-a.jpg")),
    ),
    objects = listOf(
      ObjectEntry("dishwasher", ObjectGroup.Other, label = "Lave-vaisselle", roomId = "kitchen", photos = listOf("o1-a.jpg")),
      ObjectEntry("lamp", ObjectGroup.Other, label = "Lampe", photos = listOf("o2-a.jpg")),
    ),
    details = RecordDetails(
      roomPhotos = mapOf("kitchen" to listOf(evidence("k-overview.jpg")), "bath" to listOf(evidence("b-overview.jpg"))),
      readings = listOf(MeterReading("m1", label = "Électricité", identifier = "CH-1", value = "18342", unit = "kWh", photos = listOf(evidence("m1-a.jpg")))),
    ),
  )

  @Test fun everyPlateAppearsExactlyOnceAndNothingElse() {
    val files = AnnexContent.sections(record, s).flatMap { section -> section.cards.map { it.file } }
    assertEquals(Report.plates(record).map { it.file }.sorted(), files.sorted())
    assertEquals(files.size, files.toSet().size)
  }

  @Test fun sectionsFollowTheWalkthroughOverviewThenFindingsThenObjects() {
    val sections = AnnexContent.sections(record, s)
    assertEquals(listOf("Cuisine", "Salle d’eau", s.objects, s.defects, s.meters), sections.map { it.title })
    assertEquals(listOf("k-overview.jpg", "d2-a.jpg", "d2-b.jpg", "o1-a.jpg", "d3-a.jpg"), sections[0].cards.map { it.file })
    assertEquals(listOf("b-overview.jpg", "d1-a.jpg"), sections[1].cards.map { it.file })
    assertEquals(listOf("o2-a.jpg", "d5-a.jpg"), sections[2].cards.map { it.file })
    assertEquals(listOf("d4-a.jpg"), sections[3].cards.map { it.file })
    assertEquals(listOf("m1-a.jpg"), sections[4].cards.map { it.file })
  }

  @Test fun aCardSaysWhereItBelongsWhatItShowsAndWhenItWasTaken() {
    val kitchen = AnnexContent.sections(record, s)[0].cards
    val second = kitchen[2]
    assertEquals("Cuisine", second.where)
    assertEquals(2, second.number)
    assertEquals("Rayure", second.title)
    assertTrue(second.context.toString(), s.item(ItemKey.Floor) in second.context)
    assertEquals(listOf("${s.photo} 2 / 2", "${s.capturedOn} 21.10.2025 09:00", "d2-b.jpg"), second.meta)
    assertNull(kitchen[0].number)
    assertEquals(s.overviewPhotos, kitchen[0].title)

    val bathFinding = AnnexContent.sections(record, s)[1].cards[1]
    assertTrue(bathFinding.context.toString(), s.chargedToTenant in bathFinding.context)
    // A finding on an object names the object as well as the room.
    val onObject = kitchen[4]
    assertEquals(3, onObject.number)
    assertEquals("Cuisine · Lave-vaisselle", onObject.where)
  }
}
