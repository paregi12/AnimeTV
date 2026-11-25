package com.paregi.animetv

import androidx.compose.runtime.Composable

/** Renders a QR code using the Android host implementation. */
@Composable
expect fun PlatformQrCode(value: String)
