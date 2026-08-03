package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SafeTint
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint

/** Prototype screen 05. */
@Composable
fun StudentDashboardScreen(
    user: UserProfile,
    alerts: List<AlertRecord>,
    myResponses: Map<String, SafetyResponse>,
    online: Boolean,
    onOpenHistory: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenDemo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAlert: (AlertRecord) -> Unit,
) {
    val latest = alerts.firstOrNull()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.initials)
                Column(Modifier.weight(1f)) {
                    Text("Good day,", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    Text(
                        user.name.ifBlank { "Student" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(Icons.Filled.Notifications, null, tint = Ink)
            }
        }

        item {
            SystemStatusCard(latest = latest, online = online)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                QuickTile(Icons.Filled.History, "Alert History", "${alerts.size} recorded events", onOpenHistory)
                QuickTile(Icons.Filled.MenuBook, "Safety Guide", "Drop, cover, hold and 27 more", onOpenGuide)
                QuickTile(Icons.Filled.ContactEmergency, "Emergency Contacts", "SMS fallback when offline", onOpenContacts)
                QuickTile(Icons.Filled.Science, "Demo Mode", "Simulate alert levels", onOpenDemo, badge = "DEV")
                QuickTile(Icons.Filled.Settings, "Settings", "Alerts, account, privacy", onOpenSettings)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Recent alerts")
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = SirenBlue,
                    modifier = Modifier.clickable { onOpenHistory() },
                )
            }
        }

        if (alerts.isEmpty()) {
            item {
                Text(
                    "No seismic events recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        } else {
            items(alerts.take(5), key = { it.id }) { alert ->
                RecentAlertRow(
                    alert = alert,
                    response = myResponses[alert.id],
                    onClick = { onOpenAlert(alert) },
                )
            }
        }
    }
}

@Composable
private fun SystemStatusCard(latest: AlertRecord?, online: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.cardLarge))
            .background(SirenGradients.night)
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "System status",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Pill(
                text = if (online) "MONITORING" else "OFFLINE",
                fg = if (online) Safe else Color.White,
                bg = Color.White.copy(alpha = 0.16f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.VerifiedUser,
                null,
                tint = Safe,
                modifier = Modifier.size(38.dp),
            )
            Column {
                Text(
                    "All Safe",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    if (online) "No seismic activity · sensor online"
                    else "Waiting for connection · queued locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        Text(
            latest?.let {
                "Peak ${it.magnitudeG.asG()} · ${it.nodeId ?: it.source.label}"
            } ?: "No readings yet · awaiting first sensor report",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
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
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Layout.tile))
                .background(SurfaceTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SirenBlue, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                badge?.let { Pill(it, SirenBlue, SurfaceTint) }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
    }
}

@Composable
fun RecentAlertRow(
    alert: AlertRecord,
    response: SafetyResponse?,
    onClick: () -> Unit,
) {
    val when_ = remember(alert.detectedAt) { DateFmt.shortDateTime(alert.detectedAt) }
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
                "${alert.intensity.severity} shaking · ${alert.magnitudeG.asG()}",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                buildString {
                    append(when_)
                    response?.let {
                        append(" · you replied ")
                        append(if (it.status == ResponseStatus.SAFE) "Safe" else "Needs help")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        if (alert.source.name == "SIMULATED") {
            Pill("SIM", InkSubtle, Border)
        } else if (response?.status == ResponseStatus.SAFE) {
            Pill("Safe", Safe, SafeTint)
        }
    }
}
