package com.siren.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.siren.mobile.data.LinkResult
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Role
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.screens.AlertScreen
import com.siren.mobile.ui.screens.DemoScreen
import com.siren.mobile.ui.screens.EmergencyContactsScreen
import com.siren.mobile.ui.screens.HistoryScreen
import com.siren.mobile.ui.screens.LiveSafetyDashboardScreen
import com.siren.mobile.ui.screens.LoginScreen
import com.siren.mobile.ui.screens.ParentDashboardScreen
import com.siren.mobile.ui.screens.ParentLinkingScreen
import com.siren.mobile.ui.screens.RoleSelectionScreen
import com.siren.mobile.ui.screens.SafetyConfirmationScreen
import com.siren.mobile.ui.screens.SafetyGuideScreen
import com.siren.mobile.ui.screens.SettingsScreen
import com.siren.mobile.ui.screens.SignUpScreen
import com.siren.mobile.ui.screens.SplashScreen
import com.siren.mobile.ui.screens.StudentDashboardScreen
import com.siren.mobile.ui.screens.TeacherDashboardScreen
import com.siren.mobile.ui.theme.SirenTheme
import kotlinx.coroutines.launch

private sealed interface Dest {
    data object Home : Dest
    data object People : Dest
    data object History : Dest
    data object Settings : Dest
    data object Link : Dest
    data object Demo : Dest
    data object Contacts : Dest
    data object Guide : Dest
    data class Live(val alertId: String) : Dest
}

private data class NavItem(val dest: Dest, val label: String, val icon: ImageVector)

/** Which way the last navigation went, so transitions read correctly. */
private enum class NavDirection { Forward, Back, Tab }

/** Shared entry point — hosted by MainActivity on Android and MainViewController on iOS. */
@Composable
fun App() {
    val settings by SirenRepository.settings.collectAsState()
    SirenTheme(darkTheme = settings.darkMode) {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    val repo = SirenRepository
    val scope = rememberCoroutineScope()

    val authResolved by repo.authResolved.collectAsState()
    val signedIn by repo.signedIn.collectAsState()
    val user by repo.user.collectAsState()
    val alerts by repo.alerts.collectAsState()
    val alertsLoaded by repo.alertsLoaded.collectAsState()
    val myResponses by repo.myResponses.collectAsState()
    val roster by repo.roster.collectAsState()
    val settings by repo.settings.collectAsState()
    val online by repo.online.collectAsState()
    val incomingAlert by repo.incomingAlert.collectAsState()
    val authLoading by repo.authLoading.collectAsState()
    val authError by repo.authError.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        repo.events.collect { snackbar.showSnackbar(it.text) }
    }

    // ------------------------------------------------------------ auth gate

    if (!authResolved) {
        SplashScreen(Platform.services.versionName)
        return
    }

    if (!signedIn) {
        AuthFlow(
            loading = authLoading,
            error = authError,
            onSignIn = { email, pw -> scope.launch { repo.signIn(email, pw) } },
            onSignUp = { name, email, pw, role -> scope.launch { repo.signUp(name, email, pw, role) } },
            onForgot = { email -> scope.launch { repo.resetPassword(email) } },
            onClearError = { repo.clearAuthError() },
        )
        return
    }

    val profile = user
    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // -------------------------------------------------------------- in-app

    val backStack = remember { mutableStateListOf<Dest>(Dest.Home) }
    var direction by remember { mutableStateOf(NavDirection.Tab) }
    val current = backStack.last()

    fun push(d: Dest) {
        direction = NavDirection.Forward
        backStack.add(d)
    }

    fun pop() {
        if (backStack.size > 1) {
            direction = NavDirection.Back
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun selectTab(d: Dest) {
        direction = NavDirection.Tab
        backStack.clear()
        backStack.add(d)
    }

    PlatformBackHandler(enabled = backStack.size > 1) { pop() }

    val tabs = when (profile.role) {
        Role.STUDENT -> listOf(
            NavItem(Dest.Home, "Home", Icons.Filled.Home),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )

        Role.TEACHER -> listOf(
            NavItem(Dest.Home, "Overview", Icons.Filled.Dashboard),
            NavItem(Dest.People, "Roster", Icons.Filled.Groups),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )

        Role.PARENT -> listOf(
            NavItem(Dest.Home, "Home", Icons.Filled.Home),
            NavItem(Dest.People, "Children", Icons.Filled.FamilyRestroom),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )
    }

    val showBottomBar = tabs.any { it.dest == current }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.dest,
                            onClick = { selectTab(item.dest) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            AnimatedContent(
                targetState = current,
                label = "screen",
                transitionSpec = {
                    // Short and directional: forward slides in from the right, back
                    // reverses it, tab switches simply cross-fade. All under 300ms.
                    when (direction) {
                        NavDirection.Tab ->
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))

                        NavDirection.Forward ->
                            (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))) togetherWith
                                fadeOut(tween(140))

                        NavDirection.Back ->
                            (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))) togetherWith
                                fadeOut(tween(140))
                    }
                },
            ) { dest ->
                when (dest) {
                    Dest.Home -> when (profile.role) {
                        Role.STUDENT -> StudentDashboardScreen(
                            user = profile,
                            alerts = alerts,
                            myResponses = myResponses,
                            online = online,
                            loading = !alertsLoaded,
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenContacts = { push(Dest.Contacts) },
                            onOpenDemo = { push(Dest.Demo) },
                            onOpenSettings = { selectTab(Dest.Settings) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenAlert = { repo.showAlertById(it.id) },
                        )

                        Role.TEACHER -> TeacherDashboardScreen(
                            user = profile,
                            roster = roster,
                            activeAlert = alerts.firstOrNull(),
                            online = online,
                            loading = !alertsLoaded,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                        )

                        Role.PARENT -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onCall = { Platform.services.dial(it) },
                        )
                    }

                    Dest.People -> when (profile.role) {
                        Role.TEACHER -> TeacherDashboardScreen(
                            user = profile,
                            roster = roster,
                            activeAlert = alerts.firstOrNull(),
                            online = online,
                            loading = !alertsLoaded,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                        )

                        else -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onCall = { Platform.services.dial(it) },
                        )
                    }

                    Dest.History -> HistoryScreen(
                        alerts = alerts,
                        myResponses = myResponses,
                        loading = !alertsLoaded,
                    )

                    Dest.Settings -> SettingsScreen(
                        user = profile,
                        settings = settings,
                        versionName = Platform.services.versionName,
                        onUpdateSettings = { repo.updateSettings(it) },
                        onChangeRole = { repo.updateRole(it) },
                        onOpenContacts = { push(Dest.Contacts) },
                        onSignOut = { repo.signOut() },
                    )

                    Dest.Link -> ParentLinkingScreen(
                        linked = roster,
                        working = false,
                        onLink = { code ->
                            scope.launch {
                                val msg = when (val result = repo.linkStudent(code)) {
                                    is LinkResult.Success -> "Linked ${result.studentName}"
                                    LinkResult.NotFound -> "No student found for that code."
                                    LinkResult.AlreadyLinked -> "That student is already linked."
                                    is LinkResult.Failed -> result.reason
                                }
                                snackbar.showSnackbar(msg)
                            }
                        },
                        onBack = { pop() },
                    )

                    Dest.Demo -> DemoScreen(
                        alerts = alerts,
                        onTrigger = { repo.simulateAlert(it) },
                        onBack = { pop() },
                    )

                    Dest.Contacts -> EmergencyContactsScreen(
                        contacts = settings.contacts,
                        onAdd = { repo.addEmergencyContact(it) },
                        onRemove = { repo.removeEmergencyContact(it) },
                        onCall = { Platform.services.dial(it) },
                        onText = { Platform.services.sendSms(it) },
                        onBack = { pop() },
                    )

                    Dest.Guide -> SafetyGuideScreen(onBack = { pop() })

                    is Dest.Live -> {
                        val alert = alerts.firstOrNull { it.id == dest.alertId }
                        if (alert == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("That event is no longer available.")
                            }
                        } else {
                            LiveSafetyDashboardScreen(
                                alert = alert,
                                roster = roster,
                                onCloseEvent = { repo.closeEvent(alert.id) },
                                onBack = { pop() },
                            )
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------- full-screen alert

    val incoming = incomingAlert
    AnimatedVisibility(
        visible = incoming != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(140)),
    ) {
        if (incoming != null) {
            var confirming by remember(incoming.id) { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                if (confirming) {
                    SafetyConfirmationScreen(
                        alert = incoming,
                        myResponse = myResponses[incoming.id],
                        onRespond = { repo.submitMyResponse(incoming.id, it) },
                        onDone = { repo.consumeIncomingAlert() },
                        onBack = { confirming = false },
                    )
                } else {
                    AlertScreen(
                        alert = incoming,
                        vibrationEnabled = settings.vibration,
                        onConfirmStatus = { confirming = true },
                        onDismiss = { repo.consumeIncomingAlert() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthFlow(
    loading: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, Role) -> Unit,
    onForgot: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var step by remember { mutableStateOf(0) } // 0 = login, 1 = role, 2 = sign-up
    var role by remember { mutableStateOf(Role.STUDENT) }

    AnimatedContent(
        targetState = step,
        label = "auth",
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140))
            } else {
                (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140))
            }
        },
    ) { s ->
        when (s) {
            0 -> LoginScreen(
                loading = loading,
                error = error,
                onSignIn = onSignIn,
                onCreateAccount = {
                    onClearError()
                    step = 1
                },
                onForgotPassword = onForgot,
            )

            1 -> RoleSelectionScreen(
                onContinue = {
                    role = it
                    step = 2
                },
                onBack = { step = 0 },
            )

            else -> SignUpScreen(
                role = role,
                loading = loading,
                error = error,
                onSignUp = { name, email, pw -> onSignUp(name, email, pw, role) },
                onBack = {
                    onClearError()
                    step = 1
                },
            )
        }
    }
}
