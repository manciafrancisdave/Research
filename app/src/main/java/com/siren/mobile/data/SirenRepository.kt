package com.siren.mobile.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val Context.settingsStore by preferencesDataStore(name = "siren_settings")
private val SETTINGS_KEY = stringPreferencesKey("settings_json")

/**
 * Single source of truth for auth, Firestore data and local settings.
 *
 * Authentication is Firebase Email/Password. The ESP32 firmware signs in with its own
 * dedicated account and writes alert documents directly, so the app only ever listens.
 *
 * Firestore's on-device cache is enabled by default on Android, which is what gives us
 * offline queueing: a safety confirmation written with no connectivity is persisted
 * locally and replayed automatically once the device is back online.
 */
class SirenRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "SirenRepository"
        private const val ALERT_TOPIC = "alerts"
        private const val ALERT_LIMIT = 100L

        @Volatile
        private var instance: SirenRepository? = null

        fun get(context: Context): SirenRepository =
            instance ?: synchronized(this) {
                instance ?: SirenRepository(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val usersCol get() = db.collection("users")
    private val alertsCol get() = db.collection("alerts")

    // ---------------------------------------------------------------- state

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    /** Distinguishes "signed out" from "signed in, profile still loading". */
    private val _signedIn = MutableStateFlow(auth.currentUser != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertRecord>>(emptyList())
    val alerts: StateFlow<List<AlertRecord>> = _alerts.asStateFlow()

    /** This user's own responses, keyed by alert id. */
    private val _myResponses = MutableStateFlow<Map<String, SafetyResponse>>(emptyMap())
    val myResponses: StateFlow<Map<String, SafetyResponse>> = _myResponses.asStateFlow()

    /** Roster for teachers (their class) or parents (their linked children). */
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

    /** False until the first auth state callback lands, so the splash knows when to leave. */
    private val _authResolved = MutableStateFlow(false)
    val authResolved: StateFlow<Boolean> = _authResolved.asStateFlow()

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    // ------------------------------------------------------- internal state

    private var rosterMembers: List<UserProfile> = emptyList()
    private var rosterResponses: List<SafetyResponse> = emptyList()

    private var userReg: ListenerRegistration? = null
    private var alertsReg: ListenerRegistration? = null
    private var myRespReg: ListenerRegistration? = null
    private var rosterMembersReg: ListenerRegistration? = null
    private var rosterRespReg: ListenerRegistration? = null

    private var currentRosterAlertId: String? = null
    private var alertsInitialized = false
    private var lastIncomingId: String? = null

    init {
        auth.addAuthStateListener { fb ->
            val uid = fb.currentUser?.uid
            _signedIn.value = uid != null
            if (uid == null) {
                detachAll()
                _user.value = null
            } else {
                attachFor(uid)
            }
            _authResolved.value = true
        }
        scope.launch { loadSettings() }
        watchConnectivity()
        FirebaseMessaging.getInstance().subscribeToTopic(ALERT_TOPIC)
    }

    // ------------------------------------------------------------ auth API

    suspend fun signIn(email: String, password: String): Boolean {
        _authLoading.value = true
        _authError.value = null
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
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
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user?.uid ?: error("No uid returned")
            val profile = mutableMapOf<String, Any>(
                "name" to name.trim(),
                "email" to email.trim(),
                "role" to role.wire,
                "classId" to "",
                "schoolId" to "",
                "linkedStudentIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            // Only students carry a linking code for parents to enter.
            if (role == Role.STUDENT) profile["shortCode"] = newShortCode()
            usersCol.document(uid).set(profile).await()
            true
        } catch (e: Exception) {
            _authError.value = authMessage(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    suspend fun resetPassword(email: String): Boolean = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        notifyUi("Password reset link sent to ${email.trim()}")
        true
    } catch (e: Exception) {
        notifyUi(authMessage(e), isError = true)
        false
    }

    fun signOut() {
        detachAll()
        auth.signOut()
        _signedIn.value = false
        _user.value = null
        _alerts.value = emptyList()
        _myResponses.value = emptyMap()
        _roster.value = emptyList()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    private fun authMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
        is FirebaseAuthInvalidCredentialsException -> "That email or password is not correct."
        is FirebaseAuthInvalidUserException -> "No account found for that email."
        is FirebaseAuthUserCollisionException -> "An account already uses that email."
        else -> e.message ?: "Sign-in failed. Check your connection and try again."
    }

    private fun newShortCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    // ----------------------------------------------------------- listeners

    private fun attachFor(uid: String) {
        detachAll()

        userReg = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener reportListenerError("profile", err)
            if (snap == null || !snap.exists()) return@addSnapshotListener

            @Suppress("UNCHECKED_CAST")
            val linked = (snap.get("linkedStudentIds") as? List<String>).orEmpty()
            val profile = UserProfile(
                uid = uid,
                name = snap.getString("name").orEmpty(),
                email = snap.getString("email").orEmpty(),
                role = Role.fromName(snap.getString("role")),
                classId = snap.getString("classId").orEmpty(),
                schoolId = snap.getString("schoolId").orEmpty(),
                shortCode = snap.getString("shortCode").orEmpty(),
                linkedStudentIds = linked,
            )
            _user.value = profile
            attachRosterMembers(profile)
        }

        alertsReg = alertsCol
            .orderBy("detectedAt", Query.Direction.DESCENDING)
            .limit(ALERT_LIMIT)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener reportListenerError("alerts", err)
                val list = snap?.documents?.mapNotNull { toAlert(it) }.orEmpty()
                _alerts.value = list

                // Don't replay the whole backlog as a "new" alert on first attach.
                val newest = list.firstOrNull()
                if (!alertsInitialized) {
                    alertsInitialized = true
                    lastIncomingId = newest?.id
                } else if (newest != null && newest.id != lastIncomingId) {
                    lastIncomingId = newest.id
                    if (!newest.closed) _incomingAlert.value = newest
                }
                newest?.let { ensureRosterListener(it.id) }
            }

        myRespReg = usersCol.document(uid).collection("responses")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener reportListenerError("responses", err)
                _myResponses.value = snap?.documents.orEmpty().mapNotNull { d ->
                    val alertId = d.getString("alertId") ?: d.id
                    SafetyResponse(
                        alertId = alertId,
                        userId = uid,
                        name = _user.value?.name.orEmpty(),
                        status = ResponseStatus.fromName(d.getString("status")),
                        respondedAt = d.getTimestamp("respondedAt")?.toDate()?.time
                            ?: System.currentTimeMillis(),
                    )
                }.associateBy { it.alertId }
            }
    }

    /** Teachers watch their class; parents watch their linked children. */
    private fun attachRosterMembers(profile: UserProfile) {
        rosterMembersReg?.remove()
        rosterMembersReg = null

        val query = when (profile.role) {
            Role.TEACHER -> {
                if (profile.classId.isBlank()) null
                else usersCol.whereEqualTo("role", Role.STUDENT.wire)
                    .whereEqualTo("classId", profile.classId)
            }

            Role.PARENT -> {
                val ids = profile.linkedStudentIds.take(30)
                if (ids.isEmpty()) null
                else usersCol.whereIn(com.google.firebase.firestore.FieldPath.documentId(), ids)
            }

            Role.STUDENT -> null
        }

        if (query == null) {
            rosterMembers = emptyList()
            recomputeRoster()
            return
        }

        rosterMembersReg = query.addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener reportListenerError("roster", err)
            rosterMembers = snap?.documents.orEmpty().map { d ->
                UserProfile(
                    uid = d.id,
                    name = d.getString("name").orEmpty(),
                    role = Role.fromName(d.getString("role")),
                    classId = d.getString("classId").orEmpty(),
                )
            }
            recomputeRoster()
        }
    }

    /** Responses for the alert currently being tracked on the live dashboard. */
    private fun ensureRosterListener(alertId: String) {
        if (currentRosterAlertId == alertId && rosterRespReg != null) return
        currentRosterAlertId = alertId
        rosterRespReg?.remove()
        rosterRespReg = alertsCol.document(alertId).collection("responses")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener reportListenerError("live status", err)
                rosterResponses = snap?.documents.orEmpty().map { d ->
                    SafetyResponse(
                        alertId = alertId,
                        userId = d.id,
                        name = d.getString("name").orEmpty(),
                        status = ResponseStatus.fromName(d.getString("status")),
                        respondedAt = d.getTimestamp("respondedAt")?.toDate()?.time
                            ?: System.currentTimeMillis(),
                    )
                }
                recomputeRoster()
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
        userReg?.remove(); userReg = null
        alertsReg?.remove(); alertsReg = null
        myRespReg?.remove(); myRespReg = null
        rosterMembersReg?.remove(); rosterMembersReg = null
        rosterRespReg?.remove(); rosterRespReg = null
        currentRosterAlertId = null
        alertsInitialized = false
        lastIncomingId = null
        rosterMembers = emptyList()
        rosterResponses = emptyList()
    }

    private fun reportListenerError(what: String, err: Exception) {
        Log.w(TAG, "listener failed: $what", err)
        notifyUi("Couldn't sync $what. Check your connection.", isError = true)
    }

    private fun toAlert(doc: DocumentSnapshot): AlertRecord? {
        if (!doc.exists()) return null
        val magnitude = doc.getDouble("magnitudeG") ?: doc.getDouble("magnitude_g") ?: 0.0
        return AlertRecord(
            id = doc.id,
            intensity = doc.getString("intensity")
                ?.let { Intensity.fromName(it) }
                ?: Intensity.fromMagnitude(magnitude),
            magnitudeG = magnitude,
            detectedAt = doc.getTimestamp("detectedAt")?.toDate()?.time
                ?: System.currentTimeMillis(),
            source = AlertSource.fromName(doc.getString("source")),
            nodeId = doc.getString("nodeId"),
            closed = doc.getBoolean("closed") ?: false,
        )
    }

    // -------------------------------------------------------------- writes

    fun updateRole(role: Role) {
        val uid = auth.currentUser?.uid ?: return
        usersCol.document(uid).update("role", role.wire)
            .addOnFailureListener { notifyUi("Couldn't change your role: ${it.message}", true) }
    }

    /** A parent links a student by typing the code the registrar issued. */
    suspend fun linkStudent(code: String): LinkResult {
        val uid = auth.currentUser?.uid ?: return LinkResult.Failed("You are signed out.")
        val entered = code.trim().uppercase()
        if (entered.isEmpty()) return LinkResult.NotFound
        return try {
            val snap = usersCol
                .whereEqualTo("shortCode", entered)
                .whereEqualTo("role", Role.STUDENT.wire)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull() ?: return LinkResult.NotFound
            if (_user.value?.linkedStudentIds?.contains(doc.id) == true) return LinkResult.AlreadyLinked
            usersCol.document(uid).update("linkedStudentIds", FieldValue.arrayUnion(doc.id)).await()
            LinkResult.Success(doc.getString("name").orEmpty().ifEmpty { "Student" })
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: "Linking failed.")
        }
    }

    fun unlinkStudent(studentUid: String) {
        val uid = auth.currentUser?.uid ?: return
        usersCol.document(uid).update("linkedStudentIds", FieldValue.arrayRemove(studentUid))
            .addOnFailureListener { notifyUi("Couldn't unlink: ${it.message}", true) }
    }

    /**
     * Demo mode. Simulated events are tagged so they are visibly separate from real
     * sensor readings in the history and in the paper's evaluation data.
     */
    fun simulateAlert(intensity: Intensity) {
        val magnitude = when (intensity) {
            Intensity.GREEN -> 0.22
            Intensity.YELLOW -> 0.48
            Intensity.RED -> 0.74
        }
        alertsCol.add(
            mapOf(
                "intensity" to intensity.wire,
                "magnitudeG" to magnitude,
                "detectedAt" to FieldValue.serverTimestamp(),
                "source" to AlertSource.SIMULATED.wire,
                "nodeId" to "SIMULATOR",
                "closed" to false,
            )
        ).addOnFailureListener { notifyUi("Couldn't start the simulation: ${it.message}", true) }
    }

    fun submitMyResponse(alertId: String, status: ResponseStatus) {
        val uid = auth.currentUser?.uid ?: return
        val name = _user.value?.name.orEmpty()
        val payload = mapOf(
            "userId" to uid,
            "name" to name,
            "status" to status.wire,
            "respondedAt" to FieldValue.serverTimestamp(),
        )
        // Written to the alert (for the live dashboard) and mirrored under the user
        // (so History can show "you replied Safe" without a collection-group index).
        alertsCol.document(alertId).collection("responses").document(uid).set(payload)
        usersCol.document(uid).collection("responses").document(alertId)
            .set(payload + ("alertId" to alertId))
            .addOnFailureListener { notifyUi("Couldn't record your response: ${it.message}", true) }
    }

    fun closeEvent(alertId: String) {
        alertsCol.document(alertId).update("closed", true)
            .addOnFailureListener { notifyUi("Couldn't close the event: ${it.message}", true) }
    }

    fun showAlertById(alertId: String) {
        scope.launch {
            val existing = _alerts.value.firstOrNull { it.id == alertId }
            if (existing != null) {
                _incomingAlert.value = existing
                return@launch
            }
            runCatching { alertsCol.document(alertId).get().await() }
                .getOrNull()
                ?.let { toAlert(it) }
                ?.let { _incomingAlert.value = it }
        }
    }

    fun consumeIncomingAlert() {
        _incomingAlert.value = null
    }

    // ------------------------------------------------------------ settings

    private suspend fun loadSettings() {
        val json = runCatching {
            appContext.settingsStore.data.first()[SETTINGS_KEY]
        }.getOrNull()
        if (json != null) {
            runCatching { _settings.value = settingsFromJson(JSONObject(json)) }
        }
    }

    fun updateSettings(transform: (SirenSettings) -> SirenSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        scope.launch {
            runCatching {
                appContext.settingsStore.edit { it[SETTINGS_KEY] = settingsToJson(updated).toString() }
            }
        }
    }

    fun addEmergencyContact(contact: EmergencyContact) =
        updateSettings { it.copy(contacts = it.contacts + contact) }

    fun removeEmergencyContact(id: String) =
        updateSettings { s -> s.copy(contacts = s.contacts.filterNot { it.id == id }) }

    private fun settingsToJson(s: SirenSettings): JSONObject = JSONObject().apply {
        put("criticalAlerts", s.criticalAlerts)
        put("vibration", s.vibration)
        put("darkMode", s.darkMode)
        put("contacts", JSONArray().apply {
            s.contacts.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id)
                        .put("name", c.name)
                        .put("relation", c.relation)
                        .put("phone", c.phone)
                        .put("primary", c.primary)
                )
            }
        })
    }

    private fun settingsFromJson(o: JSONObject): SirenSettings {
        val arr = o.optJSONArray("contacts") ?: JSONArray()
        val contacts = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            EmergencyContact(
                id = c.optString("id"),
                name = c.optString("name"),
                relation = c.optString("relation"),
                phone = c.optString("phone"),
                primary = c.optBoolean("primary"),
            )
        }
        return SirenSettings(
            criticalAlerts = o.optBoolean("criticalAlerts", true),
            vibration = o.optBoolean("vibration", true),
            darkMode = o.optBoolean("darkMode", false),
            contacts = contacts,
        )
    }

    // -------------------------------------------------------- connectivity

    private fun watchConnectivity() {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        }
    }

    fun setOnline(value: Boolean) {
        _online.value = value
    }

    private fun notifyUi(text: String, isError: Boolean = false) {
        _events.tryEmit(UiMessage(text, isError))
    }
}
