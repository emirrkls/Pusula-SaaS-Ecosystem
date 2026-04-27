package com.pusula.service.data.model

import com.google.gson.annotations.SerializedName

data class DailySummaryDTO(
    val date: String? = null,
    val totalIncome: Double? = null,
    val totalExpense: Double? = null,
    val netCash: Double? = null,
    @SerializedName("isClosed")
    val isClosed: Boolean? = null,
    @SerializedName("closed")
    val closed: Boolean? = null,
    val incomeDetails: List<IncomeItemDTO> = emptyList(),
    val expenseDetails: List<ExpenseItemDTO> = emptyList()
)

data class IncomeItemDTO(
    val ticketId: Long? = null,
    val customerName: String? = null,
    val amount: Double? = null
)

data class ExpenseItemDTO(
    val id: Long? = null,
    val category: String? = null,
    val description: String? = null,
    val amount: Double? = null
)

data class FixedExpenseDefinitionDTO(
    val id: Long? = null,
    val name: String? = null,
    val defaultAmount: Double? = null,
    val category: String? = null,
    val dayOfMonth: Int? = null,
    val description: String? = null,
    @SerializedName("isPaidThisMonth")
    val paidThisMonth: Boolean = false
)

data class ExpenseDTO(
    val id: Long? = null,
    val amount: Double,
    val description: String,
    val date: String,
    val category: String,
    val fixedExpenseId: Long? = null
)

data class DailyTotalDTO(
    val date: String? = null,
    val income: Double? = null,
    val expense: Double? = null
)

data class CategoryReportDTO(
    val breakdown: Map<String, Double> = emptyMap()
)

data class MonthlySummaryDTO(
    val period: String? = null,
    val displayPeriod: String? = null,
    val totalIncome: Double? = null,
    val totalExpense: Double? = null,
    val netProfit: Double? = null,
    val carryOver: Double? = null
)

data class CurrentAccountDTO(
    val id: Long? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val balance: Double? = null,
    val lastUpdated: String? = null
)

data class CloseDayRequest(
    val companyId: Long? = null,
    val date: String,
    val userId: Long? = null
)

data class DailyClosingDTO(
    val id: Long? = null,
    val companyId: Long? = null,
    val date: String? = null,
    val totalIncome: Double? = null,
    val totalExpense: Double? = null,
    val netCash: Double? = null,
    val closedByUserId: Long? = null,
    val createdAt: String? = null
)
