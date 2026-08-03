package com.siren.mobile.platform

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.siren.mobile.model.Intensity

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
    override val versionName: String,
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

        val magnitude = String.format(java.util.Locale.US, "%.2fg", magnitudeG)
        val title = if (intensity == Intensity.GREEN) {
            "Minor tremor detected — $magnitude"
        } else {
            "Earthquake detected — $magnitude"
        }
        val body = if (intensity == Intensity.GREEN) {
            "No action needed. Logged for the record."
        } else {
            "Drop, cover, hold on. Tap to confirm your status."
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
}
