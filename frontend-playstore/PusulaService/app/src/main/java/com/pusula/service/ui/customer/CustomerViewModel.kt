package com.pusula.service.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val customers: List<CustomerDTO> = emptyList(),
    val technicians: List<TechnicianDTO> = emptyList(),
    val saving: Boolean = false,
    val creatingTicket: Boolean = false,
    val customerSavedAt: Long? = null,
    val ticketCreatedAt: Long? = null
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repository: CustomerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerUiState())
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()

    fun loadCustomers(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            val customers = repository.getCustomers()
            val technicians = repository.getTechnicians()
            Pair(customers, technicians)
        }
            .onSuccess { (customers, technicians) ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        customers = customers,
                        technicians = technicians
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                technicians = emptyList(),
                        error = throwable.message ?: "Müşteriler yüklenemedi"
                    )
                }
            }
    }

    fun saveCustomer(id: Long?, name: String, phone: String, address: String) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        val action = if (id == null) {
            runCatching { repository.createCustomer(name, phone, address) }
        } else {
            runCatching { repository.updateCustomer(id, name, phone, address) }
        }
        action.onSuccess {
            _uiState.update { it.copy(saving = false, customerSavedAt = System.currentTimeMillis()) }
            loadCustomers(refresh = true)
        }.onFailure { throwable ->
            _uiState.update { it.copy(saving = false, error = throwable.message ?: "Müşteri kaydedilemedi") }
        }
    }

    fun createTicket(customerId: Long, description: String, notes: String, technicianId: Long?) = viewModelScope.launch {
        _uiState.update { it.copy(creatingTicket = true, error = null) }
        runCatching { repository.createTicketForCustomer(customerId, description, notes, technicianId) }
            .onSuccess {
                _uiState.update { it.copy(creatingTicket = false, ticketCreatedAt = System.currentTimeMillis()) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        creatingTicket = false,
                        error = throwable.message ?: "Servis fişi oluşturulamadı"
                    )
                }
            }
    }

    fun consumeCustomerSavedEvent() {
        _uiState.update { it.copy(customerSavedAt = null) }
    }

    fun consumeTicketCreatedEvent() {
        _uiState.update { it.copy(ticketCreatedAt = null) }
    }
}
