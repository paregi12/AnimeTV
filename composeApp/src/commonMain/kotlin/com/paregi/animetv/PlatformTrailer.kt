package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Embedded AniList trailer surface, provided by the Android host. */
@Composable
expect fun PlatformTrailer(
    youtubeId: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackState: ((String) -> Unit)? = null
)
