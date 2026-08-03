package com.siren.mobile.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.Intensity
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.Warn
import com.siren.mobile.ui.theme.WarnTint

/**
 * Prototype screen 12. Exists so the app can be demonstrated and evaluated without the
 * Arduino/ESP32 rig attached — the hardware and app tracks did not finish together.
 */
@Composable
fun DemoScreen(
    alerts: List<AlertRecord>,
    onTrigger: (Intensity) -> Unit,
    onBack: () -> Unit,
) {
    val log = alerts.filter { it.source == AlertSource.SIMULATED }.take(10)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(
            title = "Demo mode",
            onBack = onBack,
            trailing = { Pill("DEV ONLY", Warn, WarnTint) },
        )

        InfoBanner(
            "Simulated events are tagged in history and never dispatch SMS to guardians.",
            Icons.Filled.Warning,
            tone = BannerTone.Warn,
        )

        SectionLabel("Trigger a simulated event")

        TriggerRow(Intensity.GREEN, "0.22 g · minor") { onTrigger(Intensity.GREEN) }
        TriggerRow(Intensity.YELLOW, "0.48 g · moderate") { onTrigger(Intensity.YELLOW) }
        TriggerRow(Intensity.RED, "0.74 g · severe") { onTrigger(Intensity.RED) }

        SectionLabel("Simulation log")

        if (log.isEmpty()) {
            Text(
                "No simulations run yet.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        } else {
            log.forEach { entry ->
                val stamp = "${DateFmt.date(entry.detectedAt)} · ${DateFmt.clockSeconds(entry.detectedAt)}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Layout.field))
                        .background(Surface)
                        .padding(Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(Layout.pill))
                            .background(intensityColor(entry.intensity))
                    )
                    Text(stamp, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    Text(
                        "${entry.intensity.severity} ${entry.magnitudeG.asG()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(Modifier.padding(bottom = Space.xxl))
    }
}

@Composable
private fun TriggerRow(intensity: Intensity, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(Surface)
            .clickable { onClick() }
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayCircle,
            null,
            tint = intensityColor(intensity),
            modifier = Modifier.size(34.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Trigger ${intensity.label} Alert",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
        Pill("L${intensity.level}", intensityColor(intensity), Border)
    }
}
