package com.siren.mobile.platform

import com.siren.mobile.model.Intensity
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackTypeSuccess
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionCriticalAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS half of [PlatformServices].
 *
 * NOT YET COMPILED OR RUN — this project was developed on Windows, where Kotlin/Native
 * cannot build Apple targets. Treat every line here as unverified until it has been
 * built on a Mac (see CLAUDE.md → "iOS prerequisites").
 *
 * iOS has no arbitrary vibration patterns like Android's Vibrator. The escalation is
 * approximated with the Taptic Engine plus the system vibration sound, repeated for
 * higher intensities.
 */
class IosPlatformServices(
    override val versionName: String,
) : PlatformServices {

    private companion object {
        const val KEY_SETTINGS = "siren_settings_json"

        /** kSystemSoundID_Vibrate — the only true "vibrate" available to apps. */
        const val SYSTEM_SOUND_VIBRATE: UInt = 4095u
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    // ------------------------------------------------------------ vibration

    override fun vibrateForIntensity(intensity: Intensity) {
        val repeats = when (intensity) {
            Intensity.GREEN -> 1
            Intensity.YELLOW -> 3
            Intensity.RED -> 6
        }
        val style = when (intensity) {
            Intensity.GREEN -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            Intensity.YELLOW -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            Intensity.RED -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        val generator = UIImpactFeedbackGenerator(style)
        generator.prepare()
        repeat(repeats) {
            generator.impactOccurred()
            AudioServicesPlaySystemSound(SYSTEM_SOUND_VIBRATE)
        }
    }

    override fun vibrateTap() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            .also { it.prepare() }
            .impactOccurred()
    }

    override fun vibrateConfirm() {
        UINotificationFeedbackGenerator()
            .also { it.prepare() }
            .notificationOccurred(UINotificationFeedbackTypeSuccess)
    }

    /** iOS cannot cancel an in-flight system vibration. */
    override fun cancelVibration() = Unit

    // ---------------------------------------------------------------- alarm

    private var alarmPlayer: AVAudioPlayer? = null

    /**
     * iOS is materially weaker than Android here, and that has to be stated rather
     * than papered over:
     *
     *  - Looping audio while backgrounded needs the `audio` background mode, and iOS
     *    can still suspend it under memory pressure.
     *  - Playing through silent mode at all requires Apple's **Critical Alerts**
     *    entitlement, granted only on request.
     *  - A push cannot itself loop a sound; the app has to be foregrounded (or opened
     *    from the notification) for this to run.
     *
     * NOT YET COMPILED — see the class header.
     */
    override fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean) {
        showAlertNotification(alertId, intensity, magnitudeG)
        if (vibrate) vibrateForIntensity(intensity)
        if (intensity == Intensity.GREEN) return

        val url = NSBundle.mainBundle.URLForResource("siren_alarm", "wav") ?: return
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            alarmPlayer = AVAudioPlayer(contentsOfURL = url, error = null).apply {
                // -1 = loop forever; only stopAlarm() ends it.
                numberOfLoops = if (intensity == Intensity.RED) -1 else 3
                prepareToPlay()
                play()
            }
            Platform.setAlarmActive(true)
        }
    }

    override fun stopAlarm() {
        runCatching {
            alarmPlayer?.stop()
            alarmPlayer = null
            AVAudioSession.sharedInstance().setActive(false, null)
        }
        Platform.setAlarmActive(false)
    }

    // -------------------------------------------------------- notifications

    /** Call once at start-up, before any alert can arrive. */
    fun requestNotificationPermission() {
        // Critical alerts additionally require an Apple entitlement, granted only on
        // request — without it Red alerts cannot bypass silent mode.
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionSound or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionCriticalAlert
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { _, _ -> }
    }

    override fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double) {
        val magnitude = magnitudeG.formatTwo()
        val content = UNMutableNotificationContent().apply {
            setTitle(
                if (intensity == Intensity.GREEN) "Minor tremor detected — ${magnitude}g"
                else "Earthquake detected — ${magnitude}g"
            )
            setBody(
                if (intensity == Intensity.GREEN) "No action needed. Logged for the record."
                else "Drop, cover, hold on. Tap to confirm your status."
            )
            setSound(
                if (intensity == Intensity.RED) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            setUserInfo(mapOf("alertId" to alertId))
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = alertId,
            content = content,
            trigger = null, // deliver immediately
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }

    override fun clearNotifications() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removeAllDeliveredNotifications()
    }

    // --------------------------------------------------------------- URLs

    private fun open(scheme: String, phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        val url = NSURL.URLWithString("$scheme:$digits") ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { }
    }

    override fun dial(phone: String) = open("tel", phone)

    override fun sendSms(phone: String) = open("sms", phone)

    // -------------------------------------------------------------- storage

    override fun readSettingsJson(): String? = defaults.stringForKey(KEY_SETTINGS)

    override fun writeSettingsJson(json: String) {
        defaults.setObject(json, KEY_SETTINGS)
    }

    /**
     * TODO(mac): wire to FirebaseMessaging.messaging().subscribe(toTopic: "alerts")
     * once the Firebase iOS pod is linked. Left as a no-op so this file stays free of
     * pod-dependent imports and the iOS target still compiles without CocoaPods.
     */
    override fun subscribeToAlertsTopic() = Unit

    override fun nowMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

private fun Double.formatTwo(): String {
    val scaled = kotlin.math.round(this * 100).toLong()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}
