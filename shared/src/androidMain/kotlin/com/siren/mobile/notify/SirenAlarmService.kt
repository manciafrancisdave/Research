package com.siren.mobile.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.platform.Platform

/**
 * Plays the emergency alarm from a foreground service.
 *
 * A foreground service is the point of this class: audio driven from a composable or
 * an Activity dies the moment the app is backgrounded, which is exactly when an
 * earthquake alarm most needs to keep sounding.
 *
 * For [Intensity.RED] the alarm loops indefinitely and deliberately ignores audio-focus
 * loss. It ends only when the user acts — "I'm Safe", "I Need Help" or "Stop alarm" —
 * never on a timer and never because the notification was swiped away.
 */
class SirenAlarmService : Service() {

    companion object {
        private const val TAG = "SirenAlarm"

        const val ACTION_START = "com.siren.mobile.alarm.START"
        const val ACTION_STOP = "com.siren.mobile.alarm.STOP"
        const val ACTION_SAFE = "com.siren.mobile.alarm.SAFE"
        const val ACTION_HELP = "com.siren.mobile.alarm.HELP"

        const val EXTRA_ALERT_ID = "alertId"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_MAGNITUDE = "magnitude"
        const val EXTRA_TIMEOUT_MS = "timeoutMs"
        const val EXTRA_VIBRATE = "vibrate"

        /** Its own channel, silent: the audio comes from MediaPlayer, so letting the
         *  notification play a sound too would double it up. */
        private const val CHANNEL_ALARM = "siren_alarm_playback"
        private const val NOTIFICATION_ID = 4102
        private const val WATCHDOG_INTERVAL_MS = 2_000L

        /**
         * Injected by AndroidPlatformServices — this class lives in the shared library
         * and cannot see the app module's R class or MainActivity.
         */
        @Volatile
        var soundResId: Int = 0

        @Volatile
        var smallIconResId: Int = 0

        @Volatile
        var activityClass: Class<*>? = null
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentAlertId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var shouldRun = false

    /**
     * The alarm must never fall silent while an event is unanswered. MediaPlayer looping
     * can stall — an MP3's encoder padding leaves a gap at the wrap point, and the OS or
     * another app can interrupt playback outright. This re-starts it if that happens.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!shouldRun) return
            player?.let { p ->
                if (!p.isPlaying) runCatching { p.start() }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)

            ACTION_STOP -> stopEverything()

            ACTION_SAFE -> {
                currentAlertId?.let { SirenRepository.submitMyResponse(it, ResponseStatus.SAFE) }
                stopEverything()
            }

            ACTION_HELP -> {
                currentAlertId?.let { SirenRepository.submitMyResponse(it, ResponseStatus.NEEDS_HELP) }
                stopEverything()
            }

            else -> stopEverything()
        }
        // START_NOT_STICKY: if the process is killed we do not want a stale alarm
        // resurrecting itself with no event behind it.
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID).orEmpty()
        val intensity = Intensity.fromName(intent.getStringExtra(EXTRA_INTENSITY))
        val magnitude = intent.getDoubleExtra(EXTRA_MAGNITUDE, 0.0)
        val timeout = intent.getLongExtra(EXTRA_TIMEOUT_MS, 0L)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)

        // Re-issuing START for the alarm already sounding must not restart it.
        if (currentAlertId == alertId && player?.isPlaying == true) return

        currentAlertId = alertId
        shouldRun = true
        ensureChannel()
        startInForeground(alertId, intensity, magnitude)

        acquireWakeLock()
        requestFocus(intensity)
        startPlayback(intensity)
        if (vibrate) startVibration(intensity)

        Platform.setAlarmActive(true)

        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)

        timeoutRunnable?.let(handler::removeCallbacks)
        if (timeout > 0L) {
            // Yellow steps down after a while. Red never does.
            timeoutRunnable = Runnable { stopEverything() }.also {
                handler.postDelayed(it, timeout)
            }
        }
    }

    // ------------------------------------------------------------- playback

    private fun startPlayback(intensity: Intensity) {
        if (soundResId == 0) {
            Log.w(TAG, "No alarm sound resource injected; alarm will be silent")
            return
        }
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_ALARM is what lets this play through silent mode and DND.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                resources.openRawResourceFd(soundResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = intensity != Intensity.GREEN
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure { Log.e(TAG, "Alarm playback failed", it) }
    }

    private fun requestFocus(intensity: Intensity) {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener {
                    // Intentionally empty for Red: losing focus (a call, another app)
                    // must not silence an earthquake alarm.
                    if (intensity == Intensity.GREEN) stopEverything()
                }
                .build()
                .also { am.requestAudioFocus(it) }
        }
    }

    private fun startVibration(intensity: Intensity) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = when (intensity) {
            Intensity.GREEN -> longArrayOf(0, 250)
            Intensity.YELLOW -> longArrayOf(0, 400, 200, 400, 200, 400)
            Intensity.RED -> longArrayOf(0, 800, 200, 800, 200, 1200, 300)
        }
        // repeat index 0 => loops until cancelled, for anything above Green.
        val repeat = if (intensity == Intensity.GREEN) -1 else 0
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat)) }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siren:alarm").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        }
    }

    // -------------------------------------------------------- notification

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            "Earthquake alarm",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "The alarm that sounds until you confirm your safety status"
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            // Silent + no vibration: this service drives both itself.
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun command(action: String): PendingIntent {
        val intent = Intent(this, SirenAlarmService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startInForeground(alertId: String, intensity: Intensity, magnitude: Double) {
        val open = activityClass?.let {
            PendingIntent.getActivity(
                this,
                alertId.hashCode(),
                Intent(this, it)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("extra_alert_id", alertId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(if (smallIconResId != 0) smallIconResId else android.R.drawable.stat_sys_warning)
            .setContentTitle("Earthquake detected — ${intensity.levelText}")
            .setContentText("Confirm your status to silence the alarm.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Ongoing + not auto-cancel: swiping must not silence the alarm.
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            // These actions are the required fallback when the full-screen intent is
            // denied (Android 14+ restricts USE_FULL_SCREEN_INTENT). Without them a
            // user could be left with a looping alarm and no visible way to stop it.
            .addAction(0, "I'm safe", command(ACTION_SAFE))
            .addAction(0, "I need help", command(ACTION_HELP))
            .addAction(0, "Stop alarm", command(ACTION_STOP))

        open?.let {
            builder.setContentIntent(it)
            if (intensity == Intensity.RED) builder.setFullScreenIntent(it, true)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ----------------------------------------------------------- teardown

    private fun stopEverything() {
        shouldRun = false
        handler.removeCallbacks(watchdog)
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { req ->
                (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(req)
            }
        }
        focusRequest = null

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null

        currentAlertId = null
        Platform.setAlarmActive(false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Belt and braces: every exit path must release the wake lock and audio focus. */
    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
