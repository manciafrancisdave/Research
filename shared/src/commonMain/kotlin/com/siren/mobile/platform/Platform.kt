package com.siren.mobile.platform

import com.siren.mobile.model.Intensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the shared code needs that only a platform can provide.
 *
 * Implemented by `AndroidPlatformServices` (NotificationManager, Vibrator,
 * SharedPreferences, Intents, foreground alarm service) and `IosPlatformServices`
 * (UNUserNotificationCenter, Taptic, NSUserDefaults, AVAudioPlayer).
 *
 * Deliberately an interface rather than expect/actual: it keeps the shared code free
 * of platform source-set plumbing and makes the surface easy to see at a glance.
 */
/**
 * A phone number that has had a code sent to it, plus whatever token the platform needs
 * to redeem that code. Opaque on purpose — Android carries a verification id, iOS carries
 * a `PhoneAuthCredential` handle, and shared code should not care which.
 */
data class PhoneVerification(
    val phone: String,
    val token: String,
) {
    sealed interface Result {
        data class SignedIn(val uid: String, val phone: String) : Result
        data class Failed(val reason: String) : Result
    }
}

/** Outcome of asking for an SMS code. */
sealed interface PhoneCodeRequest {
    /** The SMS is on its way; ask the user for the code. */
    data class Sent(val verification: PhoneVerification) : PhoneCodeRequest

    /**
     * Android can verify some numbers without the user typing anything (instant
     * validation or auto-retrieval), in which case sign-in has already happened.
     */
    data class AutoVerified(val uid: String, val phone: String) : PhoneCodeRequest

    data class Failed(val reason: String) : PhoneCodeRequest
}

interface PlatformServices {
    /** Shown on the splash and in Settings → About. */
    val versionName: String

    fun vibrateForIntensity(intensity: Intensity)
    fun vibrateTap()
    fun vibrateConfirm()
    fun cancelVibration()

    fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double)
    fun clearNotifications()

    /**
     * Starts the emergency alarm.
     *
     * Green chimes once. Yellow repeats briefly. **Red loops indefinitely and must
     * survive the screen locking, the app being backgrounded, the notification being
     * swiped away and the device being on silent** — it stops only via [stopAlarm].
     */
    fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean)

    /**
     * The only thing that silences a Red alarm. Reached from "I'm Safe", "I Need Help"
     * or an explicit "Stop alarm" — never on a timer.
     */
    fun stopAlarm()

    fun dial(phone: String)
    fun sendSms(phone: String)

    /** Settings are persisted as a JSON blob: SharedPreferences / NSUserDefaults. */
    fun readSettingsJson(): String?
    fun writeSettingsJson(json: String)

    /** Subscribes the device to the "alerts" FCM topic. */
    fun subscribeToAlertsTopic()

    /** Wall-clock milliseconds; `System.currentTimeMillis` has no common equivalent. */
    fun nowMillis(): Long

    // ------------------------------------------------------------ phone auth

    /**
     * Whether this build can even attempt phone sign-up.
     *
     * False hides the option rather than letting a user fill in a number and hit a
     * failure they cannot act on. GitLive does not wrap `PhoneAuthProvider`, so this
     * goes through the platform SDKs directly — the same reason Cloud Messaging does.
     */
    val phoneAuthSupported: Boolean

    /** Sends the SMS verification code to an E.164 number. */
    suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest

    /** Redeems the code the user typed. */
    suspend fun confirmPhoneCode(verification: PhoneVerification, code: String): PhoneVerification.Result

    // ------------------------------------------------- full-screen alerting

    /**
     * Whether the OS will currently let a Red alert take over the screen.
     *
     * Android 14 restricts `USE_FULL_SCREEN_INTENT` to calling and alarm apps and grants
     * it to nobody else by default. When it is denied the full-screen alert simply never
     * appears and nothing says why, so Settings surfaces this and offers a way to fix it.
     */
    fun canUseFullScreenIntent(): Boolean

    /** Opens the OS screen where the user can grant full-screen alerts. */
    fun openFullScreenIntentSettings()

    /** Opens this app's notification settings, for when notifications are switched off. */
    fun openNotificationSettings()

    /** Whether the user has allowed notifications at all. */
    fun notificationsEnabled(): Boolean

    /**
     * Whether the alarm can raise the full-screen alert **itself**, without relying on
     * the full-screen intent.
     *
     * This is the second half of the problem [canUseFullScreenIntent] describes. When the
     * intent is denied the OS quietly downgrades a Red alert to a heads-up notification,
     * and the app's own attempt to start the alert Activity is then blocked as well —
     * Android 10 forbids background activity starts. Permission to draw over other apps
     * is the documented exemption from that rule, and it is what alarm clocks rely on for
     * the same reason. Without one of the two grants a locked phone can only ever show a
     * notification, however loudly the alarm is sounding behind it.
     */
    fun canLaunchAlertOverOtherApps(): Boolean

    /** Opens the OS screen where the user can allow drawing over other apps. */
    fun openOverlaySettings()

    // --------------------------------------------------------- profile photo

    /** Whether this platform can open an image picker. */
    val photoPickerSupported: Boolean

    /**
     * Opens the system photo picker and returns a **base64 JPEG, already downscaled**,
     * or null if the user backed out.
     *
     * Resizing belongs on this side of the seam: shared code cannot decode or re-encode
     * an image, and a full-size camera photo is several megabytes against Firestore's
     * 1 MiB document limit — the write would be rejected outright. Implementations must
     * cap the long edge at [PROFILE_PHOTO_MAX_PX] and compress before encoding.
     */
    suspend fun pickProfilePhoto(): String?
}

/** Long-edge cap for profile pictures. Keeps an encoded photo around 20 KB. */
const val PROFILE_PHOTO_MAX_PX = 256

/** JPEG quality used when encoding a picked profile picture. */
const val PROFILE_PHOTO_QUALITY = 80

/**
 * No-op stand-in so previews and early start-up calls cannot crash. Replaced by the
 * real implementation in `SirenApp.onCreate` (Android) and `MainViewController` (iOS).
 */
private object NoOpPlatformServices : PlatformServices {
    override val versionName = "2.4.0"
    override fun vibrateForIntensity(intensity: Intensity) = Unit
    override fun vibrateTap() = Unit
    override fun vibrateConfirm() = Unit
    override fun cancelVibration() = Unit
    override fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double) = Unit
    override fun clearNotifications() = Unit
    override fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean) = Unit
    override fun stopAlarm() = Unit
    override fun dial(phone: String) = Unit
    override fun sendSms(phone: String) = Unit
    override fun readSettingsJson(): String? = null
    override fun writeSettingsJson(json: String) = Unit
    override fun subscribeToAlertsTopic() = Unit
    override fun nowMillis(): Long = 0L
    override val phoneAuthSupported = false
    override suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest =
        PhoneCodeRequest.Failed("Phone sign-up is not available on this device.")

    override suspend fun confirmPhoneCode(
        verification: PhoneVerification,
        code: String,
    ): PhoneVerification.Result =
        PhoneVerification.Result.Failed("Phone sign-up is not available on this device.")

    override fun canUseFullScreenIntent() = true
    override fun openFullScreenIntentSettings() = Unit
    override fun openNotificationSettings() = Unit
    override fun notificationsEnabled() = true
    override fun canLaunchAlertOverOtherApps() = true
    override fun openOverlaySettings() = Unit
    override val photoPickerSupported = false
    override suspend fun pickProfilePhoto(): String? = null
}

object Platform {
    private var impl: PlatformServices = NoOpPlatformServices

    private val _alarmActive = MutableStateFlow(false)

    /** Drives the "Stop alarm" affordance — the UI must always offer a way out. */
    val alarmActive: StateFlow<Boolean> = _alarmActive.asStateFlow()

    fun install(services: PlatformServices) {
        impl = services
    }

    /** Called by the platform implementations as playback starts and stops. */
    fun setAlarmActive(active: Boolean) {
        _alarmActive.value = active
    }

    val services: PlatformServices get() = impl
}
