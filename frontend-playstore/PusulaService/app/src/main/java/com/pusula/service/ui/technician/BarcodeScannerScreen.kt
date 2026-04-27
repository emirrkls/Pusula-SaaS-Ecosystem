package com.pusula.service.ui.technician

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.featureGated
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.CameraBarcodeScannerView
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning
import kotlinx.coroutines.delay

@Composable
fun BarcodeScannerScreen(
    ticketId: Long,
    onDone: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    var quantity by remember { mutableIntStateOf(1) }
    var scanLocked by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.usedPartAddedTicketId) {
        if (uiState.usedPartAddedTicketId == ticketId) {
            viewModel.consumeUsedPartAdded()
            onDone()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Barkod Tarayıcı") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            AppHeroCard(
                eyebrow = "Servis fişi #$ticketId",
                title = "Parça tarat",
                subtitle = "Kamerayı barkoda doğrultun, otomatik okunsun.",
                badge = if (uiState.barcodeItem != null) "Ürün bulundu" else "Hazır"
            )

            CameraBarcodeScannerView(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { code ->
                scanLocked = true
                viewModel.lookupBarcode(code)
            }

            val item = uiState.barcodeItem
            if (item != null) {
                AppDashboardSection(title = "Bulunan Parça") {
                    AppGhostCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBadge(icon = Icons.Outlined.Inventory2, tint = AccentCyan)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = item.partName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                                Text(
                                    text = listOfNotNull(item.brand?.takeIf { it.isNotBlank() }, "Stok: ${item.quantity}").joinToString(" • "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "₺${"%.2f".format(item.sellPrice ?: 0.0)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Success
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .featureGated(viewModel.sessionManager, "BASIC_INVENTORY")
                                .readOnlyProtected(session.isReadOnly),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuantityButton(icon = Icons.Outlined.Remove) { if (quantity > 1) quantity-- }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "$quantity",
                                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            QuantityButton(icon = Icons.Outlined.Add) { quantity++ }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    viewModel.addUsedPart(ticketId, item, quantity)
                                }
                            ) {
                                Text("Sepete Ekle")
                            }
                        }
                    }
                }
            } else if (scanLocked) {
                AppEmptyState(
                    title = "Ürün bulunamadı",
                    subtitle = "Tekrar tarama için hazırlanılıyor...",
                    icon = Icons.Outlined.QrCodeScanner,
                    tint = AccentPurple
                )
                LaunchedEffect(uiState.error) {
                    delay(2000)
                    scanLocked = false
                    viewModel.clearBarcodeResult()
                }
            } else {
                AppEmptyState(
                    title = "Tarama bekleniyor",
                    subtitle = "Barkodu kamera çerçevesine yerleştirin.",
                    icon = Icons.Outlined.QrCodeScanner,
                    tint = Info
                )
            }
        }
    }
}

@Composable
private fun QuantityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp).width(44.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}
