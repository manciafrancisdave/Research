package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.StatTile
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.DangerTint
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prototype screen 10 — the live roll-call for an open event. */
@Composable
fun LiveSafetyDashboardScreen(
    alert: AlertRecord,
    roster: List<LinkedPerson>,
    onCloseEvent: () -> Unit,
    onBack: () -> Unit,
) {
    val responded = roster.count { it.status != ResponseStatus.NO_RESPONSE }
    val safe = roster.count { it.status == ResponseStatus.SAFE }
    val help = roster.count { it.status == ResponseStatus.NEEDS_HELP }
    val noReply = roster.count { it.status == ResponseStatus.NO_RESPONSE }
    val pct = if (roster.isEmpty()) 0 else (responded * 100) / roster.size

    val started = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(alert.detectedAt))

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            ScreenHeader(
                title = "Live safety check",
                onBack = onBack,
                trailing = {
                    Pill(
                        if (alert.closed) "CLOSED" else "LIVE",
                        if (alert.closed) InkSubtle else Danger,
                        DangerTint,
                    )
                },
            )
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.cardLarge))
                    .background(SirenGradients.night)
                    .padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    String.format(Locale.US, "Event %.2fg · started %s", alert.magnitudeG, started),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        "$responded",
                        style = MaterialTheme.typography.displaySmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "of ${roster.size} responded · $pct%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                LinearProgressIndicator(
                    progress = { if (roster.isEmpty()) 0f else responded.toFloat() / roster.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Layout.pill)),
                    color = Safe,
                    trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                StatTile("$safe", "Safe", Safe, Modifier.weight(1f))
                StatTile("$help", "Needs help", Danger, Modifier.weight(1f))
                StatTile("$noReply", "No response", Warn, Modifier.weight(1f))
            }
        }

        item { SectionLabel("Sorted by urgency") }

        if (roster.isEmpty()) {
            item {
                Text(
                    "No students in this roster yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        } else {
            items(roster, key = { it.uid }) { person -> RosterRow(person) }
        }

        if (!alert.closed) {
            item {
                PrimaryButton(
                    text = "Close this event",
                    onClick = onCloseEvent,
                    brush = SirenGradients.brand,
                )
            }
            item {
                Text(
                    "Closing the event locks every response and marks the roll-call complete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSubtle,
                )
            }
        }
    }
}
