package com.paregi.animetv.domain

data class Anime(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val titleAliases: List<String> = emptyList(),
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val trailerId: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val score: Float? = null,
    val status: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val episodeCount: Int? = null,
    val format: String? = null,
    val adult: Boolean = false,
    val latestSubEpisode: Int = 0,
    val latestDubEpisode: Int = 0,
    val malId: String? = null,
    val aniListId: String? = null,
    val sourceIndex: Int = SourceCatalog.GOTAKU_INDEX,
    val nextAiringEpisode: Int? = null
)

data class Episode(
    val id: String,
    val animeId: String,
    val number: String,
    val title: String? = null,
    val watchedPositionMs: Long = 0,
    val durationMs: Long = 0,
    val hasSub: Boolean = true,
    val hasDub: Boolean = false,
    val filler: Boolean = false,
    val imageUrl: String? = null
)

data class WatchProgress(
    val animeId: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    val anime: Anime? = null
)

data class AiringEntry(
    val anime: Anime,
    val episodeNumber: String,
    val airingAtEpochSeconds: Long
)

data class SearchFilters(
    val query: String = "",
    val types: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val status: Set<String> = emptySet(),
    val year: Int? = null,
    val sort: String = "latest",
    val season: String? = null,
    val aniList: Boolean = false
)

data class SubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val url: String,
    val defaultSelected: Boolean = false
)

data class SkipRange(
    val startSeconds: Double,
    val endSeconds: Double,
    val kind: Kind
) {
    enum class Kind { INTRO, OUTRO, FILLER }
}

enum class PlaybackStreamType { SUB, DUB }

data class PlaybackChoice(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val skipRanges: List<SkipRange> = emptyList(),
    val selectedStreamType: PlaybackStreamType = PlaybackStreamType.SUB,
    val streamServerNames: List<String> = emptyList(),
    val selectedStreamServerIndex: Int = 0,
    val keySeed: String? = null,
    val segmentBytes: Int = 0
)

data class AniListReview(
    val score: Int?,
    val rating: Int?,
    val summary: String
)

data class AniListAnimeInfo(
    val id: String,
    val malId: String? = null,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val trailerId: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val adult: Boolean = false,
    val genres: List<String> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val source: String? = null,
    val countryOfOrigin: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val nextAiringEpisode: Int? = null,
    val durationMinutes: Int? = null,
    val score: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val trending: Int? = null,
    val studios: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val reviews: List<AniListReview> = emptyList()
)

