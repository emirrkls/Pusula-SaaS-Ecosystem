package com.pusula.service.data.model

data class CustomerDTO(
    val id: Long? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val coordinates: String? = null
)

data class CreateTicketRequest(
    val customerId: Long,
    val description: String,
    val notes: String? = null,
    val status: String = "PENDING",
    val assignedTechnicianId: Long? = null
)
