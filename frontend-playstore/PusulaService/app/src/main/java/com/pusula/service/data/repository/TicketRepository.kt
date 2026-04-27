package com.pusula.service.data.repository

import com.pusula.service.data.model.CollectionRequest
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.SignatureRequest
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.model.UsedPartDTO
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getMyAssignedTickets(): List<FieldTicketDTO> = apiService.myAssignedTickets()

    suspend fun getAllTickets(): List<FieldTicketDTO> = apiService.allTickets().map(::toFieldTicket)

    suspend fun getTicketById(ticketId: Long): FieldTicketDTO = toFieldTicket(apiService.ticketById(ticketId))

    suspend fun getUsedParts(ticketId: Long): List<UsedPartDTO> = apiService.ticketParts(ticketId)

    suspend fun addUsedPart(ticketId: Long, part: UsedPartDTO): UsedPartDTO =
        apiService.addTicketPart(ticketId, part)

    suspend fun completeService(ticketId: Long, amount: Double, method: String): FieldTicketDTO =
        apiService.completeTicket(ticketId, CollectionRequest(amount, method))

    suspend fun getTechnicians(): List<TechnicianDTO> = apiService.technicians()

    suspend fun assignTechnician(ticketId: Long, technicianId: Long): FieldTicketDTO =
        apiService.assignTechnician(ticketId, technicianId)

    suspend fun uploadSignature(ticketId: Long, base64: String): Map<String, Any> {
        apiService.uploadSignature(ticketId, SignatureRequest(base64))
        return mapOf("success" to true)
    }

    suspend fun lookupBarcode(code: String): InventoryItemDTO = apiService.inventoryByBarcode(code)

    suspend fun getInventory(): List<InventoryItemDTO> = apiService.inventory()

    private fun toFieldTicket(dto: com.pusula.service.data.model.ServiceTicketDTO): FieldTicketDTO = FieldTicketDTO(
        id = dto.id,
        customerId = dto.customerId,
        customerName = dto.customerName,
        customerPhone = dto.customerPhone,
        customerAddress = dto.customerAddress,
        assignedTechnicianId = dto.assignedTechnicianId,
        assignedTechnicianName = dto.assignedTechnicianName,
        status = dto.status,
        scheduledDate = dto.scheduledDate,
        description = dto.description,
        notes = dto.notes,
        collectedAmount = dto.collectedAmount,
        paymentMethod = dto.paymentMethod,
        isWarrantyCall = dto.isWarrantyCall,
        parentTicketId = dto.parentTicketId
    )
}
