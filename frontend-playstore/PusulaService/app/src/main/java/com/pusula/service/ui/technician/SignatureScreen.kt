package com.pusula.service.ui.technician

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay

@Composable
fun SignatureScreen(
    ticketId: Long,
    onDone: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.sessionManager.state.collectAsState()
    val points = remember { mutableStateListOf<Offset>() }

    Scaffold(topBar = { TopAppBar(title = { Text("İmza Al") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            AppHeroCard(
                eyebrow = "Servis fişi #$ticketId",
                title = "Müşteri imzası",
                subtitle = "Aşağıdaki alanı kullanarak imzanızı atın.",
                badge = if (points.isEmpty()) "Boş" else "${points.size} nokta"
            )

            AppDashboardSection(title = "İmza Alanı") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(Spacing.sm)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ComposeColor.White)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { points.add(it) },
                                        onDrag = { change, _ -> points.add(change.position) }
                                    )
                                }
                        ) {
                            for (i in 1 until points.size) {
                                drawLine(
                                    brush = SolidColor(ComposeColor.Black),
                                    start = points[i - 1],
                                    end = points[i],
                                    strokeWidth = 6f
                                )
                            }
                            drawLine(
                                brush = SolidColor(ComposeColor.Black.copy(alpha = 0.4f)),
                                start = Offset(20f, size.height - 28f),
                                end = Offset(size.width - 20f, size.height - 28f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { points.clear() },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Temizle")
                }
                Button(
                    onClick = {
                        val encoded = pointsToBase64(points)
                        viewModel.uploadSignature(ticketId, encoded)
                    },
                    modifier = Modifier.weight(1f).readOnlyProtected(session.isReadOnly)
                ) {
                    Icon(imageVector = Icons.Outlined.Done, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Kaydet")
                }
            }

            if (uiState.signatureSaved) {
                AppEmptyState(
                    title = "İmza kaydedildi",
                    subtitle = "Pencere otomatik kapanacak...",
                    icon = Icons.Outlined.Done,
                    tint = Success
                )
                LaunchedEffect(uiState.signatureSaved) {
                    delay(1500)
                    viewModel.consumeSignatureSaved()
                    onDone()
                }
            } else if (points.isEmpty()) {
                AppEmptyState(
                    title = "Henüz imza yok",
                    subtitle = "Yukarıdaki alana imza atınca buradaki bilgi güncellenir.",
                    icon = Icons.Outlined.Edit,
                    tint = AccentPurple
                )
            }
        }
    }
}

private fun pointsToBase64(points: List<Offset>): String {
    val bitmap = Bitmap.createBitmap(900, 360, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    for (i in 1 until points.size) {
        canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
