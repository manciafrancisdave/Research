package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface

private enum class HistoryFilter { ALL, SENSOR, SIMULATION }

/** Prototype screen 11 — read-only record, doubling as the study's evaluation data. */
@Composable
fun HistoryScreen(
    alerts: List<AlertRecord>,
    myResponses: Map<String, SafetyResponse>,
    onBack: (() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val visible = alerts.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.SENSOR -> it.source == AlertSource.ESP32
            HistoryFilter.SIMULATION -> it.source == AlertSource.SIMULATED
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(title = "Alert history", onBack = onBack) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilterChip("All events", filter == HistoryFilter.ALL) { filter = HistoryFilter.ALL }
                FilterChip("Sensor", filter == HistoryFilter.SENSOR) { filter = HistoryFilter.SENSOR }
                FilterChip("Simulation", filter == HistoryFilter.SIMULATION) { filter = HistoryFilter.SIMULATION }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = "No events recorded",
                    subtitle = "Alerts from the sensor or Demo Mode will appear here.",
                    icon = Icons.Filled.History,
                )
            }
        } else {
            items(visible, key = { it.id }) { alert ->
                HistoryRow(alert, myResponses[alert.id])
            }

            item {
                InfoBanner(
                    "Read-only record · retained for the study's evaluation period.",
                    Icons.Filled.Lock,
                    fg = InkSubtle,
                    bg = Border,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(alert: AlertRecord, response: SafetyResponse?) {
    val stamp = remember(alert.detectedAt) { DateFmt.dateTime(alert.detectedAt) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(Surface)
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Layout.tile))
                    .background(intensityColor(alert.intensity).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = intensityColor(alert.intensity),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${alert.intensity.severity} · Level ${alert.intensity.level}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(stamp, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    alert.magnitudeG.asG(),
                    style = MaterialTheme.typography.titleMedium,
                    color = intensityColor(alert.intensity),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "peak accel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSubtle,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (response?.status) {
                    ResponseStatus.SAFE -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = Safe, modifier = Modifier.size(16.dp))
                        Text("You replied Safe", style = MaterialTheme.typography.bodySmall, color = Safe)
                    }

                    ResponseStatus.NEEDS_HELP -> {
                        Icon(Icons.Filled.Sos, null, tint = Danger, modifier = Modifier.size(16.dp))
                        Text("You asked for help", style = MaterialTheme.typography.bodySmall, color = Danger)
                    }

                    else -> Text(
                        "No response recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
            }
            Pill(alert.source.label, InkSubtle, Border)
        }
    }
}
