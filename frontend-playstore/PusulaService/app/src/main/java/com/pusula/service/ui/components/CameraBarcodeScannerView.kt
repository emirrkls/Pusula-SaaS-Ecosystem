package com.pusula.service.ui.components

import android.Manifest
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Warning
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraBarcodeScannerView(
    modifier: Modifier = Modifier,
    onCodeDetected: (String) -> Unit
) {
    val permission = rememberPermissionState(Manifest.permission.CAMERA)
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanLocked by remember { mutableStateOf(false) }

    if (!permission.status.isGranted) {
        AppEmptyState(
            title = "Kamera izni gerekli",
            subtitle = "Barkod taramak için kamera izni vermeniz gerekiyor.",
            icon = Icons.Outlined.QrCodeScanner,
            tint = Warning
        )
        Button(
            onClick = { permission.launchPermissionRequest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kamera izni ver")
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(Spacing.sm)) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val scanner = BarcodeScanning.getClient()
                    val analysis = ImageAnalysis.Builder().build().also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (!scanLocked) {
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(image).addOnSuccessListener { barcodes ->
                                        val code = barcodes.firstOrNull()?.rawValue
                                        if (!code.isNullOrBlank()) {
                                            scanLocked = true
                                            val vibrator = ContextCompat.getSystemService(
                                                ctx,
                                                Vibrator::class.java
                                            )
                                            vibrator?.vibrate(
                                                VibrationEffect.createOneShot(60, 80)
                                            )
                                            onCodeDetected(code)
                                        }
                                    }.addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    previewView
                }
            )
        }
    }
}
