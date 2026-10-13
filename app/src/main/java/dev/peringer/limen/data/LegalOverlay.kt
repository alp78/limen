package dev.peringer.limen.data

import java.time.LocalDate
import kotlinx.serialization.Serializable

const val LEGAL_RULE_SET_ID = "ch-tenancy-2026.2"

/** A dated legal/practice check that is recorded with the handover. */
@Serializable
enum class LegalRequirement {
  LeaseAndLocalTiming,
  InitialRentForm,
  DepositProof,
  TenantLiabilityInsurance,
  LandlordLiabilityInsurance,
  VaudExitReadiness,
  CantonalDepositHandling,
}

/** Missing or disputed items remain recordable, but require an explanatory note. */
@Serializable
enum class LegalCheckStatus { Confirmed, MissingOrDisputed, NotApplicable, ToVerify }

/**
 * The operator's recorded outcome for the checks selected by the dated overlay.
 *
 * A map keeps the file forward-compatible: adding a future canton rule does not alter older
 * records, and changing canton or handover date can reset this whole small record safely.
 */
@Serializable
data class LegalOverlayRecord(
  val checks: Map<LegalRequirement, LegalCheckStatus> = emptyMap(),
  val note: String = "",
  val ruleSetId: String = "",
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
  val notes: Map<LegalRequirement, String> = emptyMap(),
) {
  fun status(requirement: LegalRequirement): LegalCheckStatus? = checks[requirement]

  fun withStatus(requirement: LegalRequirement, status: LegalCheckStatus): LegalOverlayRecord =
    copy(checks = checks + (requirement to status), ruleSetId = LEGAL_RULE_SET_ID)

  fun withoutStatus(requirement: LegalRequirement): LegalOverlayRecord =
    copy(checks = checks - requirement)
}

/** What the official initial-rent form snapshot says for the selected object and date. */
enum class InitialRentFormRule { Required, ConditionalLocal, NotRequired, VerifyCurrent }

/** The effective, role-neutral layer placed on top of the common Swiss handover flow. */
data class ResolvedLegalOverlay(
  val canton: SwissCanton,
  val handoverDate: LocalDate,
  val initialRentReferenceDate: LocalDate?,
  val profile: LegalProfile,
  val initialRentFormRule: InitialRentFormRule,
  val requirements: List<LegalRequirement>,
  val ruleSetId: String = LEGAL_RULE_SET_ID,
) {
  fun isComplete(record: LegalOverlayRecord): Boolean {
    if (record.ruleSetId != ruleSetId) return false
    if (requirements.any { record.status(it) == null }) return false
    if (initialRentFormRule in setOf(InitialRentFormRule.VerifyCurrent, InitialRentFormRule.ConditionalLocal) &&
      record.status(LegalRequirement.InitialRentForm) == LegalCheckStatus.Confirmed &&
      record.notes[LegalRequirement.InitialRentForm].isNullOrBlank() &&
      !(record.ruleSetId == "ch-tenancy-2026.1" && record.note.isNotBlank())) return false
    return requirements.all { record.status(it) == LegalCheckStatus.Confirmed ||
      record.notes[it].orEmpty().isNotBlank() || record.ruleSetId == "ch-tenancy-2026.1" && record.note.isNotBlank() }
  }
}

private val fullInitialRentFormCantons2026 = setOf(
  SwissCanton.BE,
  SwissCanton.BS,
  SwissCanton.FR,
  SwissCanton.GE,
  SwissCanton.LU,
  SwissCanton.ZG,
  SwissCanton.ZH,
)

private val conditionalInitialRentFormCantons2026 = setOf(
  SwissCanton.NE,
  SwissCanton.VD,
)

/**
 * Resolves the small canton layer from the object's canton and the relevant legal dates.
 *
 * The initial-rent-form table is deliberately limited to its official 2026 snapshot. A
 * initial-rent-form rule follows the lease conclusion date, not the later key handover. A
 * missing or different-year date asks the operator to verify the then-current rule instead
 * of silently using stale vacancy data. Vaud RULV periods still follow the handover date.
 */
fun Protocol.resolvedLegalOverlay(): ResolvedLegalOverlay? {
  val canton = objectCanton ?: return null
  val date = runCatching { LocalDate.parse(handoverDate) }.getOrNull() ?: return null
  val initialRentReferenceDate = runCatching { LocalDate.parse(contract.conclusionDate) }.getOrNull()
  val profile = legalProfile()
  if (isFinalized() && details.legalSnapshot == null) {
    // Select the original resolver explicitly. Future rule additions must not rewrite a
    // finalized pre-snapshot record while leaving its evidence reference unchanged.
    val historicalRule = when {
      initialRentReferenceDate?.year != 2026 -> InitialRentFormRule.VerifyCurrent
      canton in setOf(SwissCanton.BE, SwissCanton.BS, SwissCanton.FR, SwissCanton.GE, SwissCanton.LU, SwissCanton.ZG, SwissCanton.ZH) -> InitialRentFormRule.Required
      canton in setOf(SwissCanton.NE, SwissCanton.VD) -> InitialRentFormRule.ConditionalLocal
      else -> InitialRentFormRule.NotRequired
    }
    val historicalRequirements = if (legalOverlay.ruleSetId == "ch-tenancy-2026.1") buildList {
      add(LegalRequirement.LeaseAndLocalTiming)
      if (kind == Kind.Entry && historicalRule != InitialRentFormRule.NotRequired) add(LegalRequirement.InitialRentForm)
      if (profile.isVaudRulv()) {
        if (kind == Kind.Exit) add(LegalRequirement.VaudExitReadiness) else {
          if (deposit.kind != DepositKind.None) add(LegalRequirement.DepositProof)
          add(LegalRequirement.TenantLiabilityInsurance); add(LegalRequirement.LandlordLiabilityInsurance)
        }
      }
    } else legalOverlay.checks.keys.sortedBy { it.ordinal }
    return ResolvedLegalOverlay(canton, date, initialRentReferenceDate, profile, historicalRule,
      historicalRequirements, legalOverlay.ruleSetId.ifBlank { "legacy-unversioned" })
  }
  val initialRentFormRule = when {
    initialRentReferenceDate?.year != 2026 -> InitialRentFormRule.VerifyCurrent
    canton in fullInitialRentFormCantons2026 -> InitialRentFormRule.Required
    canton == SwissCanton.NE -> neInitialRentRule(details.municipality, details.habitableRooms)
    canton == SwissCanton.VD -> when (details.district) {
      "Aigle" -> InitialRentFormRule.NotRequired
      in vaudDistricts -> InitialRentFormRule.Required
      else -> InitialRentFormRule.ConditionalLocal
    }
    else -> InitialRentFormRule.NotRequired
  }

  val requirements = buildList {
    // Local termination dates, contractual appointment terms and public holidays can affect
    // practical timing even though the core handover record is governed federally.
    add(LegalRequirement.LeaseAndLocalTiming)
    if (deposit.kind != DepositKind.None && canton in setOf(SwissCanton.FR, SwissCanton.GE, SwissCanton.TI)) {
      add(LegalRequirement.CantonalDepositHandling)
    }

    if (kind == Kind.Entry && initialRentFormRule != InitialRentFormRule.NotRequired) {
      add(LegalRequirement.InitialRentForm)
    }

    if (profile.isVaudRulv()) {
      if (kind == Kind.Entry) {
        if (deposit.kind != DepositKind.None) add(LegalRequirement.DepositProof)
        add(LegalRequirement.TenantLiabilityInsurance)
        add(LegalRequirement.LandlordLiabilityInsurance)
      } else {
        add(LegalRequirement.VaudExitReadiness)
      }
    }
  }

  val snapshot = details.legalSnapshot
  return ResolvedLegalOverlay(
    canton = canton,
    handoverDate = date,
    initialRentReferenceDate = initialRentReferenceDate,
    profile = snapshot?.let { LegalProfile.valueOf(it.profile) } ?: profile,
    initialRentFormRule = snapshot?.let { InitialRentFormRule.valueOf(it.initialRentFormRule) } ?: initialRentFormRule,
    requirements = snapshot?.requirements ?: requirements,
    ruleSetId = snapshot?.ruleSetId ?: LEGAL_RULE_SET_ID,
  )
}

val vaudDistricts = listOf("Aigle", "Broye-Vully", "Gros-de-Vaud", "Jura-Nord vaudois", "Lausanne",
  "Lavaux-Oron", "Morges", "Nyon", "Ouest lausannois", "Riviera-Pays-d’Enhaut")

/** Legal dwelling categories, not the number of rooms selected for inspection. */
fun neInitialRentRule(municipality: String, rooms: String): InitialRentFormRule {
  if (municipality.isBlank()) return InitialRentFormRule.ConditionalLocal
  val count = rooms.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.toInt()
    ?: return InitialRentFormRule.ConditionalLocal
  val local = java.text.Normalizer.normalize(municipality.trim().lowercase(java.util.Locale.ROOT),
    java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
  val range = when (local) {
    "latena" -> 4..5
    "la grande beroche" -> 3..5
    "neuchatel", "cornaux", "cressier", "le landeron", "boudry", "cortaillod", "milvignes",
    "rochefort", "val-de-ruz" -> 2..5
    // An arbitrary misspelling must never turn an unresolved locality into an exemption.
    else -> return InitialRentFormRule.ConditionalLocal
  }
  return if (count in range) InitialRentFormRule.Required else InitialRentFormRule.NotRequired
}

fun Protocol.freezeLegalRules(): LegalSnapshot? = resolvedLegalOverlay()?.let {
  LegalSnapshot(it.ruleSetId, it.profile.name, it.initialRentFormRule.name, it.requirements, legalSources(objectCanton))
}

fun legalSources(canton: SwissCanton?): List<String> = buildList {
  add("https://www.bwo.admin.ch/fr/formule-officielle-loyer-initial")
  add("https://www.bwo.admin.ch/de/rechtsgebiet-mietrecht")
  when (canton) {
    SwissCanton.VD -> { add("https://www.vd.ch/territoire-et-construction/logement/droit-du-bail"); add("https://www.vd.ch/territoire-et-construction/logement/politique-du-logement/penurie-de-logements") }
    SwissCanton.NE -> { add("https://rsn.ne.ch/DATA/program/books/rsne/htm/224.1.htm"); add("https://rsn.ne.ch/DATA/program/books/rsne/htm/846.02.htm") }
    SwissCanton.FR -> add("https://bdlf.fr.ch/app/fr/texts_of_law/222.3.1")
    SwissCanton.GE -> add("https://silgeneve.ch/legis/data/rsg_i4_10.htm")
    SwissCanton.TI -> add("https://www3.ti.ch/CAN/RLeggi/public/index.php/raccolta-leggi/legge/num/153")
    else -> Unit
  }
}

const val CONCILIATION_DIRECTORY = "https://www.bwo.admin.ch/dam/de/sd-web/tzHM7tFgxwhW/20260114_Schlichtungsbeh%C3%B6rden.pdf"

/** Recheck facts that changed without discarding unrelated confirmations. */
fun Protocol.invalidatedLegalRequirements(next: Protocol): Set<LegalRequirement> {
  if (address != next.address || place != next.place || objectCanton != next.objectCanton) return LegalRequirement.entries.toSet()
  return buildSet {
    if (handoverDate != next.handoverDate || contract.startDate != next.contract.startDate || contract.endDate != next.contract.endDate) {
      addAll(listOf(LegalRequirement.LeaseAndLocalTiming, LegalRequirement.DepositProof,
        LegalRequirement.TenantLiabilityInsurance, LegalRequirement.LandlordLiabilityInsurance, LegalRequirement.VaudExitReadiness))
    }
    if (contract.conclusionDate != next.contract.conclusionDate) addAll(listOf(LegalRequirement.InitialRentForm, LegalRequirement.DepositProof, LegalRequirement.LeaseAndLocalTiming))
    if (deposit != next.deposit) addAll(listOf(LegalRequirement.DepositProof, LegalRequirement.CantonalDepositHandling))
    if (details.municipality != next.details.municipality || details.district != next.details.district) addAll(listOf(LegalRequirement.InitialRentForm, LegalRequirement.LeaseAndLocalTiming))
    if (details.habitableRooms != next.details.habitableRooms) add(LegalRequirement.InitialRentForm)
    if (romandFrameworkInLease != next.romandFrameworkInLease || details.actualTransferDate != next.details.actualTransferDate ||
      details.notificationClause != next.details.notificationClause || details.notificationDays != next.details.notificationDays) add(LegalRequirement.LeaseAndLocalTiming)
  }
}
