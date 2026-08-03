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
 * Intensity bands follow the campus ESP32 accelerometer calibration used in the study:
 * Green <= 0.30 g, Yellow 0.31 - 0.60 g, Red >= 0.61 g.
 */
enum class Intensity(
    val label: String,
    val severity: String,
    val level: Int,
    val range: String,
    val behaviour: String,
) {
    GREEN("Green", "Minor", 1, "≤ 0.30 g", "Notification only, single vibration"),
    YELLOW("Yellow", "Moderate", 2, "0.31 – 0.60 g", "Full-screen alert, repeating vibration"),
    RED("Red", "Severe", 3, "≥ 0.61 g", "Alarm sound, continuous vibration, status required");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromMagnitude(g: Double): Intensity = when {
            g >= 0.61 -> RED
            g >= 0.31 -> YELLOW
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
    val role: Role = Role.STUDENT,
    val classId: String = "",
    val schoolId: String = "",
    /** Short code a parent types to link this student. Students only. */
    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),
) {
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

data class SirenSettings(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val darkMode: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList(),
)
