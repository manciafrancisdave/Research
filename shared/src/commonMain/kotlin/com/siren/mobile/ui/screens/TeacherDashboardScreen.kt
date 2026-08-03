package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.OfflineBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionHeader
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.components.SkeletonList
import com.siren.mobile.ui.components.StatTile
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.util.tabular

private enum class RosterFilter(val label: String) {
    ALL("Everyone"),
    NEEDS_HELP("Needs help"),
    NO_REPLY("No reply"),
}

/** Prototype screen 06 — the adviser's live class overview. */
@Composable
fun TeacherDashboardScreen(
    user: UserProfile,
    roster: List<LinkedPerson>,
    activeAlert: AlertRecord?,
    online: Boolean,
    loading: Boolean,
    onOpenLive: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RosterFilter.ALL) }

    val status = SirenTheme.status
    val safe = roster.count { it.status == ResponseStatus.SAFE }
    val help = roster.count { it.status == ResponseStatus.NEEDS_HELP }
    val noReply = roster.count { it.status == ResponseStatus.NO_RESPONSE }

    val visible = roster
        .filter {
            when (filter) {
                RosterFilter.ALL -> true
                RosterFilter.NEEDS_HELP -> it.status == ResponseStatus.NEEDS_HELP
                RosterFilter.NO_REPLY -> it.status == ResponseStatus.NO_RESPONSE
            }
        }
        .filter { it.name.contains(query, ignoreCase = true) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            DashboardHeader(
                initials = user.initials,
                eyebrow = if (user.classId.isBlank()) "Adviser" else "Adviser · ${user.classId}",
                name = user.name.ifBlank { "Teacher" },
                trailing = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Alert history")
                    }
                },
            )
        }

        if (!online) item { OfflineBanner() }

        // A live event outranks everything else on this screen.
        if (activeAlert != null && !activeAlert.closed) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Layout.cardLarge))
                        .background(status.hero)
                        .padding(Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Live safety check",
                            style = MaterialTheme.typography.labelMedium,
                            color = status.onHeroMuted,
                        )
                        Pill("LIVE", Color.White, status.dangerFill)
                    }
                    Text(
                        "${roster.size - noReply} of ${roster.size} responded",
                        style = MaterialTheme.typography.headlineSmall.tabular(),
                        color = status.onHero,
                    )
                    Text(
                        "Event ${activeAlert.magnitudeG.asG()} · started ${DateFmt.clock(activeAlert.detectedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = status.onHeroMuted,
                    )
                    com.siren.mobile.ui.components.PrimaryButton(
                        text = "Open roll call",
                        onClick = onOpenLive,
                        tone = com.siren.mobile.ui.components.ButtonTone.OnColor,
                        onColorContent = status.hero,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                StatTile("${roster.size}", "Total", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("$safe", "Safe", status.safe, Modifier.weight(1f))
                StatTile("$help", "Needs help", status.danger, Modifier.weight(1f))
                StatTile("$noReply", "No reply", status.warn, Modifier.weight(1f))
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
                RosterFilter.entries.forEach { f ->
                    val count = when (f) {
                        RosterFilter.ALL -> roster.size
                        RosterFilter.NEEDS_HELP -> help
                        RosterFilter.NO_REPLY -> noReply
                    }
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} · $count") },
                        shape = RoundedCornerShape(Layout.pill),
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }

        item { SectionHeader(title = "Class roster") }

        when {
            loading -> item { SkeletonList(rows = 5) }

            roster.isEmpty() -> item {
                EmptyState(
                    title = "No students yet",
                    subtitle = "Students appear here once their account has the same classId as yours. Set it in the Firebase console.",
                    icon = Icons.Filled.Groups,
                )
            }

            visible.isEmpty() -> item {
                EmptyState(
                    title = "Nobody matches",
                    subtitle = "Try a different filter or clear the search.",
                    icon = Icons.Filled.Search,
                )
            }

            else -> item {
                ListGroup {
                    visible.forEachIndexed { i, person ->
                        RosterRow(person)
                        if (i < visible.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}
