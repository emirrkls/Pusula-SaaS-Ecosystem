package com.pusula.service.core

import java.util.Locale

data class PlanCardPresentation(val isCurrent: Boolean, val actionTitle: String, val canBuy: Boolean)

/** Presentation only. Checkout still performs fresh server and Play ownership preflight. */
object PlanSubscriptionPolicy {
    fun hasActivePaidAccess(session: SessionState): Boolean =
        session.isAuthenticated && !session.isReadOnly && session.trialDaysRemaining == null &&
            session.planType.trim().uppercase(Locale.ROOT) in setOf("USTA", "PATRON")

    fun present(session: SessionState, billing: PlayBillingUiState, planCode: String,
                option: PlayPlanOption?, guide: Boolean = false): PlanCardPresentation {
        // A former plan/receipt is retained for ownership/history after expiry. It is not active access.
        val activeAccess = hasActivePaidAccess(session)
        val sameActivePlan = activeAccess && session.planType.equals(planCode, ignoreCase = true)
        val exactCurrent = sameActivePlan && (billing.server.basePlanId.isNullOrBlank() ||
            option?.basePlanId == billing.server.basePlanId)
        val action = when {
            sameActivePlan && !exactCurrent -> "Dönemi Değiştir"
            activeAccess && !billing.server.receiptHash.isNullOrBlank() -> "Paketi Değiştir"
            else -> "Google Play ile Abone Ol"
        }
        return PlanCardPresentation(
            isCurrent = exactCurrent,
            actionTitle = action,
            canBuy = session.isAuthenticated && session.isAdmin && session.companyId != null &&
                !session.token.isNullOrBlank() && !exactCurrent && option != null && !guide &&
                !billing.loading && !billing.busy && billing.server.ready && billing.server.purchaseAllowed &&
                billing.ownershipReady && billing.server.scheduledPlan == null
        )
    }
}
