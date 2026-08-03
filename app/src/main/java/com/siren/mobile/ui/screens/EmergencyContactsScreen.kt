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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.EmergencyContact
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint

/**
 * Prototype screen 13. Mirrors the hardware's SMS fallback list, so contacts are still
 * reachable when the phone has no data connection.
 */
@Composable
fun EmergencyContactsScreen(
    contacts: List<EmergencyContact>,
    onAdd: (EmergencyContact) -> Unit,
    onRemove: (String) -> Unit,
    onCall: (String) -> Unit,
    onText: (String) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(
            title = "Emergency contacts",
            onBack = onBack,
            trailing = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add contact",
                    tint = SirenBlue,
                    modifier = Modifier.clickable { adding = true },
                )
            },
        )

        InfoBanner(
            "SMS fallback is active — alerts still reach these contacts without internet.",
            Icons.Filled.Sms,
        )

        if (contacts.isEmpty()) {
            Text(
                "No contacts saved yet. Add at least one guardian or responder.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        } else {
            contacts.forEach { contact ->
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
                        Avatar(
                            contact.name.trim().split(" ")
                                .filter { it.isNotEmpty() }
                                .take(2)
                                .joinToString("") { it.first().uppercase() }
                                .ifEmpty { "?" },
                            size = 40.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(contact.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                                if (contact.primary) Pill("PRIMARY", SirenBlue, SurfaceTint)
                            }
                            Text(
                                "${contact.relation} · ${contact.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSubtle,
                            )
                        }
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = InkSubtle,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRemove(contact.id) },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        ContactAction("Quick call", Icons.Filled.Call, Modifier.weight(1f)) { onCall(contact.phone) }
                        ContactAction("Text", Icons.Filled.Sms, Modifier.weight(1f)) { onText(contact.phone) }
                    }
                }
            }
        }

        Box(Modifier.padding(bottom = Space.xxl))
    }

    if (adding) {
        AddContactDialog(
            onDismiss = { adding = false },
            onSave = {
                onAdd(it)
                adding = false
            },
        )
    }
}

@Composable
private fun ContactAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Layout.field))
            .background(SurfaceTint)
            .clickable { onClick() }
            .padding(vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SirenBlue, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SirenBlue)
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add emergency contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SirenField(name, { name = it }, "Name")
                SirenField(relation, { relation = it }, "Relation")
                SirenField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    onSave(
                        EmergencyContact(
                            id = "c${System.currentTimeMillis()}",
                            name = name.trim(),
                            relation = relation.trim().ifBlank { "Contact" },
                            phone = phone.trim(),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
