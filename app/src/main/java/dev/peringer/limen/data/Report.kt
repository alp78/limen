package dev.peringer.limen.data

/**
 * What the exported document will contain, chosen before anything is drawn.
 *
 * Kept apart from [PdfExport] because that file cannot be tested: it needs a Canvas, a
 * Paint and a PdfDocument, none of which exist in a plain JVM test. The decisions worth
 * verifying are all here, and none of them touch Android.
 *
 * The numbering is the contract between the body and the photo annex. A finding is
 * numbered by its position in the whole record rather than within its room, so the number
 * under a photograph names exactly one finding on exactly one page. Renumber here and the
 * annex captions lie.
 */
object Report {

  /** A finding with the number it prints under. */
  data class Numbered(val number: Int, val defect: Defect)

  /** A photograph and what it belongs to, so the annex can be read on its own. */
  data class Plate(val file: String, val sha256: String, val owner: PlateOwner)

  sealed interface PlateOwner {
    /** A finding, cited by the number it prints under in the body. */
    data class Finding(val number: Int) : PlateOwner
    /** A supplied object, cited by name, because an object has no number to cite. */
    data class Supplied(val entry: ObjectEntry) : PlateOwner
  }

  /**
   * This room's findings, and nothing else.
   *
   * A room contributes exactly its defects here. Its separate inspected flag is what lets
   * the PDF distinguish an explicitly clean room from one that was never checked.
   */
  fun roomFindings(p: Protocol, roomId: String): List<Numbered> =
    p.defects.withIndex()
      .filter { (_, d) -> d.objectId.isBlank() && d.roomId == roomId }
      .map { (i, d) -> Numbered(i + 1, d) }

  /** Findings about a supplied object rather than a surface of the flat. */
  fun objectFindings(p: Protocol): List<Numbered> =
    p.defects.withIndex()
      .filter { (_, d) -> d.objectId.isNotBlank() }
      .map { (i, d) -> Numbered(i + 1, d) }

  /**
   * Findings whose room is gone.
   *
   * Removing a room takes its findings with it, so this is normally empty. It exists
   * because a record that lost a room some other way would otherwise print a heading count
   * and an annex caption for a finding that appears on no page.
   */
  fun orphanedFindings(p: Protocol): List<Numbered> =
    p.defects.withIndex()
      .filter { (_, d) -> d.objectId.isBlank() && p.rooms.none { it.id == d.roomId } }
      .map { (i, d) -> Numbered(i + 1, d) }

  /** Every photograph on the record, findings first and then the supplied objects. */
  fun plates(p: Protocol): List<Plate> = buildList {
    p.defects.forEachIndexed { i, d ->
      d.photoFiles().forEach { name ->
        add(Plate(name, d.photoEvidence.firstOrNull { it.file == name }?.sha256.orEmpty(), PlateOwner.Finding(i + 1)))
      }
    }
    p.objects.forEach { entry ->
      entry.photoFiles().forEach { name ->
        add(Plate(name, entry.photoEvidence.firstOrNull { it.file == name }?.sha256.orEmpty(), PlateOwner.Supplied(entry)))
      }
    }
  }
}
