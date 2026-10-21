package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*

/**
 * The inspection: one panel per room, as the app shows one tile per room. A room with
 * findings is the amber warning panel and carries them, numbered, inside; a room without
 * is the raised panel with its status as the pill. The record-wide numbering is the
 * contract with the annex, so a finding's number here names the one under its photograph.
 */
internal fun Sheet.rooms(p: Protocol) {
  if (p.rooms.isEmpty()) return
  heading(s.stepInspection)
  val sections = AnnexContent.sections(p, s)
  p.rooms.forEach { room -> panel(roomPanel(p, room, sections.firstOrNull { it.key == room.id })) }
}

private fun Sheet.roomPanel(p: Protocol, room: Room, section: AnnexContent.Section?): Panel {
  val found = Report.roomFindings(p, room.id)
  val pill = when {
    found.isNotEmpty() -> Pill(s.defectCount(found.size), PillTone.Warning)
    room.inspected -> Pill(s.roomInspected, PillTone.Seal)
    else -> Pill(s.notInspected, PillTone.Dim)
  }
  val blocks = buildList<Block> {
    // The room's readout first, as the hub door carries its own: how many photographs the
    // annex holds for it and on which page. A closing row would be stranded by a page break.
    section?.let { add(Chips(listOf(Chip("${s.photos}: ${it.cards.size}"), Chip(annexPointer(it))), keepWithNext = true)) }
    if (room.hasLegacyGrid()) add(ItemGrid(legacyGrid(p, room)))
    // A room explicitly confirmed as inspected says so even with no finding. Legacy seeded
    // rooms stay labelled not inspected by their pill, so the paper never turns their
    // silence into a claim that somebody checked them.
    if (found.isEmpty() && room.inspected) add(Text(s.inspectedNoDefects, Style.Body))
    found.forEach { add(findingBlock(p, it)) }
  }
  return Panel(
    Outline.roomName(room, s), blocks, pill,
    tone = if (found.isNotEmpty()) Tone.Warning else Tone.Raised,
    level = 1, key = room.id,
  )
}

/** Where a section's photographs are: its page once counted, the annex alone while counting. */
internal fun Sheet.annexPointer(section: AnnexContent.Section): String =
  annexPages[section.key]?.let { "${s.pdfDefectAnnex}: ${s.pdfPage} $it" } ?: s.pdfDefectAnnex

/**
 * The exhaustive item grid, printed only for a record that was signed with one. The state
 * names the findings on an item by number, as the grid always has.
 */
private fun Sheet.legacyGrid(p: Protocol, room: Room): List<Pair<String, String>> = room.items.map { item ->
  val name = if (item.key == ItemKey.Custom && item.label.isNotBlank()) item.label else s.item(item.key)
  val defectNos = p.defects.withIndex()
    .filter { (_, d) ->
      d.roomId == room.id && d.item == item.key &&
        (d.itemId.isBlank() || item.itemId.isBlank() || d.itemId == item.itemId)
    }
    .map { (i, _) -> i + 1 }
  val state = when {
    defectNos.isNotEmpty() -> "! " + defectNos.joinToString(",")
    item.grade == Grade.NotChecked -> ""
    p.style == GradeStyle.Binary -> s.grade(Grade.Ok)
    else -> s.gradeShort(item.grade)
  }
  name to state
}
