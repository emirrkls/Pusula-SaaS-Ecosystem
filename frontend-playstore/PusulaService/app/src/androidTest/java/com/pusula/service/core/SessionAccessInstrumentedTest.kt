package com.pusula.service.core

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.SubscriptionContextDto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Offline storage/transition tests on the dedicated fixture AVD, never the signed-in Play AVD. */
class SessionAccessInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: SessionManager
    private val gson = Gson()

    @Before fun before() { session = SessionManager(context); session.logout() }
    @After fun after() { session.logout() }

    private fun login(field: String, value: Boolean) = gson.fromJson(
        """{"token":"offline-session-fixture","role":"COMPANY_ADMIN","companyId":29,"planType":"USTA","$field":$value}""",
        AuthResponse::class.java)
    private fun subscription(value: Boolean) = gson.fromJson(
        """{"planType":"USTA","isReadOnly":$value}""", SubscriptionContextDto::class.java)

    @Test fun expiredFreshLoginAndColdRestoreBothRemainReadOnlyForEitherBackendField() {
        for (field in listOf("readOnly", "isReadOnly")) {
            session.configure(login(field, true))
            assertTrue(session.state.value.isAuthenticated)
            assertTrue(session.state.value.isReadOnly)
            val restored = SessionManager(context)
            restored.tryRestoreSession()
            assertTrue(restored.state.value.isAuthenticated)
            assertEquals("COMPANY_ADMIN", restored.state.value.role)
            assertTrue(restored.state.value.isReadOnly)
            restored.mergeSubscription(subscription(true))
            assertTrue(restored.state.value.isReadOnly)
        }
    }

    @Test fun activeAccessReturnsOnlyWhenAuthoritativelyKnownAfterColdRestore() {
        for (field in listOf("readOnly", "isReadOnly")) {
            session.configure(login(field, false))
            assertFalse(session.state.value.isReadOnly)
            val restored = SessionManager(context)
            restored.tryRestoreSession()
            assertTrue("Cached role must not unlock writes before context arrives", restored.state.value.isReadOnly)
            restored.mergeSubscription(subscription(false))
            assertFalse(restored.state.value.isReadOnly)
        }
    }

    @Test fun missingAccessFieldFailsClosedAndAnUnknownRefreshDoesNotUnlockWrites() {
        session.configure(gson.fromJson("""{"token":"offline-session-fixture","role":"COMPANY_ADMIN"}""", AuthResponse::class.java))
        assertTrue(session.state.value.isReadOnly)
        session.mergeSubscription(gson.fromJson("{}", SubscriptionContextDto::class.java))
        assertTrue(session.state.value.isReadOnly)
        session.mergeSubscription(subscription(false))
        assertFalse(session.state.value.isReadOnly)
        session.mergeSubscription(subscription(true))
        assertTrue(session.state.value.isReadOnly)
    }

    @Test fun anAuthoritativePaidContextClearsAFormerTrialMarker() {
        session.configure(login("readOnly", false).copy(trialDaysRemaining = 3))
        assertFalse(PlanSubscriptionPolicy.hasActivePaidAccess(session.state.value))
        session.mergeSubscription(subscription(false))
        assertNull(session.state.value.trialDaysRemaining)
        assertTrue(PlanSubscriptionPolicy.hasActivePaidAccess(session.state.value))
    }
}
