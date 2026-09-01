package com.pusula.service.ui.technician

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pusula.service.BuildConfig
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.AppTopBar
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.ImageSourcePickerDialog
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.util.ImageUploadHelper
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ServicePhotoScreen(
    ticketId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val context = LocalContext.current
    var pendingType by remember { mutableStateOf("BEFORE") }
    var pendingNote by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        val file = cameraFile
        if (success && (file != null || uri != null)) {
            createMultipartFromUri(context, uri, file, "file")?.let { part ->
                viewModel.uploadServicePhoto(ticketId, pendingType, pendingNote, part)
            } ?: viewModel.reportPhotoPrepareError()
        }
        cameraUri = null
        cameraFile = null
    }

    val launchCameraCapture: () -> Unit = {
        val photoFile = File.createTempFile("ticket_capture_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        cameraFile = photoFile
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            createMultipartFromUri(context, uri, sourceFile = null, partName = "file")?.let { part ->
                viewModel.uploadServicePhoto(ticketId, pendingType, pendingNote, part)
            } ?: viewModel.reportPhotoPrepareError()
        }
    }

    LaunchedEffect(ticketId) { viewModel.loadServicePhotos(ticketId) }

    BackHandler(onBack = onBack)

    if (showSourcePicker) {
        ImageSourcePickerDialog(
            onDismiss = { showSourcePicker = false },
            onCamera = {
                showSourcePicker = false
                if (cameraPermission.status.isGranted) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = {
                showSourcePicker = false
                galleryPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    Scaffold(topBar = { AppTopBar(title = "Servis Görselleri", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Kategori: ${servicePhotoTypeLabel(pendingType)}")
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    SERVICE_PHOTO_TYPES.forEach { (type, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            pendingType = type
                            categoryMenuExpanded = false
                        })
                    }
                }
            }
            OutlinedTextField(
                value = pendingNote,
                onValueChange = { if (it.length <= 500) pendingNote = it },
                label = { Text("Görsel notu") },
                placeholder = { Text("Örn. İç ünite 2, seri no 12345") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { showSourcePicker = true },
                modifier = Modifier.fillMaxWidth().readOnlyProtected(session.isReadOnly)
            ) { Text("Kamera veya Galeriden Görsel Ekle") }

            if (uiState.photoUploading || uiState.photosLoading) {
                CircularProgressIndicator()
            }

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.servicePhotos.isEmpty() && !uiState.photosLoading) {
                AppEmptyState(
                    title = "Görsel eklenmemiş",
                    subtitle = "Cihaz etiketi, seri numarası, arıza detayı veya işlem görseli ekleyebilirsiniz.",
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
                                        text = servicePhotoTypeLabel(photo.type),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = photo.note?.takeIf { it.isNotBlank() } ?: photo.uploadedByName.orEmpty(),
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

private val SERVICE_PHOTO_TYPES = listOf(
    "BEFORE" to "İşlem Öncesi", "AFTER" to "İşlem Sonrası",
    "INDOOR_UNIT_SERIAL" to "İç Ünite Seri No", "OUTDOOR_UNIT_SERIAL" to "Dış Ünite Seri No",
    "DEVICE_LABEL" to "Cihaz Etiketi", "FAULT_DETAIL" to "Arıza Detayı",
    "INSTALLATION" to "Montaj / Tesisat", "OTHER" to "Diğer"
)

private fun servicePhotoTypeLabel(type: String): String =
    SERVICE_PHOTO_TYPES.firstOrNull { it.first == type }?.second ?: "Diğer"

private fun fullPhotoUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        BuildConfig.API_BASE_URL.removeSuffix("/") + url
    }
}

private fun createMultipartFromUri(
    context: Context,
    uri: Uri?,
    sourceFile: File?,
    partName: String
): MultipartBody.Part? {
    val prepared = when {
        sourceFile != null && sourceFile.exists() && sourceFile.length() > 0L ->
            ImageUploadHelper.prepareForUpload(context, Uri.fromFile(sourceFile), sourceFile)
        uri != null -> ImageUploadHelper.prepareForUpload(context, uri)
        else -> null
    } ?: return null
    val requestBody = prepared.asRequestBody("image/jpeg".toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, "photo.jpg", requestBody)
}
