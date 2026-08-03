package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.siren.mobile.model.Role
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space

private const val MIN_PASSWORD = 6

/**
 * Account creation. The role picked on the previous screen is carried in and written
 * to the profile, so a user never sees the email/password fields before choosing one.
 */
@Composable
fun SignUpScreen(
    role: Role,
    loading: Boolean,
    error: String?,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD
    val mismatch = confirm.isNotEmpty() && confirm != password
    val valid = name.isNotBlank() && email.isNotBlank() &&
        password.length >= MIN_PASSWORD && confirm == password

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Create your account", onBack = onBack)

        Text(
            "Signing up as ${role.label}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (error != null) {
            InfoBanner(error, Icons.Filled.Warning, tone = BannerTone.Danger)
        }

        SirenField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            leadingIcon = Icons.Filled.Person,
        )
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
            isError = tooShort,
            supportingText = if (tooShort) {
                "Use at least $MIN_PASSWORD characters"
            } else {
                "At least $MIN_PASSWORD characters"
            },
        )
        SirenField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            leadingIcon = Icons.Filled.Lock,
            isPassword = true,
            isError = mismatch,
            supportingText = if (mismatch) "Passwords do not match" else null,
        )

        if (role == Role.STUDENT) {
            InfoBanner(
                "You'll get a 6-character linking code after signing up. Give it to your parent or guardian so they can follow your safety status.",
                Icons.Filled.Badge,
            )
        }

        PrimaryButton(
            text = "Create account",
            onClick = { onSignUp(name, email, password) },
            enabled = valid,
            loading = loading,
        )

        Text(
            "Your role is saved now and can be changed later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }
}
