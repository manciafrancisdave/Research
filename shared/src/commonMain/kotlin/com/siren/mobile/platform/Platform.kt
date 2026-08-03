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
}

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
