package com.siren.mobile.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette lifted from the prototype so the build matches the approved screens.
val SirenBlue = Color(0xFF2563EB)
val SirenBlueDark = Color(0xFF1D4ED8)
val SirenBlueLight = Color(0xFF3B82F6)
val SirenBlueDeep = Color(0xFF1E3A8A)

val Navy = Color(0xFF0B1220)
val NavySoft = Color(0xFF16223A)

val AppBg = Color(0xFFF4F6FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceTint = Color(0xFFEFF6FF)
val SurfaceTintAlt = Color(0xFFDBEAFE)

val Ink = Color(0xFF0F172A)
val InkMuted = Color(0xFF475569)
val InkSubtle = Color(0xFF64748B)
val InkFaint = Color(0xFF94A3B8)
val Border = Color(0xFFE2E8F0)
val BorderStrong = Color(0xFFCBD5E1)

// Intensity + status colours
val Safe = Color(0xFF16A34A)
val SafeTint = Color(0xFFDCFCE7)
val Warn = Color(0xFFF59E0B)
val WarnTint = Color(0xFFFEF3C7)
val Danger = Color(0xFFDC2626)
val DangerTint = Color(0xFFFEE2E2)

val IntensityGreen = Color(0xFF22C55E)
val IntensityYellow = Color(0xFFF59E0B)
val IntensityRed = Color(0xFFEF4444)

object SirenGradients {
    val brand = Brush.linearGradient(listOf(SirenBlueLight, SirenBlueDark))
    val splash = Brush.linearGradient(listOf(SirenBlueLight, SirenBlue, SirenBlueDeep))
    val night = Brush.linearGradient(listOf(NavySoft, Navy))
    val danger = Brush.linearGradient(listOf(Color(0xFFF87171), Color(0xFFB91C1C)))
    val warn = Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))
    val safe = Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF15803D)))
}
