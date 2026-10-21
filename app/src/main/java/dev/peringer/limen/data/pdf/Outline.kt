package dev.peringer.limen.data.pdf

import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*

/**
 * The sections a record prints, in order, decided before anything is drawn. The cover's
 * contents is printed from this list in both passes; the drawing pass marks each section as
 * it opens, and the export checks that the marks are this list. Pure Kotlin, so the order,
 * which is the paper canon's, is tested on the JVM.
 */
internal object Outline {

  data class Entry(val title: String, val level: Int)

  fun plan(p: Protocol, s: Str): List<Entry> = buildList {
    val cards = p.dossierCards()
    fun add(title: String, level: Int = 0) = add(Entry(title, level))
    if (legalOverlay(p)) add(s.legalOverlayTitle)
    if (controller(p)) add(s.controllerTitle)
    if (DossierCard.Contract in cards) add(s.contract)
    if (DossierCard.Deposit in cards) add(s.deposit)
    if (meters(p)) add(s.meters)
    if (DossierCard.Keys in cards) add(s.keysLabel)
    if (p.rooms.isNotEmpty()) {
      add(s.stepInspection)
      p.rooms.forEach { add(roomName(it, s), 1) }
    }
    if (objects(p)) add(s.objects)
    if (strays(p).isNotEmpty()) add(s.defects)
    extraContent(p, s).forEach { add(it.first) }
    if (remarks(p)) add(s.remarks)
    add(s.signRecordTitle)
    if (p.signatures.any { it.plane == Plane.Costs }) add(s.costSignatureTitle)
    if (p.deliveryReceipts.isNotEmpty()) add(s.copyDelivery)
    if (release(p)) add(s.releaseTitle)
    if (Report.plates(p).isNotEmpty()) {
      add(s.pdfDefectAnnex)
      AnnexContent.sections(p, s).forEach { add(it.title, 1) }
    }
  }

  fun roomName(room: Room, s: Str): String = room.name.ifBlank { s.room(room.kind) }

  /** Only the dated canton checks actually recorded, never inferred into a frozen record. */
  fun legalOverlay(p: Protocol): Boolean = p.legalOverlay != LegalOverlayRecord() && p.resolvedLegalOverlay() != null

  fun controller(p: Protocol): Boolean = !p.controllerIdentity.isBlank()

  fun meters(p: Protocol): Boolean = DossierCard.Meters in p.dossierCards() && !p.meters.isBlank()

  /** The older toggle inventory, the object rows, or both. */
  fun objects(p: Protocol): Boolean = legacyObjects(p) || p.objects.isNotEmpty()

  fun legacyObjects(p: Protocol): Boolean =
    p.extras.appliances.isNotEmpty() || p.extras.customAppliances.isNotEmpty() ||
      p.extras.furniture.isNotEmpty() || p.extras.customFurniture.isNotEmpty()

  /**
   * Findings with no panel to sit in: a finding whose room is gone, or one on an object the
   * record no longer lists, in the record's own numbering.
   */
  fun strays(p: Protocol): List<Report.Numbered> =
    (Report.orphanedFindings(p) + Report.objectFindings(p).filter { f -> p.objects.none { it.id == f.defect.objectId } })
      .sortedBy { it.number }

  fun remarks(p: Protocol): Boolean =
    DossierCard.Notes in p.dossierCards() && (p.remarks.isNotBlank() || p.chargesStatementBy.isNotBlank())

  fun release(p: Protocol): Boolean = p.kind == Kind.Exit && p.deposit.kind != DepositKind.None
}
