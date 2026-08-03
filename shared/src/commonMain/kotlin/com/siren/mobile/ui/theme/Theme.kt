package com.siren.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Status colours are not part of Material's scheme, so they ride along in their own
 * CompositionLocal. Screens read `SirenTheme.status.danger` instead of importing raw
 * palette values — that is what makes dark mode actually work.
 */
@Immutable
data class StatusColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    /** Saturated variants for icons/bars, where contrast rules are looser than text. */
    val safeFill: Color,
    val warnFill: Color,
    val dangerFill: Color,
)

private val LightStatus = StatusColors(
    safe = SafeText, safeContainer = SafeTint, onSafeContainer = Color(0xFF06351A),
    warn = WarnText, warnContainer = WarnTint, onWarnContainer = Color(0xFF432B04),
    danger = DangerText, dangerContainer = DangerTint, onDangerContainer = Color(0xFF4C0F0F),
    safeFill = SafeFill, warnFill = WarnFill, dangerFill = DangerFill,
)

private val DarkStatus = StatusColors(
    safe = SafeOnDark, safeContainer = Color(0xFF10331F), onSafeContainer = Color(0xFFB7F7CE),
    warn = WarnOnDark, warnContainer = Color(0xFF3A2B0A), onWarnContainer = Color(0xFFFCE7B0),
    danger = DangerOnDark, dangerContainer = Color(0xFF3B1618), onDangerContainer = Color(0xFFFFD2D2),
    safeFill = SafeOnDark, warnFill = WarnOnDark, dangerFill = DangerOnDark,
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object SirenTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}

private val LightColors = lightColorScheme(
    primary = SirenBlue,
    onPrimary = Color.White,
    primaryContainer = SurfaceTint,
    onPrimaryContainer = SirenBlueDeep,
    secondary = SirenBlueDark,
    onSecondary = Color.White,
    secondaryContainer = SurfaceTintAlt,
    onSecondaryContainer = SirenBlueDeep,
    background = AppBg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = InkSubtle,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE1E6EF),
    error = DangerText,
    onError = Color.White,
    errorContainer = DangerTint,
    onErrorContainer = Color(0xFF4C0F0F),
    outline = BorderStrong,
    outlineVariant = Border,
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = SirenBlueOnDark,
    onPrimary = Color(0xFF06152F),
    primaryContainer = Color(0xFF1B3468),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = SirenBlueLight,
    onSecondary = Color(0xFF06152F),
    secondaryContainer = Color(0xFF1B3468),
    onSecondaryContainer = Color(0xFFD6E4FF),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkInkSubtle,
    surfaceContainerLowest = DarkSurfaceLow,
    surfaceContainerLow = Color(0xFF121A29),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF2A3750),
    error = DangerOnDark,
    onError = Color(0xFF2B0708),
    errorContainer = Color(0xFF3B1618),
    onErrorContainer = Color(0xFFFFD2D2),
    outline = Color(0xFF3B4864),
    outlineVariant = DarkBorder,
    scrim = Color(0xCC000000),
)

/** One radius scale. Screens must not invent their own corner values. */
private val SirenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corner radii and fixed sizes, on a 4dp grid. */
object Layout {
    val card = 16.dp
    val cardLarge = 22.dp
    val field = 14.dp
    val pill = 999.dp
    val tile = 12.dp
    val fieldHeight = 56.dp
    val buttonHeight = 52.dp
    val screenPadding = 20.dp
    val rowMinHeight = 56.dp
    /** Android/iOS minimum accessible touch target. */
    val touchTarget = 48.dp
    val hairline = 1.dp
}

/** 4dp base grid. Every gap in the app comes from here. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
}

@Composable
fun SirenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = sirenTypography(),
            shapes = SirenShapes,
            content = content,
        )
    }
}
