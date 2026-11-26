package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Imperative playback controller for an embedded [PlatformTrailer]. */
@Stable
class PlatformTrailerController internal constructor() {
    internal var commandDispatcher: ((String) -> Unit)? = null

    fun play() {
        commandDispatcher?.invoke("play")
    }

    fun pause() {
        commandDispatcher?.invoke("pause")
    }

    fun toggle() {
        commandDispatcher?.invoke("toggle")
    }

    fun toggleMute() {
        commandDispatcher?.invoke("mute")
    }
}

@Composable
fun rememberPlatformTrailerController(): PlatformTrailerController =
    remember { PlatformTrailerController() }

/** Embedded AniList trailer surface, provided by the Android host. */
@Composable
expect fun PlatformTrailer(
    youtubeId: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackState: ((String) -> Unit)? = null,
    controller: PlatformTrailerController? = null,
    hideUntilPlaying: Boolean = true
)
