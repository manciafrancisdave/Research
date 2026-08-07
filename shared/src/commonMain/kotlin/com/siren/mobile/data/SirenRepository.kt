package com.siren.mobile.data

import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.DefaultEmergencyContacts
import com.siren.mobile.model.EmergencyContact
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.LinkRequest
import com.siren.mobile.model.LinkRequestStatus
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.Role
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.model.SirenSettings
import com.siren.mobile.model.UserProfile
import com.siren.mobile.platform.Platform
import com.siren.mobile.platform.PhoneCodeRequest
import com.siren.mobile.platform.PhoneVerification
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single source of truth for auth, Firestore data and local settings — shared by the
 * Android and iOS apps through the GitLive Firebase Multiplatform SDK.
 *
 * Firestore's on-device cache is enabled by default on both platforms, which is what
 * gives us offline queueing: a safety confirmation written with no connectivity is
 * persisted locally and replayed automatically once back online.
 */
object SirenRepository {

    private const val ALERT_LIMIT = 100

    /**
     * Every listener and write runs on this scope. The handler is not decoration: a
     * `launch` that throws with no handler reaches the thread's default uncaught
     * handler, which on Android kills the process. A Firestore permission change or a
     * missing `google-services.json` would then present as the app crashing on launch
     * rather than as one broken flow, so failures are reported and swallowed here.
     */
    private val errors = CoroutineExceptionHandler { _, e ->
        notifyUi(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong syncing.", isError = true)
    }
    private val scope = CoroutineScope(SupervisorJob() + errors)
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    private val usersCol get() = db.collection("users")
    private val alertsCol get() = db.collection("alerts")
    private val linkRequestsCol get() = db.collection("linkRequests")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------------------------------------------------------------- state

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    /** Distinguishes "signed out" from "signed in, profile still loading". */
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertRecord>>(emptyList())
    val alerts: StateFlow<List<AlertRecord>> = _alerts.asStateFlow()

    /** False until the first alerts snapshot lands, so screens can show skeletons
     *  instead of flashing an "empty" state at a user who is simply still loading. */
    private val _alertsLoaded = MutableStateFlow(false)
    val alertsLoaded: StateFlow<Boolean> = _alertsLoaded.asStateFlow()

    private val _myResponses = MutableStateFlow<Map<String, SafetyResponse>>(emptyMap())
    val myResponses: StateFlow<Map<String, SafetyResponse>> = _myResponses.asStateFlow()

    private val _roster = MutableStateFlow<List<LinkedPerson>>(emptyList())
    val roster: StateFlow<List<LinkedPerson>> = _roster.asStateFlow()

    /**
     * Guardian links seen from the student's side.
     *
     * A student needs to know who is following their safety status — an unexplained
     * adult watching a child's whereabouts-during-an-emergency feed is exactly the thing
     * the confirmation step exists to prevent — and during an event they want to know
     * their parent is safe too, which is why these rows carry a [ResponseStatus].
     */
    private val _guardians = MutableStateFlow<List<LinkedPerson>>(emptyList())
    val guardians: StateFlow<List<LinkedPerson>> = _guardians.asStateFlow()

    /**
     * Guardian link requests touching the signed-in user: the ones raised *about* them
     * if they are a student, the ones they raised themselves if they are a parent.
     */
    private val _linkRequests = MutableStateFlow<List<LinkRequest>>(emptyList())
    val linkRequests: StateFlow<List<LinkRequest>> = _linkRequests.asStateFlow()

    /** True while a link, role change or profile save is in flight. */
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _settings = MutableStateFlow(SirenSettings())
    val settings: StateFlow<SirenSettings> = _settings.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _incomingAlert = MutableStateFlow<AlertRecord?>(null)
    val incomingAlert: StateFlow<AlertRecord?> = _incomingAlert.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authResolved = MutableStateFlow(false)
    val authResolved: StateFlow<Boolean> = _authResolved.asStateFlow()

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    // ------------------------------------------------------- internal state

    private var rosterMembers: List<UserProfile> = emptyList()
    private var rosterResponses: List<SafetyResponse> = emptyList()

    private var profileJob: Job? = null
    private var alertsJob: Job? = null
    private var myRespJob: Job? = null
    private var rosterMembersJob: Job? = null
    private var rosterRespJob: Job? = null
    private var linkRequestJob: Job? = null

    private var currentRosterAlertId: String? = null
    private var currentLinkRole: Role? = null
    private var alertsInitialised = false
    private var lastIncomingId: String? = null

    /** Called once at start-up, after Platform.install(). */
    fun start() {
        // Settings are local-only and must load even if Firebase never comes up, so
        // they are read before anything touches the network.
        loadSettings()
        runCatching { Platform.services.subscribeToAlertsTopic() }

        scope.launch {
            // Firebase.auth throws outright when google-services.json is missing or the
            // project is misconfigured. Resolving auth anyway leaves the user on the
            // login screen with an error instead of stuck behind the splash forever.
            try {
                auth.authStateChanged.collect { firebaseUser ->
                    val uid = firebaseUser?.uid
                    _signedIn.value = uid != null
                    if (uid == null) {
                        detachAll()
                        _user.value = null
                    } else {
                        attachFor(uid)
                    }
                    _authResolved.value = true
                }
            } catch (e: Exception) {
                _authError.value = "Couldn't reach the sign-in service. Check your connection."
                _signedIn.value = false
                _authResolved.value = true
            }
        }
    }

    // ------------------------------------------------------------ auth API

    suspend fun signIn(email: String, password: String): Boolean {
        _authLoading.value = true
        _authError.value = null
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password)
            markHasAccount()
            true
        } catch (e: Exception) {
            _authError.value = authMessage(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /**
     * Creates the account and writes the profile. The role is chosen before the account
     * exists (prototype screen 03) and is passed straight through to the user document.
     */
    suspend fun signUp(name: String, email: String, password: String, role: Role): Boolean {
        _authLoading.value = true
        _authError.value = null
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password)
            val uid = result.user?.uid ?: error("No uid returned")
            writeNewProfile(uid, name, email = email.trim(), phone = "", role = role)
            markHasAccount()
            true
        } catch (e: Exception) {
            _authError.value = authMessage(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    // ------------------------------------------------------ phone sign-up

    /**
     * Sends the SMS verification code.
     *
     * Phone auth is not wrapped by the GitLive multiplatform SDK, so it goes through
     * [Platform.services] — the same seam Cloud Messaging already uses. It also needs
     * three things done in the Firebase console before a single message will send:
     * the **Blaze** plan, the app's **SHA-256** fingerprint registered, and **Phone**
     * enabled under Authentication → Sign-in method. Until then this returns a failure
     * rather than pretending to have sent anything.
     */
    suspend fun sendPhoneCode(phone: String): PhoneCodeRequest {
        _authLoading.value = true
        _authError.value = null
        return try {
            val result = Platform.services.sendPhoneCode(normalisePhone(phone))
            if (result is PhoneCodeRequest.Failed) _authError.value = result.reason
            result
        } finally {
            _authLoading.value = false
        }
    }

    /**
     * Confirms the SMS code and, for a brand-new account, writes the profile.
     *
     * The same call covers sign-up and sign-in: Firebase gives back an existing uid if
     * that number has been used before, so the profile is only written when there is no
     * user document yet. Overwriting would wipe a returning student's `shortCode` and
     * silently break every parent link pointing at it.
     */
    suspend fun verifyPhoneCode(
        verification: PhoneVerification,
        code: String,
        name: String,
        role: Role,
    ): Boolean {
        _authLoading.value = true
        _authError.value = null
        return try {
            when (val outcome = Platform.services.confirmPhoneCode(verification, code.trim())) {
                is PhoneVerification.Result.Failed -> {
                    _authError.value = outcome.reason
                    false
                }

                is PhoneVerification.Result.SignedIn -> {
                    val existing = runCatching { usersCol.document(outcome.uid).get() }.getOrNull()
                    if (existing?.exists != true) {
                        writeNewProfile(
                            uid = outcome.uid,
                            name = name,
                            email = "",
                            phone = outcome.phone.ifBlank { normalisePhone(verification.phone) },
                            role = role,
                        )
                    }
                    markHasAccount()
                    true
                }
            }
        } catch (e: Exception) {
            _authError.value = authMessage(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /**
     * Finishes a sign-up that Google Play validated without an SMS.
     *
     * Android can verify some SIMs instantly, which signs the user in before they are
     * ever shown a code field. The account exists at that point but the profile document
     * does not, and without one the app sits forever on the "profile still loading"
     * spinner.
     */
    suspend fun completeAutoVerifiedPhone(uid: String, phone: String, name: String, role: Role) {
        runCatching {
            val existing = usersCol.document(uid).get()
            if (!existing.exists) writeNewProfile(uid, name, email = "", phone = phone, role = role)
            markHasAccount()
        }.onFailure { _authError.value = authMessage(it as? Exception ?: Exception(it)) }
    }

    /** Trims spaces and dashes; leaves the caller's country prefix alone. */
    private fun normalisePhone(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        return when {
            cleaned.startsWith("+") -> cleaned
            // Local Philippine mobile format, the only one this school will type.
            cleaned.startsWith("09") && cleaned.length >= 11 -> "+63" + cleaned.drop(1)
            cleaned.startsWith("63") -> "+$cleaned"
            else -> cleaned
        }
    }

    private suspend fun writeNewProfile(
        uid: String,
        name: String,
        email: String,
        phone: String,
        role: Role,
    ) {
        usersCol.document(uid).set(
            UserDoc(
                name = name.trim(),
                email = email,
                phone = phone,
                role = role.wire,
                // Only students carry a linking code for parents to enter.
                shortCode = if (role == Role.STUDENT) newShortCode() else "",
            )
        )
    }

    /** Flips the app from "first run" to "returning user", so it opens on Login. */
    private fun markHasAccount() {
        if (!_settings.value.hasAccount) updateSettings { it.copy(hasAccount = true) }
    }

    suspend fun resetPassword(email: String): Boolean = try {
        auth.sendPasswordResetEmail(email.trim())
        notifyUi("Password reset link sent to ${email.trim()}")
        true
    } catch (e: Exception) {
        notifyUi(authMessage(e), isError = true)
        false
    }

    fun signOut() {
        scope.launch {
            // detachAll now empties the per-user flows as well, so signing out and
            // switching accounts clear the same way.
            detachAll()
            runCatching { auth.signOut() }
            _signedIn.value = false
            _user.value = null
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    private fun authMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("password is invalid", true) ||
                raw.contains("credential is incorrect", true) ||
                raw.contains("INVALID_LOGIN", true) -> "That email or password is not correct."

            raw.contains("no user record", true) ||
                raw.contains("USER_NOT_FOUND", true) -> "No account found for that email."

            raw.contains("already in use", true) ||
                raw.contains("EMAIL_EXISTS", true) -> "An account already uses that email."

            raw.contains("at least 6 characters", true) ||
                raw.contains("WEAK_PASSWORD", true) -> "Password must be at least 6 characters."

            raw.isBlank() -> "Sign-in failed. Check your connection and try again."
            else -> raw
        }
    }

    private fun newShortCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var seed = Platform.services.nowMillis()
        return buildString {
            repeat(6) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val idx = ((seed ushr 33).toInt() and 0x7FFFFFFF) % alphabet.length
                append(alphabet[idx])
            }
        }
    }

    // ----------------------------------------------------------- listeners

    private fun attachFor(uid: String) {
        detachAll()

        profileJob = scope.launch {
            usersCol.document(uid).snapshots
                .catch { reportListenerError("profile", it) }
                .collect { snap ->
                    if (!snap.exists) return@collect
                    val doc = snap.data(UserDoc.serializer())
                    val profile = UserProfile(
                        uid = uid,
                        name = doc.name,
                        email = doc.email,
                        phone = doc.phone,
                        role = Role.fromName(doc.role),
                        classId = doc.classId,
                        schoolId = doc.schoolId,
                        shortCode = doc.shortCode,
                        linkedStudentIds = doc.linkedStudentIds,
                    )
                    _user.value = profile
                    attachRosterMembers(profile)
                    attachLinkRequests(profile)
                    recomputeGuardians()
                }
        }

        alertsJob = scope.launch {
            alertsCol
                .orderBy("detectedAt", Direction.DESCENDING)
                .limit(ALERT_LIMIT)
                .snapshots
                .catch { reportListenerError("alerts", it) }
                .collect { query ->
                    val list = query.documents.mapNotNull { snap ->
                        runCatching {
                            val doc = snap.data(AlertDoc.serializer())
                            AlertRecord(
                                id = snap.id,
                                intensity = Intensity.fromName(doc.intensity),
                                magnitudeG = doc.magnitudeG,
                                detectedAt = doc.detectedAt.toMillis(),
                                source = AlertSource.fromName(doc.source),
                                nodeId = doc.nodeId,
                                closed = doc.closed,
                            )
                        }.getOrNull()
                    }
                    _alerts.value = list
                    _alertsLoaded.value = true

                    // Don't replay the whole backlog as "new" on first attach.
                    val newest = list.firstOrNull()
                    if (!alertsInitialised) {
                        alertsInitialised = true
                        lastIncomingId = newest?.id
                    } else if (newest != null && newest.id != lastIncomingId) {
                        lastIncomingId = newest.id
                        if (!newest.closed) {
                            // Green is informational only — a notification and one
                            // buzz, no full-screen takeover, no safety response owed.
                            // `startAlarm` already draws that line in the platform
                            // layer; this gate has to match it, or a Green event
                            // hijacks the whole screen for something nobody needs to
                            // act on. Yellow and Red still take over, as intended.
                            if (newest.intensity != Intensity.GREEN) {
                                _incomingAlert.value = newest
                            }
                            // Foreground path. The push path starts the alarm from
                            // SirenMessagingService instead.
                            Platform.services.startAlarm(
                                newest.id,
                                newest.intensity,
                                newest.magnitudeG,
                                _settings.value.vibration,
                            )
                        }
                    }
                    newest?.let { ensureRosterListener(it.id) }
                }
        }

        myRespJob = scope.launch {
            usersCol.document(uid).collection("responses").snapshots
                .catch { reportListenerError("responses", it) }
                .collect { query ->
                    _myResponses.value = query.documents.mapNotNull { snap ->
                        runCatching {
                            val doc = snap.data(ResponseDoc.serializer())
                            SafetyResponse(
                                alertId = doc.alertId.ifBlank { snap.id },
                                userId = uid,
                                name = doc.name,
                                status = ResponseStatus.fromName(doc.status),
                                respondedAt = doc.respondedAt.toMillis(),
                            )
                        }.getOrNull()
                    }.associateBy { it.alertId }
                }
        }
    }

    /** Teachers watch their class; parents watch their linked children. */
    private fun attachRosterMembers(profile: UserProfile) {
        rosterMembersJob?.cancel()
        rosterMembersJob = null

        when (profile.role) {
            Role.TEACHER -> {
                if (profile.classId.isBlank()) {
                    rosterMembers = emptyList()
                    recomputeRoster()
                    return
                }
                rosterMembersJob = scope.launch {
                    usersCol
                        .where { "classId" equalTo profile.classId }
                        .snapshots
                        .catch { reportListenerError("roster", it) }
                        .collect { query ->
                            rosterMembers = query.documents.mapNotNull { snap ->
                                runCatching {
                                    val doc = snap.data(UserDoc.serializer())
                                    if (Role.fromName(doc.role) != Role.STUDENT) return@runCatching null
                                    UserProfile(
                                        uid = snap.id,
                                        name = doc.name,
                                        role = Role.STUDENT,
                                        classId = doc.classId,
                                    )
                                }.getOrNull()
                            }
                            recomputeRoster()
                        }
                }
            }

            Role.PARENT -> {
                val ids = profile.linkedStudentIds
                if (ids.isEmpty()) {
                    rosterMembers = emptyList()
                    recomputeRoster()
                    return
                }
                // One document flow per child, combined — avoids needing a whereIn
                // query on document ids (and the composite index that implies).
                rosterMembersJob = scope.launch {
                    combine(ids.map { id -> usersCol.document(id).snapshots }) { snaps ->
                        snaps.mapIndexedNotNull { i, snap ->
                            if (!snap.exists) null
                            else runCatching {
                                val doc = snap.data(UserDoc.serializer())
                                UserProfile(
                                    uid = ids[i],
                                    name = doc.name,
                                    role = Role.STUDENT,
                                    classId = doc.classId,
                                )
                            }.getOrNull()
                        }
                    }
                        .catch { reportListenerError("linked students", it) }
                        .collect {
                            rosterMembers = it
                            recomputeRoster()
                        }
                }
            }

            Role.STUDENT -> {
                rosterMembers = emptyList()
                recomputeRoster()
            }
        }
    }

    /**
     * Guardian link requests, watched from whichever end the signed-in user is on.
     *
     * One equality filter each way, deliberately: `studentId ==` for a student and
     * `parentId ==` for a parent. Adding a second filter for `status` would be the
     * obvious thing to do and would drag a composite index in behind it, so status is
     * filtered client-side instead — these lists are a handful of documents at most.
     */
    private fun attachLinkRequests(profile: UserProfile) {
        // The profile snapshot fires on every field change; only re-subscribe when the
        // side of the relationship actually changed, or a role switch would rebuild the
        // listener on every keystroke of a name edit.
        if (currentLinkRole == profile.role && linkRequestJob != null) return
        currentLinkRole = profile.role
        linkRequestJob?.cancel()
        linkRequestJob = null

        val field = when (profile.role) {
            Role.STUDENT -> "studentId"
            Role.PARENT -> "parentId"
            // Advisers add students to their class directly; they raise no requests.
            Role.TEACHER -> {
                _linkRequests.value = emptyList()
                recomputeGuardians()
                return
            }
        }

        linkRequestJob = scope.launch {
            linkRequestsCol
                .where { field equalTo profile.uid }
                .snapshots
                .catch { reportListenerError("guardian links", it) }
                .collect { query ->
                    val list = query.documents.mapNotNull { snap ->
                        runCatching {
                            val doc = snap.data(LinkRequestDoc.serializer())
                            LinkRequest(
                                id = snap.id,
                                studentId = doc.studentId,
                                studentName = doc.studentName,
                                parentId = doc.parentId,
                                parentName = doc.parentName,
                                parentContact = doc.parentContact,
                                status = LinkRequestStatus.fromName(doc.status),
                                requestedAt = doc.requestedAt.toMillis(),
                                respondedAt = doc.respondedAt?.toMillis(),
                            )
                        }.getOrNull()
                    }.sortedByDescending { it.requestedAt }

                    _linkRequests.value = list
                    if (profile.role == Role.PARENT) adoptApprovedLinks(list)
                    recomputeGuardians()
                }
        }
    }

    /**
     * Mirrors approved requests into the parent's own `linkedStudentIds`.
     *
     * The student is the one who approves, but Firestore only lets each user write their
     * own document, so the parent's client is what actually commits the link once it
     * sees the approval. A decline (or a later revocation) removes it again.
     */
    private suspend fun adoptApprovedLinks(requests: List<LinkRequest>) {
        val uid = auth.currentUser?.uid ?: return
        val current = _user.value?.linkedStudentIds.orEmpty().toSet()
        val approved = requests.filter { it.status == LinkRequestStatus.APPROVED }.map { it.studentId }
        val refused = requests.filter { it.status != LinkRequestStatus.APPROVED }.map { it.studentId }

        val target = current + approved - refused.toSet()
        if (target == current) return
        runCatching { usersCol.document(uid).update("linkedStudentIds" to target.toList()) }
            .onFailure { notifyUi("Couldn't update your linked students: ${it.message}", true) }
    }

    /**
     * The student's view of who is following them, carrying each guardian's own safety
     * status for the event currently being tracked.
     */
    private fun recomputeGuardians() {
        val profile = _user.value
        if (profile == null || profile.role != Role.STUDENT) {
            _guardians.value = emptyList()
            return
        }
        val byUid = rosterResponses.associateBy { it.userId }
        _guardians.value = _linkRequests.value
            .filter { it.status == LinkRequestStatus.APPROVED }
            .map { req ->
                val r = byUid[req.parentId]
                LinkedPerson(
                    uid = req.parentId,
                    name = req.parentName.ifBlank { "Parent / Guardian" },
                    klass = "Parent / Guardian",
                    status = r?.status ?: ResponseStatus.NO_RESPONSE,
                    respondedAt = r?.respondedAt,
                )
            }
            .sortedBy { it.name }
    }

    /** Responses for the alert currently being tracked on the live dashboard. */
    private fun ensureRosterListener(alertId: String) {
        if (currentRosterAlertId == alertId && rosterRespJob != null) return
        currentRosterAlertId = alertId
        rosterRespJob?.cancel()
        rosterRespJob = scope.launch {
            alertsCol.document(alertId).collection("responses").snapshots
                .catch { reportListenerError("live status", it) }
                .collect { query ->
                    rosterResponses = query.documents.mapNotNull { snap ->
                        runCatching {
                            val doc = snap.data(ResponseDoc.serializer())
                            SafetyResponse(
                                alertId = alertId,
                                userId = snap.id,
                                name = doc.name,
                                status = ResponseStatus.fromName(doc.status),
                                respondedAt = doc.respondedAt.toMillis(),
                            )
                        }.getOrNull()
                    }
                    recomputeRoster()
                    // Guardians read their status out of the same snapshot, which is how
                    // a student sees "Mum confirmed safe" during an event.
                    recomputeGuardians()
                }
        }
    }

    /** Needs-help first, then no-reply, then safe — the order the dashboard requires. */
    private fun recomputeRoster() {
        val byUid = rosterResponses.associateBy { it.userId }
        _roster.value = rosterMembers
            .map { member ->
                val r = byUid[member.uid]
                LinkedPerson(
                    uid = member.uid,
                    name = member.name,
                    klass = member.classId,
                    status = r?.status ?: ResponseStatus.NO_RESPONSE,
                    respondedAt = r?.respondedAt,
                )
            }
            .sortedWith(
                compareBy(
                    {
                        when (it.status) {
                            ResponseStatus.NEEDS_HELP -> 0
                            ResponseStatus.NO_RESPONSE -> 1
                            ResponseStatus.SAFE -> 2
                        }
                    },
                    { it.name },
                )
            )
    }

    /**
     * Cancels every listener **and empties the state they fed**.
     *
     * The emptying is not optional. `attachFor` calls this when the signed-in uid
     * changes, so without it a second account signing in on the same phone sees the
     * previous user's roster, their safety responses and even their live full-screen
     * alert, until each listener happens to deliver its first snapshot. On a shared
     * school handset that is one student's status shown under another's name.
     */
    private fun detachAll() {
        profileJob?.cancel(); profileJob = null
        alertsJob?.cancel(); alertsJob = null
        myRespJob?.cancel(); myRespJob = null
        rosterMembersJob?.cancel(); rosterMembersJob = null
        rosterRespJob?.cancel(); rosterRespJob = null
        linkRequestJob?.cancel(); linkRequestJob = null
        currentRosterAlertId = null
        currentLinkRole = null
        alertsInitialised = false
        _alertsLoaded.value = false
        lastIncomingId = null
        rosterMembers = emptyList()
        rosterResponses = emptyList()

        _alerts.value = emptyList()
        _myResponses.value = emptyMap()
        _roster.value = emptyList()
        _guardians.value = emptyList()
        _linkRequests.value = emptyList()
        _incomingAlert.value = null
    }

    private fun reportListenerError(what: String, err: Throwable) {
        notifyUi("Couldn't sync $what. Check your connection.", isError = true)
    }

    // -------------------------------------------------------------- writes

    /**
     * Changes the signed-in user's role.
     *
     * This reverses the v2.x decision to freeze the role at sign-up: the app told users
     * their role could be changed later and Settings had nowhere to do it, so the
     * promise was simply broken. Switching *to* Student mints a linking code if the
     * account has never had one — without it a student is invisible to parents and to
     * their adviser, and the roster silently stays empty.
     */
    suspend fun updateRole(role: Role): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val current = _user.value
        if (current?.role == role) return true
        _working.value = true
        return try {
            if (role == Role.STUDENT && current?.shortCode.isNullOrBlank()) {
                usersCol.document(uid).update("role" to role.wire, "shortCode" to newShortCode())
            } else {
                usersCol.document(uid).update("role" to role.wire)
            }
            notifyUi("You are now signed in as ${role.label}.")
            true
        } catch (e: Exception) {
            notifyUi("Couldn't change your role: ${e.message}", true)
            false
        } finally {
            _working.value = false
        }
    }

    /**
     * Corrects the profile — a misspelt name at sign-up used to be permanent, and a
     * misspelt name is what a teacher reads off the roll call during an evacuation.
     */
    suspend fun updateProfile(name: String, classId: String, phone: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        _working.value = true
        return try {
            usersCol.document(uid).update(
                "name" to name.trim(),
                "classId" to classId.trim(),
                "phone" to normalisePhone(phone),
            )
            notifyUi("Profile updated.")
            true
        } catch (e: Exception) {
            notifyUi("Couldn't save your profile: ${e.message}", true)
            false
        } finally {
            _working.value = false
        }
    }

    /**
     * A parent asks to follow a student by typing the code from the student's Settings.
     *
     * This raises a request rather than completing the link. The code is six characters
     * and gets read aloud across a classroom; anyone who overhears one could otherwise
     * attach themselves to that student's live safety feed with the student never being
     * told. The student confirms, and only then does [adoptApprovedLinks] commit it.
     */
    suspend fun requestLink(code: String): LinkResult {
        val uid = auth.currentUser?.uid ?: return LinkResult.Failed("You are signed out.")
        val me = _user.value ?: return LinkResult.Failed("Your profile is still loading.")
        val entered = code.trim().uppercase()
        if (entered.isEmpty()) return LinkResult.NotFound
        _working.value = true
        return try {
            val match = findStudentByCode(entered) ?: return LinkResult.NotFound
            val studentName = match.second.name.ifBlank { "Student" }

            if (me.linkedStudentIds.contains(match.first)) return LinkResult.AlreadyLinked

            val outstanding = _linkRequests.value.firstOrNull {
                it.studentId == match.first && it.parentId == uid && it.status == LinkRequestStatus.PENDING
            }
            if (outstanding != null) return LinkResult.AlreadyRequested(studentName)

            // Deterministic id: re-asking after a decline overwrites the old request
            // instead of piling up a second one the student has to dismiss twice.
            linkRequestsCol.document("${match.first}_$uid").set(
                LinkRequestDoc(
                    studentId = match.first,
                    studentName = studentName,
                    parentId = uid,
                    parentName = me.name.ifBlank { "Parent / Guardian" },
                    parentContact = me.contact,
                    status = LinkRequestStatus.PENDING.wire,
                    requestedAt = Timestamp.now(),
                    respondedAt = null,
                )
            )
            LinkResult.Requested(studentName)
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: "Linking failed.")
        } finally {
            _working.value = false
        }
    }

    /** The student's answer to "is this person really your parent or guardian?". */
    suspend fun respondToLinkRequest(requestId: String, approve: Boolean): Boolean {
        _working.value = true
        return try {
            linkRequestsCol.document(requestId).update(
                "status" to if (approve) LinkRequestStatus.APPROVED.wire else LinkRequestStatus.DECLINED.wire,
                "respondedAt" to Timestamp.now(),
            )
            notifyUi(if (approve) "Guardian confirmed." else "Request declined.")
            true
        } catch (e: Exception) {
            notifyUi("Couldn't answer that request: ${e.message}", true)
            false
        } finally {
            _working.value = false
        }
    }

    /**
     * An adviser adds a student to their class by typing the student's code.
     *
     * The roster is driven entirely by `classId`, and nothing in the app ever wrote one
     * — every teacher account shipped with a blank class and therefore an empty roll
     * call, with no way to fix it from inside the app. This is that missing half.
     */
    suspend fun linkStudentToClass(code: String): LinkResult {
        auth.currentUser?.uid ?: return LinkResult.Failed("You are signed out.")
        val me = _user.value ?: return LinkResult.Failed("Your profile is still loading.")
        if (me.classId.isBlank()) {
            return LinkResult.Failed("Set your class name in Settings → Edit profile first.")
        }
        val entered = code.trim().uppercase()
        if (entered.isEmpty()) return LinkResult.NotFound
        _working.value = true
        return try {
            val match = findStudentByCode(entered) ?: return LinkResult.NotFound
            val (studentId, doc) = match
            if (doc.classId == me.classId) return LinkResult.AlreadyLinked

            usersCol.document(studentId).update("classId" to me.classId)
            LinkResult.Success(doc.name.ifBlank { "Student" })
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: "Couldn't add that student.")
        } finally {
            _working.value = false
        }
    }

    /** Removes a student from this adviser's class. */
    fun removeStudentFromClass(studentUid: String) {
        scope.launch {
            runCatching { usersCol.document(studentUid).update("classId" to "") }
                .onFailure { notifyUi("Couldn't remove that student: ${it.message}", true) }
        }
    }

    private suspend fun findStudentByCode(code: String): Pair<String, UserDoc>? {
        val snap = usersCol.where { "shortCode" equalTo code }.get()
        return snap.documents.firstNotNullOfOrNull { doc ->
            runCatching {
                val parsed = doc.data(UserDoc.serializer())
                if (Role.fromName(parsed.role) == Role.STUDENT) doc.id to parsed else null
            }.getOrNull()
        }
    }

    /**
     * Drops a guardian link from the parent's side. The request document goes with it,
     * so the student's list stops showing somebody who is no longer following them.
     */
    fun unlinkStudent(studentUid: String) {
        val uid = auth.currentUser?.uid ?: return
        val updated = _user.value?.linkedStudentIds.orEmpty().filterNot { it == studentUid }
        scope.launch {
            runCatching {
                usersCol.document(uid).update("linkedStudentIds" to updated)
                linkRequestsCol.document("${studentUid}_$uid").delete()
            }.onFailure { notifyUi("Couldn't unlink: ${it.message}", true) }
        }
    }

    /** Revokes a guardian from the student's side. */
    fun revokeGuardian(parentUid: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                linkRequestsCol.document("${uid}_$parentUid").update(
                    "status" to LinkRequestStatus.DECLINED.wire,
                    "respondedAt" to Timestamp.now(),
                )
                notifyUi("Guardian removed.")
            }.onFailure { notifyUi("Couldn't remove that guardian: ${it.message}", true) }
        }
    }

    /**
     * Demo mode. Simulated events are tagged so they stay visibly separate from real
     * sensor readings in the history and in the paper's evaluation data.
     */
    fun simulateAlert(intensity: Intensity) {
        // Each value must sit inside its own band in Intensity.fromMagnitude. These
        // moved when the bands were rebased on intensity levels — the old 0.22 / 0.48
        // / 0.74 all land in RED under the current thresholds, which would have made
        // every simulation fire the full alarm.
        val magnitude = when (intensity) {
            Intensity.GREEN -> 0.005
            Intensity.YELLOW -> 0.050
            Intensity.RED -> 0.300
        }
        scope.launch {
            runCatching {
                alertsCol.add(
                    AlertDoc(
                        intensity = intensity.wire,
                        magnitudeG = magnitude,
                        detectedAt = Timestamp.now(),
                        source = AlertSource.SIMULATED.wire,
                        nodeId = "SIMULATOR",
                        closed = false,
                    )
                )
            }.onFailure { notifyUi("Couldn't start the simulation: ${it.message}", true) }
        }
    }

    fun submitMyResponse(alertId: String, status: ResponseStatus) {
        // Responding is one of the three things that silences the alarm.
        Platform.services.stopAlarm()
        val uid = auth.currentUser?.uid ?: return
        val payload = ResponseDoc(
            userId = uid,
            name = _user.value?.name.orEmpty(),
            status = status.wire,
            respondedAt = Timestamp.now(),
            alertId = alertId,
        )
        scope.launch {
            runCatching {
                // Written to the alert (for the live dashboard) and mirrored under the
                // user (so History works without a collection-group index).
                alertsCol.document(alertId).collection("responses").document(uid).set(payload)
                usersCol.document(uid).collection("responses").document(alertId).set(payload)
            }.onFailure { notifyUi("Couldn't record your response: ${it.message}", true) }
        }
    }

    fun closeEvent(alertId: String) {
        scope.launch {
            runCatching { alertsCol.document(alertId).update("closed" to true) }
                .onFailure { notifyUi("Couldn't close the event: ${it.message}", true) }
        }
    }

    fun showAlertById(alertId: String) {
        scope.launch {
            _alerts.value.firstOrNull { it.id == alertId }?.let {
                _incomingAlert.value = it
                return@launch
            }
            runCatching {
                val snap = alertsCol.document(alertId).get()
                if (!snap.exists) return@runCatching
                val doc = snap.data(AlertDoc.serializer())
                _incomingAlert.value = AlertRecord(
                    id = snap.id,
                    intensity = Intensity.fromName(doc.intensity),
                    magnitudeG = doc.magnitudeG,
                    detectedAt = doc.detectedAt.toMillis(),
                    source = AlertSource.fromName(doc.source),
                    nodeId = doc.nodeId,
                    closed = doc.closed,
                )
            }
        }
    }

    fun consumeIncomingAlert() {
        Platform.services.stopAlarm()
        Platform.services.clearNotifications()
        _incomingAlert.value = null
    }

    // ------------------------------------------------------------ settings

    private fun loadSettings() {
        val raw = Platform.services.readSettingsJson()
        if (raw == null) {
            // Fresh install — SirenSettings() already carries the official Bogo
            // responder numbers. Persist now so the seeded flag is recorded.
            persistSettings(_settings.value)
            return
        }
        runCatching {
            val doc = json.decodeFromString(SettingsDoc.serializer(), raw)
            val stored = doc.toModel()
            if (doc.seededDefaults) {
                _settings.value = stored
            } else {
                // Install that predates the official numbers: add only the ones it has
                // never seen, then record that we have. After this a deleted default
                // stays deleted rather than reappearing on every launch.
                val known = stored.contacts.map { it.id }.toSet()
                val merged = stored.copy(
                    contacts = DefaultEmergencyContacts.filterNot { it.id in known } + stored.contacts,
                )
                _settings.value = merged
                persistSettings(merged)
            }
        }
    }

    private fun persistSettings(settings: SirenSettings) {
        runCatching {
            Platform.services.writeSettingsJson(
                json.encodeToString(SettingsDoc.serializer(), SettingsDoc.from(settings))
            )
        }
    }

    fun updateSettings(transform: (SirenSettings) -> SirenSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persistSettings(updated)
    }

    /** Re-adds any official responder number the user has removed. */
    fun restoreDefaultContacts() = updateSettings { s ->
        val known = s.contacts.map { it.id }.toSet()
        s.copy(contacts = DefaultEmergencyContacts.filterNot { it.id in known } + s.contacts)
    }

    fun addEmergencyContact(contact: EmergencyContact) =
        updateSettings { it.copy(contacts = it.contacts + contact) }

    fun removeEmergencyContact(id: String) =
        updateSettings { s -> s.copy(contacts = s.contacts.filterNot { it.id == id }) }

    fun setOnline(value: Boolean) {
        _online.value = value
    }

    private fun notifyUi(text: String, isError: Boolean = false) {
        _events.tryEmit(UiMessage(text, isError))
    }
}

// ------------------------------------------------------------------- DTOs

private fun Timestamp?.toMillis(): Long =
    this?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L }
        ?: Platform.services.nowMillis()

@Serializable
internal data class UserDoc(
    val name: String = "",
    val email: String = "",
    /** Empty for email accounts; E.164 for phone sign-ups. */
    val phone: String = "",
    val role: String = "student",
    val classId: String = "",
    val schoolId: String = "",
    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),
)

/**
 * A guardian link awaiting the student's confirmation.
 *
 * Top-level with `studentId` and `parentId` denormalised onto it so each side can watch
 * its own view with a single equality filter and no composite index. The document id is
 * always `"{studentId}_{parentId}"`, which makes the relationship unique by construction
 * — re-requesting after a decline overwrites rather than duplicating.
 */
@Serializable
internal data class LinkRequestDoc(
    val studentId: String = "",
    val studentName: String = "",
    val parentId: String = "",
    val parentName: String = "",
    val parentContact: String = "",
    val status: String = "pending",
    val requestedAt: Timestamp? = null,
    val respondedAt: Timestamp? = null,
)

@Serializable
internal data class AlertDoc(
    val intensity: String = "green",
    val magnitudeG: Double = 0.0,
    val detectedAt: Timestamp? = null,
    val source: String = "esp32",
    val nodeId: String? = null,
    val closed: Boolean = false,
)

@Serializable
internal data class ResponseDoc(
    val userId: String = "",
    val name: String = "",
    val status: String = "no_response",
    val respondedAt: Timestamp? = null,
    val alertId: String = "",
)

/**
 * `darkMode` used to live here. It is gone with the dark scheme, and the stored JSON
 * on existing installs still carries the key — `ignoreUnknownKeys = true` on [json]
 * is what lets those settings keep loading instead of resetting and wiping the user's
 * saved emergency contacts. Do not tighten that flag.
 */
@Serializable
internal data class SettingsDoc(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val contacts: List<ContactDoc> = emptyList(),
    /**
     * Records that the official responder numbers have been added once. Without it,
     * a user who deliberately deletes one would get it back on the next launch.
     */
    val seededDefaults: Boolean = false,
    /**
     * Set the first time an account is created or signed into on this device, which is
     * what flips the opening screen from Create Account back to Login.
     *
     * Defaults to false, so an install that predates this field opens on Create Account
     * once. That is the right way round: the alternative is a returning user seeing a
     * sign-up form, which is confusing but harmless, versus a brand-new user being asked
     * to sign in with credentials they do not have.
     */
    val hasAccount: Boolean = false,
) {
    fun toModel() = SirenSettings(
        criticalAlerts = criticalAlerts,
        vibration = vibration,
        contacts = contacts.map { EmergencyContact(it.id, it.name, it.relation, it.phone, it.primary) },
        hasAccount = hasAccount,
    )

    companion object {
        fun from(s: SirenSettings) = SettingsDoc(
            criticalAlerts = s.criticalAlerts,
            vibration = s.vibration,
            contacts = s.contacts.map { ContactDoc(it.id, it.name, it.relation, it.phone, it.primary) },
            seededDefaults = true,
            hasAccount = s.hasAccount,
        )
    }
}

@Serializable
internal data class ContactDoc(
    val id: String = "",
    val name: String = "",
    val relation: String = "",
    val phone: String = "",
    val primary: Boolean = false,
)
