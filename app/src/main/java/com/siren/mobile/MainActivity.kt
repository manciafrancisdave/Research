package com.siren.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.ui.AppRoot
import com.siren.mobile.ui.theme.SirenTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ALERT_ID = "extra_alert_id"
    }

    private val repo by lazy { SirenRepository.get(applicationContext) }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the system splash until Firebase has told us whether we're signed in.
        splash.setKeepOnScreenCondition { !repo.authResolved.value }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            SirenTheme(darkTheme = settings.darkMode) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Tapping the notification deep-links straight to that alert. */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ALERT_ID)?.let { repo.showAlertById(it) }
    }
}
