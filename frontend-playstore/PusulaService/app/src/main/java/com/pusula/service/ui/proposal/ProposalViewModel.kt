package com.pusula.service.ui.proposal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.ProposalDTO
import com.pusula.service.data.model.ProposalItemDTO
import com.pusula.service.data.repository.ProposalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProposalUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val deletingId: Long? = null,
    val convertingId: Long? = null,
    val downloadingPdfId: Long? = null,
    val error: String? = null,
    val proposals: List<ProposalDTO> = emptyList(),
    val customers: List<CustomerDTO> = emptyList(),
    val proposalSavedAt: Long? = null
)

@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val repository: ProposalRepository,
    val sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProposalUiState())
    val uiState: StateFlow<ProposalUiState> = _uiState.asStateFlow()

    fun loadData(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            Pair(repository.getProposals(), repository.getCustomers())
        }.onSuccess { (proposals, customers) ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    proposals = proposals.sortedByDescending { item -> item.id ?: 0L },
                    customers = customers
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = throwable.message ?: "Teklif verileri yüklenemedi"
                )
            }
        }
    }

    fun saveProposal(
        editingId: Long?,
        customerId: Long,
        title: String,
        status: String,
        validUntil: String,
        note: String,
        taxRate: Double,
        discount: Double,
        items: List<ProposalItemDTO>
    ) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        val action = if (editingId == null) {
            runCatching {
                repository.createProposal(
                    customerId = customerId,
                    title = title,
                    status = status,
                    validUntil = validUntil,
                    note = note,
                    taxRate = taxRate,
                    discount = discount,
                    items = items
                )
            }
        } else {
            runCatching {
                repository.updateProposal(
                    id = editingId,
                    customerId = customerId,
                    title = title,
                    status = status,
                    validUntil = validUntil,
                    note = note,
                    taxRate = taxRate,
                    discount = discount,
                    items = items
                )
            }
        }

        action.onSuccess {
            _uiState.update { it.copy(saving = false, proposalSavedAt = System.currentTimeMillis()) }
            loadData(refresh = true)
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    saving = false,
                    error = throwable.message ?: "Teklif kaydedilemedi"
                )
            }
        }
    }

    fun consumeProposalSavedEvent() {
        _uiState.update { it.copy(proposalSavedAt = null) }
    }

    fun deleteProposal(id: Long) = viewModelScope.launch {
        _uiState.update { it.copy(deletingId = id, error = null) }
        runCatching { repository.deleteProposal(id) }
            .onSuccess { loadData(refresh = true) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        deletingId = null,
                        error = throwable.message ?: "Teklif silinemedi"
                    )
                }
            }
    }

    fun convertToJob(id: Long) = viewModelScope.launch {
        _uiState.update { it.copy(convertingId = id, error = null) }
        runCatching { repository.convertToJob(id) }
            .onSuccess { loadData(refresh = true) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        convertingId = null,
                        error = throwable.message ?: "Teklif işe dönüştürülemedi"
                    )
                }
            }
    }

    suspend fun downloadProposalPdf(id: Long): ByteArray {
        _uiState.update { it.copy(downloadingPdfId = id, error = null) }
        return runCatching { repository.downloadProposalPdf(id) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        downloadingPdfId = null,
                        error = throwable.message ?: "Teklif PDF indirilemedi"
                    )
                }
            }
            .getOrThrow()
            .also { _uiState.update { state -> state.copy(downloadingPdfId = null) } }
    }
}
