package com.pusula.service.ui.theme

import androidx.compose.ui.graphics.Color

/** Marka renkleri — logo: cyan pusula + navy metin (#555555 ikincil). */
val BrandCyan = Color(0xFF00B6EB)
val BrandNavy = Color(0xFF1C3461)
val BrandGray = Color(0xFF555555)
val BrandGrayLight = Color(0xFF94A3B8)

val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFF5F8FB)
val SurfaceSubtle = Color(0xFFEEF4F8)
val BorderLight = Color(0xFFE2E8F0)

/** Eski adlar — marka paletine yönlendirilir (ekranlar tek tek güncellenmeden tutarlılık). */
val CyanPrimary = BrandCyan
val BlueSecondary = BrandNavy
val AccentCyan = BrandCyan
val AccentPurple = BrandNavy
val AccentOrange = BrandNavy.copy(alpha = 0.72f)

val DarkStart = SurfaceMuted
val DarkEnd = SurfaceWhite
val SurfaceDark = SurfaceWhite

/** Anlamsal tonlar — marka ile uyumlu, sade. */
val Success = Color(0xFF0F766E)
val Warning = Color(0xFFB45309)
val ErrorTone = Color(0xFFB91C1C)
val Info = BrandCyan
