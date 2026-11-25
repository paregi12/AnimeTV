package com.paregi.animetv.domain

interface AnimeRepository {
    suspend fun selectSource(index: Int): Int = index
    suspend fun selectSource(index: Int, domain: String?): Int = selectSource(index)
    suspend fun benchmarkDomain(sourceIndex: Int, domain: String): DomainBenchmarkResult =
        DomainBenchmarkResult(domain = domain, latencyMs = 0, success = true)
    suspend fun benchmarkDomains(sourceIndex: Int, domains: List<String>): List<DomainBenchmarkResult> =
        domains.map { benchmarkDomain(sourceIndex, it) }
    suspend fun loadHome(
        useAniListTrending: Boolean = false,
        highResolutionImages: Boolean = false
    ): HomeFeed
    suspend fun search(filters: SearchFilters, page: Int = 1): SearchPage
    suspend fun loadDetails(animeId: String): AnimeDetails
    suspend fun loadAniListInfo(anime: Anime): AniListAnimeInfo? = null
    suspend fun loadSchedule(day: Int? = null, includeNonJapan: Boolean = false): List<AiringEntry>
    suspend fun loadEpisodes(animeId: String): List<Episode>
    suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean = false,
        mirrorIndex: Int = 0,
        subtitleLanguage: String = "",
        hasSub: Boolean = true,
        requestedStreamType: PlaybackStreamType? = null
    ): PlaybackChoice
    suspend fun translatePlaybackSubtitle(
        choice: PlaybackChoice,
        targetLanguage: String
    ): SubtitleTrack? = null
}
