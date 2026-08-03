package com.siren.mobile.platform

import com.siren.mobile.model.Intensity

/**
 * Everything the shared code needs that only a platform can provide.
 *
 * Implemented by `AndroidPlatformServices` (NotificationManager, Vibrator,
 * SharedPreferences, Intents) and `IosPlatformServices` (UNUserNotificationCenter,
 * UIImpactFeedbackGenerator, NSUserDefaults, UIApplication.openURL).
 *
 * Deliberately an interface rather than expect/actual: it keeps the shared code
 * free of platform source-set plumbing and makes the surface easy to see at a glance.
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

    fun dial(phone: String)
    fun sendSms(phone: String)

    /** Settings are persisted as a JSON blob: SharedPreferences / NSUserDefaults. */
    fun readSettingsJson(): String?
    fun writeSettingsJson(json: String)

    /** Subscribes the device to the "alerts" FCM topic. */
    fun subscribeToAlertsTopic()
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
    override fun dial(phone: String) = Unit
    override fun sendSms(phone: String) = Unit
    override fun readSettingsJson(): String? = null
    override fun writeSettingsJson(json: String) = Unit
    override fun subscribeToAlertsTopic() = Unit
}

object Platform {
    private var impl: PlatformServices = NoOpPlatformServices

    fun install(services: PlatformServices) {
        impl = services
    }

    val services: PlatformServices get() = impl
}
