package com.pusula.service.data.model

data class FieldTicketDTO(
    val id: Long,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    val customerCoordinates: String? = null,
    val customerBalance: Double? = null,
    val assignedTechnicianId: Long? = null,
    val assignedTechnicianName: String? = null,
    val status: String? = null,
    val scheduledDate: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val technicianPrivateNote: String? = null,
    val collectedAmount: Double? = null,
    val paymentMethod: String? = null,
    val isWarrantyCall: Boolean? = null,
    val parentTicketId: Long? = null,
    val createdAt: String? = null
)

data class UsedPartDTO(
    val id: Long? = null,
    val ticketId: Long? = null,
    val inventoryId: Long,
    val partName: String,
    val quantityUsed: Double,
    val sellingPriceSnapshot: Double,
    val unitOfMeasure: String? = null,
    val sourceVehicleId: Long? = null,
    val clientRequestId: String? = null
)

data class CollectionRequest(
    val collectedAmount: Double,
    val paymentMethod: String,
    val laborFee: Double? = null,
    val technicianNote: String? = null
)

data class TechnicianNoteDTO(
    val id: Long,
    val serviceTicketId: Long,
    val authorUserId: Long? = null,
    val authorName: String,
    val noteType: String,
    val content: String,
    val createdAt: String
)

data class AddTechnicianNoteRequest(val content: String)

data class SignatureRequest(
    val signature: String
)

data class TechnicianDTO(
    val id: Long,
    val fullName: String? = null,
    val role: String? = null
)

data class ServicePhotoDTO(
    val id: Long,
    val ticketId: Long,
    val url: String,
    val type: String,
    val note: String? = null,
    val uploadedByName: String? = null,
    val uploadedAt: String? = null,
    val serviceDate: String? = null,
    val customerName: String? = null,
    val ticketDescription: String? = null
)

data class AuditLogDTO(
    val id: Long? = null,
    val userName: String? = null,
    val actionType: String? = null,
    val entityType: String? = null,
    val description: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: String? = null
)
