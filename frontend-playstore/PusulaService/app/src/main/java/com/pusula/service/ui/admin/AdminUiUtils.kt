package com.pusula.service.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

fun formatAmount(amount: Double?): String {
    val value = amount ?: 0.0
    val absValue = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absValue >= 1_000_000 -> "${sign}${String.format(Locale.US, "%.1fM", absValue / 1_000_000)}"
        absValue >= 1_000 -> "${sign}${String.format(Locale.US, "%.1fK", absValue / 1_000)}"
        else -> "${sign}${String.format(Locale.US, "%.0f", absValue)}"
    }
}

@Composable
fun LazyLoadingOverlay(isLoading: Boolean) {
    if (!isLoading) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxFontSize: TextUnit = 24.sp,
    minFontSize: TextUnit = 14.sp
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = fontSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && fontSize > minFontSize) {
                fontSize = (fontSize.value - 1).sp
            }
        }
    )
    LaunchedEffect(text) {
        fontSize = maxFontSize
    }
}
