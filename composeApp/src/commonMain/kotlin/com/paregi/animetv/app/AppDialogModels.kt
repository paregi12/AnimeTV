package com.paregi.animetv.app

import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode

internal data class TrackingPromptState(
    val anime: Anime,
    val episode: Episode,
    val providers: List<AccountProvider>
)
