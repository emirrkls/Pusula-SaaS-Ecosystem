package com.pusula.service.ui.technician

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.ui.components.AppDropdownField
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.components.AppSecondaryButton
import com.pusula.service.ui.components.AppStatusBadge
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning
import com.pusula.service.util.safeForComposeText
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val adminFilters = listOf("Atama Bekleyen", "Bugün Açılan", "Atanan", "Devam Eden", "Kapanan", "Tümü")
private val technicianFilters = listOf("Atanan", "Kapanan", "Tümü")
private val businessZoneId: ZoneId = ZoneId.of("Europe/Istanbul")
private val localDateTimeParsers = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
)

private fun isTodayInBusinessZone(dateRaw: String?): Boolean {
    if (dateRaw.isNullOrBlank()) return false
    val businessToday = LocalDate.now(businessZoneId)

    runCatching {
        OffsetDateTime.parse(dateRaw).atZoneSameInstant(businessZoneId).toLocalDate()
    }.getOrNull()?.let { return it == businessToday }

    runCatching {
        ZonedDateTime.parse(dateRaw).withZoneSameInstant(businessZoneId).toLocalDate()
    }.getOrNull()?.let { return it == businessToday }

    for (formatter in localDateTimeParsers) {
        try {
            val local = java.time.LocalDateTime.parse(dateRaw, formatter)
            val dateInBusiness = local.atZone(ZoneId.systemDefault()).withZoneSameInstant(businessZoneId).toLocalDate()
            return dateInBusiness == businessToday
        } catch (_: DateTimeParseException) {
            // try next parser
        }
    }

    return dateRaw.startsWith(businessToday.toString())
}

private fun ticketMatchesFilter(ticket: FieldTicketDTO, filter: String, isAdmin: Boolean): Boolean {
    if (filter == "Tümü") return true
    val s = ticket.status?.trim()?.uppercase().orEmpty()
    return when (filter) {
        "Atama Bekleyen" -> ticket.assignedTechnicianId == null && s == "PENDING"
        "Bugün Açılan" -> isTodayInBusinessZone(ticket.createdAt)
        "Atanan" -> if (isAdmin) {
            s == "ASSIGNED"
        } else {
            s == "ASSIGNED" || s == "IN_PROGRESS"
        }
        "Devam Eden" -> s == "IN_PROGRESS"
        "Kapanan" -> s == "COMPLETED" || s == "CANCELLED"
        else -> s.equals(filter, ignoreCase = true)
    }
}

private fun statusLabelForDisplay(apiStatus: String?): String {
    return when (apiStatus?.trim()?.uppercase().orEmpty()) {
        "PENDING" -> "Bekliyor"
        "ASSIGNED" -> "Atandı"
        "IN_PROGRESS" -> "Devam Ediyor"
        "COMPLETED" -> "Tamamlandı"
        "CANCELLED" -> "İptal"
        else -> if (apiStatus.isNullOrBlank()) "Bilinmiyor" else apiStatus.safeForComposeText("Bilinmiyor")
    }
}

private fun statusTone(apiStatus: String?): Color = when (apiStatus?.trim()?.uppercase().orEmpty()) {
    "COMPLETED" -> Success
    "IN_PROGRESS" -> BrandCyan
    "CANCELLED" -> ErrorTone
    "PENDING", "ASSIGNED" -> Warning
    else -> BrandNavy.copy(alpha = 0.5f)
}

private fun pendingUnassignedTickets(tickets: List<FieldTicketDTO>): List<FieldTicketDTO> =
    tickets.filter {
        val status = it.status?.trim()?.uppercase().orEmpty()
        it.assignedTechnicianId == null && status == "PENDING"
    }

@Composable
fun TicketListScreen(
    onOpenTicket: (Long) -> Unit,
    requestedFilter: String? = null,
    onRequestedFilterApplied: () -> Unit = {},
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val availableFilters = if (session.isAdmin) adminFilters else technicianFilters
    val defaultFilter = if (session.isAdmin) "Atama Bekleyen" else "Atanan"
    var selectedFilter by remember(session.isAdmin) { mutableStateOf(defaultFilter) }
    var showCreateTicketDialog by remember { mutableStateOf(false) }
    var showBulkAssignDialog by remember { mutableStateOf(false) }

    val openCreateTicketDialog = {
        showCreateTicketDialog = true
        if (uiState.customers.isEmpty()) {
            viewModel.loadCustomers()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadTickets() }
    LaunchedEffect(session.isAdmin) { selectedFilter = defaultFilter }
    LaunchedEffect(requestedFilter, session.isAdmin) {
        if (!requestedFilter.isNullOrBlank() && requestedFilter in availableFilters) {
            selectedFilter = requestedFilter
            onRequestedFilterApplied()
        }
    }
    LaunchedEffect(uiState.ticketCreatedId) {
        if (uiState.ticketCreatedId != null) {
            showCreateTicketDialog = false
            viewModel.consumeTicketCreated()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loading && uiState.tickets.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.tickets.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically)
                ) {
                    AppEmptyState(
                        title = "İş emirleri yüklenemedi",
                        subtitle = uiState.error?.safeForComposeText(),
                        icon = Icons.Outlined.AssignmentLate,
                        tint = ErrorTone
                    )
                    AppPrimaryButton(
                        text = "Servis Fişi Oluştur",
                        onClick = openCreateTicketDialog,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> {
                val filtered = uiState.tickets.filter {
                    ticketMatchesFilter(it, selectedFilter, session.isAdmin)
                }
                val pendingUnassigned = pendingUnassignedTickets(uiState.tickets)
                val activeCount = uiState.tickets.count { (it.status?.uppercase() ?: "") == "IN_PROGRESS" }

                LaunchedEffect(showBulkAssignDialog, pendingUnassigned.size) {
                    if (showBulkAssignDialog && pendingUnassigned.isEmpty()) {
                        showBulkAssignDialog = false
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.refreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.md,
                            bottom = Spacing.xxl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                    ) {
                        item {
                            OperationCompactHeader(
                                eyebrow = if (session.isAdmin) "Operasyon" else "İşlerim",
                                title = "${uiState.tickets.size} iş emri",
                                subtitle = if (session.isAdmin) {
                                    "${pendingUnassigned.size} atama bekliyor · $activeCount devam ediyor"
                                } else {
                                    "$activeCount devam ediyor"
                                }
                            )
                        }
                        item {
                            FilterRow(
                                filters = availableFilters,
                                selectedFilter = selectedFilter,
                                onSelect = { selectedFilter = it }
                            )
                        }
                        item {
                            if (session.isAdmin && pendingUnassigned.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    AppPrimaryButton(
                                        text = "Servis Fişi Oluştur",
                                        onClick = openCreateTicketDialog,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppSecondaryButton(
                                        text = "Toplu Atama (${pendingUnassigned.size})",
                                        onClick = { showBulkAssignDialog = true },
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.bulkAssigning
                                    )
                                }
                            } else {
                                AppPrimaryButton(
                                    text = "Servis Fişi Oluştur",
                                    onClick = openCreateTicketDialog,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        item {
                            TicketListSectionHeader(
                                title = "İş Emirleri",
                                subtitle = "${filtered.size} sonuç"
                            )
                        }
                        if (filtered.isEmpty()) {
                            item {
                                AppEmptyState(
                                    title = if (selectedFilter == "Tümü") "İş emri yok" else "Bu filtrede sonuç yok",
                                    subtitle = if (selectedFilter == "Tümü") {
                                        "Yeni bir fiş açıldığında burada görünecek."
                                    } else {
                                        "Farklı bir filtre seçmeyi deneyin."
                                    },
                                    icon = Icons.Outlined.AssignmentInd,
                                    tint = BrandCyan
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { ticket ->
                                TicketCard(
                                    ticket = ticket,
                                    isAdmin = session.isAdmin,
                                    technicians = uiState.technicians,
                                    onAssignTechnician = { techId -> viewModel.assignTechnician(ticket.id, techId) },
                                    onClick = { onOpenTicket(ticket.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTicketDialog) {
        CreateTicketFromOperationDialog(
            customers = uiState.customers,
            technicians = uiState.technicians,
            customersLoading = uiState.customersLoading,
            customerSaving = uiState.customerSaving,
            creatingTicket = uiState.creatingTicket,
            onDismiss = { showCreateTicketDialog = false },
            onRefreshCustomers = { viewModel.loadCustomers(refresh = true) },
            onQuickCreateCustomer = { name, phone, address, onCreated ->
                viewModel.createCustomerQuick(name, phone, address, onCreated)
            },
            onCreateTicket = { customerId, description, notes, techId ->
                viewModel.createServiceTicket(customerId, description, notes, techId)
            }
        )
    }

    if (showBulkAssignDialog) {
        BulkAssignDialog(
            tickets = pendingUnassignedTickets(uiState.tickets),
            technicians = uiState.technicians,
            assigning = uiState.bulkAssigning,
            onDismiss = { showBulkAssignDialog = false },
            onAssignSelected = { ticketIds, technicianId ->
                viewModel.assignTechnicianBulk(ticketIds, technicianId)
            }
        )
    }
}

@Composable
private fun OperationCompactHeader(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TicketListSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterRow(
    filters: List<String>,
    selectedFilter: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        filters.forEach { filter ->
            val selected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = { onSelect(filter) }),
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                )
            ) {
                Text(
                    text = filter,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: FieldTicketDTO,
    isAdmin: Boolean,
    technicians: List<TechnicianDTO>,
    onAssignTechnician: (Long) -> Unit,
    onClick: () -> Unit
) {
    var selectedTechName by remember(ticket.id, ticket.assignedTechnicianName) {
        val label = ticket.assignedTechnicianName
            ?.safeForComposeText()
            ?.takeIf { it.isNotBlank() }
        mutableStateOf(label ?: "Teknisyen Seç")
    }
    val isUnassigned = ticket.assignedTechnicianId == null
    val tone = statusTone(ticket.status)

    AppGhostCard(onClick = onClick) {
        // status accent strip
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tone)
                    .padding(horizontal = 2.dp, vertical = 18.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ticket.customerName.safeForComposeText("Müşteri"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    AppStatusBadge(text = statusLabelForDisplay(ticket.status), statusKey = ticket.status)
                }
                val phoneDisplay = ticket.customerPhone.safeForComposeText()
                if (phoneDisplay.isNotBlank()) {
                    Text(
                        text = phoneDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val addressDisplay = ticket.customerAddress.safeForComposeText()
                if (addressDisplay.isNotBlank()) {
                    Text(
                        text = addressDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ticket.scheduledDate
                            ?.takeIf { it.isNotBlank() }
                            ?.safeForComposeText("Tarih: -")
                            ?: "Tarih: -",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val techLine = ticket.assignedTechnicianName.safeForComposeText()
                    Text(
                        text = if (techLine.isBlank()) "Atanmadı" else techLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (techLine.isBlank()) ErrorTone else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isAdmin && isUnassigned) {
                    AppDropdownField(
                        label = "Teknisyen Atama",
                        selectedValue = selectedTechName,
                        options = technicians.map { tech ->
                            (tech.fullName ?: "Teknisyen #${tech.id}").safeForComposeText("Teknisyen #${tech.id}")
                        },
                        onSelected = { selected ->
                            selectedTechName = selected
                            val tech = technicians.firstOrNull { t ->
                                (t.fullName ?: "Teknisyen #${t.id}").safeForComposeText("Teknisyen #${t.id}") == selected
                            }
                            if (tech != null) onAssignTechnician(tech.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
