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
   * successor unless that group is taller than a page; a single row taller than a page is
   * placed anyway, since only a photograph can be and the annex has already sized those.
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
      // a panel never opens with the one row a kept group left behind. A kept group taller
      // than a fresh page is not held together by moving it, since no page can: it fills the
      // page and runs on, where backing off would give each of its rows a page of its own.
      val filled = to
      while (to > from && rows[to - 1].keepWithNext) to--
      if (to < filled && held(rows, to) > full) to = filled
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

  /**
   * The row space the panel needs on the current page for [segments] to open it there
   * instead of moving it whole to a fresh page; [full] or more when less than a page
   * would not do. A heading asks it before it is drawn, so that it never ends a page with
   * its panel on the next. It reads the answer off [segments], so the two cannot disagree.
   */
  fun opening(rows: List<Slot>, full: Float): Float {
    var space = 0f
    var n = 0
    while (n < rows.size && space < full && segments(rows, space, full).first().newPageBefore) space += rows[n++].height
    return space
  }

  /** The height of the group of kept rows that starts at [start], with the row that closes it. */
  private fun held(rows: List<Slot>, start: Int): Float {
    var height = 0f
    var at = start
    while (at < rows.size) {
      height += rows[at].height
      if (!rows[at++].keepWithNext) break
    }
    return height
  }
}
