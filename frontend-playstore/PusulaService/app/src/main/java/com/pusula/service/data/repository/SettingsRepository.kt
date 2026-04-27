package com.pusula.service.data.repository

import com.pusula.service.data.model.CompanyDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MultipartBody

@Singleton
class SettingsRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getUsers(): List<UserDTO> = apiService.users()

    suspend fun createUser(
        username: String,
        fullName: String,
        role: String,
        password: String
    ): UserDTO = apiService.createUser(
        UserDTO(
            username = username,
            fullName = fullName,
            role = role,
            password = password
        )
    )

    suspend fun updateUser(
        id: Long,
        username: String,
        fullName: String,
        role: String,
        password: String?
    ): UserDTO = apiService.updateUser(
        id = id,
        request = UserDTO(
            id = id,
            username = username,
            fullName = fullName,
            role = role,
            password = password?.takeIf { it.isNotBlank() }
        )
    )

    suspend fun deleteUser(id: Long) {
        apiService.deleteUser(id = id)
    }

    suspend fun resetPassword(id: Long, password: String) {
        apiService.resetUserPassword(id, mapOf("password" to password))
    }

    suspend fun uploadUserSignature(id: Long, filePart: MultipartBody.Part) {
        apiService.uploadUserSignature(id = id, file = filePart)
    }

    suspend fun getVehicles(): List<VehicleDTO> = apiService.vehicles()

    suspend fun createVehicle(licensePlate: String, driverName: String, isActive: Boolean): VehicleDTO =
        apiService.createVehicle(
            VehicleDTO(
                licensePlate = licensePlate,
                driverName = driverName.ifBlank { null },
                isActive = isActive
            )
        )

    suspend fun updateVehicle(
        id: Long,
        licensePlate: String,
        driverName: String,
        isActive: Boolean
    ): VehicleDTO = apiService.updateVehicle(
        id = id,
        request = VehicleDTO(
            id = id,
            licensePlate = licensePlate,
            driverName = driverName.ifBlank { null },
            isActive = isActive
        )
    )

    suspend fun deleteVehicle(id: Long) = apiService.deleteVehicle(id)

    suspend fun getCompany(): CompanyDTO = apiService.myCompany()

    suspend fun updateCompany(
        name: String,
        phone: String,
        email: String,
        address: String
    ): CompanyDTO = apiService.updateMyCompany(
        CompanyDTO(
            name = name,
            phone = phone.ifBlank { null },
            email = email.ifBlank { null },
            address = address.ifBlank { null }
        )
    )

    suspend fun uploadCompanyLogo(filePart: MultipartBody.Part): CompanyDTO =
        apiService.uploadCompanyLogo(file = filePart)
}
