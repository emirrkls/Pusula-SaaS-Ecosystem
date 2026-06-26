package com.pusula.service.ui.technician

import android.Manifest
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
    var showSourcePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        val file = cameraFile
        if (success && (file != null || uri != null)) {
            createMultipartFromUri(context, uri, file, "file")?.let { part ->
                viewModel.uploadServicePhoto(ticketId, pendingType, part)
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

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            createMultipartFromUri(context, uri, sourceFile = null, partName = "file")?.let { part ->
                viewModel.uploadServicePhoto(ticketId, pendingType, part)
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
            onGallery = { galleryPicker.launch("image/*") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = {
                        pendingType = "BEFORE"
                        showSourcePicker = true
                    },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) { Text("Öncesi Ekle") }
                Button(
                    onClick = {
                        pendingType = "AFTER"
                        showSourcePicker = true
                    },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) { Text("Sonrası Ekle") }
            }

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
