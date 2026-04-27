package com.pusula.service.ui.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

private data class PaymentMethodOption(
    val label: String,
    val apiValue: String
)

private val paymentMethods = listOf(
    PaymentMethodOption(label = "Nakit", apiValue = "CASH"),
    PaymentMethodOption(label = "Kredi Kartı", apiValue = "CREDIT_CARD"),
    PaymentMethodOption(label = "Cari Hesap", apiValue = "CURRENT_ACCOUNT")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    ticketId: Long,
    onDone: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val ticket = uiState.selectedTicket
    val total = uiState.usedParts.sumOf { it.sellingPriceSnapshot * it.quantityUsed }

    var expanded by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(paymentMethods.first()) }
    var amountText by remember { mutableStateOf("%.2f".format(total)) }
    var confirmCari by remember { mutableStateOf(false) }

    val entered = amountText.toDoubleOrNull() ?: 0.0
    val remain = (total - entered).coerceAtLeast(0.0)

    LaunchedEffect(uiState.serviceCompletedTicketId) {
        if (uiState.serviceCompletedTicketId == ticketId) {
            viewModel.consumeServiceCompleted()
            onDone()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Tahsilat") }) }) { padding ->
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
                    eyebrow = "Servis fişi #$ticketId",
                    title = "₺${"%.2f".format(total)}",
                    subtitle = ticket?.customerName?.let { "Müşteri: $it" },
                    badge = ticket?.customerBalance?.let { "Bakiye: ₺${"%.2f".format(it)}" }
                )
            }

            item {
                AppDashboardSection(title = "Ödeme Yöntemi") {
                    AppGhostCard {
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = method.label,
                                onValueChange = {},
                                label = { Text("Yöntem") },
                                readOnly = true,
                                leadingIcon = {
                                    AppIconBadge(
                                        icon = methodIcon(method.label),
                                        tint = methodTint(method.label),
                                        size = 28.dp,
                                        iconSize = 14.dp,
                                        cornerRadius = 8.dp
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                paymentMethods.forEach {
                                    DropdownMenuItem(
                                        text = { Text(it.label) },
                                        onClick = {
                                            method = it
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppDashboardSection(title = "Tahsilat Tutarı") {
                    AppGhostCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                label = { Text("Tahsil edilen tutar") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cari'ye aktarılacak",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₺${"%.2f".format(remain)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (remain > 0) Warning else Success
                                )
                            }
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (remain > 0 && method.label != "Cari Hesap") {
                                confirmCari = true
                            } else {
                                viewModel.completeService(ticketId, entered, method.apiValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().readOnlyProtected(session.isReadOnly)
                    ) {
                        Text("Tahsilatı Kaydet")
                    }
                }
            }
        }
    }

    if (confirmCari) {
        AlertDialog(
            onDismissRequest = { confirmCari = false },
            title = { Text("Güvenlik Onayı") },
            text = { Text("Kalan tutar cari hesaba aktarılacak. Onaylıyor musunuz?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.completeService(ticketId, entered, method.apiValue)
                    confirmCari = false
                }) {
                    Text("Onayla")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCari = false }) { Text("Vazgeç") }
            }
        )
    }
}

private fun methodIcon(method: String) = when (method) {
    "Kredi Kartı" -> Icons.Outlined.CreditCard
    "Cari Hesap" -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.Outlined.Payments
}

private fun methodTint(method: String) = when (method) {
    "Kredi Kartı" -> Info
    "Cari Hesap" -> AccentOrange
    else -> AccentPurple
}
