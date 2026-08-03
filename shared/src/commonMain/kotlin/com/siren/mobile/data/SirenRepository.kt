package com.siren.mobile.data

import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.EmergencyContact
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.Role
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.model.SirenSettings
import com.siren.mobile.model.UserProfile
import com.siren.mobile.platform.Platform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
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

    private val scope = CoroutineScope(SupervisorJob())
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    private val usersCol get() = db.collection("users")
    private val alertsCol get() = db.collection("alerts")

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

    private var currentRosterAlertId: String? = null
    private var alertsInitialised = false
    private var lastIncomingId: String? = null

    /** Called once at start-up, after Platform.install(). */
    fun start() {
        scope.launch { loadSettings() }
        Platform.services.subscribeToAlertsTopic()

        scope.launch {
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
        }
    }

    // ------------------------------------------------------------ auth API

    suspend fun signIn(email: String, password: String): Boolean {
        _authLoading.value = true
        _authError.value = null
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password)
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
            usersCol.document(uid).set(
                UserDoc(
                    name = name.trim(),
                    email = email.trim(),
                    role = role.wire,
                    // Only students carry a linking code for parents to enter.
                    shortCode = if (role == Role.STUDENT) newShortCode() else "",
                )
            )
            true
        } catch (e: Exception) {
            _authError.value = authMessage(e)
            false
        } finally {
            _authLoading.value = false
        }
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
            detachAll()
            runCatching { auth.signOut() }
            _signedIn.value = false
            _user.value = null
            _alerts.value = emptyList()
            _myResponses.value = emptyMap()
            _roster.value = emptyList()
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
                        role = Role.fromName(doc.role),
                        classId = doc.classId,
                        schoolId = doc.schoolId,
                        shortCode = doc.shortCode,
                        linkedStudentIds = doc.linkedStudentIds,
                    )
                    _user.value = profile
                    attachRosterMembers(profile)
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
                            _incomingAlert.value = newest
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

    private fun detachAll() {
        profileJob?.cancel(); profileJob = null
        alertsJob?.cancel(); alertsJob = null
        myRespJob?.cancel(); myRespJob = null
        rosterMembersJob?.cancel(); rosterMembersJob = null
        rosterRespJob?.cancel(); rosterRespJob = null
        currentRosterAlertId = null
        alertsInitialised = false
        _alertsLoaded.value = false
        lastIncomingId = null
        rosterMembers = emptyList()
        rosterResponses = emptyList()
    }

    private fun reportListenerError(what: String, err: Throwable) {
        notifyUi("Couldn't sync $what. Check your connection.", isError = true)
    }

    // -------------------------------------------------------------- writes

    fun updateRole(role: Role) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching { usersCol.document(uid).update("role" to role.wire) }
                .onFailure { notifyUi("Couldn't change your role: ${it.message}", true) }
        }
    }

    /** A parent links a student by typing the code the registrar issued. */
    suspend fun linkStudent(code: String): LinkResult {
        val uid = auth.currentUser?.uid ?: return LinkResult.Failed("You are signed out.")
        val entered = code.trim().uppercase()
        if (entered.isEmpty()) return LinkResult.NotFound
        return try {
            val snap = usersCol.where { "shortCode" equalTo entered }.get()
            val match = snap.documents.firstOrNull { doc ->
                runCatching { Role.fromName(doc.data(UserDoc.serializer()).role) == Role.STUDENT }
                    .getOrDefault(false)
            } ?: return LinkResult.NotFound

            if (_user.value?.linkedStudentIds?.contains(match.id) == true) return LinkResult.AlreadyLinked

            val updated = (_user.value?.linkedStudentIds.orEmpty() + match.id).distinct()
            usersCol.document(uid).update("linkedStudentIds" to updated)
            LinkResult.Success(match.data(UserDoc.serializer()).name.ifBlank { "Student" })
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: "Linking failed.")
        }
    }

    fun unlinkStudent(studentUid: String) {
        val uid = auth.currentUser?.uid ?: return
        val updated = _user.value?.linkedStudentIds.orEmpty().filterNot { it == studentUid }
        scope.launch {
            runCatching { usersCol.document(uid).update("linkedStudentIds" to updated) }
                .onFailure { notifyUi("Couldn't unlink: ${it.message}", true) }
        }
    }

    /**
     * Demo mode. Simulated events are tagged so they stay visibly separate from real
     * sensor readings in the history and in the paper's evaluation data.
     */
    fun simulateAlert(intensity: Intensity) {
        val magnitude = when (intensity) {
            Intensity.GREEN -> 0.22
            Intensity.YELLOW -> 0.48
            Intensity.RED -> 0.74
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
        val raw = Platform.services.readSettingsJson() ?: return
        runCatching { _settings.value = json.decodeFromString(SettingsDoc.serializer(), raw).toModel() }
    }

    fun updateSettings(transform: (SirenSettings) -> SirenSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching {
            Platform.services.writeSettingsJson(
                json.encodeToString(SettingsDoc.serializer(), SettingsDoc.from(updated))
            )
        }
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
    val role: String = "student",
    val classId: String = "",
    val schoolId: String = "",
    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),
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

@Serializable
internal data class SettingsDoc(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val darkMode: Boolean = false,
    val contacts: List<ContactDoc> = emptyList(),
) {
    fun toModel() = SirenSettings(
        criticalAlerts = criticalAlerts,
        vibration = vibration,
        darkMode = darkMode,
        contacts = contacts.map { EmergencyContact(it.id, it.name, it.relation, it.phone, it.primary) },
    )

    companion object {
        fun from(s: SirenSettings) = SettingsDoc(
            criticalAlerts = s.criticalAlerts,
            vibration = s.vibration,
            darkMode = s.darkMode,
            contacts = s.contacts.map { ContactDoc(it.id, it.name, it.relation, it.phone, it.primary) },
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
