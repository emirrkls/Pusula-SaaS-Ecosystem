package com.pusula.service.ui.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.model.UsedPartDTO
import com.pusula.service.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TicketUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val tickets: List<FieldTicketDTO> = emptyList(),
    val selectedTicket: FieldTicketDTO? = null,
    val technicians: List<TechnicianDTO> = emptyList(),
    val usedParts: List<UsedPartDTO> = emptyList(),
    val inventory: List<InventoryItemDTO> = emptyList(),
    val barcodeItem: InventoryItemDTO? = null,
    val signatureSaved: Boolean = false,
    val assigningTicketId: Long? = null,
    val bulkAssigning: Boolean = false,
    val usedPartAddedTicketId: Long? = null,
    val serviceCompletedTicketId: Long? = null
)

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val repository: TicketRepository,
    val sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TicketUiState())
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()

    fun loadTickets(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            val session = sessionManager.state.value
            if (session.isAdmin) repository.getAllTickets() else repository.getMyAssignedTickets()
        }.onSuccess { tickets ->
            _uiState.update { it.copy(loading = false, refreshing = false, tickets = tickets) }
            if (sessionManager.state.value.isAdmin) {
                loadTechnicians()
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = throwable.message ?: "İş emirleri yüklenemedi"
                )
            }
        }
    }

    private fun loadTechnicians() = viewModelScope.launch {
        runCatching { repository.getTechnicians() }
            .onSuccess { technicians ->
                _uiState.update { it.copy(technicians = technicians, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message ?: "Teknisyen listesi alınamadı") }
            }
    }

    fun selectTicket(ticketId: Long) = viewModelScope.launch {
        val fromList = _uiState.value.tickets.firstOrNull { it.id == ticketId }
        val selected = fromList ?: runCatching { repository.getTicketById(ticketId) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        selectedTicket = null,
                        error = throwable.message ?: "İş emri detayları alınamadı"
                    )
                }
            }
            .getOrNull()

        if (selected == null) return@launch
        _uiState.update { state ->
            state.copy(
                selectedTicket = selected,
                tickets = if (state.tickets.any { it.id == selected.id }) state.tickets else (state.tickets + selected)
            )
        }
        if (sessionManager.state.value.isAdmin && _uiState.value.technicians.isEmpty()) {
            loadTechnicians()
        }
        loadUsedParts(ticketId)
    }

    fun loadUsedParts(ticketId: Long) = viewModelScope.launch {
        runCatching { repository.getUsedParts(ticketId) }
            .onSuccess { _uiState.update { s -> s.copy(usedParts = it, error = null) } }
            .onFailure { _uiState.update { s -> s.copy(error = it.message ?: "Parçalar alınamadı") } }
    }

    fun addUsedPart(ticketId: Long, item: InventoryItemDTO, quantity: Int) = viewModelScope.launch {
        runCatching {
            repository.addUsedPart(
                ticketId = ticketId,
                part = UsedPartDTO(
                    inventoryId = item.id,
                    partName = item.partName,
                    quantityUsed = quantity,
                    sellingPriceSnapshot = item.sellPrice ?: 0.0
                )
            )
        }.onSuccess {
            loadUsedParts(ticketId)
            _uiState.update {
                s -> s.copy(
                    barcodeItem = null,
                    error = null,
                    usedPartAddedTicketId = ticketId
                )
            }
        }.onFailure {
            _uiState.update { s -> s.copy(error = it.message ?: "Parça eklenemedi") }
        }
    }

    fun lookupBarcode(code: String) = viewModelScope.launch {
        runCatching { repository.lookupBarcode(code) }
            .onSuccess { _uiState.update { s -> s.copy(barcodeItem = it, error = null) } }
            .onFailure {
                _uiState.update {
                    s -> s.copy(barcodeItem = null, error = it.message ?: "Barkod bulunamadı")
                }
            }
    }

    fun clearBarcodeResult() {
        _uiState.update { it.copy(barcodeItem = null) }
    }

    fun completeService(ticketId: Long, amount: Double, method: String) = viewModelScope.launch {
        runCatching { repository.completeService(ticketId, amount, method) }
            .onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        tickets = state.tickets.map { if (it.id == ticketId) updated else it },
                        selectedTicket = updated,
                        serviceCompletedTicketId = ticketId
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message ?: "Tahsilat kaydedilemedi") }
            }
    }

    fun uploadSignature(ticketId: Long, base64: String) = viewModelScope.launch {
        runCatching { repository.uploadSignature(ticketId, base64) }
            .onSuccess { _uiState.update { it.copy(signatureSaved = true, error = null) } }
            .onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(error = throwable.message ?: "İmza yüklenemedi")
                }
            }
    }

    fun consumeSignatureSaved() {
        _uiState.update { it.copy(signatureSaved = false) }
    }

    fun assignTechnician(ticketId: Long, technicianId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(assigningTicketId = ticketId, error = null) }
        runCatching { repository.assignTechnician(ticketId, technicianId) }
            .onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        assigningTicketId = null,
                        tickets = state.tickets.map { if (it.id == ticketId) updated else it },
                        selectedTicket = if (state.selectedTicket?.id == ticketId) updated else state.selectedTicket
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        assigningTicketId = null,
                        error = throwable.message ?: "Teknisyen ataması yapılamadı"
                    )
                }
            }
    }

    fun assignTechnicianBulk(ticketIds: List<Long>, technicianId: Long) = viewModelScope.launch {
        if (ticketIds.isEmpty()) return@launch
        _uiState.update { it.copy(bulkAssigning = true, error = null) }
        runCatching {
            ticketIds.forEach { ticketId ->
                repository.assignTechnician(ticketId, technicianId)
            }
        }.onSuccess {
            loadTickets(refresh = true)
            _uiState.update { it.copy(bulkAssigning = false) }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    bulkAssigning = false,
                    error = throwable.message ?: "Toplu atama yapılamadı"
                )
            }
        }
    }

    fun consumeUsedPartAdded() {
        _uiState.update { it.copy(usedPartAddedTicketId = null) }
    }

    fun consumeServiceCompleted() {
        _uiState.update { it.copy(serviceCompletedTicketId = null) }
    }
}
