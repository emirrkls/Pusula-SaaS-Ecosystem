package com.pusula.service.ui.main

import org.junit.Assert.*
import org.junit.Test

class MainModuleAccessTest {
    @Test fun accountAndOtherUngatedDestinationsRemainReachable() {
        assertTrue(canOpenMainModule(null, emptyMap()))
    }
    @Test fun missingOrFalseEntitlementDoesNotOpenLockedModule() {
        assertFalse(canOpenMainModule("FINANCE_MODULE", emptyMap()))
        assertFalse(canOpenMainModule("FINANCE_MODULE", mapOf("FINANCE_MODULE" to false)))
    }
    @Test fun entitledModuleOpensNormally() {
        assertTrue(canOpenMainModule("FINANCE_MODULE", mapOf("FINANCE_MODULE" to true)))
    }
}
