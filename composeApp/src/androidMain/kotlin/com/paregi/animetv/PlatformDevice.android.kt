package com.paregi.animetv

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun platformIsTelevision(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration.uiMode) {
        val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}

@Composable
actual fun platformIsTouchDevice(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val hasTouchscreenFeature = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
    var isTouch by remember(context, hasTouchscreenFeature) {
        mutableStateOf(hasTouchscreenFeature)
    }
    val window = remember(context) { context.findActivity()?.window }

    DisposableEffect(window, view, isTouch) {
        if (isTouch) {
            return@DisposableEffect onDispose {}
        }
        if (window != null) {
            val originalCallback = window.callback
            if (originalCallback != null) {
                val wrapper = TouchObservingWindowCallback(originalCallback) {
                    isTouch = true
                }
                window.callback = wrapper
                return@DisposableEffect onDispose {
                    if (window.callback === wrapper) {
                        window.callback = originalCallback
                    }
                }
            }
        }
        view.setOnTouchListener { _, event ->
            if (isPhysicalTouchDown(event)) {
                isTouch = true
            }
            false
        }
        onDispose {
            view.setOnTouchListener(null)
        }
    }

    return isTouch
}

@Composable
actual fun platformIsLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }
}

private class TouchObservingWindowCallback(
    private val delegate: Window.Callback,
    private val onTouchDetected: () -> Unit
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && isPhysicalTouchDown(event)) {
            onTouchDetected()
        }
        return delegate.dispatchTouchEvent(event)
    }
}

private fun isPhysicalTouchDown(event: MotionEvent): Boolean {
    val action = event.actionMasked
    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) {
        return false
    }
    val toolType = event.getToolType(event.actionIndex)
    return event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
        toolType == MotionEvent.TOOL_TYPE_FINGER
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
