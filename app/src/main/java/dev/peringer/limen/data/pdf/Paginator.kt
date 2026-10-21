package dev.peringer.limen.data.pdf

/** One atomic row of a panel: never split across pages. [keepWithNext] holds it with the row after it. */
internal data class Slot(val height: Float, val keepWithNext: Boolean = false)

/**
 * The rows [from] until [to] of a panel drawn as one closed panel, on a new page when
 * [newPageBefore], headed as a continuation when [continued].
 */
internal data class Segment(val from: Int, val to: Int, val newPageBefore: Boolean, val continued: Boolean)

/**
 * Where a panel's rows break across pages. Heights in, index ranges out: the whole
 * page-break policy sits here, away from any Canvas, so it is tested on the JVM and so
 * both drawing passes, which call it with the same heights, break in the same places.
 */
internal object Paginator {

  /**
   * [available] is the row space left on the current page and [full] the row space of a
   * fresh page, both with the panel's own chrome already taken off. A panel that would
   * open with fewer than [minFirstRows] rows moves whole to a fresh page instead of
   * leaving a header over one line; trailing [Slot.keepWithNext] rows move with their
   * successor; a single row taller than a page is placed anyway, since only a photograph
   * can be and the annex has already sized those.
   */
  fun segments(rows: List<Slot>, available: Float, full: Float, minFirstRows: Int = 2): List<Segment> {
    if (rows.isEmpty()) return listOf(Segment(0, 0, newPageBefore = available < 0f, continued = false))
    val out = mutableListOf<Segment>()
    var from = 0
    var space = available
    var fresh = false
    while (from < rows.size) {
      var to = from
      var used = 0f
      while (to < rows.size && used + rows[to].height <= space) {
        used += rows[to].height
        to++
      }
      if (to == rows.size) {
        out += Segment(from, to, fresh, continued = out.isNotEmpty())
        break
      }
      // Rows that hold on to their successor leave with it, before the opening is judged, so
      // a panel never opens with the one row a kept group left behind.
      while (to - 1 > from && rows[to - 1].keepWithNext) to--
      val tooFew = out.isEmpty() && to - from < minOf(minFirstRows, rows.size)
      if ((to == from || tooFew) && !fresh && space < full) {
        fresh = true
        space = full
        continue
      }
      if (to == from) to = from + 1
      out += Segment(from, to, fresh, continued = out.isNotEmpty())
      from = to
      fresh = true
      space = full
    }
    return out
  }
}
