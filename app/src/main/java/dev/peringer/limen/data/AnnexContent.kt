package dev.peringer.limen.data

import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.defectItemLabel
import dev.peringer.limen.ui.displayName
import dev.peringer.limen.ui.importedOn
import dev.peringer.limen.ui.overviewPhotos
import dev.peringer.limen.ui.reading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The photo annex as people read it at a handover: the photographs gathered into the rooms
 * they were taken in, in walkthrough order, each with what it shows. A card met on a page of
 * its own still says which room it belongs to and what it documents, so nobody has to turn
 * back to the findings to know what they are looking at.
 *
 * Text and order only, tested on the JVM; [PdfExport] lays it out and the HTML copy follows.
 */
internal object AnnexContent {

  /**
   * A room, the supplied objects without one, the findings whose room is gone, or the
   * meters. [key] is the room's id, or the group's name, so the body can cite the page the
   * section opens on even when two rooms share a name.
   */
  data class Section(val title: String, val cards: List<Card>, val key: String = "")

  /**
   * One photograph and its legend. [number] is the finding it documents, as numbered in the
   * body of the record; [where] repeats the section, for a card read on its own.
   */
  data class Card(val file: String, val where: String, val number: Int?, val title: String, val context: List<String>, val meta: List<String>)

  private val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
  private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

  fun sections(p: Protocol, s: Str): List<Section> {
    val evidence = p.allPhotoEvidence().associateBy { it.file }
    val placed = mutableSetOf<String>()
    val sections = mutableListOf<Section>()
    fun roomName(room: Room) = room.name.ifBlank { s.room(room.kind) }

    /** The cards for one owner's photographs, numbered within that owner. */
    fun cards(files: List<String>, where: String, number: Int?, title: String, context: List<String>): List<Card> {
      val fresh = files.filter { it.isNotBlank() && it !in placed }
      return fresh.mapIndexed { i, file ->
        placed += file
        Card(file, where, number, title, context.filter { it.isNotBlank() }, meta(file, i, fresh.size, evidence[file], s))
      }
    }
    fun finding(index: Int, where: String): List<Card> {
      val d = p.defects[index]
      val context = buildList {
        p.defectItemLabel(d, s)?.let(::add)
        if (d.chargedToTenant) add(s.chargedToTenant)
        when (d.fixBy) {
          FixBy.Tenant -> add("${s.fixByLabel}: ${s.role(Role.Tenant)}")
          FixBy.Landlord -> add("${s.fixByLabel}: ${s.role(Role.Landlord)}")
          FixBy.Open -> Unit
        }
        if (d.tenantResponse != TenantResponse.NotStated) add("${s.tenantResponse}: ${s.tenantResponse(d.tenantResponse)}")
      }
      return cards(d.photoFiles(), where, index + 1, d.text.ifBlank { "–" }, context)
    }
    fun objectCards(entry: ObjectEntry, where: String): List<Card> {
      val name = entry.displayName(s)
      val own = cards(entry.photoFiles(), where, null, name, listOf(entry.note))
      val findings = p.defects.indices.filter { p.defects[it].objectId == entry.id }.flatMap { finding(it, "$where · $name") }
      return own + findings
    }

    p.rooms.forEach { room ->
      val name = roomName(room)
      val cards = cards(p.details.roomPhotos[room.id].orEmpty().map { it.file }, name, null, s.overviewPhotos, emptyList()) +
        Report.roomFindings(p, room.id).flatMap { finding(it.number - 1, name) } +
        p.objects.filter { it.roomId == room.id }.flatMap { objectCards(it, name) }
      if (cards.isNotEmpty()) sections += Section(name, cards, room.id)
    }
    val roomless = p.objects.filter { entry -> p.rooms.none { it.id == entry.roomId } }.flatMap { objectCards(it, s.objects) } +
      p.defects.indices.filter { i -> p.defects[i].objectId.isNotBlank() && p.objects.none { it.id == p.defects[i].objectId } }
        .flatMap { finding(it, s.objects) }
    if (roomless.isNotEmpty()) sections += Section(s.objects, roomless, "objects")
    val orphaned = Report.orphanedFindings(p).flatMap { finding(it.number - 1, s.defects) }
    if (orphaned.isNotEmpty()) sections += Section(s.defects, orphaned, "defects")
    val meters = p.details.readings.flatMap { r ->
      val reading = listOf(r.value, r.unit).filter(String::isNotBlank).joinToString(" ")
      val taken = listOf(SwissDate.display(r.date), r.time).filter(String::isNotBlank).joinToString(" ")
      cards(r.photos.map { it.file }, s.meters, null, listOf(r.label, r.identifier).filter(String::isNotBlank).joinToString(" · "),
        listOf(if (reading.isNotBlank()) "${s.reading}: $reading" else "", taken, r.note))
    }
    if (meters.isNotEmpty()) sections += Section(s.meters, meters, "meters")
    // Nothing the annex is asked to print may go missing because no section claimed it.
    val rest = Report.plates(p).map { it.file }.filter { it !in placed }
    if (rest.isNotEmpty()) sections += Section(s.photos, cards(rest, s.photos, null, s.photos, emptyList()), "photos")
    return sections
  }

  /** Which photograph of how many, when it was taken or imported, and its file. */
  private fun meta(file: String, index: Int, count: Int, evidence: PhotoEvidence?, s: Str): List<String> = buildList {
    add("${s.photo} ${index + 1} / $count")
    if (evidence != null) {
      val imported = evidence.source == PhotoSource.Gallery
      val epoch = if (imported) evidence.importedEpochMs.takeIf { it > 0 } ?: evidence.capturedEpochMs
        else evidence.capturedEpochMs.takeIf { it > 0 } ?: evidence.importedEpochMs
      if (epoch > 0) add("${if (imported) s.importedOn else s.capturedOn} ${STAMP.format(Instant.ofEpochMilli(epoch).atZone(ZURICH))}")
    }
    add(file)
  }
}
