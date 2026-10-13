package dev.peringer.limen.ui

/** Accept Swiss grouping without accepting infinities, NaN or negative money/age. */
internal fun nonNegativeNumber(value: String): Double? = value
  .filterNot { it in "'’" || it.isWhitespace() }.replace(',', '.')
  .takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }
  ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1_000_000_000_000.0 }
