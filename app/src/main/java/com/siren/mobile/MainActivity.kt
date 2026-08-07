package com.siren.mobile

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.AndroidPlatformServices
import com.siren.mobile.ui.App

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ALERT_ID = AndroidPlatformServices.EXTRA_ALERT_ID
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the system splash until Firebase has told us whether we're signed in.
        splash.setKeepOnScreenCondition { !SirenRepository.authResolved.value }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Tapping the notification — or a full-screen intent firing — lands here. */
    private fun handleIntent(intent: Intent?) {
        val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
        showOverLockScreen()
        SirenRepository.showAlertById(alertId)
    }

    /**
     * Brings the alert into view on a locked, dark phone.
     *
     * The full-screen intent launches this activity, but on a locked device that alone
     * puts it *behind* the keyguard with the screen still off — the alarm sounds and the
     * user sees nothing. These three calls are what turn the display on, draw above the
     * lock screen, and keep it lit while the alert is up.
     *
     * The keyguard is only asked to dismiss if it is not secured: `requestDismissKeyguard`
     * on a PIN-protected phone prompts for the PIN, which is precisely the wrong thing to
     * put between somebody and an earthquake warning. Showing over the lock screen is
     * enough, and the "I'm safe" / "I need help" actions work from there.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardSecure == false) {
            runCatching { keyguard.requestDismissKeyguard(this, null) }
        }
    }
}
