package com.pusula.service.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.data.model.CompanyDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO
import com.pusula.service.data.repository.SettingsRepository
import com.google.gson.Gson
import com.pusula.service.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import retrofit2.HttpException

data class PendingDeleteReassign(
    val userId: Long,
    val activeTicketCount: Long
)

data class SettingsUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val pendingDeleteReassign: PendingDeleteReassign? = null,
    val users: List<UserDTO> = emptyList(),
    val vehicles: List<VehicleDTO> = emptyList(),
    val company: CompanyDTO? = null,
    val userSavedAt: Long? = null,
    val vehicleSavedAt: Long? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun loadSettings(refresh: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        runCatching {
            Triple(repository.getUsers(), repository.getVehicles(), repository.getCompany())
        }.onSuccess { (users, vehicles, company) ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    users = users,
                    vehicles = vehicles,
                    company = company
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = throwable.toUserMessage("Ayar verileri yüklenemedi")
                )
            }
        }
    }

    fun saveUser(id: Long?, username: String, fullName: String, role: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        val action = if (id == null) {
            runCatching { repository.createUser(username, fullName, role, password) }
        } else {
            runCatching { repository.updateUser(id, username, fullName, role, password.ifBlank { null }) }
        }
        action.onSuccess {
            _uiState.update { it.copy(saving = false, userSavedAt = System.currentTimeMillis()) }
            loadSettings(refresh = true)
        }.onFailure { throwable ->
            _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Kullanıcı kaydedilemedi")) }
        }
    }

    fun deleteUser(id: Long, reassignTo: Long? = null) = viewModelScope.launch {
        _uiState.update {
            it.copy(saving = true, error = null, pendingDeleteReassign = null)
        }
        runCatching { repository.deleteUser(id, reassignTo) }
            .onSuccess {
                _uiState.update { it.copy(saving = false, pendingDeleteReassign = null) }
                loadSettings(refresh = true)
            }
            .onFailure { throwable ->
                val conflict = parseActiveTicketConflictCount(throwable, reassignTo != null)
                if (conflict != null) {
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = null,
                            pendingDeleteReassign = PendingDeleteReassign(id, conflict)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(saving = false, error = throwable.toUserMessage("Kullanıcı silinemedi"))
                    }
                }
            }
    }

    fun dismissPendingDeleteReassign() {
        _uiState.update { it.copy(pendingDeleteReassign = null) }
    }

    fun resetPassword(id: Long, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        runCatching { repository.resetPassword(id, password) }
            .onSuccess { _uiState.update { it.copy(saving = false) } }
            .onFailure { throwable ->
                _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Şifre sıfırlanamadı")) }
            }
    }

    fun saveVehicle(id: Long?, licensePlate: String, driverName: String, isActive: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        val action = if (id == null) {
            runCatching { repository.createVehicle(licensePlate, driverName, isActive) }
        } else {
            runCatching { repository.updateVehicle(id, licensePlate, driverName, isActive) }
        }
        action.onSuccess {
            _uiState.update { it.copy(saving = false, vehicleSavedAt = System.currentTimeMillis()) }
            loadSettings(refresh = true)
        }.onFailure { throwable ->
            _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Araç kaydedilemedi")) }
        }
    }

    fun deleteVehicle(id: Long) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        runCatching { repository.deleteVehicle(id) }
            .onSuccess {
                _uiState.update { it.copy(saving = false) }
                loadSettings(refresh = true)
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Araç silinemedi")) }
            }
    }

    fun saveCompany(name: String, phone: String, email: String, address: String) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        runCatching { repository.updateCompany(name, phone, email, address) }
            .onSuccess { updated ->
                _uiState.update { it.copy(saving = false, company = updated) }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Firma güncellenemedi")) }
            }
    }

    fun uploadUserSignature(id: Long, filePart: MultipartBody.Part) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        runCatching { repository.uploadUserSignature(id, filePart) }
            .onSuccess {
                _uiState.update { it.copy(saving = false) }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("İmza yüklenemedi")) }
            }
    }

    fun uploadCompanyLogo(filePart: MultipartBody.Part) = viewModelScope.launch {
        _uiState.update { it.copy(saving = true, error = null) }
        runCatching { repository.uploadCompanyLogo(filePart) }
            .onSuccess { updated ->
                _uiState.update { it.copy(saving = false, company = updated) }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(saving = false, error = throwable.toUserMessage("Logo yüklenemedi")) }
            }
    }

    fun consumeUserSavedEvent() {
        _uiState.update { it.copy(userSavedAt = null) }
    }

    fun consumeVehicleSavedEvent() {
        _uiState.update { it.copy(vehicleSavedAt = null) }
    }
}

private data class ActiveTicketsErrorBody(
    val error: String?,
    val count: Number?
)

/** HTTP 409 with ACTIVE_TICKETS: open jobs must be reassigned before deleting the technician. */
private fun parseActiveTicketConflictCount(throwable: Throwable, reassignAttempted: Boolean): Long? {
    if (reassignAttempted) return null
    val http = throwable as? HttpException ?: return null
    if (http.code() != 409) return null
    val raw = http.response()?.errorBody()?.string() ?: return null
    return runCatching {
        val body = Gson().fromJson(raw, ActiveTicketsErrorBody::class.java)
        if (body.error == "ACTIVE_TICKETS" && body.count != null) body.count.toLong() else null
    }.getOrNull()
}
