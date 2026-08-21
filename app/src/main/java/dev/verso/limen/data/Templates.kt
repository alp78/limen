package dev.verso.limen.data

import kotlinx.serialization.Serializable

/**
 * The room canon, distilled from the MV mp form's 215 positions, the HEV two-pager and
 * the romand (Lafidu) form. A template is a starting point: rooms and items can be added,
 * renamed or removed on the way through the flat, but the walk begins with what the paper
 * forms have asked about for decades, so nothing routine is forgotten at the door.
 */
@Serializable
enum class RoomKind {
  Kitchen, Bathroom, SepWC, Hall, LivingRoom, Bedroom, Room,
  Balcony, Cellar, Attic, Garage, LaundryRoom, HobbyRoom, Mailbox, Custom,
}

/**
 * Every item a template row can carry. The canonical rows render per language from
 * [dev.verso.limen.ui.Str]; [Custom] rows carry their own label. Kept flat on purpose:
 * one enum, so the store's JSON stays a list of names.
 */
@Serializable
enum class ItemKey {
  // The shell of every room.
  Floor, Walls, Ceiling, Skirtings, Door, DoorLock, Windows, WindowSills,
  Blinds, BlindStraps, CurtainRails, Radiator, SocketsSwitches, Lighting,
  // Kitchen.
  Stove, Oven, OvenAccessories, ExtractorHood, Fridge, Freezer, Dishwasher,
  Sink, Tap, Worktop, Cupboards, Tiles,
  // Bath and WC.
  Bathtub, Shower, ShowerHose, WC, Cistern, WCSeat, Washbasin, MirrorCabinet,
  TowelRail, SoapHolder, Ventilation,
  // Elsewhere in the building.
  CellarRack, WashingMachine, Dryer, MailboxLock,
  Custom,
}

object Templates {

  /** The rooms a fresh protocol proposes, in walking order. */
  val defaultRooms: List<RoomKind> = listOf(
    RoomKind.Hall,
    RoomKind.Kitchen,
    RoomKind.LivingRoom,
    RoomKind.Bedroom,
    RoomKind.Bathroom,
    RoomKind.Balcony,
    RoomKind.Cellar,
    RoomKind.Mailbox,
  )

  private val shell = listOf(
    ItemKey.Floor, ItemKey.Walls, ItemKey.Ceiling, ItemKey.Skirtings,
    ItemKey.Door, ItemKey.DoorLock, ItemKey.Windows, ItemKey.WindowSills,
    ItemKey.Blinds, ItemKey.BlindStraps, ItemKey.CurtainRails,
    ItemKey.Radiator, ItemKey.SocketsSwitches, ItemKey.Lighting,
  )

  /** The item rows a room of this kind starts with. */
  fun itemsFor(kind: RoomKind): List<ItemKey> =
    when (kind) {
      RoomKind.Kitchen ->
        shell + listOf(
          ItemKey.Stove, ItemKey.Oven, ItemKey.OvenAccessories, ItemKey.ExtractorHood,
          ItemKey.Fridge, ItemKey.Freezer, ItemKey.Dishwasher,
          ItemKey.Sink, ItemKey.Tap, ItemKey.Worktop, ItemKey.Cupboards, ItemKey.Tiles,
        )
      RoomKind.Bathroom ->
        shell + listOf(
          ItemKey.Tiles, ItemKey.Bathtub, ItemKey.Shower, ItemKey.ShowerHose,
          ItemKey.WC, ItemKey.Cistern, ItemKey.WCSeat, ItemKey.Washbasin,
          ItemKey.MirrorCabinet, ItemKey.TowelRail, ItemKey.SoapHolder, ItemKey.Tap,
          ItemKey.Ventilation,
        )
      RoomKind.SepWC ->
        listOf(
          ItemKey.Floor, ItemKey.Walls, ItemKey.Ceiling, ItemKey.Door,
          ItemKey.Tiles, ItemKey.WC, ItemKey.Cistern, ItemKey.WCSeat, ItemKey.Washbasin,
          ItemKey.Tap, ItemKey.Ventilation, ItemKey.SocketsSwitches, ItemKey.Lighting,
        )
      RoomKind.Balcony ->
        listOf(ItemKey.Floor, ItemKey.Walls, ItemKey.Door, ItemKey.Windows, ItemKey.Lighting)
      RoomKind.Cellar ->
        listOf(
          ItemKey.Floor, ItemKey.Walls, ItemKey.Door,
          ItemKey.CellarRack, ItemKey.Lighting,
        )
      RoomKind.Attic -> listOf(ItemKey.Floor, ItemKey.Walls, ItemKey.Door, ItemKey.Lighting)
      RoomKind.Garage -> listOf(ItemKey.Floor, ItemKey.Walls, ItemKey.Door, ItemKey.Lighting)
      RoomKind.LaundryRoom ->
        listOf(
          ItemKey.Floor, ItemKey.Walls, ItemKey.Door, ItemKey.WashingMachine, ItemKey.Dryer,
          ItemKey.Sink, ItemKey.Tap, ItemKey.Lighting,
        )
      RoomKind.Mailbox -> listOf(ItemKey.MailboxLock)
      // Hall, living room, bedrooms, hobby room, custom: the shell carries them.
      else -> shell
    }

}
