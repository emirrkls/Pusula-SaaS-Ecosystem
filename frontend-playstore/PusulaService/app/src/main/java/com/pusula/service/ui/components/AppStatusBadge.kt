package com.pusula.service.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@Composable
fun AppStatusBadge(text: String, statusKey: String?) {
    val key = statusKey?.trim()?.uppercase().orEmpty()
    val color: Color = when (key) {
        "PENDING", "ASSIGNED" -> Warning
        "IN_PROGRESS" -> Info
        "COMPLETED" -> Success
        "CANCELLED" -> ErrorTone
        else -> MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = com.pusula.service.ui.theme.Spacing.sm, vertical = com.pusula.service.ui.theme.Spacing.xs),
        color = color
    )
}
