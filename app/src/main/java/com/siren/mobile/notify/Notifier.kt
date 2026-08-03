package com.siren.mobile.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.siren.mobile.MainActivity
import com.siren.mobile.R
import com.siren.mobile.model.Intensity

/**
 * Builds the alert notification. Escalation follows the prototype's intensity scale:
 * Green is a quiet single buzz, Yellow repeats, Red adds the alarm sound and a
 * full-screen intent so the alert takes over the lock screen.
 */
object Notifier {

    const val CHANNEL_ALERTS = "siren_alerts"
    private const val CHANNEL_QUIET = "siren_alerts_minor"
    private const val NOTIFICATION_ID = 4101

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alert_channel_desc)
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

    fun showAlert(
        context: Context,
        alertId: String,
        intensity: Intensity,
        magnitudeG: Double,
    ) {
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ALERT_ID, alertId)
        }
        val pending = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val magnitude = String.format(java.util.Locale.US, "%.2fg", magnitudeG)
        val title = when (intensity) {
            Intensity.GREEN -> "Minor tremor detected — $magnitude"
            Intensity.YELLOW -> "Earthquake detected — $magnitude"
            Intensity.RED -> "Earthquake detected — $magnitude"
        }
        val body = when (intensity) {
            Intensity.GREEN -> "No action needed. Logged for the record."
            else -> "Drop, cover, hold on. Tap to confirm your status."
        }

        val builder = NotificationCompat.Builder(
            context,
            if (intensity == Intensity.GREEN) CHANNEL_QUIET else CHANNEL_ALERTS,
        )
            .setSmallIcon(R.drawable.ic_notification)
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
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
