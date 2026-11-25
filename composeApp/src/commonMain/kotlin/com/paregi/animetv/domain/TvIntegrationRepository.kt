package com.paregi.animetv.domain

interface TvIntegrationRepository {
    suspend fun updateHomeChannels(feed: HomeFeed)
    suspend fun updateContinueWatching(
        anime: Anime,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        force: Boolean = false
    )
    suspend fun clearContinueWatching()
    fun setLaunchHandler(handler: ((TvLaunchRequest) -> Unit)?)
}

data class TvLaunchRequest(
    val anime: Anime,
    val episodeId: String? = null,
    val positionMs: Long = 0L
)
