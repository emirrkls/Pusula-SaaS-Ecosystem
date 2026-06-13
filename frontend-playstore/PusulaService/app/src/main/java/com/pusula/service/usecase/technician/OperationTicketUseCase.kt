package com.pusula.service.usecase.technician

import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.repository.TicketRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationTicketUseCase @Inject constructor(
    private val ticketRepository: TicketRepository
) {
    suspend fun loadCustomers(): List<CustomerDTO> = ticketRepository.getCustomers()

    suspend fun createQuickCustomer(
        name: String,
        phone: String,
        address: String
    ): CustomerDTO = ticketRepository.createCustomer(name, phone, address)

    suspend fun createServiceTicket(
        customerId: Long,
        description: String,
        notes: String,
        assignedTechnicianId: Long?
    ): FieldTicketDTO = ticketRepository.createTicketForCustomer(
        customerId = customerId,
        description = description,
        notes = notes,
        assignedTechnicianId = assignedTechnicianId
    )
}
