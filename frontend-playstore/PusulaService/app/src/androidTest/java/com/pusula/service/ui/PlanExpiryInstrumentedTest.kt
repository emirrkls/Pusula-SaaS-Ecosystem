package com.pusula.service.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pusula.service.core.*
import com.pusula.service.data.model.PlanDTO
import com.pusula.service.ui.admin.PlanSubscriptionCard
import com.pusula.service.ui.main.ReadOnlyBanner
import com.pusula.service.ui.theme.PusulaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production card, fake callbacks/data only: no BillingClient or production account is created. */
class PlanExpiryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val plan = PlanDTO(id = 1, name = "USTA", displayName = "Usta Paketi")
    private val session = SessionState(isAuthenticated = true, role = "COMPANY_ADMIN", companyId = 29,
        token = "offline-fixture", planType = "USTA")
    private val monthly = PlayPlanOption("usta", "usta-monthly", "fixture-offer", BillingPeriod.MONTHLY, "₺79,99", "TRY")
    private val yearly = monthly.copy(basePlanId = "usta-yearly", period = BillingPeriod.YEARLY, formattedPrice = "₺799,99")
    private val billing = PlayBillingUiState(options = listOf(monthly, yearly), ownershipReady = true,
        server = GoogleBillingStatus(ready = true, purchaseAllowed = true, plan = "USTA",
            receiptHash = "historical-or-active-hash", basePlanId = "usta-monthly"))

    @Test fun accessPendingOrRestrictedBannerDoesNotInventAnExpiredSubscriptionReason() {
        compose.setContent { PusulaTheme { ReadOnlyBanner() } }
        compose.onNodeWithText("Hesabınız yalnızca görüntüleme modunda.").assertIsDisplayed()
        compose.onNodeWithText("Aboneliğiniz sona erdi", substring = true).assertDoesNotExist()
    }

    @Test fun expiredSamePlanShowsNewSubscriptionAndItsCallbackIsReachable() {
        var selections = 0
        compose.setContent { PusulaTheme { PlanSubscriptionCard(plan, session.copy(isReadOnly = true), billing,
            BillingPeriod.MONTHLY, onPick = { selections++ }) } }
        compose.onNodeWithText("Aktif Paket").assertDoesNotExist()
        compose.onNodeWithText("Mevcut").assertDoesNotExist()
        compose.onNodeWithTag("plan.purchase.usta").assertIsEnabled().assertTextContains("Google Play ile Abone Ol").performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun activeSamePeriodRemainsDisabledButBecomesPurchasableAfterExpiry() {
        var state by mutableStateOf(session)
        compose.setContent { PusulaTheme { PlanSubscriptionCard(plan, state, billing, BillingPeriod.MONTHLY, onPick = {}) } }
        compose.onNodeWithTag("plan.purchase.usta").assertIsNotEnabled().assertTextContains("Aktif Paket")
        compose.onNodeWithText("Mevcut").assertExists()
        compose.runOnIdle { state = state.copy(isReadOnly = true) }
        compose.onNodeWithTag("plan.purchase.usta").assertIsEnabled().assertTextContains("Google Play ile Abone Ol")
        compose.onNodeWithText("Mevcut").assertDoesNotExist()
    }

    @Test fun expiryNeverBypassesAccountRestrictionOrGuideReadOnlyGate() {
        var guide by mutableStateOf(false)
        var status by mutableStateOf(billing.copy(server = billing.server.copy(purchaseAllowed = false, reason = "account_restricted")))
        compose.setContent { PusulaTheme { PlanSubscriptionCard(plan, session.copy(isReadOnly = true), status,
            BillingPeriod.YEARLY, guide = guide, onPick = {}) } }
        compose.onNodeWithTag("plan.purchase.usta").assertIsNotEnabled()
        compose.runOnIdle { status = billing; guide = true }
        compose.onNodeWithTag("plan.purchase.usta").assertIsNotEnabled()
        compose.runOnIdle { guide = false }
        compose.onNodeWithTag("plan.purchase.usta").assertIsEnabled().assertTextContains("Google Play ile Abone Ol")
    }
}
