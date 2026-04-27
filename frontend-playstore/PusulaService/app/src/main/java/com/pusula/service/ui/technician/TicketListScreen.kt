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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

private val filters = listOf("Tümü", "Atama Bekleyen", "Atanan", "Devam Eden", "Kapanan")

private fun ticketMatchesFilter(ticket: FieldTicketDTO, filter: String): Boolean {
    if (filter == "Tümü") return true
    val s = ticket.status?.trim()?.uppercase().orEmpty()
    return when (filter) {
        "Atama Bekleyen" -> ticket.assignedTechnicianId == null && s == "PENDING"
        "Atanan" -> s == "ASSIGNED"
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
        else -> if (apiStatus.isNullOrBlank()) "Bilinmiyor" else apiStatus
    }
}

private fun statusTone(apiStatus: String?): Color = when (apiStatus?.trim()?.uppercase().orEmpty()) {
    "COMPLETED" -> Success
    "IN_PROGRESS" -> Info
    "CANCELLED" -> ErrorTone
    "PENDING", "ASSIGNED" -> Warning
    else -> AccentOrange
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketListScreen(
    onOpenTicket: (Long) -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    var selectedFilter by remember { mutableStateOf("Tümü") }

    LaunchedEffect(Unit) { viewModel.loadTickets() }

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
                    verticalArrangement = Arrangement.Center
                ) {
                    AppEmptyState(
                        title = "İş emirleri yüklenemedi",
                        subtitle = uiState.error,
                        icon = Icons.Outlined.AssignmentLate,
                        tint = ErrorTone
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.refreshing,
                    onRefresh = { viewModel.loadTickets(refresh = true) }
                ) {
                    val filtered = uiState.tickets.filter { ticketMatchesFilter(it, selectedFilter) }
                    val unassignedInFiltered = filtered.filter { it.assignedTechnicianId == null }
                    val activeCount = uiState.tickets.count { (it.status?.uppercase() ?: "") == "IN_PROGRESS" }
                    val unassignedCount = uiState.tickets.count {
                        val s = it.status?.uppercase() ?: ""
                        it.assignedTechnicianId == null && s == "PENDING"
                    }
                    var bulkExpanded by remember(selectedFilter, filtered.size) { mutableStateOf(false) }
                    var bulkTechName by remember(selectedFilter, filtered.size) { mutableStateOf("Teknisyen Seç") }
                    var bulkTechId by remember(selectedFilter, filtered.size) { mutableStateOf<Long?>(null) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                subtitle = "$unassignedCount atama bekliyor • $activeCount devam ediyor",
                                badge = if (session.isAdmin) "Yönetici görünümü" else "Teknisyen görünümü"
                            )
                        }
                        item {
                            FilterRow(selectedFilter = selectedFilter, onSelect = { selectedFilter = it })
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
                                                            text = { Text(tech.fullName ?: "Teknisyen #${tech.id}") },
                                                            onClick = {
                                                                bulkTechName = tech.fullName ?: "Teknisyen #${tech.id}"
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
                                        tint = Info
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
}

@Composable
private fun FilterRow(selectedFilter: String, onSelect: (String) -> Unit) {
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
        mutableStateOf(ticket.assignedTechnicianName ?: "Teknisyen Seç")
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
                        ticket.customerName ?: "Müşteri",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    AppStatusBadge(text = statusLabelForDisplay(ticket.status), statusKey = ticket.status)
                }
                if (!ticket.customerPhone.isNullOrBlank()) {
                    Text(
                        text = ticket.customerPhone.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!ticket.customerAddress.isNullOrBlank()) {
                    Text(
                        text = ticket.customerAddress.orEmpty(),
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
                        text = ticket.scheduledDate?.takeIf { it.isNotBlank() } ?: "Tarih: -",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (ticket.assignedTechnicianName.isNullOrBlank()) "Atanmadı" else ticket.assignedTechnicianName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ticket.assignedTechnicianName.isNullOrBlank()) ErrorTone else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isAdmin && isUnassigned) {
                    AppDropdownField(
                        label = "Teknisyen Atama",
                        selectedValue = selectedTechName,
                        options = technicians.map { it.fullName ?: "Teknisyen #${it.id}" },
                        onSelected = { selected ->
                            selectedTechName = selected
                            val tech = technicians.firstOrNull { (it.fullName ?: "Teknisyen #${it.id}") == selected }
                            if (tech != null) onAssignTechnician(tech.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
