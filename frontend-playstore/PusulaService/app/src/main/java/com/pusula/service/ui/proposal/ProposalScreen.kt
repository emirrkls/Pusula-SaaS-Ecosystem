package com.pusula.service.ui.proposal

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.ProposalDTO
import com.pusula.service.data.model.ProposalItemDTO
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppStatusBadge
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.util.ProposalPdfHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalScreen(
    viewModel: ProposalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editingProposal by remember { mutableStateOf<ProposalDTO?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteProposalId by remember { mutableStateOf<Long?>(null) }
    var convertProposalId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(uiState.proposalSavedAt) {
        if (uiState.proposalSavedAt != null) {
            showForm = false
            editingProposal = null
            viewModel.consumeProposalSavedEvent()
        }
    }

    val filtered = uiState.proposals.filter { proposal ->
        val q = query.trim().lowercase()
        q.isBlank() ||
            (proposal.customerName?.lowercase()?.contains(q) == true) ||
            (proposal.preparedByName?.lowercase()?.contains(q) == true) ||
            (proposal.title?.lowercase()?.contains(q) == true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Teklifler") }) }
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
                    onRefresh = { viewModel.loadData(refresh = true) },
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
                            val totalRevenue = uiState.proposals.sumOf { it.totalPrice ?: 0.0 }
                            AppHeroCard(
                                eyebrow = "Teklif yönetimi",
                                title = "${uiState.proposals.size} teklif",
                                subtitle = "Toplam değer ₺${"%.2f".format(Locale("tr", "TR"), totalRevenue)}",
                                badge = if (filtered.size != uiState.proposals.size) "${filtered.size} sonuç" else null
                            )
                        }
                        item {
                            AppGhostCard(padding = PaddingValues(Spacing.md)) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Teklif ara") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                Button(
                                    onClick = {
                                        editingProposal = null
                                        showForm = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .readOnlyProtected(session.isReadOnly)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.padding(start = Spacing.xs))
                                    Text("Yeni Teklif Oluştur")
                                }
                            }
                        }
                        if (uiState.error != null) {
                            item {
                                AppEmptyState(
                                    title = "Bir hata oluştu",
                                    subtitle = uiState.error,
                                    icon = Icons.Outlined.SearchOff,
                                    tint = ErrorTone
                                )
                            }
                        }
                        item {
                            AppDashboardSection(
                                title = "Teklifler",
                                subtitle = "${filtered.size} kayıt"
                            ) {}
                        }
                        if (filtered.isEmpty()) {
                            item {
                                AppEmptyState(
                                    title = if (query.isBlank()) "Henüz teklif yok" else "Eşleşen teklif yok",
                                    subtitle = if (query.isBlank()) "Yukarıdan yeni teklif oluşturabilirsiniz."
                                    else "Farklı bir arama deneyin.",
                                    icon = Icons.Outlined.SearchOff,
                                    tint = AccentPurple
                                )
                            }
                        } else {
                            items(filtered, key = { it.id ?: "${it.customerName}-${it.totalPrice}" }) { proposal ->
                                ProposalRow(
                                    proposal = proposal,
                                    readOnly = session.isReadOnly,
                                    deleting = uiState.deletingId == proposal.id,
                                    converting = uiState.convertingId == proposal.id,
                                    downloadingPdf = uiState.downloadingPdfId == proposal.id,
                                    onEdit = {
                                        editingProposal = proposal
                                        showForm = true
                                    },
                                    onPdf = {
                                        proposal.id?.let { proposalId ->
                                            scope.launch {
                                                runCatching {
                                                    viewModel.downloadProposalPdf(proposalId)
                                                }.onSuccess { bytes ->
                                                    ProposalPdfHelper.saveAndShare(context, proposalId, bytes)
                                                }
                                            }
                                        }
                                    },
                                    onConvert = {
                                        if (proposal.id != null) convertProposalId = proposal.id
                                    },
                                    onDelete = {
                                        if (proposal.id != null) deleteProposalId = proposal.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        ProposalFormDialog(
            initial = editingProposal,
            customers = uiState.customers,
            saving = uiState.saving,
            readOnly = session.isReadOnly,
            onDismiss = {
                showForm = false
                editingProposal = null
            },
            onSave = { editingId, customerId, title, status, validUntil, note, taxRate, discount, items ->
                viewModel.saveProposal(
                    editingId = editingId,
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
        )
    }

    if (deleteProposalId != null) {
        AlertDialog(
            onDismissRequest = { deleteProposalId = null },
            title = { Text("Teklif silinsin mi?") },
            text = { Text("Bu teklif kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProposal(deleteProposalId ?: return@TextButton)
                        deleteProposalId = null
                    }
                ) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { deleteProposalId = null }) { Text("Vazgeç") }
            }
        )
    }

    if (convertProposalId != null) {
        AlertDialog(
            onDismissRequest = { convertProposalId = null },
            title = { Text("İşe dönüştürülsün mü?") },
            text = { Text("Teklif onaylanıp servis iş emrine dönüştürülecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.convertToJob(convertProposalId ?: return@TextButton)
                        convertProposalId = null
                    }
                ) { Text("Dönüştür") }
            },
            dismissButton = {
                TextButton(onClick = { convertProposalId = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun ProposalRow(
    proposal: ProposalDTO,
    readOnly: Boolean,
    deleting: Boolean,
    converting: Boolean,
    downloadingPdf: Boolean,
    onEdit: () -> Unit,
    onPdf: () -> Unit,
    onConvert: () -> Unit,
    onDelete: () -> Unit
) {
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppIconBadge(icon = Icons.Outlined.PictureAsPdf, tint = proposalStatusTint(proposal.status))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = proposal.title ?: "Teklif #${proposal.id ?: "-"}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = proposal.customerName.orEmpty().ifBlank { "Müşterisiz" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (!proposal.validUntil.isNullOrBlank()) {
                    Text(
                        text = "Geçerlilik: ${proposal.validUntil}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "₺${"%.2f".format(Locale("tr", "TR"), proposal.totalPrice ?: 0.0)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                AppStatusBadge(text = statusLabel(proposal.status), statusKey = proposal.status ?: "")
            }
        }
        if (proposal.subtotal != null || proposal.taxAmount != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Ara toplam: ₺${"%.2f".format(Locale("tr", "TR"), proposal.subtotal ?: 0.0)} • KDV: ₺${"%.2f".format(Locale("tr", "TR"), proposal.taxAmount ?: 0.0)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            TextButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onEdit
            ) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text("Düzenle")
            }
            TextButton(
                modifier = Modifier.weight(1f),
                enabled = !downloadingPdf,
                onClick = onPdf
            ) {
                Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text(if (downloadingPdf) "..." else "PDF")
            }
            TextButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                enabled = !converting && proposal.status != "APPROVED" && proposal.status != "REJECTED" && !readOnly,
                onClick = onConvert
            ) {
                Icon(imageVector = Icons.Outlined.SwapHoriz, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text(if (converting) "..." else "İşe Dönüştür")
            }
            TextButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                enabled = !deleting && !readOnly,
                onClick = onDelete
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = ErrorTone)
                Spacer(Modifier.padding(start = Spacing.xs))
                Text(if (deleting) "..." else "Sil", color = ErrorTone)
            }
        }
    }
}

private fun proposalStatusTint(status: String?) = when (status?.uppercase()) {
    "APPROVED" -> com.pusula.service.ui.theme.Success
    "REJECTED" -> ErrorTone
    "SENT" -> Info
    else -> AccentCyan
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalFormDialog(
    initial: ProposalDTO?,
    customers: List<CustomerDTO>,
    saving: Boolean,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        editingId: Long?,
        customerId: Long,
        title: String,
        status: String,
        validUntil: String,
        note: String,
        taxRate: Double,
        discount: Double,
        items: List<ProposalItemDTO>
    ) -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var validUntil by remember(initial?.id) {
        mutableStateOf(
            initial?.validUntil?.takeIf { it.isNotBlank() }
                ?: LocalDate.now().plusDays(30).toString()
        )
    }
    var showDatePicker by remember(initial?.id) { mutableStateOf(false) }
    val isCreateMode = initial == null
    var status by remember(initial?.id) { mutableStateOf(initial?.status ?: "DRAFT") }
    var taxRateText by remember(initial?.id) { mutableStateOf((initial?.taxRate ?: 20.0).toString()) }
    var discountText by remember(initial?.id) { mutableStateOf((initial?.discount ?: 0.0).toString()) }
    var selectedCustomerId by remember(initial?.id) { mutableStateOf(initial?.customerId) }
    var expandedStatus by remember(initial?.id) { mutableStateOf(false) }
    var expandedCustomer by remember(initial?.id) { mutableStateOf(false) }

    var itemDescription by remember(initial?.id) { mutableStateOf("") }
    var itemQtyText by remember(initial?.id) { mutableStateOf("1") }
    var itemUnitPriceText by remember(initial?.id) { mutableStateOf("") }
    var items by remember(initial?.id) { mutableStateOf(initial?.items ?: emptyList()) }

    val customerTitle = customers.firstOrNull { it.id == selectedCustomerId }?.name.orEmpty()
    val availableStatuses = remember(initial?.id, status) {
        allowedNextStatuses(initialStatus = initial?.status, currentStatus = status)
    }
    val subtotal = items.sumOf { (it.totalPrice ?: (it.unitPrice * it.quantity)) }
    val taxRate = taxRateText.toDoubleOrNull() ?: 0.0
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val total = subtotal + (subtotal * taxRate / 100.0) - discount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Teklif" else "Teklif Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedCustomer,
                    onExpandedChange = { expandedCustomer = it }
                ) {
                    OutlinedTextField(
                        value = customerTitle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Müşteri") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCustomer) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCustomer,
                        onDismissRequest = { expandedCustomer = false }
                    ) {
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text = { Text(customer.name) },
                                onClick = {
                                    selectedCustomerId = customer.id
                                    expandedCustomer = false
                                }
                            )
                        }
                    }
                }

                if (isCreateMode) {
                    OutlinedTextField(
                        value = statusLabel("DRAFT"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Durum") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expandedStatus,
                        onExpandedChange = {
                            expandedStatus = availableStatuses.size > 1 && it
                        }
                    ) {
                        OutlinedTextField(
                            value = statusLabel(status),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Durum") },
                            trailingIcon = {
                                if (availableStatuses.size > 1) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatus)
                                }
                            },
                            modifier = Modifier
                                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStatus,
                            onDismissRequest = { expandedStatus = false }
                        ) {
                            availableStatuses.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(statusLabel(option)) },
                                    onClick = {
                                        status = option
                                        expandedStatus = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = validUntil,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Geçerlilik Tarihi") },
                    trailingIcon = {
                        TextButton(
                            modifier = Modifier.readOnlyProtected(readOnly),
                            onClick = { showDatePicker = true }
                        ) { Text("Seç") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .readOnlyProtected(readOnly)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = taxRateText,
                        onValueChange = { taxRateText = it },
                        label = { Text("KDV %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it },
                        label = { Text("İndirim") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Kalemler", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = itemDescription,
                        onValueChange = { itemDescription = it },
                        label = { Text("Açıklama") },
                        modifier = Modifier.weight(1.8f)
                    )
                    OutlinedTextField(
                        value = itemQtyText,
                        onValueChange = { itemQtyText = it },
                        label = { Text("Adet") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = itemUnitPriceText,
                        onValueChange = { itemUnitPriceText = it },
                        label = { Text("Fiyat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(
                    modifier = Modifier.readOnlyProtected(readOnly),
                    onClick = {
                        val qty = itemQtyText.toIntOrNull() ?: 0
                        val unitPrice = itemUnitPriceText.toDoubleOrNull() ?: 0.0
                        if (itemDescription.isNotBlank() && qty > 0 && unitPrice > 0.0) {
                            items = items + ProposalItemDTO(
                                description = itemDescription.trim(),
                                quantity = qty,
                                unitPrice = unitPrice,
                                totalPrice = qty * unitPrice
                            )
                            itemDescription = ""
                            itemQtyText = "1"
                            itemUnitPriceText = ""
                        }
                    }
                ) { Text("Kalem Ekle") }

                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.description} x${item.quantity}")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${"%.2f".format(Locale("tr", "TR"), item.totalPrice ?: 0.0)} ₺")
                            TextButton(
                                modifier = Modifier.readOnlyProtected(readOnly),
                                onClick = {
                                    items = items.filterIndexed { i, _ -> i != index }
                                }
                            ) { Text("Sil") }
                        }
                    }
                }

                Text("Ara toplam: ${"%.2f".format(Locale("tr", "TR"), subtotal)} ₺")
                Text("Toplam: ${"%.2f".format(Locale("tr", "TR"), total)} ₺", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !readOnly && selectedCustomerId != null && title.isNotBlank() &&
                    validUntil.isNotBlank() && items.isNotEmpty(),
                onClick = {
                    onSave(
                        initial?.id,
                        selectedCustomerId ?: return@TextButton,
                        title.trim(),
                        if (isCreateMode) "DRAFT" else status,
                        validUntil.trim(),
                        note.trim(),
                        taxRateText.toDoubleOrNull() ?: 0.0,
                        discountText.toDoubleOrNull() ?: 0.0,
                        items
                    )
                }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )

    if (showDatePicker) {
        val initialMillis = remember(validUntil) {
            runCatching {
                LocalDate.parse(validUntil)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        validUntil = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    }
                    showDatePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Vazgeç") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun statusLabel(status: String?): String = when (status) {
    "DRAFT" -> "Taslak"
    "SENT" -> "Gönderildi"
    "APPROVED" -> "Onaylandı"
    "REJECTED" -> "Reddedildi"
    else -> status ?: "-"
}

private fun allowedNextStatuses(initialStatus: String?, currentStatus: String): List<String> {
    return when (initialStatus ?: "DRAFT") {
        "DRAFT" -> listOf("DRAFT", "SENT", "REJECTED")
        "SENT" -> listOf("SENT", "APPROVED", "REJECTED")
        "REJECTED" -> listOf("REJECTED")
        "APPROVED" -> listOf("APPROVED")
        else -> listOf(currentStatus)
    }
}
