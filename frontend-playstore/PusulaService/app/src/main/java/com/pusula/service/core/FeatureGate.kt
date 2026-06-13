package com.pusula.service.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun FeatureGated(
    sessionManager: SessionManager,
    featureKey: String,
    showUpgradeHint: Boolean = false,
    content: @Composable () -> Unit
) {
    val state by sessionManager.state.collectAsState()
    val enabled = state.features[featureKey] ?: false

    when {
        enabled -> content()
        showUpgradeHint -> {
            Box {
                content()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Paketinizi yükseltin", color = Color.White)
                }
            }
        }
    }
}

fun Modifier.readOnlyProtected(isReadOnly: Boolean): Modifier = composed {
    if (!isReadOnly) return@composed this
    graphicsLayer { alpha = 0.6f }
        .pointerInput(Unit) {
            detectTapGestures { /* taps absorbed; avoids infinite awaitPointerEvent loops */ }
        }
}

fun featureLabelTr(featureKey: String): String = when (featureKey) {
    "FINANCE_MODULE" -> "Finans modülü"
    "BASIC_INVENTORY" -> "Stok / barkod"
    "PROPOSAL_MODULE" -> "Teklif modülü"
    "PDF_EXPORT" -> "PDF dışa aktarma"
    "MULTI_TECHNICIAN" -> "Çoklu teknisyen"
    "VEHICLE_TRACKING" -> "Araç takibi"
    else -> featureKey
}

fun Modifier.featureGated(
    sessionManager: SessionManager,
    featureKey: String,
    onLockedTap: () -> Unit = {}
): Modifier = composed {
    val state by sessionManager.state.collectAsState()
    val enabled = state.features[featureKey] ?: false
    if (enabled) {
        this
    } else {
        this
            .graphicsLayer { alpha = 0.45f }
            .pointerInput(sessionManager, featureKey) {
                detectTapGestures(
                    onTap = {
                        val snapshot = sessionManager.state.value.features[featureKey] ?: false
                        if (!snapshot) onLockedTap()
                    }
                )
            }
    }
}
