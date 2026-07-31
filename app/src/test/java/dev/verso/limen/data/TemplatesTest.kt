package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

  @Test
  fun everyRoomKindStartsWithRows() {
    RoomKind.entries.forEach { kind ->
      assertTrue("$kind has no template rows", Templates.itemsFor(kind).isNotEmpty())
    }
  }

  @Test
  fun noTemplateRepeatsARow() {
    RoomKind.entries.forEach { kind ->
      val items = Templates.itemsFor(kind)
      assertEquals("$kind repeats a row", items.size, items.distinct().size)
    }
  }

  @Test
  fun theKitchenKnowsItsAppliances() {
    val kitchen = Templates.itemsFor(RoomKind.Kitchen)
    listOf(
      ItemKey.Stove, ItemKey.Oven, ItemKey.OvenAccessories, ItemKey.ExtractorHood,
      ItemKey.Fridge, ItemKey.Sink, ItemKey.Tap,
    ).forEach { assertTrue("kitchen misses $it", it in kitchen) }
  }

  @Test
  fun theBathroomKnowsItsFittings() {
    val bath = Templates.itemsFor(RoomKind.Bathroom)
    listOf(ItemKey.Bathtub, ItemKey.ShowerHose, ItemKey.WC, ItemKey.WCSeat, ItemKey.Washbasin)
      .forEach { assertTrue("bathroom misses $it", it in bath) }
  }

  @Test
  fun theWalkStartsAtTheDoorAndEndsAtTheMailbox() {
    val rooms = Templates.defaultRooms
    assertEquals(RoomKind.Hall, rooms.first())
    assertEquals(RoomKind.Mailbox, rooms.last())
    assertTrue(RoomKind.Kitchen in rooms)
    assertTrue(RoomKind.Bathroom in rooms)
  }
}
