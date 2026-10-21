package com.pusula.service.core

import com.google.gson.Gson
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.SubscriptionContextDto
import org.junit.Assert.*
import org.junit.Test

class AuthAccessContractTest {
    private val gson = Gson()
    private fun auth(field: String, value: Boolean) = gson.fromJson(
        """{"token":"offline-fixture","role":"COMPANY_ADMIN","companyId":29,"planType":"USTA","$field":$value}""",
        AuthResponse::class.java
    )

    @Test fun expiredLoginReadsTheActualBackendReadOnlyField() {
        val response = auth("readOnly", true)
        assertEquals(true, response.isReadOnly)
        assertEquals("COMPANY_ADMIN", response.role)
        assertEquals("USTA", response.planType)
        assertEquals(29, response.companyId)
    }

    @Test fun activeLoginPreservesFalseFromTheActualBackendField() {
        assertEquals(false, auth("readOnly", false).isReadOnly)
    }

    @Test fun legacyIsReadOnlyLoginFieldRemainsCompatible() {
        for (value in listOf(true, false)) assertEquals(value, auth("isReadOnly", value).isReadOnly)
    }

    @Test fun restoredSubscriptionContextKeepsItsCanonicalField() {
        for (value in listOf(true, false)) {
            val context = gson.fromJson("""{"isReadOnly":$value}""", SubscriptionContextDto::class.java)
            assertEquals(value, context.isReadOnly)
        }
    }

    @Test fun subscriptionContextAlsoAcceptsTheAuthFieldSpelling() {
        val context = gson.fromJson("""{"readOnly":true}""", SubscriptionContextDto::class.java)
        assertEquals(true, context.isReadOnly)
    }

    @Test fun anAbsentAccessFieldRemainsUnknownInsteadOfInventingAnEntitlement() {
        assertNull(gson.fromJson("""{"token":"offline-fixture","role":"COMPANY_ADMIN"}""",
            AuthResponse::class.java).isReadOnly)
        assertNull(gson.fromJson("{}", SubscriptionContextDto::class.java).isReadOnly)
    }
}
