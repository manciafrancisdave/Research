package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableRestaurant
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Border
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.DangerTint
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenBlueDark
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.SurfaceTint
import com.siren.mobile.ui.theme.SurfaceTintAlt

/** Prototype screen 02. */
@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        // Drop / Cover / Hold illustration band
        Row(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(Layout.cardLarge))
                .background(Brush.linearGradient(listOf(SurfaceTintAlt, SurfaceTint))),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Icons.Filled.TableRestaurant to "Drop",
                Icons.Filled.Shield to "Cover",
                Icons.Filled.BackHand to "Hold",
            ).forEach { (icon, label) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Icon(icon, null, tint = SirenBlue, modifier = Modifier.size(40.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = SirenBlueDark,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                "Sign in to stay protected and connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }

        if (error != null) {
            InfoBanner(error, Icons.Filled.Lock, fg = Danger, bg = DangerTint)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SirenField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                leadingIcon = Icons.Filled.Mail,
                keyboardType = KeyboardType.Email,
            )
            SirenField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                isPassword = true,
            )

            Text(
                "Forgot password?",
                style = MaterialTheme.typography.bodySmall,
                color = SirenBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onForgotPassword(email) },
            )

            PrimaryButton(
                text = "Sign In",
                onClick = { onSignIn(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                loading = loading,
                icon = Icons.Filled.ArrowForward,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New here? ", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            Text(
                "Create an account",
                style = MaterialTheme.typography.bodySmall,
                color = SirenBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onCreateAccount() },
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border)
        )
    }
}
