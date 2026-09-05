package com.pusula.service.data.repository

import com.pusula.service.data.model.AuditLogDTO
import com.pusula.service.data.model.CollectionRequest
import com.pusula.service.data.model.CreateTicketRequest
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.ServicePhotoDTO
import com.pusula.service.data.model.SignatureRequest
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.model.UsedPartDTO
import com.pusula.service.data.model.TechnicianNoteDTO
import com.pusula.service.data.model.AddTechnicianNoteRequest
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class TicketRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getMyAssignedTickets(): List<FieldTicketDTO> = apiService.myAssignedTickets()

    suspend fun getAllTickets(): List<FieldTicketDTO> = apiService.allTickets().map(::toFieldTicket)

    suspend fun getTicketById(ticketId: Long): FieldTicketDTO = toFieldTicket(apiService.ticketById(ticketId))

    suspend fun getUsedParts(ticketId: Long): List<UsedPartDTO> = apiService.ticketParts(ticketId)

    suspend fun downloadServiceReportPdf(ticketId: Long): ByteArray =
        apiService.serviceReportPdf(ticketId).bytes()

    suspend fun addUsedPart(ticketId: Long, part: UsedPartDTO): UsedPartDTO =
        apiService.addTicketPart(ticketId, part)

    suspend fun completeService(
        ticketId: Long,
        amount: Double,
        method: String,
        laborFee: Double,
        technicianNote: String? = null
    ): FieldTicketDTO = apiService.completeTicket(
        ticketId,
        CollectionRequest(amount, method, laborFee, technicianNote?.trim()?.ifBlank { null })
    )

    suspend fun getTechnicianNotes(ticketId: Long): List<TechnicianNoteDTO> =
        apiService.technicianNotes(ticketId)

    suspend fun addTechnicianNote(ticketId: Long, content: String, important: Boolean = false): TechnicianNoteDTO =
        apiService.addTechnicianNote(ticketId, AddTechnicianNoteRequest(content.trim(), important))

    suspend fun getTechnicians(): List<TechnicianDTO> = apiService.technicians()

    suspend fun assignTechnician(ticketId: Long, technicianId: Long): FieldTicketDTO =
        apiService.assignTechnician(ticketId, technicianId)

    suspend fun uploadSignature(ticketId: Long, base64: String): Map<String, Any> {
        apiService.uploadSignature(ticketId, SignatureRequest(base64))
        return mapOf("success" to true)
    }

    suspend fun uploadServicePhoto(
        ticketId: Long,
        type: String,
        note: String,
        filePart: MultipartBody.Part
    ): ServicePhotoDTO = apiService.uploadServicePhoto(
        ticketId = ticketId,
        type = type.toRequestBody("text/plain".toMediaType()),
        note = note.toRequestBody("text/plain".toMediaType()),
        file = filePart
    )

    suspend fun getServicePhotos(ticketId: Long): List<ServicePhotoDTO> = apiService.getServicePhotos(ticketId)

    suspend fun getCompanyServicePhotos(
        type: String? = null,
        ticketId: Long? = null,
        startDate: String? = null,
        endDate: String? = null,
        query: String? = null,
        limit: Int? = null
    ): List<ServicePhotoDTO> = apiService.getCompanyServicePhotos(
        type = type,
        ticketId = ticketId,
        startDate = startDate,
        endDate = endDate,
        query = query,
        limit = limit
    )

    suspend fun deleteServicePhoto(ticketId: Long, photoId: Long) =
        apiService.deleteServicePhoto(ticketId, photoId)

    suspend fun lookupBarcode(code: String): InventoryItemDTO =
        apiService.inventoryByBarcode(code.trim())

    suspend fun getInventory(): List<InventoryItemDTO> = apiService.inventory()

    suspend fun getCustomers(): List<CustomerDTO> = apiService.customers()

    suspend fun createCustomer(name: String, phone: String, address: String): CustomerDTO =
        apiService.createCustomer(
            CustomerDTO(
                name = name,
                phone = phone.ifBlank { null },
                address = address.ifBlank { null }
            )
        )

    suspend fun createTicketForCustomer(
        customerId: Long,
        description: String,
        notes: String,
        technicianPrivateNote: String,
        assignedTechnicianId: Long?
    ): FieldTicketDTO = apiService.createTicket(
        CreateTicketRequest(
            customerId = customerId,
            description = description,
            notes = notes.ifBlank { null },
            technicianPrivateNote = technicianPrivateNote.ifBlank { null },
            assignedTechnicianId = assignedTechnicianId
        )
    )

    suspend fun getTicketTimeline(ticketId: Long): List<AuditLogDTO> =
        apiService.ticketTimeline(ticketId)

    suspend fun updateTicketStatus(ticketId: Long, status: String): FieldTicketDTO =
        toFieldTicket(apiService.updateTicket(ticketId, mapOf("status" to status)))

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
        technicianPrivateNote = dto.technicianPrivateNote,
        collectedAmount = dto.collectedAmount,
        paymentMethod = dto.paymentMethod,
        isWarrantyCall = dto.isWarrantyCall,
        parentTicketId = dto.parentTicketId,
        createdAt = dto.createdAt
    )
}
