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
    if (isReadOnly) {
        this
            .graphicsLayer { alpha = 0.6f }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    } else {
        this
    }
}

fun Modifier.featureGated(
    sessionManager: SessionManager,
    featureKey: String
): Modifier = composed {
    val state by sessionManager.state.collectAsState()
    val enabled = state.features[featureKey] ?: false
    if (enabled) {
        this
    } else {
        this
            .graphicsLayer { alpha = 0.45f }
            .pointerInput(featureKey) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    }
}
