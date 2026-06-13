package com.pusula.service.ui.technician

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pusula.service.BuildConfig
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@Composable
fun ServicePhotoScreen(
    ticketId: Long,
    onDone: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val context = LocalContext.current
    var pendingType by remember { mutableStateOf("BEFORE") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            createMultipartFromUri(context, uri, "file")?.let { part ->
                viewModel.uploadServicePhoto(ticketId, pendingType, part)
            }
        }
    }

    LaunchedEffect(ticketId) { viewModel.loadServicePhotos(ticketId) }

    Scaffold(topBar = { TopAppBar(title = { Text("Servis Görselleri") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = {
                        pendingType = "BEFORE"
                        picker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) { Text("Öncesi Ekle") }
                Button(
                    onClick = {
                        pendingType = "AFTER"
                        picker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) { Text("Sonrası Ekle") }
            }

            if (uiState.photoUploading || uiState.photosLoading) {
                CircularProgressIndicator()
            }

            if (uiState.servicePhotos.isEmpty() && !uiState.photosLoading) {
                AppEmptyState(
                    title = "Görsel eklenmemiş",
                    subtitle = "Kalite takibi için önce/sonra fotoğrafı ekleyebilirsiniz.",
                    icon = Icons.Outlined.Image,
                    tint = AccentPurple
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(uiState.servicePhotos, key = { it.id }) { photo ->
                        AppGhostCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                AsyncImage(
                                    model = fullPhotoUrl(photo.url),
                                    contentDescription = "Servis görseli",
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (photo.type == "BEFORE") "Öncesi" else "Sonrası",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = photo.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.deleteServicePhoto(ticketId, photo.id) },
                                    modifier = Modifier.readOnlyProtected(session.isReadOnly)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("Sil")
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Geri Dön")
            }
        }
    }
}

private fun fullPhotoUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        BuildConfig.API_BASE_URL.removeSuffix("/") + url
    }
}

private fun createMultipartFromUri(context: Context, uri: Uri, partName: String): MultipartBody.Part? {
    return runCatching {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("ticket_photo_", ".jpg", context.cacheDir)
        tempFile.outputStream().use { output -> inputStream.copyTo(output) }
        val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
        MultipartBody.Part.createFormData(partName, tempFile.name, requestBody)
    }.getOrNull()
}
