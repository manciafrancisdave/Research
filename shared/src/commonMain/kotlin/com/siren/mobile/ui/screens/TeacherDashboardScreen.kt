package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.ButtonTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.OfflineBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.RosterBreakdown
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionHeader
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.components.SkeletonList
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.tabular

private enum class RosterFilter(val label: String) {
    ALL("Everyone"),
    NEEDS_HELP("Needs help"),
    NO_REPLY("No reply"),
}

/**
 * Prototype screen 06 — the adviser's live class overview, the Overview tab.
 *
 * Deliberately holds no roster list, no search and no add-student field: those are the
 * Roster tab ([TeacherRosterScreen]). Both tabs used to render this one composable, so
 * "Overview" and "Roster" were the same page and the bottom bar had two buttons that
 * did nothing to tell apart. This screen answers "is my class all right?" at a glance;
 * the other answers "who is in it?".
 */
@Composable
fun TeacherDashboardScreen(
    user: UserProfile,
    roster: List<LinkedPerson>,
    activeAlert: AlertRecord?,
    online: Boolean,
    loading: Boolean,
    onOpenLive: () -> Unit,
    onOpenRoster: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGuide: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val status = SirenTheme.status
    val safe = roster.count { it.status == ResponseStatus.SAFE }
    val help = roster.count { it.status == ResponseStatus.NEEDS_HELP }
    val noReply = roster.count { it.status == ResponseStatus.NO_RESPONSE }

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
                photo = user.photo,
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
                        "${activeAlert.intensity.levelText} · started ${DateFmt.clock(activeAlert.detectedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = status.onHeroMuted,
                    )
                    PrimaryButton(
                        text = "Open roll call",
                        onClick = onOpenLive,
                        tone = ButtonTone.OnColor,
                        onColorContent = status.hero,
                    )
                }
            }
        }

        // Only meaningful once there is a class to describe.
        when {
            roster.isNotEmpty() ->
                item { RosterBreakdown(safe = safe, needsHelp = help, noReply = noReply) }

            loading -> item { SkeletonList(rows = 3) }
        }

        // The roster is keyed entirely on classId, so an adviser with no class set can
        // never have a student. This is the first thing to fix, ahead of adding anyone.
        if (user.classId.isBlank()) {
            item {
                InfoBanner(
                    "Set the class you advise before adding students — the roll call is grouped by class.",
                    Icons.Filled.Warning,
                    tone = BannerTone.Warn,
                )
            }
            item {
                PrimaryButton(
                    text = "Set your class",
                    onClick = onEditProfile,
                    icon = Icons.Filled.School,
                )
            }
        } else if (roster.isEmpty() && !loading) {
            item {
                EmptyState(
                    title = "No students yet",
                    subtitle = "Open the Roster tab to add students with the 6-character code from their Settings.",
                    icon = Icons.Filled.Groups,
                )
            }
        }

        item {
            ListGroup {
                ListRow(
                    title = "Class roster",
                    subtitle = when {
                        user.classId.isBlank() -> "No class set yet"
                        roster.size == 1 -> "1 student in ${user.classId}"
                        else -> "${roster.size} students in ${user.classId}"
                    },
                    onClick = onOpenRoster,
                    leading = {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                RowDivider()
                ListRow(
                    title = "Alert history",
                    subtitle = "Past events and who responded",
                    onClick = onOpenHistory,
                    leading = {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                RowDivider()
                ListRow(
                    title = "Safety guide",
                    subtitle = "Drop, cover, hold and 27 more",
                    onClick = onOpenGuide,
                    leading = {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

/**
 * The Roster tab — class membership, and the only place students are added or removed.
 *
 * Split out of [TeacherDashboardScreen] so the two teacher tabs are actually different
 * screens. Everything here is class management; nothing here is a live-event readout.
 */
@Composable
fun TeacherRosterScreen(
    user: UserProfile,
    roster: List<LinkedPerson>,
    online: Boolean,
    loading: Boolean,
    working: Boolean,
    onAddStudent: (code: String) -> Unit,
    onRemoveStudent: (uid: String) -> Unit,
    onEditProfile: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RosterFilter.ALL) }
    var addingCode by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<LinkedPerson?>(null) }

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
            ScreenHeader(
                title = "Class roster",
                trailing = {
                    if (user.classId.isNotBlank()) {
                        Pill(
                            user.classId,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                },
            )
        }

        if (!online) item { OfflineBanner() }

        if (user.classId.isBlank()) {
            // Without a class there is nothing to add a student to, so the whole
            // management surface below would be inert. Show the one useful action.
            item {
                InfoBanner(
                    "Set the class you advise before adding students — the roll call is grouped by class.",
                    Icons.Filled.Warning,
                    tone = BannerTone.Warn,
                )
            }
            item {
                PrimaryButton(
                    text = "Set your class",
                    onClick = onEditProfile,
                    icon = Icons.Filled.School,
                )
            }
            item {
                EmptyState(
                    title = "No class set",
                    subtitle = "Once you have set the class you advise, add students with the 6-character code from their Settings.",
                    icon = Icons.Filled.School,
                )
            }
        } else {
            item { SectionHeader(title = "Add a student") }
            item {
                SirenField(
                    value = addingCode,
                    onValueChange = {
                        addingCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6)
                    },
                    label = "Student's linking code",
                    leadingIcon = Icons.Filled.Key,
                    supportingText = "6 characters, from the student's Settings. Adds them to ${user.classId}.",
                )
            }
            item {
                PrimaryButton(
                    text = "Add to my class",
                    onClick = {
                        onAddStudent(addingCode)
                        addingCode = ""
                    },
                    enabled = addingCode.length == 6 && !working,
                    loading = working,
                    icon = Icons.Filled.PersonAdd,
                )
            }

            // Searching and filtering an empty class is noise, not a feature.
            if (roster.isNotEmpty()) {
                item {
                    SirenField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search students",
                        leadingIcon = Icons.Filled.Search,
                    )
                }

                item {
                    // Scrolls rather than wraps — with counts appended these used to
                    // break onto three lines inside a single chip. The counts live on
                    // the Overview tab, so they are not duplicated here either.
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        RosterFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(f.label) },
                                shape = RoundedCornerShape(Layout.pill),
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = if (roster.isEmpty()) "Students" else "Students (${roster.size})",
                )
            }

            when {
                loading -> item { SkeletonList(rows = 5) }

                roster.isEmpty() -> item {
                    EmptyState(
                        title = "No students yet",
                        subtitle = "Add students with the 6-character code shown in their Settings.",
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
                            Box(Modifier.padding(start = Space.l, bottom = Space.xs)) {
                                TextButton(onClick = { removing = person }) {
                                    Text("Remove from class")
                                }
                            }
                            if (i < visible.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }

    removing?.let { person ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${person.name}?") },
            text = { Text("They stay signed up and keep receiving alerts, but drop off your roll call.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveStudent(person.uid)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
