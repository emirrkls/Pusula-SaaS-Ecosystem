package com.pusula.service.ui.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import com.pusula.service.ui.components.AppTopBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pusula.service.BuildConfig
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun ServiceQualityScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var filterType by remember { mutableStateOf<String?>(null) }
    var ticketNoInput by remember { mutableStateOf("") }
    var startDateInput by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDateInput by remember { mutableStateOf(LocalDate.now().toString()) }

    LaunchedEffect(Unit) {
        viewModel.loadServiceQualityPhotos(type = null, ticketId = null, startDate = startDateInput, endDate = endDateInput, limit = 200)
    }

    BackHandler(onBack = onBack)

    Scaffold(topBar = { AppTopBar(title = "Servis Kalite", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilledTonalButton(
                        onClick = { filterType = null },
                        enabled = filterType != null
                    ) { Text("Tümü") }
                    OutlinedButton(
                        onClick = { filterType = "BEFORE" },
                        enabled = filterType != "BEFORE"
                    ) { Text("Öncesi") }
                    OutlinedButton(
                        onClick = { filterType = "AFTER" },
                        enabled = filterType != "AFTER"
                    ) { Text("Sonrası") }
                }
            }
            item {
                OutlinedTextField(
                    value = startDateInput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Başlangıç Tarihi") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        OutlinedButton(onClick = {
                            val now = LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    startDateInput = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                },
                                now.year,
                                now.monthValue - 1,
                                now.dayOfMonth
                            ).show()
                        }) { Text("Seç") }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = endDateInput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bitiş Tarihi") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        OutlinedButton(onClick = {
                            val now = LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    endDateInput = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                },
                                now.year,
                                now.monthValue - 1,
                                now.dayOfMonth
                            ).show()
                        }) { Text("Seç") }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = ticketNoInput,
                    onValueChange = { ticketNoInput = it },
                    label = { Text("Ticket No") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        viewModel.loadServiceQualityPhotos(
                            type = filterType,
                            ticketId = ticketNoInput.toLongOrNull(),
                            startDate = startDateInput.ifBlank { null },
                            endDate = endDateInput.ifBlank { null },
                            limit = 200
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Filtreyi Uygula") }
            }

            if (uiState.serviceQualityPhotos.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "Görsel bulunamadı",
                        subtitle = "Seçilen filtreye uygun servis kalite görseli yok.",
                        icon = Icons.Outlined.PhotoLibrary,
                        tint = AccentPurple
                    )
                }
            } else {
                items(uiState.serviceQualityPhotos, key = { it.id }) { photo ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            AsyncImage(
                                model = toAbsoluteUrl(photo.url),
                                contentDescription = "Servis kalite görseli",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = if (photo.type == "BEFORE") "Öncesi" else "Sonrası",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Fiş #${photo.ticketId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = photo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toAbsoluteUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        BuildConfig.API_BASE_URL.removeSuffix("/") + url
    }
}
