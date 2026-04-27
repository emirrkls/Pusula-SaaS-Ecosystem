package com.pusula.service.ui.admin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.core.BillingManager
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.DashboardKPIs
import com.pusula.service.data.model.FieldPin
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.ProfitAnalysis
import com.pusula.service.data.model.QuotaStatus
import com.pusula.service.data.model.TechnicianStat
import com.pusula.service.data.repository.AdminRepository
import com.pusula.service.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val kpis: DashboardKPIs = DashboardKPIs(),
    val technicianStats: List<TechnicianStat> = emptyList(),
    val profitAnalysis: ProfitAnalysis = ProfitAnalysis(),
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val fieldPins: List<FieldPin> = emptyList(),
    val inventory: List<InventoryItemDTO> = emptyList(),
    val catalogQuery: String = "",
    val creatingInventory: Boolean = false,
    val updatingInventoryId: Long? = null,
    val deletingInventoryId: Long? = null,
    val inventoryCreatedAt: Long? = null,
    val inventoryUpdatedAt: Long? = null,
    val inventoryDeletedAt: Long? = null,
    val barcodeLookupLoading: Boolean = false,
    val barcodeLookupCode: String? = null,
    val barcodeLookupItem: InventoryItemDTO? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val ticketRepository: TicketRepository,
    val sessionManager: SessionManager,
    private val billingManager: BillingManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun loadDashboard(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            val kpisDeferred = async { adminRepository.getDashboardKPIs() }
            val technicianDeferred = async { adminRepository.getTechnicianStats() }
            val profitDeferred = async { adminRepository.getProfitAnalysis() }
            val quotaDeferred = async { adminRepository.getQuotaStatus() }
            val radarDeferred = async { adminRepository.getFieldRadar() }
            val inventoryDeferred = async { ticketRepository.getInventory() }
            AdminUiState(
                kpis = kpisDeferred.await(),
                technicianStats = technicianDeferred.await(),
                profitAnalysis = profitDeferred.await(),
                quotaStatus = quotaDeferred.await(),
                fieldPins = radarDeferred.await(),
                inventory = inventoryDeferred.await()
            )
        }.onSuccess { loaded ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    kpis = loaded.kpis,
                    technicianStats = loaded.technicianStats,
                    profitAnalysis = loaded.profitAnalysis,
                    quotaStatus = loaded.quotaStatus,
                    fieldPins = loaded.fieldPins,
                    inventory = loaded.inventory
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = throwable.message ?: "Yönetici paneli yüklenemedi"
                )
            }
        }
    }

    fun setCatalogQuery(query: String) {
        _uiState.update { it.copy(catalogQuery = query) }
    }

    fun createInventoryItem(
        partName: String,
        quantity: Int,
        buyPrice: Double?,
        sellPrice: Double?,
        criticalLevel: Int?,
        brand: String?,
        category: String?,
        barcode: String?
    ) = viewModelScope.launch {
        _uiState.update { it.copy(creatingInventory = true, error = null) }
        runCatching {
            adminRepository.createInventoryItem(
                partName = partName,
                quantity = quantity,
                buyPrice = buyPrice,
                sellPrice = sellPrice,
                criticalLevel = criticalLevel,
                brand = brand,
                category = category,
                barcode = barcode
            )
        }.onSuccess { created ->
            _uiState.update {
                it.copy(
                    creatingInventory = false,
                    inventory = listOf(created) + it.inventory,
                    inventoryCreatedAt = System.currentTimeMillis()
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    creatingInventory = false,
                    error = throwable.message ?: "Stok kalemi eklenemedi"
                )
            }
        }
    }

    fun consumeInventoryCreatedEvent() {
        _uiState.update { it.copy(inventoryCreatedAt = null) }
    }

    fun updateInventoryItem(
        id: Long,
        partName: String,
        quantity: Int,
        buyPrice: Double?,
        sellPrice: Double?,
        criticalLevel: Int?,
        brand: String?,
        category: String?,
        barcode: String?
    ) = viewModelScope.launch {
        _uiState.update { it.copy(updatingInventoryId = id, error = null) }
        runCatching {
            adminRepository.updateInventoryItem(
                id = id,
                partName = partName,
                quantity = quantity,
                buyPrice = buyPrice,
                sellPrice = sellPrice,
                criticalLevel = criticalLevel,
                brand = brand,
                category = category,
                barcode = barcode
            )
        }.onSuccess { updated ->
            _uiState.update { state ->
                state.copy(
                    updatingInventoryId = null,
                    inventory = state.inventory.map { if (it.id == id) updated else it },
                    inventoryUpdatedAt = System.currentTimeMillis()
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    updatingInventoryId = null,
                    error = throwable.message ?: "Stok kalemi güncellenemedi"
                )
            }
        }
    }

    fun deleteInventoryItem(id: Long) = viewModelScope.launch {
        _uiState.update { it.copy(deletingInventoryId = id, error = null) }
        runCatching { adminRepository.deleteInventoryItem(id) }
            .onSuccess {
                _uiState.update { state ->
                    state.copy(
                        deletingInventoryId = null,
                        inventory = state.inventory.filterNot { it.id == id },
                        inventoryDeletedAt = System.currentTimeMillis()
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        deletingInventoryId = null,
                        error = throwable.message ?: "Stok kalemi silinemedi"
                    )
                }
            }
    }

    fun consumeInventoryUpdatedEvent() {
        _uiState.update { it.copy(inventoryUpdatedAt = null) }
    }

    fun consumeInventoryDeletedEvent() {
        _uiState.update { it.copy(inventoryDeletedAt = null) }
    }

    fun lookupInventoryByBarcode(code: String) = viewModelScope.launch {
        _uiState.update {
            it.copy(
                barcodeLookupLoading = true,
                barcodeLookupCode = code,
                barcodeLookupItem = null,
                error = null
            )
        }
        runCatching { adminRepository.findInventoryByBarcode(code) }
            .onSuccess { found ->
                _uiState.update {
                    it.copy(
                        barcodeLookupLoading = false,
                        barcodeLookupItem = found
                    )
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(
                        barcodeLookupLoading = false,
                        barcodeLookupItem = null
                    )
                }
            }
    }

    fun clearBarcodeLookupState() {
        _uiState.update {
            it.copy(
                barcodeLookupLoading = false,
                barcodeLookupCode = null,
                barcodeLookupItem = null
            )
        }
    }

    fun purchasePlan(planName: String) {
        billingManager.purchase(planName)
    }

    fun bindBillingActivity(activity: Activity) {
        billingManager.bindActivity(activity)
        billingManager.queryProducts()
        billingManager.checkActiveSubscriptions()
    }
}
