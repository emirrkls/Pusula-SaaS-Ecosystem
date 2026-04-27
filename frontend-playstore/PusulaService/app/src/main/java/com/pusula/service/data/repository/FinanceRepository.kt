package com.pusula.service.data.repository

import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.CategoryReportDTO
import com.pusula.service.data.model.CloseDayRequest
import com.pusula.service.data.model.CurrentAccountDTO
import com.pusula.service.data.model.DailyClosingDTO
import com.pusula.service.data.model.DailySummaryDTO
import com.pusula.service.data.model.DailyTotalDTO
import com.pusula.service.data.model.ExpenseDTO
import com.pusula.service.data.model.FixedExpenseDefinitionDTO
import com.pusula.service.data.model.MonthlySummaryDTO
import com.pusula.service.data.remote.ApiService
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun getDailySummary(date: LocalDate = LocalDate.now()): DailySummaryDTO =
        apiService.financeDailySummary(date.toString())

    suspend fun getFixedExpenses(): List<FixedExpenseDefinitionDTO> = apiService.financeFixedExpenses()

    suspend fun addExpense(
        amount: Double,
        description: String,
        category: String,
        date: LocalDate = LocalDate.now()
    ): ExpenseDTO {
        val payload = ExpenseDTO(
            amount = amount,
            description = description,
            category = category,
            date = date.toString()
        )
        return apiService.financeAddExpense(payload)
    }

    suspend fun getDailyTotals(): List<DailyTotalDTO> = apiService.financeDailyTotals()

    suspend fun getCategoryReport(
        startDate: LocalDate = LocalDate.now().withDayOfMonth(1),
        endDate: LocalDate = LocalDate.now()
    ): CategoryReportDTO = apiService.financeCategoryReport(startDate.toString(), endDate.toString())

    suspend fun getCurrentAccounts(): List<CurrentAccountDTO> = apiService.financeCurrentAccounts()

    suspend fun payDebt(accountId: Long, paymentAmount: Double, discount: Double): CurrentAccountDTO {
        val payload = mapOf(
            "paymentAmount" to paymentAmount,
            "discount" to discount
        )
        return apiService.financePayDebt(accountId, payload)
    }

    suspend fun getMonthlyArchives(): List<MonthlySummaryDTO> = apiService.financeMonthlyArchives()

    suspend fun downloadMonthlyPdf(month: String): ByteArray = apiService.financeMonthlyPdf(month).bytes()

    suspend fun closeToday(): DailyClosingDTO {
        val session = sessionManager.state.value
        val request = CloseDayRequest(
            companyId = session.companyId?.toLong(),
            date = LocalDate.now().toString(),
            userId = null
        )
        return apiService.financeCloseDay(request)
    }
}
