package com.siren.mobile.platform

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
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

class AndroidPlatformServices(
    context: Context,
    private val activityClass: Class<*>,
    private val smallIconRes: Int,
    private val alarmSoundRes: Int,
    override val versionName: String,

    private val currentActivity: () -> Activity? = { null },
) : PlatformServices {

    companion object {
        private const val TAG = "SirenPlatform"
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

        SirenAlarmService.soundResId = alarmSoundRes
        SirenAlarmService.smallIconResId = smallIconRes
        SirenAlarmService.activityClass = activityClass
    }

    override fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean) {

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

            putExtra(
                SirenAlarmService.EXTRA_TIMEOUT_MS,
                if (intensity == Intensity.YELLOW) 30_000L else 0L,
            )
        }
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
    }

    override fun stopAlarm() {
        val intent = Intent(appContext, SirenAlarmService::class.java)
            .setAction(SirenAlarmService.ACTION_STOP)
        runCatching { appContext.startService(intent) }
        cancelVibration()
    }

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

    override val directSmsSupported: Boolean = true

    override suspend fun sendSmsDirect(
        recipients: List<SmsRecipient>,
        body: String,
    ): SmsDispatchResult {
        if (recipients.isEmpty()) return SmsDispatchResult()

        val requester = currentActivity() as? SmsPermissionRequester
        val granted = when {
            requester == null ->
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
                    PackageManager.PERMISSION_GRANTED

            requester.hasSmsPermission() -> true
            else -> requester.requestSmsPermission()
        }
        if (!granted) return SmsDispatchResult(permissionDenied = true, failed = recipients.size)

        val manager = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }.getOrNull() ?: return SmsDispatchResult(failed = recipients.size)

        var sent = 0
        var failed = 0
        recipients.forEach { recipient ->
            val number = digits(recipient.phone)
            // Long messages must go out as multipart or the radio silently truncates them,
            // and this one is deliberately longer than 160 characters.
            val result = runCatching {
                val parts = manager.divideMessage(body)
                if (parts.size <= 1) {
                    manager.sendTextMessage(number, null, body, null, null)
                } else {
                    manager.sendMultipartTextMessage(number, null, parts, null, null)
                }
            }
            if (result.isSuccess) {
                sent++
            } else {
                failed++
                Log.w(TAG, "Emergency SMS to ${recipient.name} failed", result.exceptionOrNull())
            }
        }
        return SmsDispatchResult(sent = sent, failed = failed)
    }

    override fun readSettingsJson(): String? = prefs.getString(KEY_SETTINGS, null)

    override fun writeSettingsJson(json: String) {
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }

    override fun subscribeToAlertsTopic() {
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic("alerts") }
    }

    override fun nowMillis(): Long = System.currentTimeMillis()

    override val phoneAuthSupported: Boolean = true

    override suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest {
        val activity = currentActivity()
            ?: return PhoneCodeRequest.Failed("Open the app before requesting a code.")
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: return PhoneCodeRequest.Failed("Sign-in is not configured on this build.")

        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

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

    override fun canLaunchAlertOverOtherApps(): Boolean =
        runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)

    override fun openOverlaySettings() {
        val direct = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { appContext.startActivity(direct) }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    override val photoPickerSupported: Boolean = true

    override suspend fun pickProfilePhoto(): String? =
        (currentActivity() as? ProfilePhotoPicker)?.pickProfilePhoto()
}
