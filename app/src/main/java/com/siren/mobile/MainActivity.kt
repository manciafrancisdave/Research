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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.AndroidPlatformServices
import com.siren.mobile.platform.ProfilePhotoEncoder
import com.siren.mobile.platform.ProfilePhotoPicker
import com.siren.mobile.ui.App
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), ProfilePhotoPicker {

    companion object {
        const val EXTRA_ALERT_ID = AndroidPlatformServices.EXTRA_ALERT_ID
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Non-null only while the photo picker is open. */
    private var pendingPhoto: CancellableContinuation<String?>? = null

    /**
     * The system photo picker.
     *
     * `PickVisualMedia` needs no storage permission at all — the user chooses one image
     * and the app receives only that. Asking for READ_MEDIA_IMAGES to set an avatar
     * would be asking to read the entire gallery.
     */
    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val waiting = pendingPhoto
            pendingPhoto = null
            if (waiting == null || !waiting.isActive) return@registerForActivityResult
            // Decoding and downscaling happen off the main thread: the encoder reads and
            // re-compresses a photo that can be several megapixels.
            lifecycleScope.launch {
                val encoded = uri?.let {
                    withContext(Dispatchers.IO) { ProfilePhotoEncoder.encode(this@MainActivity, it) }
                }
                if (waiting.isActive) waiting.resume(encoded)
            }
        }

    override suspend fun pickProfilePhoto(): String? = suspendCancellableCoroutine { cont ->
        // Only one picker at a time; a second request cancels the first rather than
        // leaving a continuation that nothing will ever resume.
        pendingPhoto?.takeIf { it.isActive }?.resume(null)
        pendingPhoto = cont
        cont.invokeOnCancellation { pendingPhoto = null }
        runCatching {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            pendingPhoto = null
            if (cont.isActive) cont.resume(null)
        }
    }

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
