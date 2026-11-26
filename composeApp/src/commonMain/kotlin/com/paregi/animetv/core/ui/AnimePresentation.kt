package com.paregi.animetv.core.ui

import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.displayRPlusRating
import kotlin.math.roundToInt

internal fun imageCdnUrl(url: String?, enabled: Boolean): String? {
    if (url.isNullOrBlank() || !enabled || url.contains("wsrv.nl", ignoreCase = true)) return url
    val supportedHost = listOf(
        "img.flawlessfiles.com/",
        "cdn.noitatnemucod.net/",
        "img.zorores.com/"
    ).any { url.contains(it, ignoreCase = true) }
    if (!supportedHost) return url
    return "https://wsrv.nl/?url=${encodeURIComponent(url)}&w=256&we"
}

private fun encodeURIComponent(value: String): String = buildString {
    val hex = "0123456789ABCDEF"
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        val safe = code in 0x41..0x5A || code in 0x61..0x7A || code in 0x30..0x39 ||
            code == 0x2D || code == 0x5F || code == 0x2E || code == 0x21 ||
            code == 0x7E || code == 0x2A || code == 0x27 || code == 0x28 || code == 0x29
        if (safe) append(code.toChar()) else {
            append('%')
            append(hex[code shr 4])
            append(hex[code and 0x0F])
        }
    }
}

internal fun displayAnimeTitle(anime: Anime, japaneseTitles: Boolean): String =
    if (japaneseTitles) anime.originalTitle?.takeIf { it.isNotBlank() } ?: anime.title else anime.title

internal fun displayAnimeSynopsis(synopsis: String): String = synopsis
    .replace(Regex("(?i)<br\\s*/?>|</(p|div|li)\\s*>"), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("(?i)&nbsp;"), " ")
    .replace(Regex("(?i)&amp;|&#38;"), "&")
    .replace(Regex("(?i)&quot;|&#34;"), "\"")
    .replace(Regex("(?i)&#39;|&apos;"), "'")
    .replace(Regex("(?i)&lt;"), "<")
    .replace(Regex("(?i)&gt;"), ">")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{2,}"), "\n")
    .trim()

internal enum class CardBadgeMode {
    Catalog,
    Schedule,
    AniListMyList,
    MalMyList,
    LocalMyList
}

internal data class CardBadgeData(
    val format: String? = null,
    val subEpisode: Int? = null,
    val dubEpisode: Int? = null,
    val totalEpisodes: Int? = null,
    val airedEpisode: Int? = null,
    val isAdult: Boolean = false,
    val rating: String? = null,
    val score: Int? = null,
    val scoreFormatted: String? = null,
    val duration: String? = null,
    val year: String? = null,
    val isUpcoming: Boolean = false,
    val progressPercent: Float? = null,
    val progressEpisode: String? = null,
    val hasNextAired: Boolean = false,
    val airingTime: String? = null,
    val airingAired: Boolean = false
)

internal fun resolveCardBadges(
    anime: Anime,
    progressPercent: Float? = null,
    progressEpisode: String? = null,
    airingAired: Boolean? = null,
    airingTime: String? = null,
    mode: CardBadgeMode = CardBadgeMode.Catalog,
    showBottomBadges: Boolean = false
): CardBadgeData {
    val normFormat = anime.format
        ?.replace("TV_SHORT", "TV", ignoreCase = true)
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?.uppercase()
    val rPlusRating = displayRPlusRating(anime.rating, anime.adult)
    val isAdult = rPlusRating != null
    val nextAiredEp = anime.nextAiringEpisode?.let { it - 1 }?.takeIf { it > 0 }
    val watchedNum = progressEpisode?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0
    val isAniList = anime.id.startsWith("anilist:")

    return when (mode) {
        CardBadgeMode.LocalMyList -> CardBadgeData(
            format = normFormat,
            progressPercent = progressPercent,
            progressEpisode = progressEpisode?.takeIf { it.isNotBlank() }
        )
        CardBadgeMode.MalMyList -> CardBadgeData(
            format = normFormat,
            totalEpisodes = anime.episodeCount?.takeIf { it > 0 },
            progressEpisode = progressEpisode?.takeIf { it.isNotBlank() }
        )
        CardBadgeMode.AniListMyList -> CardBadgeData(
            format = normFormat,
            totalEpisodes = anime.episodeCount?.takeIf { it > 0 },
            airedEpisode = nextAiredEp,
            isAdult = isAdult,
            rating = rPlusRating,
            progressEpisode = progressEpisode?.takeIf { it.isNotBlank() },
            hasNextAired = nextAiredEp != null && watchedNum < nextAiredEp
        )
        CardBadgeMode.Schedule -> {
            val isMovie = normFormat == "MOVIE"
            val totalEpisodes = if (isMovie) null else anime.episodeCount?.takeIf { it > 0 }
            val airedEpisode = if (isMovie) {
                1
            } else {
                nextAiredEp ?: if (anime.status?.contains("airing", ignoreCase = true) == true) {
                    anime.episodeCount?.takeIf { it > 0 }
                } else null
            }
            CardBadgeData(
                format = normFormat,
                totalEpisodes = totalEpisodes,
                airedEpisode = airedEpisode,
                isAdult = isAdult,
                rating = rPlusRating,
                progressPercent = progressPercent,
                progressEpisode = progressEpisode,
                airingTime = airingTime,
                airingAired = airingAired ?: false
            )
        }
        CardBadgeMode.Catalog -> {
            if (!isAniList) {
                val haveEpCount = (if (anime.latestSubEpisode > 0) 1 else 0) + (if (anime.latestDubEpisode > 0) 1 else 0)
                val positiveEpisodeCount = anime.episodeCount?.takeIf { it > 0 }
                CardBadgeData(
                    format = normFormat,
                    subEpisode = anime.latestSubEpisode.takeIf { it > 0 },
                    dubEpisode = anime.latestDubEpisode.takeIf { it > 0 },
                    totalEpisodes = if (haveEpCount < 2) positiveEpisodeCount else null,
                    airedEpisode = if (haveEpCount == 0) positiveEpisodeCount else null,
                    progressPercent = progressPercent,
                    progressEpisode = progressEpisode,
                    airingTime = airingTime,
                    airingAired = airingAired ?: false
                )
            } else {
                val allowBottom = showBottomBadges
                val durationFormatted = if (allowBottom) {
                    anime.duration?.let { dur ->
                        Regex("\\d+").find(dur)?.value?.let { "$it min" } ?: dur.lowercase()
                    }
                } else null
                val scoreVal = if (allowBottom) {
                    anime.score?.takeIf { it > 0f }?.let { if (it > 10f) it / 10f else it }
                } else null
                val scoreFormatted = scoreVal?.takeIf { it > 0f }?.let {
                    val rounded = kotlin.math.round(it * 10f) / 10f
                    if (rounded % 1f == 0f) "${rounded.toInt()}.0" else rounded.toString()
                }
                val scorePercent = if (allowBottom) {
                    anime.score?.takeIf { it > 0f }?.let { raw ->
                        if (raw <= 10f) (raw * 10f).roundToInt() else raw.roundToInt()
                    }?.coerceIn(0, 100)
                } else null
                val isMovie = normFormat == "MOVIE"
                val totalEpisodes = if (isMovie) null else anime.episodeCount?.takeIf { it > 0 }
                val airedEpisode = if (isMovie) {
                    1
                } else {
                    nextAiredEp ?: if (anime.status?.contains("airing", ignoreCase = true) == true) {
                        anime.episodeCount?.takeIf { it > 0 }
                    } else null
                }
                val isUpcoming = allowBottom && anime.status?.contains("not_yet", ignoreCase = true) == true
                CardBadgeData(
                    format = normFormat,
                    totalEpisodes = totalEpisodes,
                    airedEpisode = airedEpisode,
                    isAdult = allowBottom && isAdult,
                    rating = if (allowBottom) rPlusRating else null,
                    score = scorePercent,
                    scoreFormatted = scoreFormatted,
                    duration = durationFormatted,
                    year = if (allowBottom) anime.year?.toString() else null,
                    isUpcoming = isUpcoming,
                    progressPercent = progressPercent,
                    progressEpisode = progressEpisode,
                    airingTime = airingTime,
                    airingAired = airingAired ?: false
                )
            }
        }
    }
}
