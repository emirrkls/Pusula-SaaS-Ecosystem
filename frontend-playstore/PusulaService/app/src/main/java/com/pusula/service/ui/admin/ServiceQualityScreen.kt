package com.pusula.service.ui.admin

import android.app.DatePickerDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pusula.service.BuildConfig
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import java.time.LocalDate
import com.pusula.service.data.model.ServicePhotoDTO

@Composable
fun ServiceQualityScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var filterType by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var startDateInput by remember { mutableStateOf("") }
    var endDateInput by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<ServicePhotoDTO?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadServiceQualityPhotos(type = null, ticketId = null, startDate = null, endDate = null, limit = 500)
    }

    BackHandler(onBack = onBack)

    selectedPhoto?.let { photo ->
        var imageScale by remember(photo.id) { mutableStateOf(1f) }
        Dialog(
            onDismissRequest = { selectedPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(modifier = Modifier.fillMaxSize().padding(Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AsyncImage(
                        model = toAbsoluteUrl(photo.url), contentDescription = "Büyük servis görseli",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .graphicsLayer(scaleX = imageScale, scaleY = imageScale)
                            .pointerInput(photo.id) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    imageScale = (imageScale * zoom).coerceIn(1f, 6f)
                                }
                            }
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(servicePhotoCategoryLabel(photo.type), style = MaterialTheme.typography.titleMedium)
                    photo.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedPhoto = null }) { Text("Kapat") }
                        TextButton(onClick = {
                            downloadServicePhoto(context, toAbsoluteUrl(photo.url), "servis-${photo.ticketId}-${photo.id}.jpg")
                        }) { Text("İndir") }
                    }
                }
            }
        }
    }

    Scaffold(topBar = { AppTopBar(title = "Servis Görselleri", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
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
                    listOf(
                        "INDOOR_UNIT_SERIAL" to "İç Seri No", "OUTDOOR_UNIT_SERIAL" to "Dış Seri No",
                        "DEVICE_LABEL" to "Etiket", "FAULT_DETAIL" to "Arıza", "INSTALLATION" to "Montaj", "OTHER" to "Diğer"
                    ).forEach { (type, label) ->
                        OutlinedButton(onClick = { filterType = type }, enabled = filterType != type) { Text(label) }
                    }
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
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("Ara") },
                    placeholder = { Text("Müşteri, iş başlığı, fiş no veya not") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        viewModel.loadServiceQualityPhotos(
                            type = filterType,
                            ticketId = null,
                            startDate = startDateInput.ifBlank { null },
                            endDate = endDateInput.ifBlank { null },
                            query = searchInput.ifBlank { null },
                            limit = 500
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Filtreyi Uygula") }
            }

            if (uiState.serviceQualityPhotos.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "Görsel bulunamadı",
                        subtitle = "Seçilen filtreye uygun servis görseli yok.",
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
                                contentDescription = "Servis görseli",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clickable { selectedPhoto = photo }
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = servicePhotoCategoryLabel(photo.type),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${photo.customerName ?: "Müşteri"} · Fiş #${photo.ticketId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = photo.ticketDescription ?: "Servis iş emri",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            photo.note?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Text(text = photo.serviceDate ?: photo.uploadedAt.orEmpty(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun servicePhotoCategoryLabel(type: String): String = when (type) {
    "BEFORE" -> "İşlem Öncesi"
    "AFTER" -> "İşlem Sonrası"
    "INDOOR_UNIT_SERIAL" -> "İç Ünite Seri No"
    "OUTDOOR_UNIT_SERIAL" -> "Dış Ünite Seri No"
    "DEVICE_LABEL" -> "Cihaz Etiketi"
    "FAULT_DETAIL" -> "Arıza Detayı"
    "INSTALLATION" -> "Montaj / Tesisat"
    else -> "Diğer"
}

private fun downloadServicePhoto(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}

private fun toAbsoluteUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        BuildConfig.API_BASE_URL.removeSuffix("/") + url
    }
}
