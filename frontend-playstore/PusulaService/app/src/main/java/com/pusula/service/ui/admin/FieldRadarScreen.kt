package com.pusula.service.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldRadarScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraState = rememberCameraPositionState()
    val selectedInfo = remember { mutableStateOf<FieldPinInfo?>(null) }

    val pins = uiState.fieldPins.mapNotNull { pin ->
        val parts = pin.coordinates?.split(",") ?: return@mapNotNull null
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        pin to LatLng(lat, lng)
    }

    val activeCount = pins.count {
        val s = it.first.ticketStatus?.lowercase().orEmpty()
        s == "inprogress" || s == "devam ediyor" || s == "in_progress"
    }
    val completedCount = pins.count {
        val s = it.first.ticketStatus?.lowercase().orEmpty()
        s == "completed" || s == "tamamlandi"
    }
    val pendingCount = pins.size - activeCount - completedCount

    LaunchedEffect(pins.size) {
        if (pins.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply { pins.forEach { include(it.second) } }.build()
        cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Saha Radarı") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pins.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                    verticalArrangement = Arrangement.Center
                ) {
                    AppEmptyState(
                        title = "Saha aktivitesi yok",
                        subtitle = "Aktif teknisyen konumu görüntülenmiyor.",
                        icon = Icons.Outlined.Map,
                        tint = Info
                    )
                }
            } else {
                GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraState) {
                    pins.forEach { (pin, point) ->
                        val hue = when (pin.ticketStatus?.lowercase()) {
                            "completed", "tamamlandi" -> BitmapDescriptorFactory.HUE_GREEN
                            "inprogress", "devam ediyor", "in_progress" -> BitmapDescriptorFactory.HUE_AZURE
                            else -> BitmapDescriptorFactory.HUE_YELLOW
                        }
                        Marker(
                            state = MarkerState(position = point),
                            title = pin.technicianName ?: "Teknisyen",
                            snippet = "${pin.customerName ?: "Müşteri"} • ${pin.ticketStatus ?: "pending"}",
                            onClick = {
                                selectedInfo.value = FieldPinInfo(
                                    technicianName = pin.technicianName ?: "Teknisyen",
                                    customerName = pin.customerName ?: "Müşteri",
                                    status = pin.ticketStatus ?: "pending"
                                )
                                false
                            },
                            icon = BitmapDescriptorFactory.defaultMarker(hue)
                        )
                    }
                }

                // Floating header summary
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(Spacing.lg)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(label = "Aktif", count = activeCount, color = Info)
                        StatusDot(label = "Bekliyor", count = pendingCount, color = Warning)
                        StatusDot(label = "Tamam", count = completedCount, color = Success)
                    }
                }

                selectedInfo.value?.let { info ->
                    AppGhostCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.lg)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            com.pusula.service.ui.components.AppIconBadge(
                                icon = Icons.Outlined.LocationOn,
                                tint = statusColor(info.status)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    info.technicianName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    info.customerName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    statusLabelTr(info.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(info.status),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            LazyLoadingOverlay(isLoading = uiState.loading)
        }
    }
}

@Composable
private fun StatusDot(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "completed", "tamamlandi" -> Success
    "inprogress", "devam ediyor", "in_progress" -> Info
    else -> Warning
}

private fun statusLabelTr(status: String): String = when (status.lowercase()) {
    "completed", "tamamlandi" -> "Tamamlandı"
    "inprogress", "devam ediyor", "in_progress" -> "Devam ediyor"
    "pending", "assigned" -> "Bekliyor"
    else -> status
}

private data class FieldPinInfo(
    val technicianName: String,
    val customerName: String,
    val status: String
)
