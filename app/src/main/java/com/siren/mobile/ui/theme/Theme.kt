package com.siren.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = SirenBlue,
    onPrimary = Color.White,
    primaryContainer = SurfaceTint,
    onPrimaryContainer = SirenBlueDark,
    secondary = SirenBlueLight,
    onSecondary = Color.White,
    background = AppBg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceTint,
    onSurfaceVariant = InkMuted,
    error = Danger,
    onError = Color.White,
    outline = Border,
    outlineVariant = BorderStrong,
)

private val DarkColors = darkColorScheme(
    primary = SirenBlueLight,
    onPrimary = Color.White,
    primaryContainer = SirenBlueDeep,
    onPrimaryContainer = Color.White,
    background = Navy,
    onBackground = Color(0xFFF8FAFC),
    surface = NavySoft,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171),
    onError = Color.White,
    outline = Color(0xFF334155),
)

/** Corner radii and sizes used across the screens, matching the prototype. */
object Layout {
    val card = 20.dp
    val cardLarge = 24.dp
    val field = 16.dp
    val pill = 999.dp
    val tile = 14.dp
    val fieldHeight = 56.dp
    val screenPadding = 20.dp
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
}

@Composable
fun SirenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SirenTypography,
        content = content,
    )
}
