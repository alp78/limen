package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What reaches the paper. The claim these guard is the one the whole restructure rests on:
 * a room contributes its findings and nothing else, and the numbering the annex cites is
 * the numbering the body prints.
 */
class ReportTest {

  private val record = Protocol(
    id = "p1",
    kind = Kind.Exit,
    createdEpochMs = 1L,
    rooms = listOf(
      Room(id = "kitchen", kind = RoomKind.Kitchen),
      Room(id = "bath", kind = RoomKind.Bathroom),
      Room(id = "hall", kind = RoomKind.Hall),
    ),
    objects = listOf(
      ObjectEntry(id = "o1", group = ObjectGroup.Appliance, catalogue = Appliance.Fridge.name),
    ),
    defects = listOf(
      Defect(id = "d1", roomId = "kitchen", item = ItemKey.Worktop, text = "Burn mark"),
      Defect(id = "d2", objectId = "o1", text = "Door seal split"),
      Defect(id = "d3", roomId = "bath", item = ItemKey.Custom, itemLabel = "Shower rail", text = "Bent"),
      Defect(id = "d4", roomId = "kitchen", text = "Ceiling stain"),
    ),
  )

  @Test
  fun aRoomContributesItsFindingsAndNothingElse() {
    assertEquals(listOf("d1", "d4"), Report.roomFindings(record, "kitchen").map { it.defect.id })
    assertEquals(listOf("d3"), Report.roomFindings(record, "bath").map { it.defect.id })
  }

  @Test
  fun aRoomWithNothingWrongInItContributesNothing() {
    assertEquals(emptyList<Report.Numbered>(), Report.roomFindings(record, "hall"))
  }

  @Test
  fun numberingIsThePositionInTheWholeRecordNotWithinTheRoom() {
    // d4 is the fourth finding on the record and the second in the kitchen. The annex
    // caption says 4, so the body must too.
    assertEquals(listOf(1, 4), Report.roomFindings(record, "kitchen").map { it.number })
    assertEquals(listOf(3), Report.roomFindings(record, "bath").map { it.number })
    assertEquals(listOf(2), Report.objectFindings(record).map { it.number })
  }

  @Test
  fun aFindingAboutAnObjectIsNotAFindingAboutARoom() {
    assertTrue(Report.roomFindings(record, "kitchen").none { it.defect.id == "d2" })
    assertEquals(listOf("d2"), Report.objectFindings(record).map { it.defect.id })
  }

  @Test
  fun everyFindingReachesExactlyOnePlaceOnThePaper() {
    val printed = record.rooms.flatMap { Report.roomFindings(record, it.id) } +
      Report.objectFindings(record) +
      Report.orphanedFindings(record)
    assertEquals(record.defects.size, printed.size)
    assertEquals(record.defects.map { it.id }.toSet(), printed.map { it.defect.id }.toSet())
  }

  @Test
  fun aFindingWhoseRoomIsGoneStillReachesThePaper() {
    val lost = record.copy(rooms = record.rooms.filterNot { it.id == "bath" })
    assertEquals(listOf("d3"), Report.orphanedFindings(lost).map { it.defect.id })
    assertEquals(listOf(3), Report.orphanedFindings(lost).map { it.number })

    val printed = lost.rooms.flatMap { Report.roomFindings(lost, it.id) } +
      Report.objectFindings(lost) +
      Report.orphanedFindings(lost)
    assertEquals(lost.defects.size, printed.size)
  }

  @Test
  fun theAnnexCitesFindingsByNumberAndObjectsByName() {
    val withPhotos = record.copy(
      defects = record.defects.map {
        if (it.id == "d3") it.copy(
          photos = listOf("d3-1.jpg"),
          photoEvidence = listOf(PhotoEvidence("d3-1.jpg", sha256 = "ABC")),
        ) else it
      },
      objects = record.objects.map { it.copy(photos = listOf("o1-1.jpg", "o1-2.jpg")) },
    )
    val plates = Report.plates(withPhotos)

    assertEquals(listOf("d3-1.jpg", "o1-1.jpg", "o1-2.jpg"), plates.map { it.file })
    assertEquals(Report.PlateOwner.Finding(3), plates[0].owner)
    assertEquals("ABC", plates[0].sha256)
    assertEquals("o1", (plates[1].owner as Report.PlateOwner.Supplied).entry.id)
  }

  @Test
  fun aRecordWithoutPhotographsHasNoAnnex() {
    assertEquals(emptyList<Report.Plate>(), Report.plates(record))
  }
}
