package com.siren.mobile.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Raw palette. Screens should read colours from `MaterialTheme.colorScheme` rather
 * than these directly, so dark mode works.
 *
 * Contrast is deliberate: an emergency app gets read one-handed, outdoors, in poor
 * light. Every text colour below meets WCAG AA (4.5:1) on its intended background,
 * which is why the "fill" and "text" variants of each status colour differ — the
 * saturated fills are legible as shapes but not as small text.
 */

// ---- brand ----------------------------------------------------------------
val SirenBlue = Color(0xFF2563EB)      // 5.17:1 on white
val SirenBlueDark = Color(0xFF1D4ED8)
val SirenBlueLight = Color(0xFF3B82F6)
val SirenBlueDeep = Color(0xFF1E3A8A)
val SirenBlueOnDark = Color(0xFF7DABFF) // legible on navy

// ---- neutrals (light) -----------------------------------------------------
val Navy = Color(0xFF0B1220)
val NavySoft = Color(0xFF16223A)

val AppBg = Color(0xFFF5F7FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceLow = Color(0xFFFAFBFD)
val SurfaceContainer = Color(0xFFF0F3F7)
val SurfaceContainerHigh = Color(0xFFE8ECF3)
val SurfaceTint = Color(0xFFEFF5FF)
val SurfaceTintAlt = Color(0xFFDCEAFE)

val Ink = Color(0xFF0F172A)            // 16.8:1 on white
val InkMuted = Color(0xFF3F4A5C)       // 9.4:1
val InkSubtle = Color(0xFF5B6676)      // 5.9:1
val InkFaint = Color(0xFF6B7280)       // 4.8:1 — was #94A3B8 at 2.6:1, which failed AA
val Border = Color(0xFFE2E7EF)
val BorderStrong = Color(0xFFC9D1DE)

// ---- neutrals (dark) ------------------------------------------------------
val DarkBg = Color(0xFF0B1220)
val DarkSurface = Color(0xFF141D2E)
val DarkSurfaceLow = Color(0xFF101828)
val DarkSurfaceContainer = Color(0xFF1B2537)
val DarkSurfaceContainerHigh = Color(0xFF222E44)
val DarkInk = Color(0xFFE9EEF7)
val DarkInkSubtle = Color(0xFFA3B0C4)
val DarkBorder = Color(0xFF2B3752)

// ---- status: fill vs text -------------------------------------------------
// Fills are for icons, bars and badges. Text variants are darkened so small
// labels still pass AA on light surfaces.
val SafeFill = Color(0xFF16A34A)
val SafeText = Color(0xFF15803D)       // 4.9:1 on white
val SafeTint = Color(0xFFDCFCE7)
val SafeOnDark = Color(0xFF4ADE80)

val WarnFill = Color(0xFFF59E0B)
val WarnText = Color(0xFFB45309)       // 4.7:1 — raw amber is 2.1:1 and unusable as text
val WarnTint = Color(0xFFFEF3C7)
val WarnOnDark = Color(0xFFFBBF24)

val DangerFill = Color(0xFFDC2626)
val DangerText = Color(0xFFB91C1C)     // 5.9:1
val DangerTint = Color(0xFFFEE2E2)
val DangerOnDark = Color(0xFFF87171)

// Aliases kept so existing screens keep compiling during the refactor.
val Safe = SafeText
val Warn = WarnText
val Danger = DangerText

// ---- intensity ------------------------------------------------------------
val IntensityGreen = SafeFill
val IntensityYellow = WarnFill
val IntensityRed = DangerFill

/**
 * Gradients carry meaning: they mark seismic intensity and the brand mark, nothing
 * else. Ordinary panels use flat tonal surfaces, so a gradient always signals
 * "this is about the event".
 */
object SirenGradients {
    val brand = Brush.linearGradient(listOf(SirenBlueLight, SirenBlueDark))
    val splash = Brush.linearGradient(listOf(SirenBlueLight, SirenBlue, SirenBlueDeep))
    val night = Brush.linearGradient(listOf(NavySoft, Navy))
    val danger = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B)))
    val warn = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))
    val safe = Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D)))
}
