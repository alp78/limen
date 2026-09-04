package dev.peringer.limen.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The canton of the handed-over object, never the participant's contact address. */
@Serializable
enum class SwissCanton(val code: String, val label: String) {
  AG("AG", "Aargau"),
  AI("AI", "Appenzell Innerrhoden"),
  AR("AR", "Appenzell Ausserrhoden"),
  BE("BE", "Bern"),
  BL("BL", "Basel-Landschaft"),
  BS("BS", "Basel-Stadt"),
  FR("FR", "Fribourg"),
  GE("GE", "Genève"),
  GL("GL", "Glarus"),
  GR("GR", "Graubünden"),
  JU("JU", "Jura"),
  LU("LU", "Luzern"),
  NE("NE", "Neuchâtel"),
  NW("NW", "Nidwalden"),
  OW("OW", "Obwalden"),
  SG("SG", "St. Gallen"),
  SH("SH", "Schaffhausen"),
  SO("SO", "Solothurn"),
  SZ("SZ", "Schwyz"),
  TG("TG", "Thurgau"),
  TI("TI", "Ticino"),
  UR("UR", "Uri"),
  VD("VD", "Vaud"),
  VS("VS", "Valais"),
  ZG("ZG", "Zug"),
  ZH("ZH", "Zürich");

  companion object {
    fun fromCode(code: String): SwissCanton? =
      entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
  }
}

enum class LegalProfile { FederalAndLease, RomandContractual, VaudRulv2020, VaudRulv2026 }

fun LegalProfile.isVaudRulv(): Boolean =
  this == LegalProfile.VaudRulv2020 || this == LegalProfile.VaudRulv2026

private val romandFrameworkCantons = setOf(
  SwissCanton.FR,
  SwissCanton.GE,
  SwissCanton.JU,
  SwissCanton.NE,
  SwissCanton.VD,
  SwissCanton.VS,
)

fun SwissCanton.supportsRomandFramework(): Boolean = this in romandFrameworkCantons

/** The binding or declared contractual basis that this record actually claims. */
fun Protocol.legalProfile(): LegalProfile {
  val date = runCatching { LocalDate.parse(handoverDate) }.getOrNull()
  if (
    objectCanton == SwissCanton.VD && date != null &&
    !date.isBefore(LocalDate.of(2026, 7, 1)) && date.isBefore(LocalDate.of(2032, 7, 1))
  ) {
    return LegalProfile.VaudRulv2026
  }
  if (
    objectCanton == SwissCanton.VD && date != null &&
    !date.isBefore(LocalDate.of(2020, 7, 1)) && date.isBefore(LocalDate.of(2026, 7, 1))
  ) {
    return LegalProfile.VaudRulv2020
  }
  return if (romandFrameworkInLease) LegalProfile.RomandContractual else LegalProfile.FederalAndLease
}
