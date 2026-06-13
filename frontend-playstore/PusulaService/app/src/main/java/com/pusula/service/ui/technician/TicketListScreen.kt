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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppDropdownField
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppPrimaryButton
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

@OptIn(ExperimentalMaterial3Api::class)
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
                        onClick = {
                            showCreateTicketDialog = true
                            if (uiState.customers.isEmpty()) {
                                viewModel.loadCustomers()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> {
                val filtered = uiState.tickets.filter {
                    ticketMatchesFilter(it, selectedFilter, session.isAdmin)
                }
                val unassignedInFiltered = filtered.filter { it.assignedTechnicianId == null }
                val activeCount = uiState.tickets.count { (it.status?.uppercase() ?: "") == "IN_PROGRESS" }
                val unassignedCount = uiState.tickets.count {
                    val s = it.status?.uppercase() ?: ""
                    it.assignedTechnicianId == null && s == "PENDING"
                }
                var bulkExpanded by remember(selectedFilter, filtered.size) { mutableStateOf(false) }
                var bulkTechName by remember(selectedFilter, filtered.size) { mutableStateOf("Teknisyen Seç") }
                var bulkTechId by remember(selectedFilter, filtered.size) { mutableStateOf<Long?>(null) }

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
                            AppHeroCard(
                                eyebrow = if (session.isAdmin) "Operasyon" else "İşlerim",
                                title = "${uiState.tickets.size} iş emri",
                                subtitle = "$unassignedCount atama bekliyor · $activeCount devam ediyor",
                                badge = if (session.isAdmin) "Yönetici görünümü" else "Teknisyen görünümü"
                            )
                        }
                        item {
                            AppPrimaryButton(
                                text = "Servis Fişi Oluştur",
                                onClick = {
                                    showCreateTicketDialog = true
                                    if (uiState.customers.isEmpty()) {
                                        viewModel.loadCustomers()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
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
                            AppDashboardSection(
                                title = "Servis Fişi",
                                subtitle = "Müşteri seç, hızlı müşteri oluştur ve fiş aç"
                            ) {
                                AppGhostCard {
                                    AppPrimaryButton(
                                        text = "Servis Fişi Oluştur",
                                        onClick = {
                                            showCreateTicketDialog = true
                                            if (uiState.customers.isEmpty()) {
                                                viewModel.loadCustomers()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        if (session.isAdmin && unassignedInFiltered.isNotEmpty()) {
                            item {
                                AppDashboardSection(
                                    title = "Toplu Atama",
                                    subtitle = "${unassignedInFiltered.size} atanmamış"
                                ) {
                                    AppGhostCard {
                                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                            ExposedDropdownMenuBox(expanded = bulkExpanded, onExpandedChange = { bulkExpanded = it }) {
                                                OutlinedTextField(
                                                    value = bulkTechName,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Teknisyen Seç") },
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bulkExpanded) },
                                                    modifier = Modifier
                                                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                                                        .fillMaxWidth()
                                                )
                                                ExposedDropdownMenu(expanded = bulkExpanded, onDismissRequest = { bulkExpanded = false }) {
                                                    uiState.technicians.forEach { tech ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    (tech.fullName ?: "Teknisyen #${tech.id}").safeForComposeText(
                                                                        "Teknisyen #${tech.id}"
                                                                    )
                                                                )
                                                            },
                                                            onClick = {
                                                                bulkTechName = (tech.fullName ?: "Teknisyen #${tech.id}")
                                                                    .safeForComposeText("Teknisyen #${tech.id}")
                                                                bulkTechId = tech.id
                                                                bulkExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            AppPrimaryButton(
                                                text = if (uiState.bulkAssigning) "Toplu Atama Yapılıyor..." else "Filtredeki Atanmamış Fişleri Ata",
                                                onClick = {
                                                    val ids = unassignedInFiltered.map { it.id }
                                                    val techId = bulkTechId
                                                    if (techId != null && ids.isNotEmpty()) {
                                                        viewModel.assignTechnicianBulk(ids, techId)
                                                    }
                                                },
                                                enabled = bulkTechId != null && !uiState.bulkAssigning,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            AppDashboardSection(
                                title = "İş Emirleri",
                                subtitle = "${filtered.size} sonuç"
                            ) {
                                if (filtered.isEmpty()) {
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
                            }
                        }
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
