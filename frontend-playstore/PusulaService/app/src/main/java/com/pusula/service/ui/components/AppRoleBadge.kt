package com.pusula.service.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@Composable
fun AppRoleBadge(role: String) {
    val roleKey = role.uppercase()
    val tone = when (roleKey) {
        "SUPER_ADMIN" -> Warning
        "COMPANY_ADMIN" -> Info
        "TECHNICIAN" -> Success
        else -> ErrorTone
    }
    Text(
        text = roleKey,
        style = MaterialTheme.typography.labelSmall,
        color = tone,
        modifier = Modifier
            .background(tone.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    )
}
