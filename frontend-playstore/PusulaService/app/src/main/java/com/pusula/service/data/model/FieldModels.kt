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
    val collectedAmount: Double? = null,
    val paymentMethod: String? = null,
    val isWarrantyCall: Boolean? = null,
    val parentTicketId: Long? = null
)

data class UsedPartDTO(
    val id: Long? = null,
    val ticketId: Long? = null,
    val inventoryId: Long,
    val partName: String,
    val quantityUsed: Int,
    val sellingPriceSnapshot: Double,
    val sourceVehicleId: Long? = null
)

data class CollectionRequest(
    val collectedAmount: Double,
    val paymentMethod: String
)

data class SignatureRequest(
    val signature: String
)

data class TechnicianDTO(
    val id: Long,
    val fullName: String? = null,
    val role: String? = null
)
