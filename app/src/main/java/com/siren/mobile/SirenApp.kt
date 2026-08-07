package com.siren.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.AndroidPlatformServices
import com.siren.mobile.platform.Platform
import java.lang.ref.WeakReference

class SirenApp : Application() {

    /**
     * The activity currently in the foreground.
     *
     * Phone verification needs a real Activity for its reCAPTCHA fallback, and
     * AndroidPlatformServices only holds an application context. Weak so a destroyed
     * activity is never held alive by this reference.
     */
    private var foreground: WeakReference<Activity>? = null

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                foreground = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (foreground?.get() === activity) foreground = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // The shared library cannot see MainActivity or this module's resources, so
        // they are injected here.
        val services = AndroidPlatformServices(
            context = this,
            activityClass = MainActivity::class.java,
            smallIconRes = R.drawable.ic_notification,
            alarmSoundRes = R.raw.siren_alarm,
            versionName = BuildConfig.VERSION_NAME,
            currentActivity = { foreground?.get()?.takeIf { !it.isFinishing } },
        )
        services.ensureChannels()
        Platform.install(services)

        // Starts the auth listener and Firestore subscriptions before the first frame.
        SirenRepository.start()
    }
}
