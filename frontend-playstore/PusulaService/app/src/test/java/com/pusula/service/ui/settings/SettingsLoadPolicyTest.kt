package com.pusula.service.ui.settings

import com.pusula.service.data.model.CompanyDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsLoadPolicyTest {
    @Test fun freePlanSkipsForbiddenVehiclesButLoadsUsersAndCompany() = runBlocking {
        val result = loadSettingsSnapshot(
            mapOf("VEHICLE_TRACKING" to false),
            { listOf(UserDTO(username = "billing-test", role = "COMPANY_ADMIN")) },
            { error("Plan-locked endpoint must not be called") },
            { CompanyDTO(name = "Billing test") }
        )
        assertEquals("billing-test", result.users.single().username)
        assertEquals("Billing test", result.company.name)
        assertTrue(result.vehicles.isEmpty())
    }

    @Test fun unknownFeatureContextFailsClosedForOptionalModule() = runBlocking {
        val result = loadSettingsSnapshot(emptyMap(), { emptyList() },
            { error("Unknown entitlement must not call vehicles") }, { CompanyDTO(name = "Test") })
        assertTrue(result.vehicles.isEmpty())
    }

    @Test fun entitledPlanLoadsVehiclesNormally() = runBlocking {
        val result = loadSettingsSnapshot(mapOf("VEHICLE_TRACKING" to true), { emptyList() },
            { listOf(VehicleDTO(licensePlate = "TEST")) }, { CompanyDTO(name = "Test") })
        assertEquals("TEST", result.vehicles.single().licensePlate)
    }

    @Test fun realNetworkFailureIsNotHiddenAsEmptyData() = runBlocking {
        val failure = IllegalStateException("network unavailable")
        try {
            loadSettingsSnapshot(mapOf("VEHICLE_TRACKING" to true), { emptyList() },
                { throw failure }, { CompanyDTO(name = "Test") })
            fail("Network failures must remain visible")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }
}
