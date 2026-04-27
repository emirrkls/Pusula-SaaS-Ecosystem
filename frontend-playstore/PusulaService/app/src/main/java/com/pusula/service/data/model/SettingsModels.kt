package com.pusula.service.data.model

data class UserDTO(
    val id: Long? = null,
    val username: String,
    val fullName: String? = null,
    val role: String,
    val password: String? = null
)

data class VehicleDTO(
    val id: Long? = null,
    val companyId: Long? = null,
    val licensePlate: String,
    val driverName: String? = null,
    val isActive: Boolean = true
)

data class CompanyDTO(
    val id: Long? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val email: String? = null,
    val logoUrl: String? = null
)
