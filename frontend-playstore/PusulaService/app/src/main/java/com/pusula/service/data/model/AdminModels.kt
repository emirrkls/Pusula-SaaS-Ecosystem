package com.pusula.service.data.model

data class DashboardKPIs(
    val monthlyRevenue: Double? = null,
    val outstandingDebt: Double? = null,
    val netProfit: Double? = null,
    val profitMargin: Double? = null,
    val activeTickets: Int? = null,
    val completedThisMonth: Int? = null,
    val inventoryValue: Double? = null
)

data class TechnicianStat(
    val userId: Long,
    val fullName: String? = null,
    val completedToday: Int? = null,
    val completedThisMonth: Int? = null,
    val collectedToday: Double? = null,
    val collectedThisMonth: Double? = null,
    val activeTickets: Int? = null,
    val lastLocation: String? = null
)

data class ProfitAnalysis(
    val totalCostOfGoodsSold: Double? = null,
    val totalRevenueFromParts: Double? = null,
    val grossProfit: Double? = null,
    val grossMarginPercent: Double? = null,
    val topProfitableParts: List<PartProfit>? = null
)

data class PartProfit(
    val partName: String,
    val buyPrice: Double? = null,
    val sellPrice: Double? = null,
    val quantitySold: Int? = null,
    val totalProfit: Double? = null,
    val marginPercent: Double? = null
)

data class QuotaStatus(
    val planName: String? = null,
    val quotas: List<QuotaItem>? = null
)

data class QuotaItem(
    val featureKey: String,
    val featureLabel: String? = null,
    val currentUsage: Int? = null,
    val limit: Int? = null,
    val usagePercent: Double? = null
)

data class FieldPin(
    val technicianId: Long,
    val technicianName: String? = null,
    val coordinates: String? = null,
    val customerName: String? = null,
    val ticketStatus: String? = null,
    val ticketId: Long? = null
)
