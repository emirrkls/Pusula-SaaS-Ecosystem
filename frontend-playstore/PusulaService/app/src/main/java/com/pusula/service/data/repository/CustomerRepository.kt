package com.pusula.service.data.repository

import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.CreateTicketRequest
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getCustomers(): List<CustomerDTO> = apiService.customers()

    suspend fun createCustomer(name: String, phone: String, address: String): CustomerDTO =
        apiService.createCustomer(
            CustomerDTO(
                name = name,
                phone = phone.ifBlank { null },
                address = address.ifBlank { null }
            )
        )

    suspend fun updateCustomer(id: Long, name: String, phone: String, address: String): CustomerDTO =
        apiService.updateCustomer(
            id = id,
            request = CustomerDTO(
                id = id,
                name = name,
                phone = phone.ifBlank { null },
                address = address.ifBlank { null }
            )
        )

    suspend fun getTechnicians(): List<TechnicianDTO> = apiService.technicians()

    suspend fun createTicketForCustomer(
        customerId: Long,
        description: String,
        notes: String,
        assignedTechnicianId: Long?
    ) = apiService.createTicket(
        CreateTicketRequest(
            customerId = customerId,
            description = description,
            notes = notes.ifBlank { null },
            assignedTechnicianId = assignedTechnicianId
        )
    )
}
