package com.pusula.service.core

import org.junit.Assert.*
import org.junit.Test

class PlanSubscriptionPolicyTest {
    private val session = SessionState(isAuthenticated = true, token = "offline-fixture", role = "COMPANY_ADMIN",
        companyId = 29, planType = "USTA")
    private val monthly = PlayPlanOption("usta", "usta-monthly", "fixture-offer", BillingPeriod.MONTHLY, "₺79,99", "TRY")
    private val yearly = monthly.copy(basePlanId = "usta-yearly", period = BillingPeriod.YEARLY)
    private val billing = PlayBillingUiState(options = listOf(monthly, yearly), ownershipReady = true,
        server = GoogleBillingStatus(ready = true, purchaseAllowed = true, plan = "USTA",
            receiptHash = "historical-or-active-hash", basePlanId = "usta-monthly"))
    private fun present(s: SessionState = session, b: PlayBillingUiState = billing,
                        code: String = "USTA", option: PlayPlanOption? = monthly, guide: Boolean = false) =
        PlanSubscriptionPolicy.present(s, b, code, option, guide)

    @Test fun expiredSameMonthlyPlanIsANewSubscriptionDespiteRetainedPlanAndReceipt() {
        val state = present(session.copy(isReadOnly = true))
        assertFalse(state.isCurrent)
        assertEquals("Google Play ile Abone Ol", state.actionTitle)
        assertTrue(state.canBuy)
    }

    @Test fun expiredYearlySelectionDoesNotClaimAnActivePeriodChange() {
        val state = present(session.copy(isReadOnly = true), option = yearly)
        assertFalse(state.isCurrent)
        assertEquals("Google Play ile Abone Ol", state.actionTitle)
        assertTrue(state.canBuy)
    }

    @Test fun activeSamePlanAndPeriodStayCurrentAndCannotOpenASecondCheckout() {
        val state = present()
        assertTrue(state.isCurrent)
        assertFalse(state.canBuy)
    }

    @Test fun activePeriodChangeIsStillOfferedAsAPeriodChange() {
        val state = present(option = yearly)
        assertFalse(state.isCurrent)
        assertTrue(state.canBuy)
        assertEquals("Dönemi Değiştir", state.actionTitle)
    }

    @Test fun activeUpgradeAndDowngradeStillUseReplacementWording() {
        val patron = monthly.copy(productId = "patron", basePlanId = "patron-monthly")
        assertEquals("Paketi Değiştir", present(code = "PATRON", option = patron).actionTitle)
        val prior = session.copy(planType = "PATRON")
        val server = billing.copy(server = billing.server.copy(plan = "PATRON", basePlanId = "patron-monthly"))
        assertEquals("Paketi Değiştir", present(prior, server).actionTitle)
    }

    @Test fun anActivePlanWithUnknownPeriodDoesNotInventAnotherSamePlanCheckout() {
        for (base in listOf(null, "")) {
            val state = present(b = billing.copy(server = billing.server.copy(basePlanId = base)))
            assertTrue(state.isCurrent)
            assertFalse(state.canBuy)
        }
    }

    @Test fun freePlanDoesNotTreatAHistoricalReceiptAsAnActiveReplacement() {
        val state = present(session.copy(planType = "CIRAK"))
        assertFalse(state.isCurrent)
        assertEquals("Google Play ile Abone Ol", state.actionTitle)
        assertTrue(state.canBuy)
    }

    @Test fun aTrialIsNotThePaidSamePlanSubscription() {
        val state = present(session.copy(trialDaysRemaining = 3))
        assertFalse(state.isCurrent)
        assertEquals("Google Play ile Abone Ol", state.actionTitle)
        assertTrue(state.canBuy)
    }

    @Test fun suspendedOrOtherProviderAccountsStillRespectAuthoritativePurchaseDenial() {
        for (reason in listOf("account_restricted", "another_provider_active", "server_not_ready")) {
            val state = present(session.copy(isReadOnly = true), billing.copy(
                server = billing.server.copy(purchaseAllowed = false, reason = reason)))
            assertFalse(state.canBuy)
        }
    }

    @Test fun loadingBusyUnreadyServerOrUnknownOwnershipKeepExpiredCheckoutDisabled() {
        for (state in listOf(billing.copy(loading = true), billing.copy(busy = true),
            billing.copy(server = billing.server.copy(ready = false)), billing.copy(ownershipReady = false))) {
            assertFalse(present(session.copy(isReadOnly = true), state).canBuy)
        }
    }

    @Test fun aScheduledChangeCannotBeStackedEvenOnAStaleExpiredScreen() {
        assertFalse(present(session.copy(isReadOnly = true),
            billing.copy(server = billing.server.copy(scheduledPlan = "PATRON"))).canBuy)
    }

    @Test fun missingProductAndGuideModeNeverEnableCheckout() {
        assertFalse(present(session.copy(isReadOnly = true), option = null).canBuy)
        assertFalse(present(session.copy(isReadOnly = true), guide = true).canBuy)
    }

    @Test fun missingOrNonAdminSessionCannotUseStaleBillingReadiness() {
        for (state in listOf(session.copy(isAuthenticated = false), session.copy(role = "TECHNICIAN"),
            session.copy(companyId = null), session.copy(token = null))) {
            assertFalse(present(state.copy(isReadOnly = true)).canBuy)
        }
    }
}
