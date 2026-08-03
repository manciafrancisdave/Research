package com.siren.mobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.ui.components.Haptics
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.intensityBrush
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space

/**
 * Prototype screen 08 — the full-screen alert. Background, badge and haptic pattern all
 * escalate with the measured peak ground acceleration.
 */
@Composable
fun AlertScreen(
    alert: AlertRecord,
    vibrationEnabled: Boolean,
    onConfirmStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "alert")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    LaunchedEffect(alert.id, vibrationEnabled) {
        if (vibrationEnabled) Haptics.forIntensity(alert.intensity)
    }

    val time = remember(alert.detectedAt) { DateFmt.clockSeconds(alert.detectedAt) }
    val date = remember(alert.detectedAt) { DateFmt.date(alert.detectedAt) }

    Column(
        Modifier
            .fillMaxSize()
            .background(intensityBrush(alert.intensity))
            .padding(horizontal = Layout.screenPadding, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        if (vibrationEnabled) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Layout.pill))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = Space.m, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Vibration, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    "ALARM · VIBRATION ON",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulse),
                )
                Text(
                    "EARTHQUAKE DETECTED",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Layout.pill))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = Space.l, vertical = Space.s)
                ) {
                    Text(
                        "${alert.intensity.severity.uppercase()} · LEVEL ${alert.intensity.level}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Peak ground acceleration",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        alert.magnitudeG.asGSpaced(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 46.sp,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AlertFact("Detected at", "$time\n$date")
                    AlertFact("Source", alert.nodeId ?: alert.source.label)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Layout.field))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(
                "Drop · Cover · Hold On, then respond",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        PrimaryButton(
            text = "Confirm Your Status",
            onClick = {
                Haptics.cancel()
                onConfirmStatus()
            },
            icon = Icons.Filled.HowToReg,
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White, Color.White)
            ),
            contentColor = intensityColor(alert.intensity),
        )

        Text(
            "Your adviser is notified the moment you respond.",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Dismiss",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(top = Space.xs)
                .clickable {
                    Haptics.cancel()
                    onDismiss()
                },
        )
    }
}

@Composable
private fun AlertFact(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
