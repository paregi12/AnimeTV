package com.paregi.animetv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier

internal val LocalNavigationSound = compositionLocalOf<() -> Unit> { {} }

@Composable
expect fun rememberPlatformClickSound(enabled: Boolean): () -> Unit

@Composable
internal fun Modifier.navigationClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val playClickSound = LocalNavigationSound.current
    return clickable(enabled = enabled) {
        playClickSound()
        onClick()
    }
}

@Composable
internal fun Modifier.navigationFocusable(enabled: Boolean = true): Modifier {
    val playClickSound = LocalNavigationSound.current
    return onFocusChanged { if (it.isFocused) playClickSound() }
        .focusable(enabled = enabled)
}
