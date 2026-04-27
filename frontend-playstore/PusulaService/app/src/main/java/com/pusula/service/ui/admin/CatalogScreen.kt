package com.pusula.service.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.components.CameraBarcodeScannerView
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingItemId by remember { mutableStateOf<Long?>(null) }
    var deletingItemId by remember { mutableStateOf<Long?>(null) }
    var showWorkflowDialog by remember { mutableStateOf(false) }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var createScannedBarcode by remember { mutableStateOf<String?>(null) }
    var editScannedBarcode by remember { mutableStateOf<String?>(null) }
    var activeBarcodeTarget by remember { mutableStateOf(BarcodeTarget.CREATE) }

    LaunchedEffect(uiState.inventoryCreatedAt) {
        if (uiState.inventoryCreatedAt != null) {
            showCreateDialog = false
            viewModel.consumeInventoryCreatedEvent()
        }
    }
    LaunchedEffect(uiState.inventoryUpdatedAt) {
        if (uiState.inventoryUpdatedAt != null) {
            editingItemId = null
            viewModel.consumeInventoryUpdatedEvent()
        }
    }
    LaunchedEffect(uiState.inventoryDeletedAt) {
        if (uiState.inventoryDeletedAt != null) {
            deletingItemId = null
            viewModel.consumeInventoryDeletedEvent()
        }
    }

    val isAllowed = session.role == "COMPANY_ADMIN" || session.role == "SUPER_ADMIN"

    if (!isAllowed) {
        Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            AppEmptyState(
                title = "Bu ekran sadece adminlere açık",
                subtitle = "Erişim için yöneticinizle iletişime geçin.",
                icon = Icons.Default.Inventory2,
                tint = ErrorTone
            )
        }
        return
    }

    val query = uiState.catalogQuery
    val filtered = remember(query, uiState.inventory) {
        uiState.inventory.filter {
            query.isBlank() || it.partName.contains(query, true) || it.brand.orEmpty().contains(query, true)
        }
    }
    val totalValue = uiState.inventory.sumOf { (it.sellPrice ?: 0.0) * it.quantity.toDouble() }
    val avgMargin = uiState.inventory
        .mapNotNull {
            val sell = it.sellPrice ?: return@mapNotNull null
            val buy = sell * 0.75
            if (buy <= 0) null else ((sell - buy) / buy) * 100.0
        }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toInt() ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
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
                    eyebrow = "Envanter",
                    title = "Katalog",
                    subtitle = "${uiState.inventory.size} parça • ₺${formatAmount(totalValue)} toplam değer",
                    badge = "Ortalama %$avgMargin marj"
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setCatalogQuery,
                        label = { Text("Parça veya marka ara") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppPrimaryButton(
                        text = "Yeni Stok Kalemi Ekle",
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppPrimaryButton(
                        text = "Stok Tarama Modu",
                        onClick = { showWorkflowDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                AppDashboardSection(
                    title = "Parçalar",
                    subtitle = "${filtered.size} sonuç"
                ) {
                    if (filtered.isEmpty()) {
                        AppEmptyState(
                            title = if (query.isBlank()) "Henüz parça yok" else "Sonuç bulunamadı",
                            subtitle = if (query.isBlank()) {
                                "İlk parçanızı eklediğinizde burada görünecek."
                            } else {
                                "Arama terimini değiştirip tekrar deneyin."
                            },
                            icon = Icons.Outlined.Inventory2
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { item ->
                val sell = item.sellPrice ?: 0.0
                val buy = sell * 0.75
                val margin = if (buy == 0.0) 0 else (((sell - buy) / buy) * 100).toInt()
                val marginColor = when {
                    margin >= 30 -> Success
                    margin >= 15 -> AccentPurple
                    else -> AccentOrange
                }
                AppGhostCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        AppIconBadge(
                            icon = Icons.Default.Inventory2,
                            tint = marginColor
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.partName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            Text(
                                text = item.brand?.takeIf { it.isNotBlank() } ?: "Marka belirtilmemiş",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = "Alış ₺${formatAmount(buy)}  •  Satış ₺${formatAmount(sell)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%$margin",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = marginColor
                            )
                            Text(
                                text = "Stok ${item.quantity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TextButton(
                            onClick = { editingItemId = item.id },
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIconBadge(icon = Icons.Outlined.Edit, tint = AccentPurple, size = 22.dp, iconSize = 12.dp, cornerRadius = 6.dp)
                            Text(" Düzenle")
                        }
                        TextButton(
                            onClick = { deletingItemId = item.id },
                            enabled = uiState.deletingInventoryId != item.id,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIconBadge(icon = Icons.Outlined.Delete, tint = ErrorTone, size = 22.dp, iconSize = 12.dp, cornerRadius = 6.dp)
                            Text(if (uiState.deletingInventoryId == item.id) " Siliniyor..." else " Sil")
                        }
                    }
                }
            }
        }
        LazyLoadingOverlay(isLoading = uiState.loading)
    }

    if (showCreateDialog) {
        InventoryCreateDialog(
            saving = uiState.creatingInventory,
            initial = null,
            initialBarcode = createScannedBarcode,
            title = "Yeni Stok Kalemi",
            onDismiss = { showCreateDialog = false },
            onScanBarcodeRequest = {
                activeBarcodeTarget = BarcodeTarget.CREATE
                showBarcodeScannerDialog = true
            },
            onSave = { partName, quantity, buyPrice, sellPrice, criticalLevel, brand, category, barcode ->
                viewModel.createInventoryItem(
                    partName = partName,
                    quantity = quantity,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    criticalLevel = criticalLevel,
                    brand = brand,
                    category = category,
                    barcode = barcode
                )
            }
        )
    }

    val editingItem = uiState.inventory.firstOrNull { it.id == editingItemId }
    if (editingItem != null) {
        InventoryCreateDialog(
            saving = uiState.updatingInventoryId == editingItem.id,
            initial = editingItem,
            initialBarcode = editScannedBarcode,
            title = "Stok Kalemi Düzenle",
            onDismiss = { editingItemId = null },
            onScanBarcodeRequest = {
                activeBarcodeTarget = BarcodeTarget.EDIT
                showBarcodeScannerDialog = true
            },
            onSave = { partName, quantity, buyPrice, sellPrice, criticalLevel, brand, category, barcode ->
                viewModel.updateInventoryItem(
                    id = editingItem.id,
                    partName = partName,
                    quantity = quantity,
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    criticalLevel = criticalLevel,
                    brand = brand,
                    category = category,
                    barcode = barcode
                )
            }
        )
    }

    if (showWorkflowDialog) {
        AlertDialog(
            onDismissRequest = {
                showWorkflowDialog = false
                viewModel.clearBarcodeLookupState()
            },
            title = { Text("Stok Tarama Modu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppPrimaryButton(
                        text = "Barkod Tara",
                        onClick = {
                            activeBarcodeTarget = BarcodeTarget.WORKFLOW
                            showBarcodeScannerDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        uiState.barcodeLookupLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                                Text("Barkod aranıyor...")
                            }
                        }
                        uiState.barcodeLookupItem != null -> {
                            val found = uiState.barcodeLookupItem ?: return@AlertDialog
                            Text("Bulundu: ${found.partName}")
                            Text("Stok: ${found.quantity}")
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TextButton(onClick = {
                                    editingItemId = found.id
                                    showWorkflowDialog = false
                                }) { Text("Düzenle") }
                                TextButton(onClick = {
                                    deletingItemId = found.id
                                    showWorkflowDialog = false
                                }) { Text("Sil") }
                            }
                        }
                        !uiState.barcodeLookupCode.isNullOrBlank() -> {
                            Text("Ürün bulunamadı: ${uiState.barcodeLookupCode}")
                            TextButton(onClick = {
                                createScannedBarcode = uiState.barcodeLookupCode
                                showCreateDialog = true
                                showWorkflowDialog = false
                            }) { Text("Yeni ürün olarak ekle") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWorkflowDialog = false
                    viewModel.clearBarcodeLookupState()
                }) { Text("Kapat") }
            }
        )
    }

    if (showBarcodeScannerDialog) {
        Dialog(
            onDismissRequest = { showBarcodeScannerDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
            ) {
                CameraBarcodeScannerView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .padding(Spacing.sm)
                ) { code ->
                    when (activeBarcodeTarget) {
                        BarcodeTarget.CREATE -> createScannedBarcode = code
                        BarcodeTarget.EDIT -> editScannedBarcode = code
                        BarcodeTarget.WORKFLOW -> viewModel.lookupInventoryByBarcode(code)
                    }
                    showBarcodeScannerDialog = false
                }
            }
        }
    }

    if (deletingItemId != null) {
        AlertDialog(
            onDismissRequest = { deletingItemId = null },
            title = { Text("Stok kalemi silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deletingItemId ?: return@TextButton
                    viewModel.deleteInventoryItem(id)
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { deletingItemId = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun InventoryCreateDialog(
    saving: Boolean,
    initial: com.pusula.service.data.model.InventoryItemDTO?,
    initialBarcode: String?,
    title: String,
    onDismiss: () -> Unit,
    onScanBarcodeRequest: () -> Unit,
    onSave: (
        partName: String,
        quantity: Int,
        buyPrice: Double?,
        sellPrice: Double?,
        criticalLevel: Int?,
        brand: String?,
        category: String?,
        barcode: String?
    ) -> Unit
) {
    var partName by remember(initial?.id) { mutableStateOf(initial?.partName.orEmpty()) }
    var quantityText by remember(initial?.id) { mutableStateOf((initial?.quantity ?: 1).toString()) }
    var buyPriceText by remember(initial?.id) { mutableStateOf(initial?.buyPrice?.toString().orEmpty()) }
    var sellPriceText by remember(initial?.id) { mutableStateOf(initial?.sellPrice?.toString().orEmpty()) }
    var criticalLevelText by remember(initial?.id) { mutableStateOf(initial?.criticalLevel?.toString().orEmpty()) }
    var brand by remember(initial?.id) { mutableStateOf(initial?.brand.orEmpty()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category.orEmpty()) }
    var barcode by remember(initial?.id) { mutableStateOf(initial?.barcode.orEmpty()) }

    LaunchedEffect(initialBarcode) {
        if (!initialBarcode.isNullOrBlank()) barcode = initialBarcode
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = partName,
                    onValueChange = { partName = it },
                    label = { Text("Parça Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Adet") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = buyPriceText,
                    onValueChange = { buyPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Alış Fiyatı (opsiyonel)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sellPriceText,
                    onValueChange = { sellPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Satış Fiyatı (opsiyonel)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = criticalLevelText,
                    onValueChange = { criticalLevelText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Kritik Seviye (opsiyonel)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marka (opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori (opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barkod (opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onScanBarcodeRequest) { Text("Barkod Tara") }
            }
        },
        confirmButton = {
            val quantity = quantityText.toIntOrNull() ?: 0
            TextButton(
                enabled = !saving && partName.isNotBlank() && quantity > 0,
                onClick = {
                    onSave(
                        partName.trim(),
                        quantity,
                        buyPriceText.replace(",", ".").toDoubleOrNull(),
                        sellPriceText.replace(",", ".").toDoubleOrNull(),
                        criticalLevelText.toIntOrNull(),
                        brand.trim().ifBlank { null },
                        category.trim().ifBlank { null },
                        barcode.trim().ifBlank { null }
                    )
                }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

private enum class BarcodeTarget {
    CREATE,
    EDIT,
    WORKFLOW
}
