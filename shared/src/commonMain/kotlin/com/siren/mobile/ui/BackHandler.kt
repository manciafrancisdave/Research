package com.siren.mobile.ui

import androidx.compose.runtime.Composable

/**
 * Hardware/gesture back. Android maps this to the system back button; iOS has no
 * global back affordance, so the actual there is a no-op — every sub-screen already
 * carries its own back arrow in the header.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
