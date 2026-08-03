package com.siren.mobile

import androidx.compose.ui.window.ComposeUIViewController
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.IosPlatformServices
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.App
import platform.UIKit.UIViewController

/**
 * iOS entry point, called from Swift:
 *
 *     Main_iosKt.MainViewController()
 *
 * FirebaseApp.configure() must already have run in the Swift AppDelegate before this
 * is called, otherwise Firebase.auth/firestore will fail.
 *
 * NOT YET COMPILED — Apple targets cannot be built on Windows. See CLAUDE.md.
 */
fun MainViewController(): UIViewController {
    val services = IosPlatformServices(versionName = "2.4.0")
    Platform.install(services)
    services.requestNotificationPermission()
    SirenRepository.start()

    return ComposeUIViewController { App() }
}
