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
        // A push carrying only a `notification` block is drawn by the system tray and
        // never reaches this method while the app is killed — which is the exact case
        // the alarm exists for. The sender must therefore use a DATA-ONLY payload with
        // priority "high". Bailing out quietly here is what that misconfiguration looks
        // like from the phone's side, so it is logged rather than silently dropped.
        val alertId = data["alertId"] ?: data["alert_id"]
        if (alertId.isNullOrBlank()) {
            Log.w(
                "SirenMessaging",
                "Push had no alertId (data=${data.keys}). Send a data-only, high-priority " +
                    "message with alertId/intensity/magnitudeG or the alarm cannot fire.",
            )
            return
        }
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
        //
        // Green is excluded on purpose. It is informational — startAlarm treats it as
        // a single chime with no foreground service, and the Firestore listener no
        // longer raises it full-screen either. Letting the push path take over the
        // screen anyway would make the same event behave differently depending on
        // whether it arrived by push or by snapshot.
        //
        // Tapping the notification still opens it: that path runs through
        // MainActivity, and is a deliberate user action rather than an interruption.
        if (intensity != Intensity.GREEN) {
            SirenRepository.showAlertById(alertId)
        }
    }
}
