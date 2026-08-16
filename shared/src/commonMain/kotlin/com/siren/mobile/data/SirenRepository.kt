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

object SirenRepository {

    private const val ALERT_LIMIT = 100

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

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertRecord>>(emptyList())
    val alerts: StateFlow<List<AlertRecord>> = _alerts.asStateFlow()

    private val _alertsLoaded = MutableStateFlow(false)
    val alertsLoaded: StateFlow<Boolean> = _alertsLoaded.asStateFlow()

    private val _myResponses = MutableStateFlow<Map<String, SafetyResponse>>(emptyMap())
    val myResponses: StateFlow<Map<String, SafetyResponse>> = _myResponses.asStateFlow()

    private val _roster = MutableStateFlow<List<LinkedPerson>>(emptyList())
    val roster: StateFlow<List<LinkedPerson>> = _roster.asStateFlow()

    private val _guardians = MutableStateFlow<List<LinkedPerson>>(emptyList())
    val guardians: StateFlow<List<LinkedPerson>> = _guardians.asStateFlow()

    private val _linkRequests = MutableStateFlow<List<LinkRequest>>(emptyList())
    val linkRequests: StateFlow<List<LinkRequest>> = _linkRequests.asStateFlow()

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

    fun start() {

        loadSettings()
        runCatching { Platform.services.subscribeToAlertsTopic() }

        scope.launch {

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

    suspend fun completeAutoVerifiedPhone(uid: String, phone: String, name: String, role: Role) {
        runCatching {
            val existing = usersCol.document(uid).get()
            if (!existing.exists) writeNewProfile(uid, name, email = "", phone = phone, role = role)
            markHasAccount()
        }.onFailure { _authError.value = authMessage(it as? Exception ?: Exception(it)) }
    }

    private fun normalisePhone(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        return when {
            cleaned.startsWith("+") -> cleaned

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

                shortCode = if (role == Role.STUDENT) newShortCode() else "",
            )
        )
    }

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
                        photo = doc.photo,
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

                    val newest = list.firstOrNull()
                    if (!alertsInitialised) {
                        alertsInitialised = true
                        lastIncomingId = newest?.id
                    } else if (newest != null && newest.id != lastIncomingId) {
                        lastIncomingId = newest.id
                        if (!newest.closed) {

                            if (newest.intensity != Intensity.GREEN) {
                                _incomingAlert.value = newest
                            }

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
                                        photo = doc.photo,
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
                                    photo = doc.photo,
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

    private fun attachLinkRequests(profile: UserProfile) {

        if (currentLinkRole == profile.role && linkRequestJob != null) return
        currentLinkRole = profile.role
        linkRequestJob?.cancel()
        linkRequestJob = null

        val field = when (profile.role) {
            Role.STUDENT -> "studentId"
            Role.PARENT -> "parentId"

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

                    recomputeGuardians()
                }
        }
    }

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
                    photo = member.photo,
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

    suspend fun changeProfilePhoto(remove: Boolean = false): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        _working.value = true
        return try {
            val encoded = if (remove) "" else Platform.services.pickProfilePhoto() ?: return false
            usersCol.document(uid).update("photo" to encoded)
            notifyUi(if (remove) "Profile picture removed." else "Profile picture updated.")
            true
        } catch (e: Exception) {
            notifyUi("Couldn't update your picture: ${e.message}", true)
            false
        } finally {
            _working.value = false
        }
    }

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

    fun simulateAlert(intensity: Intensity) {

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

    /**
     * Paints the alert from the push payload itself, then lets [showAlertById] refine it.
     *
     * [showAlertById] alone needs a Firestore read, and the moment that matters most — a
     * phone woken from a locked, dark and quite possibly offline state — is exactly when
     * that read is slowest, or never returns at all. The alarm would be sounding behind an
     * empty screen. The push already carries everything the alert screen draws, so it goes
     * up immediately and the stored document corrects it if and when it arrives.
     *
     * `detectedAt` is the arrival time rather than the detection time; the Firestore copy
     * carries the real one and overwrites this within a second on a healthy connection.
     */
    fun showAlertFromPush(alertId: String, intensity: Intensity, magnitudeG: Double) {
        if (_incomingAlert.value?.id != alertId) {
            _incomingAlert.value = AlertRecord(
                id = alertId,
                intensity = intensity,
                magnitudeG = magnitudeG,
                detectedAt = Platform.services.nowMillis(),
                source = AlertSource.ESP32,
            )
        }
        showAlertById(alertId)
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

    private fun loadSettings() {
        val raw = Platform.services.readSettingsJson()
        if (raw == null) {

            persistSettings(_settings.value)
            return
        }
        runCatching {
            val doc = json.decodeFromString(SettingsDoc.serializer(), raw)
            val stored = doc.toModel()
            if (doc.seededDefaults) {
                _settings.value = stored
            } else {

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

private fun Timestamp?.toMillis(): Long =
    this?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L }
        ?: Platform.services.nowMillis()

@Serializable
internal data class UserDoc(
    val name: String = "",
    val email: String = "",

    val phone: String = "",
    val role: String = "student",
    val classId: String = "",
    val schoolId: String = "",
    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),

    val photo: String = "",
)

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

@Serializable
internal data class SettingsDoc(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val contacts: List<ContactDoc> = emptyList(),

    val seededDefaults: Boolean = false,

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
