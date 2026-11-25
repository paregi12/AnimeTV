package com.paregi.animetv

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformClickSound(enabled: Boolean): () -> Unit {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val lastSoundAtMs = remember(audioManager, enabled) { mutableLongStateOf(0L) }
    return remember(audioManager, enabled, lastSoundAtMs) {
        {
            val now = SystemClock.uptimeMillis()
            if (enabled && now - lastSoundAtMs.longValue >= 120L) {
                lastSoundAtMs.longValue = now
                runCatching { audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f) }
            }
        }
    }
}
