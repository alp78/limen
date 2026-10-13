package dev.peringer.limen.ui

/**
 * Accept Swiss grouping without accepting infinities, NaN or negative money/age. The written
 * forms of a franc amount pass too: refusing "CHF 2'400.-" blocked finalization over the way
 * a Swiss lease prints its rent, with nothing on the field to say why.
 */
internal fun nonNegativeNumber(value: String): Double? = value
  .filterNot { it in "'’" || it.isWhitespace() }.replace(',', '.').uppercase()
  .removePrefix("CHF").removePrefix("FR.").removeSuffix("CHF").removeSuffix("FR.")
  .removeSuffix(".-").removeSuffix(".–").removeSuffix(".")
  .takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
  ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1_000_000_000_000.0 }
