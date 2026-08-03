package com.siren.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.Role
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkMuted
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint

/**
 * Prototype screen 03. The role is chosen before the account exists, so it can be
 * written into the user document at sign-up.
 */
@Composable
fun RoleSelectionScreen(
    onContinue: (Role) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(Role.STUDENT) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Choose your role", onBack = onBack)

        Text(
            "Your role determines the alerts you receive and the actions you can take.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )

        RoleCard(
            icon = Icons.Filled.School,
            title = "Student",
            blurb = "Receive alerts and confirm your safety status.",
            selected = selected == Role.STUDENT,
            onClick = { selected = Role.STUDENT },
        )
        RoleCard(
            icon = Icons.Filled.Groups,
            title = "Teacher / School Admin",
            blurb = "Monitor your class roster in real time and run drills.",
            selected = selected == Role.TEACHER,
            onClick = { selected = Role.TEACHER },
        )
        RoleCard(
            icon = Icons.Filled.FamilyRestroom,
            title = "Parent / Guardian",
            blurb = "Track the safety status of linked children.",
            selected = selected == Role.PARENT,
            onClick = { selected = Role.PARENT },
        )

        Box(Modifier.weight(1f))

        PrimaryButton(
            text = "Continue as ${selected.label.substringBefore(" /")}",
            onClick = { onContinue(selected) },
        )
        Text(
            "You can switch roles later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xl),
        )
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(if (selected) SurfaceTint else Surface)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SirenBlue else Border),
                RoundedCornerShape(Layout.card),
            )
            .clickable { onClick() }
            .padding(Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Layout.field))
                .background(if (selected) SirenBlue else SurfaceTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) Color.White else SirenBlue, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, tint = SirenBlue, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Ink,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() },
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
