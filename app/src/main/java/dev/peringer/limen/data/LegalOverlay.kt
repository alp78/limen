package dev.peringer.limen.data

import java.time.LocalDate
import kotlinx.serialization.Serializable

const val LEGAL_RULE_SET_ID = "ch-tenancy-2026.1"

/** A dated legal/practice check that is recorded with the handover. */
@Serializable
enum class LegalRequirement {
  LeaseAndLocalTiming,
  InitialRentForm,
  DepositProof,
  TenantLiabilityInsurance,
  LandlordLiabilityInsurance,
  VaudExitReadiness,
}

/** Missing or disputed items remain recordable, but require an explanatory note. */
@Serializable
enum class LegalCheckStatus { Confirmed, MissingOrDisputed, NotApplicable }

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
    val needsExplanation = requirements.any { record.status(it) != LegalCheckStatus.Confirmed }
    return !needsExplanation || record.note.isNotBlank()
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
  val initialRentFormRule = when {
    initialRentReferenceDate?.year != 2026 -> InitialRentFormRule.VerifyCurrent
    canton in fullInitialRentFormCantons2026 -> InitialRentFormRule.Required
    canton in conditionalInitialRentFormCantons2026 -> InitialRentFormRule.ConditionalLocal
    else -> InitialRentFormRule.NotRequired
  }

  val requirements = buildList {
    // Local termination dates, contractual appointment terms and public holidays can affect
    // practical timing even though the core handover record is governed federally.
    add(LegalRequirement.LeaseAndLocalTiming)

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

  return ResolvedLegalOverlay(
    canton = canton,
    handoverDate = date,
    initialRentReferenceDate = initialRentReferenceDate,
    profile = profile,
    initialRentFormRule = initialRentFormRule,
    requirements = requirements,
  )
}
