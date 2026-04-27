package com.pusula.service.data.model

data class ProposalDTO(
    val id: Long? = null,
    val companyId: Long? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val preparedById: Long? = null,
    val preparedByName: String? = null,
    val status: String? = null,
    val validUntil: String? = null,
    val note: String? = null,
    val title: String? = null,
    val taxRate: Double? = null,
    val discount: Double? = null,
    val subtotal: Double? = null,
    val taxAmount: Double? = null,
    val totalPrice: Double? = null,
    val items: List<ProposalItemDTO> = emptyList()
)

data class ProposalItemDTO(
    val id: Long? = null,
    val description: String,
    val quantity: Int,
    val unitCost: Double? = null,
    val unitPrice: Double,
    val totalPrice: Double? = null
)
