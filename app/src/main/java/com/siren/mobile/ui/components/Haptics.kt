package com.siren.mobile.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.siren.mobile.model.Intensity

/**
 * Vibration escalates with intensity, mirroring the prototype's intensity scale:
 * Green a single buzz, Yellow a repeating pattern, Red a long continuous alarm.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun forIntensity(context: Context, intensity: Intensity) {
        val pattern = when (intensity) {
            Intensity.GREEN -> longArrayOf(0, 250)
            Intensity.YELLOW -> longArrayOf(0, 400, 200, 400, 200, 400)
            Intensity.RED -> longArrayOf(0, 800, 200, 800, 200, 1200, 200, 1200)
        }
        play(context, pattern)
    }

    fun tap(context: Context) = play(context, longArrayOf(0, 20))

    fun confirm(context: Context) = play(context, longArrayOf(0, 40, 60, 40))

    private fun play(context: Context, pattern: LongArray) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    fun cancel(context: Context) {
        runCatching { vibrator(context)?.cancel() }
    }
}
