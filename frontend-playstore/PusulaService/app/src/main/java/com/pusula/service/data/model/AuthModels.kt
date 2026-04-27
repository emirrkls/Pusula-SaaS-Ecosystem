package com.pusula.service.data.model

data class AuthRequest(
    val username: String,
    val password: String,
    val orgCode: String? = null
)

data class RegisterRequest(
    val email: String? = null,
    val username: String? = null,
    val password: String,
    val fullName: String,
    val authType: String = "INDIVIDUAL"
)

data class GoogleAuthRequest(
    val idToken: String,
    val preferredUsername: String? = null
)

data class AuthResponse(
    val token: String,
    val role: String,
    val fullName: String? = null,
    val companyId: Int? = null,
    val companyName: String? = null,
    val planType: String? = null,
    val features: Map<String, Boolean>? = null,
    val quota: QuotaDTO? = null,
    val isReadOnly: Boolean? = null,
    val trialDaysRemaining: Int? = null
)

data class QuotaDTO(
    val maxTechnicians: Int,
    val maxCustomers: Int,
    val maxMonthlyTickets: Int,
    val maxMonthlyProposals: Int,
    val maxInventoryItems: Int,
    val storageLimitMb: Int,
    val currentTechnicians: Int,
    val currentCustomers: Int,
    val currentMonthlyTickets: Int,
    val currentMonthlyProposals: Int,
    val currentInventoryItems: Int,
    val currentStorageMb: Int
)

data class ServiceTicketDTO(
    val id: Long,
    val customerId: Long? = null,
    val description: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val scheduledDate: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    val assignedTechnicianId: Long? = null,
    val assignedTechnicianName: String? = null,
    val collectedAmount: Double? = null,
    val paymentMethod: String? = null,
    val isWarrantyCall: Boolean? = null,
    val parentTicketId: Long? = null,
    val completedAt: String? = null
)

data class PlanDTO(
    val id: Int,
    val name: String,
    val displayName: String,
    val priceMonthly: Double? = null,
    val priceYearly: Double? = null,
    val maxTechnicians: Int? = null,
    val maxCustomers: Int? = null,
    val maxMonthlyTickets: Int? = null,
    val maxMonthlyProposals: Int? = null,
    val maxInventoryItems: Int? = null,
    val storageLimitMb: Int? = null
)

data class InventoryItemDTO(
    val id: Long,
    val partName: String,
    val quantity: Int,
    val buyPrice: Double? = null,
    val sellPrice: Double? = null,
    val criticalLevel: Int? = null,
    val brand: String? = null,
    val category: String? = null,
    val barcode: String? = null
)

data class FinancialSummaryDTO(
    val totalIncome: Double,
    val totalExpense: Double,
    val netProfit: Double,
    val ticketCount: Int,
    val completedCount: Int,
    val pendingCount: Int
)
