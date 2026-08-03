package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.StatusChip
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prototype screen 07. */
@Composable
fun ParentDashboardScreen(
    user: UserProfile,
    children: List<LinkedPerson>,
    online: Boolean,
    schoolHotline: String = "(082) 227-4410",
    onLinkStudent: () -> Unit,
    onCall: (String) -> Unit,
) {
    val anyTrouble = children.any { it.status == ResponseStatus.NEEDS_HELP }

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
                    Text("Parent account", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    Text(
                        user.name.ifBlank { "Guardian" },
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
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.cardLarge))
                    .background(SirenGradients.night)
                    .padding(Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (anyTrouble) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                    null,
                    tint = if (anyTrouble) Danger else Safe,
                    modifier = Modifier.size(38.dp),
                )
                Column {
                    Text(
                        when {
                            children.isEmpty() -> "No children linked yet"
                            anyTrouble -> "A child needs help"
                            else -> "All children are safe"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        if (online) "Campus sensors normal" else "Offline · showing last known status",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Linked children")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLinkStudent() },
                ) {
                    Icon(Icons.Filled.Add, null, tint = SirenBlue, modifier = Modifier.size(16.dp))
                    Text("Link", style = MaterialTheme.typography.labelMedium, color = SirenBlue)
                }
            }
        }

        if (children.isEmpty()) {
            item {
                Text(
                    "Ask your child for the 6-character linking code shown in their Settings, then tap Link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        } else {
            items(children, key = { it.uid }) { child -> ChildRow(child) }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.card))
                    .background(Surface)
                    .clickable { onCall(schoolHotline) }
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
                    Icon(Icons.Filled.Call, null, tint = SirenBlue, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("School emergency line", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(
                        "$schoolHotline · 24/7 hotline",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = InkSubtle)
            }
        }
    }
}

@Composable
private fun ChildRow(child: LinkedPerson) {
    val time = child.respondedAt?.let {
        remember(it) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) }
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
        Avatar(child.initials, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(child.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                when (child.status) {
                    ResponseStatus.SAFE -> "Confirmed ${time ?: ""}".trim()
                    ResponseStatus.NEEDS_HELP -> "Needs help ${time ?: ""}".trim()
                    ResponseStatus.NO_RESPONSE -> "No reply yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        StatusChip(child.status)
    }
}
