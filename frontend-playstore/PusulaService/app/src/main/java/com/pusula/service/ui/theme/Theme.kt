package com.pusula.service.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandCyan,
    onPrimary = Color.White,
    primaryContainer = BrandCyan.copy(alpha = 0.12f),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = BrandNavy.copy(alpha = 0.08f),
    onSecondaryContainer = BrandNavy,
    tertiary = BrandGray,
    onTertiary = Color.White,
    background = SurfaceMuted,
    onBackground = BrandNavy,
    surface = SurfaceWhite,
    onSurface = BrandNavy,
    onSurfaceVariant = BrandGray,
    surfaceVariant = SurfaceSubtle,
    outline = BorderLight,
    outlineVariant = BorderLight.copy(alpha = 0.65f),
    error = ErrorTone,
    onError = Color.White,
    errorContainer = ErrorTone.copy(alpha = 0.08f),
    onErrorContainer = ErrorTone
)

@Composable
fun PusulaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
