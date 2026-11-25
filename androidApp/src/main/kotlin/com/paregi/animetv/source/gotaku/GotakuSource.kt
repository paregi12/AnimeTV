package com.paregi.animetv.source.gotaku

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Gotaku-specific response mapping kept outside the repository coordinator. */
internal object GotakuSource {
    fun anime(json: JSONObject, fallbackId: String? = null): Anime? {
        val id = json.optString("id").ifBlank { json.optString("slug") }.ifBlank { fallbackId.orEmpty() }
        if (id.isBlank()) return null
        val episodes = json.optJSONObject("episodes")
        val links = json.optJSONObject("links")
        val durationMs = json.optLong("duration")
        val episodeDurationSeconds = json.optLong("episode_duration")
        return Anime(
            id = id,
            title = json.optString("name").ifBlank { json.optString("title") }.ifBlank { id },
            originalTitle = json.optJSONArray("alt_names")?.optString(0)?.takeIf(String::isNotBlank),
            titleAliases = json.optJSONArray("alt_names").toSourceStringList(),
            posterUrl = (json.optString("poster_url").ifBlank { json.optString("poster") }).takeIf(String::isNotBlank),
            bannerUrl = (json.optString("backdrop_url").ifBlank { json.optString("banner_url") }).takeIf(String::isNotBlank),
            synopsis = json.optString("synopsis").takeIf(String::isNotBlank),
            genres = json.optJSONArray("genres").toSourceGenreList(),
            year = json.optInt("year").takeIf { it > 0 },
            score = null,
            status = json.optString("status").takeIf(String::isNotBlank),
            duration = when {
                episodeDurationSeconds > 0 -> "${episodeDurationSeconds / 60}MIN"
                durationMs >= 60_000 -> "${durationMs / 60_000}MIN"
                durationMs > 0 -> "${durationMs}MIN"
                else -> null
            },
            rating = json.optString("rating").takeIf(String::isNotBlank),
            episodeCount = episodes?.optInt("total")?.takeIf { it > 0 },
            format = json.optString("format").takeIf(String::isNotBlank),
            adult = json.optBoolean("is_adult"),
            latestSubEpisode = episodes?.optInt("latest_sub") ?: 0,
            latestDubEpisode = episodes?.optInt("latest_dub") ?: 0,
            malId = links?.optString("mal")?.substringAfterLast('/')?.takeIf(String::isNotBlank),
            aniListId = json.optString("anilist_id").takeIf(String::isNotBlank),
            sourceIndex = com.paregi.animetv.domain.SourceCatalog.GOTAKU_INDEX
        )
    }

    fun genreId(genre: String): String? = when (genre.lowercase(Locale.ROOT)) {
        "1", "action" -> "1"
        "2", "adventure" -> "2"
        "3", "avant_garde", "avant-garde" -> "3"
        "4", "award_winning", "award-winning" -> "4"
        "5", "boys_love", "boys-love" -> "5"
        "6", "comedy" -> "6"
        "7", "drama" -> "7"
        "8", "fantasy" -> "8"
        "9", "girls_love", "girls-love" -> "9"
        "10", "gourmet" -> "10"
        "11", "horror" -> "11"
        "12", "mystery" -> "12"
        "13", "romance" -> "13"
        "14", "sci_fi", "sci-fi" -> "14"
        "15", "slice_of_life", "slice-of-life" -> "15"
        "16", "sports" -> "16"
        "17", "supernatural" -> "17"
        "18", "suspense", "thriller" -> "18"
        "19", "ecchi" -> "19"
        "20", "erotica" -> "20"
        "21", "hentai" -> "21"
        else -> null
    }

    fun episodes(items: JSONArray?, animeId: String): List<Episode> {
        val array = items ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val number = item.opt("number")?.toString()?.takeIf { it != "null" } ?: (index + 1).toString()
            Episode(
                id = id,
                animeId = animeId,
                number = number,
                title = item.optString("name").takeIf(String::isNotBlank),
                hasSub = item.optBoolean("sub", true),
                hasDub = item.optBoolean("dub", false),
                filler = item.optBoolean("is_filler")
            )
        }
    }

    fun subtitles(items: JSONArray?): List<SubtitleTrack> {
        val array = items ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val rawLanguage = item.optString("lang").ifBlank { item.optString("language") }
            SubtitleTrack(
                id = item.optString("id").ifBlank { url },
                language = rawLanguage.toSubtitleLanguageTag(),
                label = item.optString("label").ifBlank { rawLanguage.ifBlank { "Subtitle" } },
                url = url,
                defaultSelected = item.optBoolean("default")
            )
        }
    }
}
