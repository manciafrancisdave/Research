package com.siren.mobile.model

/**
 * Domain model for S.I.R.E.N.
 *
 * Enum names are written to Firestore in lower case (see SirenRepository), which keeps
 * the documents readable and matches the schema the ESP32 firmware writes.
 */

enum class Role(val label: String, val blurb: String) {
    STUDENT("Student", "Receive alerts and confirm your safety status."),
    TEACHER("Teacher / School Admin", "Monitor your class roster in real time."),
    PARENT("Parent / Guardian", "Track the safety status of linked children.");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): Role =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: STUDENT
    }
}

/**
 * Intensity bands follow the campus ESP32 accelerometer calibration used in the study.
 *
 * [scale] and [shaking] lead every readout — "Intensity V–VI · Moderate shaking" —
 * because an intensity level is the language drills and PHIVOLCS advisories already use,
 * and it is what somebody mid-earthquake can act on. The measured peak ground
 * acceleration is shown **underneath it, in smaller type**, to three decimals: it is the
 * study's actual measurement, and hiding it entirely (as v2.6 did) left the evaluation
 * data invisible inside the app that collected it.
 *
 * The ordering is the point. Intensity first and large, `0.xxx g` second and small.
 *
 * These thresholds MUST stay in lockstep with BAND_YELLOW_G / BAND_RED_G in the ESP32
 * firmware. If one side changes and the other does not, the hardware and the phone
 * disagree about what colour an earthquake is.
 */
enum class Intensity(
    val label: String,
    val severity: String,
    val level: Int,
    /** Roman-numeral band shown to users, e.g. "V–VI". */
    val scale: String,
    /** Plain-language descriptor paired with [scale]. */
    val shaking: String,
    /** The g range behind this band. Documentation and the Demo screen only. */
    val range: String,
    val behaviour: String,
) {
    GREEN(
        "Green", "Minor", 1,
        "I–IV", "Light shaking",
        "0.000 – 0.010 g", "Notification only, single vibration",
    ),
    YELLOW(
        "Yellow", "Moderate", 2,
        "V–VI", "Moderate shaking",
        "0.010 – 0.120 g", "Full-screen alert, repeating vibration",
    ),
    RED(
        "Red", "Severe", 3,
        "VII+", "Destructive shaking",
        "0.120 g and above", "Alarm sound, continuous vibration, status required",
    );

    val wire: String get() = name.lowercase()

    /** "Intensity V–VI" — the headline readout. */
    val levelText: String get() = "Intensity $scale"

    /** "Intensity V–VI · Moderate shaking" — where there is room for both. */
    val levelWithShaking: String get() = "$levelText · $shaking"

    companion object {
        fun fromMagnitude(g: Double): Intensity = when {
            g >= 0.120 -> RED
            g >= 0.010 -> YELLOW
            else -> GREEN
        }

        fun fromName(name: String?): Intensity =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: GREEN
    }
}

enum class ResponseStatus(val label: String) {
    SAFE("I'm Safe"),
    NEEDS_HELP("I Need Help"),
    NO_RESPONSE("No Response Yet");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): ResponseStatus =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: NO_RESPONSE
    }
}

enum class AlertSource(val label: String) {
    ESP32("Sensor"),
    SIMULATED("Simulation");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): AlertSource =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: ESP32
    }
}

/**
 * Where a guardian link has got to.
 *
 * A parent typing a student's code no longer links the account outright — it raises a
 * request the student has to approve. The code is six characters and gets passed around
 * a classroom; without this step anyone who saw one could start following that student's
 * live location-of-safety status, and the student would never know it had happened.
 */
enum class LinkRequestStatus(val label: String) {
    PENDING("Awaiting confirmation"),
    APPROVED("Confirmed"),
    DECLINED("Declined");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): LinkRequestStatus =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: PENDING
    }
}

/**
 * One parent/guardian asking to follow one student.
 *
 * Held top-level rather than under either user so both sides can watch it with a single
 * equality filter — `parentId ==` for the parent, `studentId ==` for the student — and
 * neither query needs a composite index.
 */
data class LinkRequest(
    val id: String,
    val studentId: String,
    val studentName: String,
    val parentId: String,
    val parentName: String,
    val parentContact: String = "",
    val status: LinkRequestStatus = LinkRequestStatus.PENDING,
    val requestedAt: Long = 0L,
    val respondedAt: Long? = null,
)

data class AlertRecord(
    val id: String,
    val intensity: Intensity,
    val magnitudeG: Double,
    val detectedAt: Long,
    val source: AlertSource,
    val nodeId: String? = null,
    val closed: Boolean = false,
)

data class SafetyResponse(
    val alertId: String,
    val userId: String,
    val name: String,
    val status: ResponseStatus,
    val respondedAt: Long,
)

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String = "",
    /** Set for accounts created with phone sign-up; blank for email accounts. */
    val phone: String = "",
    val role: Role = Role.STUDENT,
    val classId: String = "",
    val schoolId: String = "",
    /** Short code a parent types to link this student. Students only. */
    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),
) {
    /** Whichever of email/phone the account actually has, for display. */
    val contact: String get() = email.ifBlank { phone }

    val initials: String
        get() = name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/** A row in the teacher roster or the parent's linked-children list. */
data class LinkedPerson(
    val uid: String,
    val name: String,
    val klass: String = "",
    val status: ResponseStatus = ResponseStatus.NO_RESPONSE,
    val respondedAt: Long? = null,
    val pending: Boolean = false,
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

data class EmergencyContact(
    val id: String,
    val name: String,
    val relation: String,
    val phone: String,
    val primary: Boolean = false,
)

/**
 * Official City of Bogo responders, seeded for every user on first run so a student
 * has someone to call before they have added anyone themselves.
 *
 * The stable `id` prefix is what lets the app tell a seeded contact from a
 * user-added one — a deliberately deleted default must not silently reappear.
 */
val DefaultEmergencyContacts: List<EmergencyContact> = listOf(
    EmergencyContact(
        id = "default_bogo_police",
        name = "Bogo Police Station",
        relation = "Police",
        phone = "0905 600 2028",
        primary = true,
    ),
    EmergencyContact(
        id = "default_emergency_response",
        name = "Emergency Response Unit",
        relation = "Rescue / medical",
        phone = "0919 920 4635",
    ),
    EmergencyContact(
        id = "default_bogo_fire",
        name = "Bogo Fire Department",
        relation = "Fire",
        phone = "0917 127 9158",
    ),
)

data class SirenSettings(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val contacts: List<EmergencyContact> = DefaultEmergencyContacts,
    /**
     * False until an account has been created or signed into on this device once.
     *
     * A first-time install opens on Create Account rather than Login: somebody who has
     * just downloaded the app has nothing to sign in with, and landing on a login form
     * asks them for credentials that do not exist yet. Once they have an account the
     * order flips back, because from then on signing in is the common case.
     */
    val hasAccount: Boolean = false,
)
