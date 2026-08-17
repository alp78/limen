package dev.verso.limen.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dates are held ISO and written DD/MM/YYYY.
 *
 * The record keeps yyyy-MM-dd, because the notice deadlines are computed from a parsed
 * LocalDate and because a stored order that sorts is worth more than a stored order that
 * reads. DD/MM/YYYY is the Swiss form convention, so it is what a person types and what the
 * PDF prints; the two meet only here, at the edges.
 *
 * Anything already on disk that is not a date passes through untouched. These fields used
 * to accept any text at all, and blanking what somebody wrote in order to enforce a format
 * introduced later would be the worse error of the two.
 */
object SwissDate {

  private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
  private val swiss: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  /** The stored value as a person reads it, or the stored value itself when it is not a date. */
  fun display(stored: String): String = parse(stored)?.format(swiss) ?: stored

  fun parse(stored: String): LocalDate? =
    runCatching { LocalDate.parse(stored.trim(), iso) }.getOrNull()

  /**
   * The typed form back to storage, or null while it is incomplete or impossible. A null is
   * not an error to report: it is the answer for "17/0" as much as for "31/02/2026", and the
   * field decides which of the two deserves a mark.
   */
  fun toStored(typed: String): String? {
    val digits = typed.filter(Char::isDigit)
    if (digits.length != 8) return null
    val day = digits.substring(0, 2).toInt()
    val month = digits.substring(2, 4).toInt()
    val year = digits.substring(4, 8).toInt()
    if (year < 1000) return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()?.format(iso)
  }

  /**
   * Digits with the separators put back in, so the field can never hold a shape that is not
   * a date. Typing is the fast road and the calendar is the sure one; masking is what keeps
   * the fast road from producing "12 mai" or "5.6.26".
   */
  fun mask(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(8)
    return buildString {
      digits.forEachIndexed { i, c ->
        if (i == 2 || i == 4) append('/')
        append(c)
      }
    }
  }

  // ── The time of the handover, on the same contract ───────────────────────────────────

  /** Stored as HH:mm on the 24 hour clock, which is how Swiss forms are printed. */
  fun toStoredTime(typed: String): String? {
    val digits = typed.filter(Char::isDigit)
    if (digits.length != 4) return null
    val hour = digits.substring(0, 2).toInt()
    val minute = digits.substring(2, 4).toInt()
    if (hour > 23 || minute > 59) return null
    return "%02d:%02d".format(hour, minute)
  }

  fun maskTime(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(4)
    return buildString {
      digits.forEachIndexed { i, c ->
        if (i == 2) append(':')
        append(c)
      }
    }
  }
}
