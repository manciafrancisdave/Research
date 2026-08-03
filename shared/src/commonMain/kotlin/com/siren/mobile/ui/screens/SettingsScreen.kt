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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.Role
import com.siren.mobile.model.SirenSettings
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint

/** Prototype screen 14. */
@Composable
fun SettingsScreen(
    user: UserProfile,
    settings: SirenSettings,
    versionName: String,
    onUpdateSettings: ((SirenSettings) -> SirenSettings) -> Unit,
    onChangeRole: (Role) -> Unit,
    onOpenContacts: () -> Unit,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var roleDialog by remember { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Layout.card))
                .background(Surface)
                .padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.initials)
            Column(Modifier.weight(1f)) {
                Text(
                    user.name.ifBlank { "Unnamed" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    listOfNotNull(user.role.label, user.classId.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = InkSubtle)
        }

        // Students show the code a parent needs in order to link to them.
        if (user.role == Role.STUDENT && user.shortCode.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.card))
                    .background(SurfaceTint)
                    .padding(Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Key, null, tint = SirenBlue, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your linking code", style = MaterialTheme.typography.labelMedium, color = InkSubtle)
                    Text(
                        user.shortCode,
                        style = MaterialTheme.typography.headlineSmall,
                        color = SirenBlue,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    "Give this to your\nparent or guardian",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSubtle,
                )
            }
        }

        SectionLabel("Alerts")

        ToggleRow(
            icon = Icons.Filled.NotificationsActive,
            title = "Critical alerts",
            subtitle = "Bypass silent mode for Level 3 events",
            checked = settings.criticalAlerts,
            onCheckedChange = { v -> onUpdateSettings { it.copy(criticalAlerts = v) } },
        )
        ToggleRow(
            icon = Icons.Filled.Vibration,
            title = "Vibration & sound",
            subtitle = "Escalates with intensity",
            checked = settings.vibration,
            onCheckedChange = { v -> onUpdateSettings { it.copy(vibration = v) } },
        )
        ToggleRow(
            icon = Icons.Filled.DarkMode,
            title = "Dark mode",
            subtitle = "Use the dark palette",
            checked = settings.darkMode,
            onCheckedChange = { v -> onUpdateSettings { it.copy(darkMode = v) } },
        )

        SectionLabel("Account")

        NavRow(Icons.Filled.ContactEmergency, "Emergency contacts", "${settings.contacts.size} saved", onOpenContacts)
        NavRow(Icons.Filled.SwapHoriz, "Switch role", user.role.label) { roleDialog = true }
        NavRow(Icons.Filled.PrivacyTip, "Privacy & data", "How your responses are stored") {}
        NavRow(Icons.Filled.Info, "About S.I.R.E.N.", "v$versionName") {}

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Layout.card))
                .background(Surface)
                .clickable { signOutDialog = true }
                .padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Logout, null, tint = Danger, modifier = Modifier.size(22.dp))
            Text("Sign out", style = MaterialTheme.typography.titleMedium, color = Danger)
        }

        InfoBanner(
            "Seismic Integrated Response and Emergency Notification · City of Bogo Senior High School, Practical Research 2.",
            Icons.Filled.Info,
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }

    if (roleDialog) {
        AlertDialog(
            onDismissRequest = { roleDialog = false },
            title = { Text("Switch role") },
            text = {
                Column {
                    Role.entries.forEach { role ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChangeRole(role)
                                    roleDialog = false
                                }
                                .padding(vertical = Space.m),
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            Text(
                                role.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (role == user.role) SirenBlue else Ink,
                                fontWeight = if (role == user.role) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { roleDialog = false }) { Text("Cancel") } },
        )
    }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You'll stop receiving alerts on this device until you sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    signOutDialog = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(Surface)
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SirenBlue, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
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
        Icon(icon, null, tint = SirenBlue, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = InkSubtle)
    }
}
