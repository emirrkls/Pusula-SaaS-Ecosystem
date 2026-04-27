package com.pusula.service.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.data.model.CategoryReportDTO
import com.pusula.service.data.model.CurrentAccountDTO
import com.pusula.service.data.model.DailySummaryDTO
import com.pusula.service.data.model.DailyTotalDTO
import com.pusula.service.data.model.ExpenseItemDTO
import com.pusula.service.data.model.FixedExpenseDefinitionDTO
import com.pusula.service.data.model.MonthlySummaryDTO
import com.pusula.service.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinanceUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val summary: DailySummaryDTO = DailySummaryDTO(),
    val expenses: List<ExpenseItemDTO> = emptyList(),
    val fixedExpenses: List<FixedExpenseDefinitionDTO> = emptyList(),
    val dailyTotals: List<DailyTotalDTO> = emptyList(),
    val categoryReport: CategoryReportDTO = CategoryReportDTO(),
    val currentAccounts: List<CurrentAccountDTO> = emptyList(),
    val monthlyArchives: List<MonthlySummaryDTO> = emptyList(),
    val overdueFixedCount: Int = 0,
    val upcomingFixedCount: Int = 0,
    val addingExpense: Boolean = false,
    val payingAccountId: Long? = null,
    val closingDay: Boolean = false,
    val downloadingMonth: String? = null,
    val debtPaymentSavedAt: Long? = null
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val financeRepository: FinanceRepository
) : ViewModel() {
    private data class FinanceLoadBundle(
        val summary: DailySummaryDTO,
        val fixed: List<FixedExpenseDefinitionDTO>,
        val totals: List<DailyTotalDTO>,
        val category: CategoryReportDTO,
        val accounts: List<CurrentAccountDTO>,
        val archives: List<MonthlySummaryDTO>
    )

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    fun loadDashboard(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            val summary = financeRepository.getDailySummary()
            val fixed = financeRepository.getFixedExpenses()
            val totals = financeRepository.getDailyTotals()
            val category = financeRepository.getCategoryReport()
            val accounts = financeRepository.getCurrentAccounts()
            val archives = financeRepository.getMonthlyArchives()
            FinanceLoadBundle(summary, fixed, totals, category, accounts, archives)
        }.onSuccess { bundle ->
            val summary = bundle.summary
            val fixed = bundle.fixed
            val totals = bundle.totals
            val category = bundle.category
            val accounts = bundle.accounts
            val archives = bundle.archives
            val expenses = summary.expenseDetails
            val currentDay = LocalDate.now().dayOfMonth
            val overdue = fixed.count { !it.paidThisMonth && (it.dayOfMonth ?: 99) < currentDay }
            val upcoming = fixed.count {
                val due = it.dayOfMonth ?: return@count false
                !it.paidThisMonth && due in currentDay..(currentDay + 3)
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    addingExpense = false,
                    summary = summary,
                    expenses = expenses,
                    fixedExpenses = fixed,
                    dailyTotals = totals,
                    categoryReport = category,
                    currentAccounts = accounts,
                    monthlyArchives = archives,
                    overdueFixedCount = overdue,
                    upcomingFixedCount = upcoming,
                    payingAccountId = null,
                    closingDay = false,
                    downloadingMonth = null
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    addingExpense = false,
                    payingAccountId = null,
                    closingDay = false,
                    downloadingMonth = null,
                    error = throwable.message ?: "Finans verileri yüklenemedi"
                )
            }
        }
    }

    fun addExpense(amount: Double, description: String, category: String) = viewModelScope.launch {
        _uiState.update { it.copy(addingExpense = true, error = null) }
        runCatching { financeRepository.addExpense(amount, description, category) }
            .onSuccess { loadDashboard(refresh = true) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        addingExpense = false,
                        error = throwable.message ?: "Gider eklenemedi"
                    )
                }
            }
    }

    fun payDebt(accountId: Long, paymentAmount: Double, discount: Double) = viewModelScope.launch {
        _uiState.update { it.copy(payingAccountId = accountId, error = null) }
        runCatching { financeRepository.payDebt(accountId, paymentAmount, discount) }
            .onSuccess {
                _uiState.update { it.copy(debtPaymentSavedAt = System.currentTimeMillis()) }
                loadDashboard(refresh = true)
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        payingAccountId = null,
                        error = throwable.message ?: "Cari ödeme kaydedilemedi"
                    )
                }
            }
    }

    fun consumeDebtPaymentSavedEvent() {
        _uiState.update { it.copy(debtPaymentSavedAt = null) }
    }

    suspend fun downloadMonthlyPdf(month: String): ByteArray {
        _uiState.update { it.copy(downloadingMonth = month, error = null) }
        return runCatching { financeRepository.downloadMonthlyPdf(month) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        downloadingMonth = null,
                        error = throwable.message ?: "Aylık PDF indirilemedi"
                    )
                }
            }
            .getOrThrow()
            .also { _uiState.update { state -> state.copy(downloadingMonth = null) } }
    }

    fun closeDay() = viewModelScope.launch {
        _uiState.update { it.copy(closingDay = true, error = null) }
        runCatching { financeRepository.closeToday() }
            .onSuccess { loadDashboard(refresh = true) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        closingDay = false,
                        error = throwable.message ?: "Gün kapatma başarısız"
                    )
                }
            }
    }
}
