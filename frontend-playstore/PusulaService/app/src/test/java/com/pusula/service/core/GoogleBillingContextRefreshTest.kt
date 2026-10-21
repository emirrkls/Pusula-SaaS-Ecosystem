package com.pusula.service.core

import com.pusula.service.data.model.SubscriptionContextDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoogleBillingContextRefreshTest {
    @Test fun foregroundExpiryRefreshesAccessBeforeExposingReadyBillingState() = runBlocking {
        val events = mutableListOf<String>()
        var session = SessionState(isAuthenticated = true, token = "offline-fixture", role = "COMPANY_ADMIN",
            companyId = 29, planType = "USTA")
        val result = refreshGoogleBillingContext({ true },
            { events += "load-access"; SubscriptionContextDto(planType = "USTA", isReadOnly = true) },
            { events += "apply-access"; session = session.copy(isReadOnly = it.isReadOnly!!) },
            { assertTrue(session.isReadOnly); events += "load-status"; GoogleBillingStatus(ready = true, purchaseAllowed = true) },
            { assertTrue(session.isReadOnly); events += "publish-status" })
        assertTrue(result.ready)
        assertEquals(listOf("load-access", "apply-access", "load-status", "publish-status"), events)
    }

    @Test fun aChangedAccountWhileLoadingContextCannotApplyAnyOldAccountData() = runBlocking {
        var same = true
        try {
            refreshGoogleBillingContext({ same },
                { same = false; SubscriptionContextDto(isReadOnly = false) },
                { fail("Old access applied to new account") },
                { fail("Old status requested"); GoogleBillingStatus() },
                { fail("Old status published") })
            fail("Account change must cancel refresh")
        } catch (_: CancellationException) { }
    }

    @Test fun aChangedAccountWhileLoadingStatusCannotPublishOldReadiness() = runBlocking {
        var same = true
        var applied = false
        try {
            refreshGoogleBillingContext({ same }, { SubscriptionContextDto(isReadOnly = true) },
                { applied = true }, { same = false; GoogleBillingStatus(ready = true) },
                { fail("Old account readiness published") })
            fail("Account change must cancel refresh")
        } catch (_: CancellationException) { }
        assertTrue(applied)
    }

    @Test fun anUnknownAccessContractNeverEnablesPurchasingReadiness() = runBlocking {
        try {
            refreshGoogleBillingContext({ true }, { SubscriptionContextDto() },
                { fail("Unknown access must not be applied") },
                { fail("Unknown entitlement must not proceed"); GoogleBillingStatus() },
                { fail("Unknown entitlement must not become ready") })
            fail("Unknown access must fail closed")
        } catch (error: IOException) { assertEquals("SUBSCRIPTION_ACCESS_UNAVAILABLE", error.message) }
    }

    @Test fun anAccessRequestFailureNeverPublishesReadyStatus() = runBlocking {
        try {
            refreshGoogleBillingContext({ true }, { throw IOException("offline") },
                { fail("No access response") }, { fail("No billing request before access"); GoogleBillingStatus() },
                { fail("No readiness on failure") })
            fail("Must propagate failure")
        } catch (_: IOException) { }
    }

    @Test fun aStatusOutageDoesNotUndoTheAlreadyKnownExpiredAccess() = runBlocking {
        var readOnly = false
        try {
            refreshGoogleBillingContext({ true }, { SubscriptionContextDto(isReadOnly = true) },
                { readOnly = it.isReadOnly!! }, { throw IOException("status offline") },
                { fail("No readiness on failure") })
            fail("Must propagate failure")
        } catch (_: IOException) { }
        assertTrue(readOnly)
    }
}
