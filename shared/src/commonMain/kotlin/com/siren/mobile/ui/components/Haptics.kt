package com.siren.mobile.ui.components

import com.siren.mobile.model.Intensity
import com.siren.mobile.platform.Platform

/**
 * Vibration escalates with intensity — Green a single buzz, Yellow a repeating
 * pattern, Red a long continuous alarm. The patterns themselves live in each
 * platform's PlatformServices implementation.
 */
object Haptics {
    fun forIntensity(intensity: Intensity) = Platform.services.vibrateForIntensity(intensity)
    fun tap() = Platform.services.vibrateTap()
    fun confirm() = Platform.services.vibrateConfirm()
    fun cancel() = Platform.services.cancelVibration()
}
