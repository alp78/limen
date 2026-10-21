package dev.peringer.limen.data

import java.text.Normalizer
import java.time.LocalDate

/**
 * The exported document's name: the address and the handover's day, dashes between, nothing
 * else, the same name the iPhone writes. The fingerprint, the language, the delivery revision
 * and the export time used to follow the address, and the owner found the name useless in a
 * mail and in a file manager; they stay in the record's export history, which is what the app
 * reads. Letters are folded to ASCII and anything else is a dash, because the archive's name
 * rule is ASCII and an iPhone restoring the record must take the file.
 */
object DocumentName {

  /**
   * `Avenue-de-la-Mesure-30-2026-10-21`. A record whose handover day is not set yet is named
   * after [today], the day of the export.
   */
  fun of(address: String, handoverDate: String, today: LocalDate): String {
    val folded = Normalizer.normalize(address, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    var slug = folded.map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
      .replace(Regex("-{2,}"), "-").trim('-')
    if (slug.isEmpty()) slug = "Limen"
    val day = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(handoverDate)) handoverDate else today.toString()
    return slug.take(80) + "-" + day
  }

  /**
   * [base], or `base-2`, `base-3` and so on, the first that is not [taken]: a second export
   * of a record on the same day keeps the earlier copy, because the history promises every
   * exact copy.
   */
  fun unused(base: String, taken: (String) -> Boolean): String {
    var candidate = base
    var counter = 2
    while (taken(candidate)) candidate = "$base-${counter++}"
    return candidate
  }
}
