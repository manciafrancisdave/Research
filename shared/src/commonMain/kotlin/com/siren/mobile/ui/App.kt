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
import com.siren.mobile.model.LinkRequestStatus
import com.siren.mobile.model.Role
import com.siren.mobile.platform.Platform
import com.siren.mobile.platform.PhoneCodeRequest
import com.siren.mobile.platform.PhoneVerification
import com.siren.mobile.ui.screens.AlertScreen
import com.siren.mobile.ui.screens.DemoScreen
import com.siren.mobile.ui.screens.EditProfileScreen
import com.siren.mobile.ui.screens.EmergencyContactsScreen
import com.siren.mobile.ui.screens.GuardiansScreen
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
    data object Guardians : Dest
    data object Profile : Dest
    data class Live(val alertId: String) : Dest
}

private data class NavItem(val dest: Dest, val label: String, val icon: ImageVector)

/** OS grants the app needs but cannot give itself. Both fail silently when missing. */
private data class AlertPermissions(val notifications: Boolean, val fullScreen: Boolean)

/** Which way the last navigation went, so transitions read correctly. */
private enum class NavDirection { Forward, Back, Tab }

/** Shared entry point — hosted by MainActivity on Android and MainViewController on iOS. */
@Composable
fun App() {
    val settings by SirenRepository.settings.collectAsState()
    SirenTheme {
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

    val guardians by repo.guardians.collectAsState()
    val linkRequests by repo.linkRequests.collectAsState()
    val working by repo.working.collectAsState()

    // Students see requests waiting on *them*; parents see requests they are waiting on.
    // Same count, opposite meaning, which is why each screen words it differently.
    val pendingLinkRequests = linkRequests.count { it.status == LinkRequestStatus.PENDING }

    /** Non-null between "code sent" and the user typing it in. */
    var phoneVerification by remember { mutableStateOf<PhoneVerification?>(null) }

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
            // A device that has never held an account opens on Create Account; once one
            // exists here, signing in becomes the common case and leads instead.
            startOnSignUp = !settings.hasAccount,
            loading = authLoading,
            error = authError,
            phoneSupported = Platform.services.phoneAuthSupported,
            codeSent = phoneVerification != null,
            onSignIn = { email, pw -> scope.launch { repo.signIn(email, pw) } },
            onSignUp = { name, email, pw, role -> scope.launch { repo.signUp(name, email, pw, role) } },
            onSendCode = { name, phone, role ->
                scope.launch {
                    when (val result = repo.sendPhoneCode(phone)) {
                        is PhoneCodeRequest.Sent -> phoneVerification = result.verification
                        is PhoneCodeRequest.AutoVerified -> {
                            phoneVerification = null
                            repo.completeAutoVerifiedPhone(result.uid, result.phone, name, role)
                        }

                        is PhoneCodeRequest.Failed -> phoneVerification = null
                    }
                }
            },
            onVerifyCode = { name, code, role ->
                phoneVerification?.let { v ->
                    scope.launch {
                        if (repo.verifyPhoneCode(v, code, name, role)) phoneVerification = null
                    }
                }
            },
            onCancelPhone = {
                phoneVerification = null
                repo.clearAuthError()
            },
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

    /** Adds a student to the adviser's class and reports the outcome. */
    fun addStudentToClass(code: String) {
        scope.launch {
            val msg = when (val result = repo.linkStudentToClass(code)) {
                is LinkResult.Success -> "${result.studentName} added to ${profile.classId}."
                LinkResult.NotFound -> "No student found for that code."
                LinkResult.AlreadyLinked -> "That student is already in your class."
                is LinkResult.Failed -> result.reason
                // Advisers add directly; the request states cannot arise here.
                is LinkResult.Requested, is LinkResult.AlreadyRequested -> "Student added."
            }
            snackbar.showSnackbar(msg)
        }
    }

    /**
     * OS-level grants, re-read whenever the app comes back to the foreground.
     *
     * Both are changed outside the app, in Android settings, so the only sane moment to
     * re-check is when a screen that reports them is composed again.
     */
    val permissionsChecked = remember(current) {
        AlertPermissions(
            notifications = Platform.services.notificationsEnabled(),
            fullScreen = Platform.services.canUseFullScreenIntent(),
        )
    }

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
                            guardians = guardians,
                            pendingGuardianRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenContacts = { push(Dest.Contacts) },
                            onOpenDemo = { push(Dest.Demo) },
                            onOpenSettings = { selectTab(Dest.Settings) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenGuardians = { push(Dest.Guardians) },
                            onOpenAlert = { repo.showAlertById(it.id) },
                        )

                        Role.TEACHER -> TeacherDashboardScreen(
                            user = profile,
                            roster = roster,
                            activeAlert = alerts.firstOrNull(),
                            online = online,
                            loading = !alertsLoaded,
                            working = working,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenGuide = { push(Dest.Guide) },
                            onAddStudent = { code -> addStudentToClass(code) },
                            onRemoveStudent = { repo.removeStudentFromClass(it) },
                            onEditProfile = { push(Dest.Profile) },
                        )

                        Role.PARENT -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            pendingRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onOpenGuide = { push(Dest.Guide) },
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
                            working = working,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenGuide = { push(Dest.Guide) },
                            onAddStudent = { code -> addStudentToClass(code) },
                            onRemoveStudent = { repo.removeStudentFromClass(it) },
                            onEditProfile = { push(Dest.Profile) },
                        )

                        else -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            pendingRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onOpenGuide = { push(Dest.Guide) },
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
                        fullScreenAlertsAllowed = permissionsChecked.fullScreen,
                        notificationsAllowed = permissionsChecked.notifications,
                        onUpdateSettings = { repo.updateSettings(it) },
                        onOpenContacts = { push(Dest.Contacts) },
                        onOpenGuide = { push(Dest.Guide) },
                        onEditProfile = { push(Dest.Profile) },
                        onChangeRole = { scope.launch { repo.updateRole(it) } },
                        onFixFullScreenAlerts = { Platform.services.openFullScreenIntentSettings() },
                        onFixNotifications = { Platform.services.openNotificationSettings() },
                        onSignOut = { repo.signOut() },
                    )

                    Dest.Profile -> EditProfileScreen(
                        user = profile,
                        working = working,
                        onSave = { name, classId, phone ->
                            scope.launch {
                                if (repo.updateProfile(name, classId, phone)) pop()
                            }
                        },
                        onBack = { pop() },
                    )

                    Dest.Guardians -> GuardiansScreen(
                        shortCode = profile.shortCode,
                        guardians = guardians,
                        requests = linkRequests,
                        working = working,
                        eventActive = alerts.firstOrNull()?.closed == false,
                        onRespond = { id, approve ->
                            scope.launch { repo.respondToLinkRequest(id, approve) }
                        },
                        onRevoke = { repo.revokeGuardian(it) },
                        onBack = { pop() },
                    )

                    Dest.Link -> ParentLinkingScreen(
                        linked = roster,
                        requests = linkRequests,
                        working = working,
                        onLink = { code ->
                            scope.launch {
                                val msg = when (val result = repo.requestLink(code)) {
                                    is LinkResult.Requested ->
                                        "Request sent to ${result.studentName}. They need to confirm it's you."

                                    is LinkResult.AlreadyRequested ->
                                        "${result.studentName} hasn't answered your last request yet."

                                    is LinkResult.Success -> "Linked ${result.studentName}"
                                    LinkResult.NotFound -> "No student found for that code."
                                    LinkResult.AlreadyLinked -> "That student is already linked."
                                    is LinkResult.Failed -> result.reason
                                }
                                snackbar.showSnackbar(msg)
                            }
                        },
                        onUnlink = { repo.unlinkStudent(it) },
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
                        onRestoreDefaults = { repo.restoreDefaultContacts() },
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

private const val STEP_LOGIN = 0
private const val STEP_ROLE = 1
private const val STEP_SIGN_UP = 2

/**
 * Login → role → sign-up, entered at whichever end suits the device.
 *
 * [startOnSignUp] is what puts Create Account in front of Login on a fresh install. The
 * two screens still reach each other in both directions; only the entry point moves.
 */
@Composable
private fun AuthFlow(
    startOnSignUp: Boolean,
    loading: Boolean,
    error: String?,
    phoneSupported: Boolean,
    codeSent: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, Role) -> Unit,
    onSendCode: (name: String, phone: String, role: Role) -> Unit,
    onVerifyCode: (name: String, code: String, role: Role) -> Unit,
    onCancelPhone: () -> Unit,
    onForgot: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var step by remember { mutableStateOf(if (startOnSignUp) STEP_ROLE else STEP_LOGIN) }
    var role by remember { mutableStateOf(Role.STUDENT) }

    // Backing out of sign-up must abandon any half-finished SMS verification, or
    // returning to it later would show a code field for a number nobody re-entered.
    fun leaveSignUp(target: Int) {
        onCancelPhone()
        onClearError()
        step = target
    }

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
            STEP_LOGIN -> LoginScreen(
                loading = loading,
                error = error,
                onSignIn = onSignIn,
                onCreateAccount = {
                    onClearError()
                    step = STEP_ROLE
                },
                onForgotPassword = onForgot,
            )

            STEP_ROLE -> RoleSelectionScreen(
                onContinue = {
                    role = it
                    onClearError()
                    step = STEP_SIGN_UP
                },
                onSignIn = {
                    onClearError()
                    step = STEP_LOGIN
                },
                // On a fresh install this is the first screen, so there is nowhere to go
                // back to and the arrow is hidden rather than left as a dead control.
                onBack = if (startOnSignUp) null else ({ step = STEP_LOGIN }),
            )

            else -> SignUpScreen(
                role = role,
                loading = loading,
                error = error,
                phoneSupported = phoneSupported,
                codeSent = codeSent,
                onSignUp = { name, email, pw -> onSignUp(name, email, pw, role) },
                onSendCode = { name, phone -> onSendCode(name, phone, role) },
                onVerifyCode = { name, code -> onVerifyCode(name, code, role) },
                onCancelPhone = onCancelPhone,
                onBack = { leaveSignUp(STEP_ROLE) },
            )
        }
    }
}
