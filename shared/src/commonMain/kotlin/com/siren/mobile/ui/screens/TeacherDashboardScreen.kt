package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
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
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.components.StatTile
import com.siren.mobile.ui.components.StatusChip
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint
import com.siren.mobile.ui.theme.Warn

/** Prototype screen 06 — the adviser's live class overview. */
@Composable
fun TeacherDashboardScreen(
    user: UserProfile,
    roster: List<LinkedPerson>,
    activeAlert: AlertRecord?,
    onOpenLive: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RosterFilter.ALL) }

    val safe = roster.count { it.status == ResponseStatus.SAFE }
    val help = roster.count { it.status == ResponseStatus.NEEDS_HELP }
    val noReply = roster.count { it.status == ResponseStatus.NO_RESPONSE }

    val visible = roster
        .filter { p ->
            when (filter) {
                RosterFilter.ALL -> true
                RosterFilter.NEEDS_HELP -> p.status == ResponseStatus.NEEDS_HELP
                RosterFilter.NO_REPLY -> p.status == ResponseStatus.NO_RESPONSE
            }
        }
        .filter { it.name.contains(query, ignoreCase = true) }

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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.initials)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (user.classId.isBlank()) "Adviser" else "Adviser · ${user.classId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                    Text(
                        user.name.ifBlank { "Teacher" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(Icons.Filled.Notifications, null, tint = Ink)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                StatTile("${roster.size}", "Total", SirenBlue, Modifier.weight(1f))
                StatTile("$safe", "Safe", Safe, Modifier.weight(1f))
                StatTile("$help", "Needs help", Danger, Modifier.weight(1f))
                StatTile("$noReply", "No reply", Warn, Modifier.weight(1f))
            }
        }

        if (activeAlert != null && !activeAlert.closed) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Layout.card))
                        .background(SurfaceTint)
                        .clickable { onOpenLive() }
                        .padding(Space.m),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Live safety check", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(
                            "Event ${activeAlert.magnitudeG.asG()} in progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                        )
                    }
                    Pill("OPEN", Danger, Border)
                }
            }
        }

        item {
            SirenField(
                value = query,
                onValueChange = { query = it },
                label = "Search students",
                leadingIcon = Icons.Filled.Search,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilterChip("All · ${roster.size}", filter == RosterFilter.ALL) { filter = RosterFilter.ALL }
                FilterChip("Needs help · $help", filter == RosterFilter.NEEDS_HELP) { filter = RosterFilter.NEEDS_HELP }
                FilterChip("No reply · $noReply", filter == RosterFilter.NO_REPLY) { filter = RosterFilter.NO_REPLY }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Live student status")
                Text(
                    "History",
                    style = MaterialTheme.typography.labelMedium,
                    color = SirenBlue,
                    modifier = Modifier.clickable { onOpenHistory() },
                )
            }
        }

        if (visible.isEmpty()) {
            item {
                Text(
                    if (roster.isEmpty())
                        "No students are assigned to your class yet. Set classId on student accounts to populate this roster."
                    else "No students match this filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        } else {
            items(visible, key = { it.uid }) { person -> RosterRow(person) }
        }
    }
}

enum class RosterFilter { ALL, NEEDS_HELP, NO_REPLY }

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Layout.pill))
            .background(if (selected) SirenBlue else Surface)
            .clickable { onClick() }
            .padding(horizontal = Space.m, vertical = Space.s),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else InkSubtle,
        )
    }
}

@Composable
fun RosterRow(person: LinkedPerson) {
    val time = person.respondedAt?.let {
        remember(it) { DateFmt.clock(it) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(Surface)
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person.initials, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(person.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                listOfNotNull(person.klass.ifBlank { null }, time).joinToString(" · ")
                    .ifBlank { "Awaiting response" },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        StatusChip(person.status)
    }
}
