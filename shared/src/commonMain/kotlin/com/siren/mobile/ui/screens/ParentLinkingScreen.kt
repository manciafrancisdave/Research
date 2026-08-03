package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SafeTint
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface

/**
 * Prototype screen 04. The registrar issues each student a 6-character code, which the
 * parent types here to link. (The prototype's "Scan QR" affordance needs a camera
 * dependency and is intentionally left out of this build.)
 */
@Composable
fun ParentLinkingScreen(
    linked: List<LinkedPerson>,
    working: Boolean,
    onLink: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(title = "Link a student", onBack = onBack)

        SirenField(
            value = code,
            onValueChange = { code = it.uppercase().take(6) },
            label = "Linking code",
            leadingIcon = Icons.Filled.Key,
            supportingText = "6 characters, shown in your child's Settings",
        )

        PrimaryButton(
            text = "Link Student",
            onClick = { onLink(code) },
            enabled = code.length == 6,
            loading = working,
        )

        InfoBanner(
            "Linking codes are issued by the school registrar. If a code does not work, ask the adviser to reissue it.",
            Icons.Filled.Info,
        )

        SectionLabel("Linked students")

        if (linked.isEmpty()) {
            Text(
                "No students linked yet.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        } else {
            linked.forEach { person ->
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
                        Text(
                            person.name.ifBlank { "Student" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Text(
                            person.klass.ifBlank { "Class not set" },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                        )
                    }
                    Pill("Linked", Safe, SafeTint)
                }
            }
        }

        Column(Modifier.padding(bottom = Space.xxl)) {}
    }
}
