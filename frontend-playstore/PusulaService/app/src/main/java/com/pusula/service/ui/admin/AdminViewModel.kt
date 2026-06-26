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
import com.pusula.service.data.model.ServicePhotoDTO
import com.pusula.service.data.model.TechnicianStat
import com.pusula.service.data.repository.AdminRepository
import com.pusula.service.data.repository.TicketRepository
import com.pusula.service.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val barcodeLookupItem: InventoryItemDTO? = null,
    val serviceQualityPhotos: List<ServicePhotoDTO> = emptyList(),
    val serviceQualityFilterType: String? = null,
    val serviceQualityFilterTicketId: Long? = null,
    val serviceQualityFilterStartDate: String? = null,
    val serviceQualityFilterEndDate: String? = null
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

    fun loadFieldRadar() = viewModelScope.launch {
        _uiState.update { it.copy(loading = true, error = null) }
        runCatching { adminRepository.getFieldRadar() }
            .onSuccess { pins ->
                _uiState.update { it.copy(loading = false, fieldPins = pins, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(loading = false, error = throwable.toUserMessage("Saha radarı yüklenemedi"))
                }
            }
    }

    fun loadDashboard(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            val today = LocalDate.now().toString()
            val todayQualityPhotos = runCatching {
                ticketRepository.getCompanyServicePhotos(
                    startDate = today,
                    endDate = today
                )
            }.getOrElse { emptyList() }
            AdminUiState(
                kpis = adminRepository.getDashboardKPIs(),
                technicianStats = adminRepository.getTechnicianStats(),
                profitAnalysis = adminRepository.getProfitAnalysis(),
                quotaStatus = adminRepository.getQuotaStatus(),
                fieldPins = adminRepository.getFieldRadar(),
                inventory = ticketRepository.getInventory(),
                serviceQualityPhotos = todayQualityPhotos
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
                    inventory = loaded.inventory,
                    serviceQualityPhotos = loaded.serviceQualityPhotos
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = throwable.toUserMessage("Yönetici paneli yüklenemedi")
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
                    error = throwable.toUserMessage("Stok kalemi eklenemedi")
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
                    error = throwable.toUserMessage("Stok kalemi güncellenemedi")
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
                        error = throwable.toUserMessage("Stok kalemi silinemedi")
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

    fun loadServiceQualityPhotos(
        type: String? = null,
        ticketId: Long? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null
    ) = viewModelScope.launch {
        _uiState.update {
            it.copy(
                serviceQualityFilterType = type,
                serviceQualityFilterTicketId = ticketId,
                serviceQualityFilterStartDate = startDate,
                serviceQualityFilterEndDate = endDate
            )
        }
        runCatching {
            ticketRepository.getCompanyServicePhotos(
                type = type,
                ticketId = ticketId,
                startDate = startDate,
                endDate = endDate,
                limit = limit
            )
        }
            .onSuccess { photos ->
                _uiState.update { it.copy(serviceQualityPhotos = photos, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(error = throwable.toUserMessage("Servis kalite görselleri yüklenemedi"))
                }
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
