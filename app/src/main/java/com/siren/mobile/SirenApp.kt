package com.siren.mobile

import android.app.Application
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.AndroidPlatformServices
import com.siren.mobile.platform.Platform

class SirenApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // The shared library cannot see MainActivity or this module's resources, so
        // they are injected here.
        val services = AndroidPlatformServices(
            context = this,
            activityClass = MainActivity::class.java,
            smallIconRes = R.drawable.ic_notification,
            versionName = BuildConfig.VERSION_NAME,
        )
        services.ensureChannels()
        Platform.install(services)

        // Starts the auth listener and Firestore subscriptions before the first frame.
        SirenRepository.start()
    }
}
