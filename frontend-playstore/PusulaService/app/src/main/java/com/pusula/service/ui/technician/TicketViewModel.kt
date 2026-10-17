package com.pusula.service.ui.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.ServicePhotoDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.data.model.UsedPartDTO
import com.pusula.service.data.model.TechnicianNoteDTO
import com.pusula.service.data.repository.TicketRepository
import com.pusula.service.usecase.technician.OperationTicketUseCase
import com.pusula.service.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.util.UUID
import java.io.File
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

data class TicketUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val tickets: List<FieldTicketDTO> = emptyList(),
    val selectedTicket: FieldTicketDTO? = null,
    val billingOrganizations: List<com.pusula.service.data.model.AccountPartyOptionDTO> = emptyList(),
    val billingOrganizationsError: String? = null,
    val collecting: Boolean = false,
    val collectionLocked: Boolean = false,
    val technicians: List<TechnicianDTO> = emptyList(),
    val usedParts: List<UsedPartDTO> = emptyList(),
    val usedPartsLoadedTicketId: Long? = null,
    val usedPartsLoading: Boolean = false,
    val inventory: List<InventoryItemDTO> = emptyList(),
    val barcodeItem: InventoryItemDTO? = null,
    val barcodeLookupFailed: Boolean = false,
    val signatureSaved: Boolean = false,
    val assigningTicketId: Long? = null,
    val bulkAssigning: Boolean = false,
    val usedPartAddedTicketId: Long? = null,
    val serviceCompletedTicketId: Long? = null,
    val customers: List<CustomerDTO> = emptyList(),
    val customersLoading: Boolean = false,
    val customerSaving: Boolean = false,
    val creatingTicket: Boolean = false,
    val ticketCreatedId: Long? = null,
    val servicePhotos: List<ServicePhotoDTO> = emptyList(),
    val photosLoading: Boolean = false,
    val photoUploading: Boolean = false,
    val photoBatch: PhotoBatchProgress = PhotoBatchProgress(),
    val ticketTimeline: List<com.pusula.service.data.model.AuditLogDTO> = emptyList(),
    val timelineLoading: Boolean = false,
    val statusUpdating: Boolean = false,
    val technicianNotes: List<TechnicianNoteDTO> = emptyList(),
    val technicianNotesLoading: Boolean = false,
    val technicianNoteSaving: Boolean = false,
    /** Sunucu servis raporu PDF indirilirken (paylaşım öncesi). */
    val downloadingServicePdfTicketId: Long? = null
)

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val repository: TicketRepository,
    private val operationTicketUseCase: OperationTicketUseCase,
    val sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TicketUiState())
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()
    private var pendingCollection: Triple<Long, String, com.pusula.service.data.model.CollectionRequest>? = null
    private var photoBatch: ServicePhotoUploadBatch<Uri, File>? = null
    private var photoOwner: com.pusula.service.core.SessionState? = null
    private var photoTicketId: Long? = null
    private var photoJob: Job? = null
    private var photoPrepare: (suspend (Uri) -> File?)? = null
    private var photoGeneration = 0L

    init {
        viewModelScope.launch {
            sessionManager.state.collect { state ->
                val owner = photoOwner
                if (owner != null && !samePhotoAccount(owner, state)) {
                    photoJob?.cancel()
                    clearPhotoBatch()
                    _uiState.update { it.copy(photoUploading = false, servicePhotos = emptyList()) }
                }
            }
        }
    }

    private fun samePhotoAccount(a: com.pusula.service.core.SessionState, b: com.pusula.service.core.SessionState) =
        b.isAuthenticated && a.supportRevision == b.supportRevision && a.token == b.token && a.companyId == b.companyId

    fun loadBillingOrganizations() = viewModelScope.launch {
        runCatching { repository.getBillingOrganizations() }
            .onSuccess { values -> _uiState.update { it.copy(billingOrganizations = values, billingOrganizationsError = null) } }
            .onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _uiState.update { it.copy(billingOrganizationsError = "Kurumlar yüklenemedi. Tekrar deneyin.") }
            }
    }

    fun completeService(ticketId: Long, request: com.pusula.service.data.model.CollectionRequest) = viewModelScope.launch {
        if (_uiState.value.collecting) return@launch
        val existing = pendingCollection
        if (existing != null && existing.first != ticketId) {
            _uiState.update { it.copy(error = "Önce bekleyen tahsilatın sonucunu kontrol edin.") }
            return@launch
        }
        val attempt = existing ?: Triple(ticketId, java.util.UUID.randomUUID().toString(), request).also { pendingCollection = it }
        _uiState.update { it.copy(collecting = true, collectionLocked = true, error = null) }
        try {
            val updated = repository.completeService(ticketId, attempt.second, attempt.third)
            pendingCollection = null
            _uiState.update { state -> state.copy(collecting = false, collectionLocked = false,
                tickets = state.tickets.map { if (it.id == ticketId) updated else it },
                selectedTicket = updated, serviceCompletedTicketId = ticketId) }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            val code = (failure as? retrofit2.HttpException)?.code()
            if (code != null && code in 400..499 && code != 409) pendingCollection = null
            _uiState.update { it.copy(collecting = false, collectionLocked = pendingCollection != null,
                error = failure.toUserMessage("Tahsilat teyit edilemedi. Aynı işlemi tekrar deneyin.")) }
        } finally { _uiState.update { it.copy(collecting = false) } }
    }

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
                    error = throwable.toUserMessage("İş emirleri yüklenemedi")
                )
            }
        }
    }

    fun loadCustomers(refresh: Boolean = false) = viewModelScope.launch {
        if (_uiState.value.customersLoading && !refresh) return@launch
        _uiState.update { it.copy(customersLoading = true, error = null) }
        runCatching { operationTicketUseCase.loadCustomers() }
            .onSuccess { customers ->
                _uiState.update {
                    it.copy(
                        customersLoading = false,
                        customers = customers
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        customersLoading = false,
                        error = throwable.toUserMessage("Müşteriler yüklenemedi")
                    )
                }
            }
    }

    fun createCustomerQuick(name: String, phone: String, address: String, onCreated: (CustomerDTO) -> Unit) =
        viewModelScope.launch {
            _uiState.update { it.copy(customerSaving = true, error = null) }
            runCatching { operationTicketUseCase.createQuickCustomer(name, phone, address) }
                .onSuccess { customer ->
                    _uiState.update { state ->
                        state.copy(
                            customerSaving = false,
                            customers = (state.customers + customer).sortedBy { it.name.lowercase() }
                        )
                    }
                    onCreated(customer)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            customerSaving = false,
                            error = throwable.toUserMessage("Müşteri kaydedilemedi")
                        )
                    }
                }
        }

    fun createServiceTicket(
        customerId: Long,
        description: String,
        notes: String,
        technicianPrivateNote: String,
        technicianId: Long?
    ) = viewModelScope.launch {
        _uiState.update { it.copy(creatingTicket = true, error = null) }
        runCatching {
            operationTicketUseCase.createServiceTicket(
                customerId = customerId,
                description = description,
                notes = notes,
                technicianPrivateNote = technicianPrivateNote,
                assignedTechnicianId = technicianId
            )
        }
            .onSuccess { created ->
                _uiState.update { state ->
                    state.copy(
                        creatingTicket = false,
                        ticketCreatedId = created.id,
                        tickets = listOf(created) + state.tickets
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        creatingTicket = false,
                        error = throwable.toUserMessage("Servis fişi oluşturulamadı")
                    )
                }
            }
    }

    fun consumeTicketCreated() {
        _uiState.update { it.copy(ticketCreatedId = null) }
    }

    private fun loadTechnicians() = viewModelScope.launch {
        runCatching { repository.getTechnicians() }
            .onSuccess { technicians ->
                _uiState.update { it.copy(technicians = technicians, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage("Teknisyen listesi alınamadı")) }
            }
    }

    fun selectTicket(ticketId: Long) = viewModelScope.launch {
        val fromList = _uiState.value.tickets.firstOrNull { it.id == ticketId }
        val selected = fromList ?: runCatching { repository.getTicketById(ticketId) }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        selectedTicket = null,
                        error = throwable.toUserMessage("İş emri detayları alınamadı")
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
        loadTechnicianNotes(ticketId)
        loadTicketTimeline(ticketId)
    }

    fun loadTechnicianNotes(ticketId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(technicianNotesLoading = true) }
        runCatching { repository.getTechnicianNotes(ticketId) }
            .onSuccess { notes -> _uiState.update { it.copy(technicianNotesLoading = false, technicianNotes = notes) } }
            .onFailure { error -> _uiState.update { it.copy(technicianNotesLoading = false, error = error.toUserMessage("Teknisyen notları yüklenemedi")) } }
    }

    fun addTechnicianNote(ticketId: Long, content: String, important: Boolean = false, onSaved: () -> Unit = {}) = viewModelScope.launch {
        if (content.isBlank()) return@launch
        _uiState.update { it.copy(technicianNoteSaving = true, error = null) }
        runCatching { repository.addTechnicianNote(ticketId, content, important) }
            .onSuccess { note ->
                _uiState.update { it.copy(technicianNoteSaving = false, technicianNotes = it.technicianNotes + note) }
                onSaved()
            }
            .onFailure { error -> _uiState.update { it.copy(technicianNoteSaving = false, error = error.toUserMessage("Teknisyen notu eklenemedi")) } }
    }

    fun loadTicketTimeline(ticketId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(timelineLoading = true) }
        runCatching { repository.getTicketTimeline(ticketId) }
            .onSuccess { logs ->
                _uiState.update { it.copy(timelineLoading = false, ticketTimeline = logs, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        timelineLoading = false,
                        ticketTimeline = emptyList(),
                        error = throwable.toUserMessage("Fiş geçmişi yüklenemedi")
                    )
                }
            }
    }

    fun updateTicketStatus(ticketId: Long, status: String) = viewModelScope.launch {
        _uiState.update { it.copy(statusUpdating = true, error = null) }
        runCatching { repository.updateTicketStatus(ticketId, status) }
            .onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        statusUpdating = false,
                        tickets = state.tickets.map { if (it.id == ticketId) updated else it },
                        selectedTicket = if (state.selectedTicket?.id == ticketId) updated else state.selectedTicket
                    )
                }
                loadTicketTimeline(ticketId)
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(statusUpdating = false, error = throwable.toUserMessage("Durum güncellenemedi"))
                }
            }
    }

    fun closeTicketWithoutCollection(ticketId: Long) = viewModelScope.launch {
        runCatching { repository.completeService(ticketId, 0.0, "WARRANTY", 0.0) }
            .onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        tickets = state.tickets.map { if (it.id == ticketId) updated else it },
                        selectedTicket = updated,
                        serviceCompletedTicketId = ticketId
                    )
                }
                loadTicketTimeline(ticketId)
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage("İş kapatılamadı")) }
            }
    }

    fun loadUsedParts(ticketId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(usedPartsLoading = true, usedPartsLoadedTicketId = null) }
        runCatching { repository.getUsedParts(ticketId) }
            .onSuccess { parts -> _uiState.update { s ->
                if (s.selectedTicket?.id == ticketId) s.copy(usedParts = parts, usedPartsLoading = false, usedPartsLoadedTicketId = ticketId, error = null) else s
            } }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _uiState.update { s -> if (s.selectedTicket?.id == ticketId) s.copy(usedPartsLoading = false, error = it.toUserMessage("Parçalar alınamadı")) else s }
            }
    }

    fun addUsedPart(ticketId: Long, item: InventoryItemDTO, quantity: Double) = viewModelScope.launch {
        runCatching {
            repository.addUsedPart(
                ticketId = ticketId,
                part = UsedPartDTO(
                    inventoryId = item.id,
                    partName = item.partName,
                    quantityUsed = quantity,
                    sellingPriceSnapshot = item.sellPrice ?: 0.0,
                    unitOfMeasure = item.unitOfMeasure,
                    clientRequestId = UUID.randomUUID().toString()
                )
            )
        }.onSuccess {
            loadUsedParts(ticketId)
            _uiState.update {
                s -> s.copy(
                    barcodeItem = null,
                    error = null,
                    usedPartAddedTicketId = null
                )
            }
        }.onFailure {
            _uiState.update { s -> s.copy(error = it.toUserMessage("Parça eklenemedi")) }
        }
    }

    fun lookupBarcode(code: String) = viewModelScope.launch {
        val normalized = code.trim()
        if (normalized.isEmpty()) return@launch
        runCatching { repository.lookupBarcode(normalized) }
            .onSuccess { _uiState.update { s -> s.copy(barcodeItem = it, barcodeLookupFailed = false, error = null) } }
            .onFailure {
                _uiState.update {
                    s -> s.copy(barcodeItem = null, barcodeLookupFailed = true, error = it.toUserMessage("Barkod bulunamadı"))
                }
            }
    }

    fun clearBarcodeResult() {
        _uiState.update { it.copy(barcodeItem = null, barcodeLookupFailed = false) }
    }

    fun completeService(
        ticketId: Long,
        amount: Double,
        method: String,
        laborFee: Double,
        technicianNote: String? = null
    ) = viewModelScope.launch {
        runCatching { repository.completeService(ticketId, amount, method, laborFee, technicianNote) }
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
                _uiState.update { it.copy(error = throwable.toUserMessage("Tahsilat kaydedilemedi")) }
            }
    }

    fun uploadSignature(ticketId: Long, base64: String) = viewModelScope.launch {
        runCatching { repository.uploadSignature(ticketId, base64) }
            .onSuccess { _uiState.update { it.copy(signatureSaved = true, error = null) } }
            .onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(error = throwable.toUserMessage("İmza yüklenemedi"))
                }
            }
    }

    fun loadServicePhotos(ticketId: Long) = viewModelScope.launch {
        _uiState.update { it.copy(photosLoading = true) }
        runCatching { repository.getServicePhotos(ticketId) }
            .onSuccess { photos ->
                _uiState.update { it.copy(photosLoading = false, servicePhotos = photos, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        photosLoading = false,
                        error = throwable.toUserMessage("Servis görselleri alınamadı")
                    )
                }
            }
    }

    fun reportPhotoPrepareError() {
        _uiState.update {
            it.copy(error = "Görsel okunamadı. Lütfen başka bir fotoğraf deneyin.")
        }
    }

    fun uploadServicePhotos(ticketId: Long, type: String, note: String, sources: List<Uri>, prepare: suspend (Uri) -> File?) {
        if (_uiState.value.photoUploading || photoBatch?.pendingIds?.isNotEmpty() == true || sources.isEmpty() ||
            com.pusula.service.core.GuideReadOnlyGate.active.value) return
        val owner = sessionManager.state.value
        if (!owner.isAuthenticated || owner.token.isNullOrBlank()) return
        photoBatch = ServicePhotoUploadBatch.create(sources.distinct(), type, note)
        photoOwner = owner
        photoTicketId = ticketId
        photoPrepare = prepare
        retryServicePhotos(ticketId)
    }

    fun retryServicePhotos(ticketId: Long) {
        val owner = photoOwner ?: return
        if (_uiState.value.photoUploading || ticketId != photoTicketId || !samePhotoAccount(owner, sessionManager.state.value)) return
        val pending = photoBatch?.pendingIds ?: return
        val generation = photoGeneration
        _uiState.update { it.copy(photoUploading = true, error = null) }
        publishPhotoProgress()
        photoJob = viewModelScope.launch {
            try {
                for (id in pending) {
                    if (!samePhotoAccount(owner, sessionManager.state.value)) return@launch
                    val batch = photoBatch ?: return@launch
                    val index = batch.items.indexOfFirst { it.id == id }
                    val entry = batch.items.getOrNull(index) ?: continue
                    publishPhotoProgress(index + 1)
                    try {
                        if (entry.payload == null) {
                            val source = entry.source ?: continue
                            val prepared = withContext(Dispatchers.IO) { photoPrepare?.invoke(source) }
                                ?: throw java.io.IOException("Görsel okunamadı. Tekrar deneyin.")
                            if (!samePhotoAccount(owner, sessionManager.state.value)) { prepared.delete(); return@launch }
                            photoBatch = photoBatch?.prepare(id, prepared)
                        }
                        val file = photoBatch?.items?.firstOrNull { it.id == id }?.payload ?: continue
                        val part = MultipartBody.Part.createFormData("file", "photo.jpg", file.asRequestBody("image/jpeg".toMediaType()))
                        val created = repository.uploadServicePhoto(ticketId, id, requireNotNull(owner.token), batch.type, batch.note, part)
                        if (!samePhotoAccount(owner, sessionManager.state.value)) return@launch
                        photoBatch = photoBatch?.succeed(id)
                        file.delete()
                        _uiState.update { it.copy(servicePhotos = listOf(created) + it.servicePhotos.filterNot { old -> old.id == created.id }) }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        if (!samePhotoAccount(owner, sessionManager.state.value)) return@launch
                        photoBatch = photoBatch?.fail(id, failure.toUserMessage("Görsel yüklenemedi"))
                    }
                    publishPhotoProgress(index + 1)
                }
            } finally {
                if (generation == photoGeneration) {
                    _uiState.update { it.copy(photoUploading = false) }
                    publishPhotoProgress()
                }
            }
        }
    }

    private fun publishPhotoProgress(current: Int = 0) {
        val batch = photoBatch
        _uiState.update { it.copy(photoBatch = if (batch == null) PhotoBatchProgress() else PhotoBatchProgress(
            batch.items.size, batch.confirmedCount, current,
            batch.items.mapIndexedNotNull { index, item -> item.error?.let { "Görsel ${index + 1}: $it" } })) }
    }

    fun abandonServicePhotos() { if (!_uiState.value.photoUploading) clearPhotoBatch() }

    private fun clearPhotoBatch() {
        photoGeneration++
        photoBatch?.items?.forEach { it.payload?.delete() }
        photoBatch = null; photoOwner = null; photoTicketId = null; photoPrepare = null
        publishPhotoProgress()
    }

    override fun onCleared() {
        photoJob?.cancel()
        clearPhotoBatch()
        super.onCleared()
    }

    fun deleteServicePhoto(ticketId: Long, photoId: Long) = viewModelScope.launch {
        runCatching { repository.deleteServicePhoto(ticketId, photoId) }
            .onSuccess {
                _uiState.update { state ->
                    state.copy(servicePhotos = state.servicePhotos.filterNot { it.id == photoId }, error = null)
                }
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage("Görsel silinemedi")) }
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
                        error = throwable.toUserMessage("Teknisyen ataması yapılamadı")
                    )
                }
            }
    }

    fun assignTechnicianBulk(ticketIds: List<Long>, technicianId: Long) = viewModelScope.launch {
        if (ticketIds.isEmpty()) return@launch
        _uiState.update { it.copy(bulkAssigning = true, error = null) }
        runCatching {
            val updatedById = linkedMapOf<Long, FieldTicketDTO>()
            ticketIds.forEach { ticketId ->
                updatedById[ticketId] = repository.assignTechnician(ticketId, technicianId)
            }
            updatedById
        }.onSuccess { updatedById ->
            _uiState.update { state ->
                state.copy(
                    bulkAssigning = false,
                    tickets = state.tickets.map { ticket ->
                        updatedById[ticket.id] ?: ticket
                    },
                    selectedTicket = state.selectedTicket?.let { selected ->
                        updatedById[selected.id] ?: selected
                    }
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    bulkAssigning = false,
                    error = throwable.toUserMessage("Toplu atama yapılamadı")
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

    suspend fun downloadServiceReportPdf(ticketId: Long): ByteArray {
        _uiState.update { it.copy(downloadingServicePdfTicketId = ticketId) }
        return try {
            repository.downloadServiceReportPdf(ticketId)
        } finally {
            _uiState.update { it.copy(downloadingServicePdfTicketId = null) }
        }
    }
}
