package com.siren.mobile.notify

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Intensity
import com.siren.mobile.platform.Platform

/**
 * Receives the push the hardware bridge triggers. The payload carries the alert id so
 * the app can pull the authoritative document from Firestore rather than trusting the
 * notification body.
 */
class SirenMessagingService : FirebaseMessagingService() {

    // Topic subscription is re-issued on every app start from SirenRepository.start(),
    // so overriding the (now deprecated) onNewToken is unnecessary here.

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val alertId = data["alertId"] ?: data["alert_id"] ?: return
        val magnitude = (data["magnitudeG"] ?: data["magnitude_g"])?.toDoubleOrNull() ?: 0.0
        val intensity = data["intensity"]
            ?.let { Intensity.fromName(it) }
            ?: Intensity.fromMagnitude(magnitude)

        Log.i("SirenMessaging", "alert $alertId $intensity ${magnitude}g")

        // Starts the escalating alarm (and its own notification). Red loops until the
        // user explicitly dismisses it.
        Platform.services.startAlarm(
            alertId,
            intensity,
            magnitude,
            SirenRepository.settings.value.vibration,
        )

        // Surfaces the full-screen alert if the app is already in the foreground.
        SirenRepository.showAlertById(alertId)
    }
}
