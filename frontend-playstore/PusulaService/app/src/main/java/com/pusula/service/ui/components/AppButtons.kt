package com.pusula.service.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.ErrorTone

private val ButtonShape = RoundedCornerShape(14.dp)

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandCyan,
            contentColor = Color.White,
            disabledContainerColor = BrandCyan.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandNavy,
            contentColor = Color.White,
            disabledContainerColor = BrandNavy.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ErrorTone,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        Text(text)
    }
}
