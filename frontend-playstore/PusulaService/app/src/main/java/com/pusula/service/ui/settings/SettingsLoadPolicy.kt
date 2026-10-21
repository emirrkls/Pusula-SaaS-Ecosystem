package com.pusula.service.ui.settings

import com.pusula.service.data.model.CompanyDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO

internal data class SettingsSnapshot(
    val users: List<UserDTO>,
    val vehicles: List<VehicleDTO>,
    val company: CompanyDTO
)

/** A plan-locked optional module must not prevent account/company settings from loading. */
internal suspend fun loadSettingsSnapshot(
    features: Map<String, Boolean>,
    loadUsers: suspend () -> List<UserDTO>,
    loadVehicles: suspend () -> List<VehicleDTO>,
    loadCompany: suspend () -> CompanyDTO
): SettingsSnapshot = SettingsSnapshot(
    users = loadUsers(),
    vehicles = if (features["VEHICLE_TRACKING"] == true) loadVehicles() else emptyList(),
    company = loadCompany()
)
