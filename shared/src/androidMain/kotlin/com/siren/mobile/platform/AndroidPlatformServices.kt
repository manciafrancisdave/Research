package com.siren.mobile.platform

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.siren.mobile.model.Intensity
import com.siren.mobile.notify.SirenAlarmService
import com.siren.mobile.util.asGSpaced
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android half of [PlatformServices].
 *
 * The activity class and notification icon are injected because this lives in the
 * shared library, which cannot reference the app module's MainActivity or resources.
 */
class AndroidPlatformServices(
    context: Context,
    private val activityClass: Class<*>,
    private val smallIconRes: Int,
    private val alarmSoundRes: Int,
    override val versionName: String,
    /**
     * The activity currently on screen, or null. Phone verification needs a real
     * Activity for its reCAPTCHA fallback, and this class only holds an app context.
     */
    private val currentActivity: () -> Activity? = { null },
) : PlatformServices {

    companion object {
        const val CHANNEL_ALERTS = "siren_alerts"
        private const val CHANNEL_QUIET = "siren_alerts_minor"
        private const val NOTIFICATION_ID = 4101
        const val EXTRA_ALERT_ID = "extra_alert_id"
        private const val PREFS = "siren_settings"
        private const val KEY_SETTINGS = "settings_json"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // The alarm service lives in this library and cannot see the app module's R
        // class or MainActivity, so they are handed over here.
        SirenAlarmService.soundResId = alarmSoundRes
        SirenAlarmService.smallIconResId = smallIconRes
        SirenAlarmService.activityClass = activityClass
    }

    // ---------------------------------------------------------------- alarm

    override fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean) {
        // Green is informational only — a single chime and buzz, no service, and it
        // respects the ringer. Anything higher escalates to the foreground service.
        if (intensity == Intensity.GREEN) {
            showAlertNotification(alertId, intensity, magnitudeG)
            if (vibrate) vibrateForIntensity(intensity)
            return
        }

        val intent = Intent(appContext, SirenAlarmService::class.java).apply {
            action = SirenAlarmService.ACTION_START
            putExtra(SirenAlarmService.EXTRA_ALERT_ID, alertId)
            putExtra(SirenAlarmService.EXTRA_INTENSITY, intensity.wire)
            putExtra(SirenAlarmService.EXTRA_MAGNITUDE, magnitudeG)
            putExtra(SirenAlarmService.EXTRA_VIBRATE, vibrate)
            // Yellow steps down on its own; Red never does.
            putExtra(
                SirenAlarmService.EXTRA_TIMEOUT_MS,
                if (intensity == Intensity.YELLOW) 30_000L else 0L,
            )
        }
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
    }

    /**
     * Stops the alarm. **Uses `startService`, never `startForegroundService`.**
     *
     * `startForegroundService` is a promise that the service will call `startForeground`
     * within five seconds, and Android kills the process with
     * `ForegroundServiceDidNotStartInTimeException` if it does not. ACTION_STOP goes
     * straight to `stopEverything()` → `stopSelf()` and never goes foreground, so that
     * promise could not be kept.
     *
     * It only crashed when the service was not *already* foreground, which is why it
     * looked intermittent: responding to a Green alert (no service is ever started for
     * one), to a Yellow after its 30-second timeout, after "Silence alarm", or to any
     * past event reopened from history. In each case "I'm Safe" started a fresh service
     * purely to tell it to stop, and the app died five seconds later — including when
     * the tap came from the alarm notification, because ACTION_SAFE calls through here
     * too.
     *
     * Stopping needs no foreground promise. If the service is not running there is
     * nothing to stop, and `startService` throwing from the background is caught here.
     */
    override fun stopAlarm() {
        val intent = Intent(appContext, SirenAlarmService::class.java)
            .setAction(SirenAlarmService.ACTION_STOP)
        runCatching { appContext.startService(intent) }
        cancelVibration()
    }

    // ------------------------------------------------------------ vibration

    private val vibrator: Vibrator?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun play(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    override fun vibrateForIntensity(intensity: Intensity) = play(
        when (intensity) {
            Intensity.GREEN -> longArrayOf(0, 250)
            Intensity.YELLOW -> longArrayOf(0, 400, 200, 400, 200, 400)
            Intensity.RED -> longArrayOf(0, 800, 200, 800, 200, 1200, 200, 1200)
        }
    )

    override fun vibrateTap() = play(longArrayOf(0, 20))

    override fun vibrateConfirm() = play(longArrayOf(0, 40, 60, 40))

    override fun cancelVibration() {
        runCatching { vibrator?.cancel() }
    }

    // -------------------------------------------------------- notifications

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            "Earthquake alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Full-screen seismic alerts from the campus sensor network"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 900)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val minor = NotificationChannel(
            CHANNEL_QUIET,
            "Minor tremors",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Low-intensity readings that do not require a response"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300)
        }

        manager.createNotificationChannel(alerts)
        manager.createNotificationChannel(minor)
    }

    override fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double) {
        ensureChannels()

        val intent = Intent(appContext, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
        }
        val pending = PendingIntent.getActivity(
            appContext,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // The intensity leads, because that is what somebody acts on. The measured
        // acceleration follows it as a trailing detail, matching how the in-app readouts
        // put the g figure underneath the level in smaller type.
        val level = intensity.levelText
        val title = if (intensity == Intensity.GREEN) {
            "Minor tremor detected — $level"
        } else {
            "Earthquake detected — $level"
        }
        val reading = magnitudeG.asGSpaced(3)
        val body = if (intensity == Intensity.GREEN) {
            "No action needed. Logged for the record. Peak ground acceleration $reading."
        } else {
            "Drop, cover, hold on. Tap to confirm your status. Peak ground acceleration $reading."
        }

        val builder = NotificationCompat.Builder(
            appContext,
            if (intensity == Intensity.GREEN) CHANNEL_QUIET else CHANNEL_ALERTS,
        )
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (intensity == Intensity.GREEN) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_MAX
            )

        if (intensity == Intensity.RED) {
            builder.setFullScreenIntent(pending, true)
            builder.setOngoing(true)
        }

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        }
    }

    override fun clearNotifications() {
        runCatching { NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID) }
    }

    // --------------------------------------------------------------- intents

    private fun digits(phone: String) = phone.filter { it.isDigit() || it == '+' }

    override fun dial(phone: String) {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${digits(phone)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun sendSms(phone: String) {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${digits(phone)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // -------------------------------------------------------------- storage

    override fun readSettingsJson(): String? = prefs.getString(KEY_SETTINGS, null)

    override fun writeSettingsJson(json: String) {
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }

    override fun subscribeToAlertsTopic() {
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic("alerts") }
    }

    override fun nowMillis(): Long = System.currentTimeMillis()

    // ------------------------------------------------------------ phone auth

    /**
     * The Android SDK always has `PhoneAuthProvider`, so the capability is present in
     * every build. Whether the *Firebase project* will actually send an SMS is a console
     * setting, and failing that way produces a specific message in [phoneAuthMessage]
     * rather than a silently missing option.
     */
    override val phoneAuthSupported: Boolean = true

    override suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest {
        val activity = currentActivity()
            ?: return PhoneCodeRequest.Failed("Open the app before requesting a code.")
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: return PhoneCodeRequest.Failed("Sign-in is not configured on this build.")

        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                /**
                 * Some numbers and some devices never need the user to type anything —
                 * Google Play can validate the SIM directly, or auto-read the SMS. When
                 * that happens sign-in is already done and asking for a code would be a
                 * dead end, so the caller is told to skip that step.
                 */
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (!cont.isActive) return
                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (!cont.isActive) return@addOnCompleteListener
                        val user = task.result?.user
                        cont.resume(
                            if (task.isSuccessful && user != null) {
                                PhoneCodeRequest.AutoVerified(user.uid, user.phoneNumber.orEmpty())
                            } else {
                                PhoneCodeRequest.Failed(phoneAuthMessage(task.exception))
                            }
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resume(PhoneCodeRequest.Failed(phoneAuthMessage(e)))
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (cont.isActive) {
                        cont.resume(PhoneCodeRequest.Sent(PhoneVerification(phoneE164, id)))
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            runCatching { PhoneAuthProvider.verifyPhoneNumber(options) }
                .onFailure { if (cont.isActive) cont.resume(PhoneCodeRequest.Failed(phoneAuthMessage(it))) }
        }
    }

    override suspend fun confirmPhoneCode(
        verification: PhoneVerification,
        code: String,
    ): PhoneVerification.Result {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: return PhoneVerification.Result.Failed("Sign-in is not configured on this build.")
        val credential = runCatching {
            PhoneAuthProvider.getCredential(verification.token, code)
        }.getOrElse {
            return PhoneVerification.Result.Failed("That code doesn't look right.")
        }

        return suspendCancellableCoroutine { cont ->
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (!cont.isActive) return@addOnCompleteListener
                val user = task.result?.user
                cont.resume(
                    if (task.isSuccessful && user != null) {
                        PhoneVerification.Result.SignedIn(user.uid, user.phoneNumber.orEmpty())
                    } else {
                        PhoneVerification.Result.Failed(phoneAuthMessage(task.exception))
                    }
                )
            }
        }
    }

    /**
     * Phone auth fails for three reasons that are nothing to do with the user, and the
     * raw SDK text for all three is unreadable. They are worth naming because every one
     * of them is fixed in the Firebase console, not in the app:
     * the project is on Spark rather than Blaze, the signing certificate's SHA-256 is
     * not registered, or the Phone provider is switched off.
     */
    private fun phoneAuthMessage(e: Throwable?): String {
        val raw = e?.message.orEmpty()
        return when {
            e is FirebaseAuthInvalidCredentialsException &&
                raw.contains("code", true) -> "That code is incorrect or has expired."

            raw.contains("BILLING_NOT_ENABLED", true) ||
                raw.contains("billing", true) ->
                "Phone sign-up is not switched on for this project yet. Use email instead."

            raw.contains("OPERATION_NOT_ALLOWED", true) ||
                raw.contains("not enabled", true) ->
                "Phone sign-up is not switched on for this project yet. Use email instead."

            raw.contains("INVALID_APP_CREDENTIAL", true) ||
                raw.contains("app is not authorized", true) ||
                raw.contains("reCAPTCHA", true) ->
                "This build isn't registered for phone sign-up yet. Use email instead."

            raw.contains("TOO_MANY_REQUESTS", true) || raw.contains("quota", true) ->
                "Too many attempts. Try again later, or use email."

            raw.contains("INVALID_PHONE_NUMBER", true) ->
                "That phone number doesn't look right. Include the area code."

            raw.contains("NETWORK", true) ->
                "Couldn't reach the network. Check your connection."

            raw.isBlank() -> "Phone sign-up failed. Try email instead."
            else -> raw
        }
    }

    // ------------------------------------------------- full-screen alerting

    /**
     * Android 14 (API 34) took `USE_FULL_SCREEN_INTENT` away from everything that is not
     * a calling or alarm app. Declaring it in the manifest is no longer enough, and when
     * it is denied a Red alert produces a looping alarm behind a notification the user
     * may never look at. Older releases grant it from the manifest alone.
     */
    override fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
    }

    override fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            openNotificationSettings()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Not every OEM ships that screen; fall back rather than throwing at the user.
        runCatching { appContext.startActivity(intent) }.onFailure { openNotificationSettings() }
    }

    override fun openNotificationSettings() {
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun notificationsEnabled(): Boolean =
        runCatching { NotificationManagerCompat.from(appContext).areNotificationsEnabled() }
            .getOrDefault(true)
}
