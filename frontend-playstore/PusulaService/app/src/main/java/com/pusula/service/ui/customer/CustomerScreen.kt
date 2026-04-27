package com.pusula.service.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppInitialsAvatar
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<CustomerDTO?>(null) }
    var isCreateMode by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<CustomerDTO?>(null) }
    var showCreateTicketDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadCustomers() }
    LaunchedEffect(uiState.customerSavedAt) {
        if (uiState.customerSavedAt != null) {
            isCreateMode = false
            editingCustomer = null
            viewModel.consumeCustomerSavedEvent()
        }
    }
    LaunchedEffect(uiState.ticketCreatedAt) {
        if (uiState.ticketCreatedAt != null) {
            showCreateTicketDialog = false
            viewModel.consumeTicketCreatedEvent()
        }
    }

    val filtered = uiState.customers.filter {
        val q = query.trim().lowercase()
        q.isBlank() ||
            it.name.lowercase().contains(q) ||
            (it.phone?.lowercase()?.contains(q) == true) ||
            (it.address?.lowercase()?.contains(q) == true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Müşteriler") }) }
    ) { padding ->
        when {
            uiState.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.refreshing,
                    onRefresh = { viewModel.loadCustomers(refresh = true) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
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
                                eyebrow = "Müşteri yönetimi",
                                title = "${uiState.customers.size} müşteri",
                                subtitle = "Tüm müşterileri arayın, düzenleyin veya servis fişi oluşturun.",
                                badge = if (filtered.size != uiState.customers.size) "${filtered.size} sonuç"
                                else null
                            )
                        }

                        item {
                            AppGhostCard(
                                padding = PaddingValues(Spacing.md)
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Müşteri ara") },
                                    leadingIcon = {
                                        AppIconBadge(
                                            icon = Icons.Outlined.Person,
                                            tint = Info,
                                            size = 28.dp,
                                            iconSize = 14.dp,
                                            cornerRadius = 8.dp
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                Button(
                                    onClick = {
                                        isCreateMode = true
                                        editingCustomer = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.padding(start = Spacing.xs))
                                    Text("Yeni Müşteri Ekle")
                                }
                            }
                        }

                        if (uiState.error != null) {
                            item {
                                AppEmptyState(
                                    title = "Bir hata oluştu",
                                    subtitle = uiState.error,
                                    icon = Icons.Outlined.PersonOff,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        item {
                            AppDashboardSection(
                                title = "Müşteriler",
                                subtitle = "${filtered.size} kayıt"
                            ) {}
                        }

                        if (filtered.isEmpty()) {
                            item {
                                AppEmptyState(
                                    title = if (query.isBlank()) "Henüz müşteri yok" else "Eşleşen müşteri yok",
                                    subtitle = if (query.isBlank()) "Yukarıdan yeni müşteri ekleyebilirsiniz."
                                    else "Farklı bir arama deneyin.",
                                    icon = Icons.Outlined.PersonOff,
                                    tint = AccentPurple
                                )
                            }
                        } else {
                            items(filtered, key = { it.id ?: it.name.hashCode().toLong() }) { customer ->
                                CustomerRow(
                                    customer = customer,
                                    selected = selectedCustomer?.id == customer.id,
                                    onClick = { selectedCustomer = customer },
                                    onEdit = {
                                        isCreateMode = false
                                        editingCustomer = customer
                                    },
                                    onCreateTicket = {
                                        selectedCustomer = customer
                                        showCreateTicketDialog = true
                                    }
                                )
                            }
                        }

                        selectedCustomer?.let { customer ->
                            item {
                                AppDashboardSection(title = "Müşteri Detayı") {
                                    AppGhostCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                        ) {
                                            AppInitialsAvatar(text = customer.name, size = 48.dp)
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = customer.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                                if (!customer.phone.isNullOrBlank()) {
                                                    Text(
                                                        text = customer.phone,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        if (!customer.address.isNullOrBlank()) {
                                            Spacer(Modifier.height(Spacing.sm))
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                            ) {
                                                AppIconBadge(
                                                    icon = Icons.Outlined.Home,
                                                    tint = AccentCyan,
                                                    size = 28.dp,
                                                    iconSize = 14.dp,
                                                    cornerRadius = 8.dp
                                                )
                                                Text(
                                                    text = customer.address,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(Spacing.md))
                                        Button(
                                            onClick = { showCreateTicketDialog = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Receipt,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.padding(start = Spacing.xs))
                                            Text("Servis Fişi Oluştur")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreateMode || editingCustomer != null) {
        CustomerFormDialog(
            initial = editingCustomer,
            saving = uiState.saving,
            onDismiss = {
                isCreateMode = false
                editingCustomer = null
            },
            onSave = { id, name, phone, address ->
                viewModel.saveCustomer(id, name, phone, address)
            }
        )
    }

    if (showCreateTicketDialog && selectedCustomer?.id != null) {
        CreateTicketDialog(
            customer = selectedCustomer!!,
            technicians = uiState.technicians,
            saving = uiState.creatingTicket,
            onDismiss = { showCreateTicketDialog = false },
            onCreate = { description, notes, techId ->
                viewModel.createTicket(
                    customerId = selectedCustomer!!.id!!,
                    description = description,
                    notes = notes,
                    technicianId = techId
                )
            }
        )
    }
}

@Composable
private fun CustomerRow(
    customer: CustomerDTO,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onCreateTicket: () -> Unit
) {
    AppGhostCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppInitialsAvatar(text = customer.name)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                val secondary = customer.phone?.takeIf { it.isNotBlank() }
                    ?: customer.address?.takeIf { it.isNotBlank() }
                if (!secondary.isNullOrBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text("Düzenle")
            }
            Button(onClick = onCreateTicket, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Outlined.Receipt, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text("Fiş Aç")
            }
        }
        if (selected) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Detay seçildi ↓",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CustomerFormDialog(
    initial: CustomerDTO?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Müşteri" else "Müşteri Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefon") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adres") },
                    leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onSave(initial?.id, name.trim(), phone.trim(), address.trim()) }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTicketDialog(
    customer: CustomerDTO,
    technicians: List<TechnicianDTO>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (description: String, notes: String, technicianId: Long?) -> Unit
) {
    var description by remember(customer.id) { mutableStateOf("") }
    var notes by remember(customer.id) { mutableStateOf("") }
    var expanded by remember(customer.id) { mutableStateOf(false) }
    var selectedTechName by remember(customer.id) { mutableStateOf("Atamasız") }
    var selectedTechId by remember(customer.id) { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servis Fişi Oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Müşteri: ${customer.name}", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notlar") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedTechName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Teknisyen (opsiyonel)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Atamasız") },
                            onClick = {
                                selectedTechId = null
                                selectedTechName = "Atamasız"
                                expanded = false
                            }
                        )
                        technicians.forEach { tech ->
                            DropdownMenuItem(
                                text = { Text(tech.fullName ?: "Teknisyen #${tech.id}") },
                                onClick = {
                                    selectedTechId = tech.id
                                    selectedTechName = tech.fullName ?: "Teknisyen #${tech.id}"
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && !saving,
                onClick = { onCreate(description.trim(), notes.trim(), selectedTechId) }
            ) {
                Text(if (saving) "Oluşturuluyor..." else "Oluştur")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
