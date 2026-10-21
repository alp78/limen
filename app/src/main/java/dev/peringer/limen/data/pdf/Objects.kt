package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*

/**
 * Everything supplied with the flat, grouped the way it was entered: one panel per group,
 * one row per object with its chips, and the findings on that object right under it.
 */
internal fun Sheet.objects(p: Protocol) {
  if (!Outline.objects(p)) return
  heading(s.objects)
  // Both, when a record carries both. Returning after the legacy block only while objects
  // was empty meant adding one row to a seeded exit dropped the whole inherited inventory.
  if (Outline.legacyObjects(p)) {
    val appliances = p.extras.appliances.sortedBy { it.ordinal }.map(s::appliance) + p.extras.customAppliances
    val furniture = p.extras.furniture.sortedBy { it.ordinal }.map(s::furniture) + p.extras.customFurniture
    val blocks = buildList<Block> {
      if (appliances.isNotEmpty()) {
        add(Text(s.appliances, Style.LabelSmall, keepWithNext = true))
        add(Chips(appliances.map { Chip(it) }))
      }
      if (furniture.isNotEmpty()) {
        add(Text(s.furniture, Style.LabelSmall, keepWithNext = true))
        add(Chips(furniture.map { Chip(it) }))
      }
    }
    panel(Panel(s.objects, blocks, inContents = false))
  }
  val onObjects = Report.objectFindings(p)
  p.objects.groupBy { it.group to it.groupLabel }.forEach { (heading, rows) ->
    val (group, groupLabel) = heading
    val blocks = buildList<Block> {
      rows.forEach { entry ->
        add(Text(entry.displayName(s), Style.BodyMedium, keepWithNext = true))
        val chips = buildList {
          add(Chip(if (entry.inspected) s.objectInspected else s.notInspected, active = entry.inspected))
          p.rooms.firstOrNull { it.id == entry.roomId }?.let { add(Chip(Outline.roomName(it, s))) }
          if (entry.photos.isNotEmpty()) add(Chip("${s.photos}: ${entry.photos.size}"))
        }
        add(Chips(chips))
        if (entry.note.isNotBlank()) add(Text(entry.note, Style.Small))
        onObjects.filter { it.defect.objectId == entry.id }.forEach { add(findingBlock(p, it)) }
        add(Gap(PdfTokens.LABEL_GAP))
      }
    }
    panel(Panel(groupLabel.ifBlank { s.objectGroup(group) }, blocks, inContents = false))
  }
}
