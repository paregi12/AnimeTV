package com.paregi.animetv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformPlaybackOrientation(landscape: Boolean) {
    val activity = LocalContext.current.findActivity()
    var forcedLandscape by remember(activity) { mutableStateOf(false) }

    LaunchedEffect(activity, landscape) {
        if (activity == null) return@LaunchedEffect
        if (landscape) {
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            forcedLandscape = true
        } else if (forcedLandscape) {
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_USER) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            }
            forcedLandscape = false
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null && forcedLandscape &&
                activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_USER
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
