package com.siren.mobile.ui.screens

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siren.mobile.model.Role
import com.siren.mobile.model.SirenSettings
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.tabular

/** Prototype screen 14. */
@Composable
fun SettingsScreen(
    user: UserProfile,
    settings: SirenSettings,
    versionName: String,
    onUpdateSettings: ((SirenSettings) -> SirenSettings) -> Unit,
    onOpenContacts: () -> Unit,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var signOutDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        ListGroup {
            ListRow(
                title = user.name.ifBlank { "Unnamed" },
                subtitle = listOfNotNull(
                    user.role.label,
                    user.classId.ifBlank { null },
                    user.email.ifBlank { null },
                ).joinToString(" · "),
                leading = { Avatar(user.initials) },
            )
        }

        // Students carry the code a parent needs, so it gets top-tier treatment.
        if (user.role == Role.STUDENT && user.shortCode.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(Layout.cardLarge),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text(
                            "Your linking code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            user.shortCode,
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                        )
                        Text(
                            "Give this to your parent or guardian so they can follow your safety status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        SectionLabel("Alerts")
        ListGroup {
            ToggleRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Critical alerts",
                subtitle = "Sound even when the phone is silenced",
                checked = settings.criticalAlerts,
            ) { v -> onUpdateSettings { it.copy(criticalAlerts = v) } }
            RowDivider()
            ToggleRow(
                icon = Icons.Filled.Vibration,
                title = "Vibration",
                subtitle = "Escalates with intensity",
                checked = settings.vibration,
            ) { v -> onUpdateSettings { it.copy(vibration = v) } }
        }

        SectionLabel("Account")
        ListGroup {
            NavRow(
                Icons.Filled.ContactEmergency,
                "Emergency contacts",
                "${settings.contacts.size} saved",
                onOpenContacts,
            )
            RowDivider()
            NavRow(Icons.Filled.PrivacyTip, "Privacy & data", "How your responses are stored") {}
            RowDivider()
            NavRow(Icons.Filled.Info, "About S.I.R.E.N.", "Version $versionName") {}
        }

        ListGroup {
            ListRow(
                title = "Sign out",
                onClick = { signOutDialog = true },
                leading = {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = null,
                        Modifier.size(24.dp),
                        tint = SirenTheme.status.danger,
                    )
                },
            )
        }

        Text(
            "Seismic Integrated Response and Emergency Notification\nCity of Bogo Senior High School · Practical Research 2",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.padding(bottom = Space.xxl))
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

/**
 * The whole row is the target — the Switch only reflects state. Tapping a 56dp-tall
 * row is far easier under stress than hitting the switch itself.
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        leading = {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
