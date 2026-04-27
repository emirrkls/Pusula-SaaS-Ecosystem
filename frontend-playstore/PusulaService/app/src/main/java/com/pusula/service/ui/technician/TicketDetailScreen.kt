package com.pusula.service.ui.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppHeroChip
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppQuickActionTile
import com.pusula.service.ui.components.AppStatusBadge
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    ticketId: Long,
    onOpenBarcode: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSignature: (Long) -> Unit,
    onGeneratePdf: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    var expanded by remember(ticketId) { mutableStateOf(false) }
    var selectedTechName by remember(ticketId) { mutableStateOf("Teknisyen Seç") }

    LaunchedEffect(ticketId) { viewModel.selectTicket(ticketId) }

    val ticket = uiState.selectedTicket

    Scaffold(topBar = { TopAppBar(title = { Text("İş Emri Detayı") }) }) { padding ->
        if (ticket == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg), contentAlignment = Alignment.Center) {
                AppEmptyState(
                    title = "İş emri yükleniyor",
                    subtitle = "Detaylar hazırlanıyor...",
                    icon = Icons.Outlined.Build,
                    tint = AccentPurple
                )
            }
            return@Scaffold
        }

        val totalAmount = uiState.usedParts.sumOf { it.sellingPriceSnapshot * it.quantityUsed }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
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
                    eyebrow = "Servis fişi #${ticket.id}",
                    title = ticket.customerName ?: "Müşteri",
                    subtitle = ticket.scheduledDate?.takeIf { it.isNotBlank() }?.let { "Plan: $it" },
                    badge = ticket.status?.let { translateStatus(it) },
                    extraContent = {
                        if (totalAmount > 0.0) {
                            AppHeroChipMoney(totalAmount)
                        }
                    }
                )
            }

            item {
                AppDashboardSection(title = "Müşteri") {
                    AppGhostCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            DetailRow(icon = Icons.Outlined.Person, label = "Ad", value = ticket.customerName.orEmpty().ifBlank { "-" })
                            DetailRow(icon = Icons.Outlined.Phone, label = "Telefon", value = ticket.customerPhone.orEmpty().ifBlank { "-" })
                            DetailRow(icon = Icons.Outlined.Home, label = "Adres", value = ticket.customerAddress.orEmpty().ifBlank { "-" })
                        }
                    }
                }
            }

            item {
                AppDashboardSection(title = "Servis") {
                    AppGhostCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                AppIconBadge(icon = Icons.Outlined.Description, tint = Info, size = 32.dp, iconSize = 16.dp, cornerRadius = 10.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Açıklama",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = ticket.description?.takeIf { it.isNotBlank() } ?: "Açıklama girilmemiş",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Atanan teknisyen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppStatusBadge(
                                    text = ticket.assignedTechnicianName ?: "Atanmadı",
                                    statusKey = if (ticket.assignedTechnicianName.isNullOrBlank()) "PENDING" else "IN_PROGRESS"
                                )
                            }
                            DetailRow(
                                icon = Icons.Outlined.Description,
                                label = "Notlar",
                                value = ticket.notes.orEmpty().ifBlank { "-" }
                            )
                            DetailRow(
                                icon = Icons.Outlined.Build,
                                label = "Garanti çağrısı",
                                value = if (ticket.isWarrantyCall == true) "Evet" else "Hayır"
                            )
                            DetailRow(
                                icon = Icons.Outlined.Build,
                                label = "Bağlı fiş",
                                value = ticket.parentTicketId?.toString() ?: "-"
                            )
                        }
                    }
                }
            }

            if (session.isAdmin) {
                item {
                    AppDashboardSection(title = "Yönetim") {
                        AppGhostCard {
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(
                                    value = selectedTechName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Teknisyen Yeniden Ata") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    uiState.technicians.forEach { tech ->
                                        DropdownMenuItem(
                                            text = { Text(tech.fullName ?: "Teknisyen #${tech.id}") },
                                            onClick = {
                                                selectedTechName = tech.fullName ?: "Teknisyen #${tech.id}"
                                                expanded = false
                                                viewModel.assignTechnician(ticketId, tech.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppDashboardSection(
                    title = "Kullanılan Parçalar",
                    subtitle = if (uiState.usedParts.isEmpty()) null else "${uiState.usedParts.size} kalem"
                ) {
                    if (uiState.usedParts.isEmpty()) {
                        AppEmptyState(
                            title = "Henüz parça eklenmemiş",
                            subtitle = "Barkod taratarak parça ekleyebilirsiniz.",
                            icon = Icons.Outlined.Inventory2,
                            tint = AccentPurple
                        )
                    }
                }
            }
            items(uiState.usedParts, key = { it.id ?: it.inventoryId }) { part ->
                AppGhostCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        AppIconBadge(icon = Icons.Outlined.Inventory2, tint = AccentCyan)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = part.partName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            Text(
                                text = "${part.quantityUsed} adet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "₺${"%.2f".format(part.sellingPriceSnapshot * part.quantityUsed)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Success
                        )
                    }
                }
            }

            item {
                AppDashboardSection(title = "İşlemler") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            AppQuickActionTile(
                                label = "Barkod",
                                icon = Icons.Outlined.QrCodeScanner,
                                tint = Info,
                                onClick = { onOpenBarcode(ticketId) },
                                modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                            )
                            AppQuickActionTile(
                                label = "Tahsilat",
                                icon = Icons.Outlined.RequestQuote,
                                tint = Success,
                                onClick = { onOpenCollection(ticketId) },
                                modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                            )
                            AppQuickActionTile(
                                label = "İmza",
                                icon = Icons.Outlined.Build,
                                tint = AccentOrange,
                                onClick = { onOpenSignature(ticketId) },
                                modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                            )
                            AppQuickActionTile(
                                label = "PDF",
                                icon = Icons.Outlined.PictureAsPdf,
                                tint = AccentPurple,
                                onClick = onGeneratePdf,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AppIconBadge(icon = icon, tint = Info, size = 32.dp, iconSize = 16.dp, cornerRadius = 10.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AppHeroChipMoney(amount: Double) {
    AppHeroChip(text = "Toplam ₺${"%.2f".format(amount)}")
}

private fun translateStatus(status: String): String = when (status.uppercase()) {
    "PENDING" -> "Bekliyor"
    "ASSIGNED" -> "Atandı"
    "IN_PROGRESS" -> "Devam Ediyor"
    "COMPLETED" -> "Tamamlandı"
    "CANCELLED" -> "İptal"
    else -> status
}
