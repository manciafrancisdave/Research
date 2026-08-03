package com.siren.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.DangerTint
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.IntensityGreen
import com.siren.mobile.ui.theme.IntensityRed
import com.siren.mobile.ui.theme.IntensityYellow
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SafeTint
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint
import com.siren.mobile.ui.theme.Warn
import com.siren.mobile.ui.theme.WarnTint

@Composable
fun SirenCard(
    modifier: Modifier = Modifier,
    background: Color = Surface,
    border: Color? = Border,
    corner: androidx.compose.ui.unit.Dp = Layout.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = background),
        border = border?.let { BorderStroke(1.dp, it) },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(Space.l), content = content)
    }
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = InkSubtle,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Pill(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Layout.pill))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(status: ResponseStatus, modifier: Modifier = Modifier) {
    val (fg, bg) = when (status) {
        ResponseStatus.SAFE -> Safe to SafeTint
        ResponseStatus.NEEDS_HELP -> Danger to DangerTint
        ResponseStatus.NO_RESPONSE -> Warn to WarnTint
    }
    val label = when (status) {
        ResponseStatus.SAFE -> "Safe"
        ResponseStatus.NEEDS_HELP -> "Needs help"
        ResponseStatus.NO_RESPONSE -> "No reply"
    }
    Pill(label, fg, bg, modifier)
}

fun intensityColor(intensity: Intensity): Color = when (intensity) {
    Intensity.GREEN -> IntensityGreen
    Intensity.YELLOW -> IntensityYellow
    Intensity.RED -> IntensityRed
}

fun intensityBrush(intensity: Intensity): Brush = when (intensity) {
    Intensity.GREEN -> SirenGradients.safe
    Intensity.YELLOW -> SirenGradients.warn
    Intensity.RED -> SirenGradients.danger
}

@Composable
fun IntensityBadge(intensity: Intensity, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(Layout.pill))
            .background(intensityColor(intensity).copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "${intensity.severity.uppercase()} · LEVEL ${intensity.level}",
            style = MaterialTheme.typography.labelSmall,
            color = intensityColor(intensity),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    background: Color = SurfaceTint,
    foreground: Color = SirenBlue,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Layout.tile))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 3).sp,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    brush: Brush = SirenGradients.brand,
    contentColor: Color = Color.White,
) {
    val fg = if (enabled) contentColor else InkSubtle
    Box(
        modifier
            .fillMaxWidth()
            .height(Layout.fieldHeight)
            .clip(RoundedCornerShape(Layout.field))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Border, Border)))
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Layout.fieldHeight)
            .clip(RoundedCornerShape(Layout.field))
            .background(Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            icon?.let { Icon(it, null, tint = Ink, modifier = Modifier.size(20.dp)) }
            Text(text, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun SirenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Layout.field),
            singleLine = singleLine,
            isError = isError,
            leadingIcon = leadingIcon?.let { { Icon(it, null, tint = InkSubtle) } },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            tint = InkSubtle,
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = SirenBlue,
                unfocusedBorderColor = Border,
            ),
        )
    }
}

@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fg: Color = SirenBlue,
    bg: Color = SurfaceTint,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.field))
            .background(bg)
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(icon, null, tint = Border, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Layout.field))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = Space.m, horizontal = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
    }
}
