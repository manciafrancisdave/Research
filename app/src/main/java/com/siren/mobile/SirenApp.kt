package com.siren.mobile

import android.app.Application
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.notify.Notifier

class SirenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        // Eagerly built so the auth listener and Firestore listeners are live before
        // the first screen composes.
        SirenRepository.get(this)
    }
}
