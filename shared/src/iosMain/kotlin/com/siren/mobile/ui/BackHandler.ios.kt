package com.siren.mobile.ui

import androidx.compose.runtime.Composable

/** iOS has no system back button; in-screen back arrows cover navigation instead. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
