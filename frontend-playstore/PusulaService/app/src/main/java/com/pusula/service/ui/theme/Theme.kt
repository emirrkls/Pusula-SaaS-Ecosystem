package com.pusula.service.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CyanPrimary,
    secondary = BlueSecondary,
    tertiary = AccentPurple,
    surface = SurfaceDark,
    error = ErrorTone
)

@Composable
fun PusulaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
