package com.paregi.animetv.source.reanime

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** ReAnime-specific response mapping kept outside the repository coordinator. */
internal object ReAnimeSource {
    fun anime(json: JSONObject, fallbackId: String? = null): Anime? {
        val id = json.optString("anime_id").ifBlank { json.optString("id") }.ifBlank { fallbackId.orEmpty() }
        if (id.isBlank()) return null
        val titles = json.optJSONObject("title")
        val cover = json.optJSONObject("cover_image")
        val name = titles?.optString("english").orEmpty()
            .ifBlank { titles?.optString("romaji").orEmpty() }
            .ifBlank { titles?.optString("native").orEmpty() }
            .ifBlank { titles?.optString("user_preferred").orEmpty() }
            .ifBlank { id }
        return Anime(
            id = id,
            title = name,
            originalTitle = titles?.optString("native")?.takeIf(String::isNotBlank),
            titleAliases = listOf("english", "romaji", "native", "user_preferred")
                .mapNotNull { titles?.optString(it)?.takeIf(String::isNotBlank) },
            posterUrl = cover?.optString("large").orEmpty()
                .ifBlank { cover?.optString("extra_large").orEmpty() }
                .ifBlank { cover?.optString("medium").orEmpty() }.takeIf(String::isNotBlank),
            bannerUrl = json.optString("banner_image").ifBlank { cover?.optString("extra_large").orEmpty() }
                .takeIf(String::isNotBlank),
            synopsis = json.optString("description").replace(Regex("<[^>]*>"), "").takeIf(String::isNotBlank),
            genres = json.optJSONArray("genres").toSourceStringList(),
            year = json.optInt("year").takeIf { it > 0 },
            score = null,
            status = json.optString("status").takeIf(String::isNotBlank),
            duration = json.optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
            rating = json.optString("rating").takeIf(String::isNotBlank),
            episodeCount = json.optInt("episodes").takeIf { it > 0 }
                ?: json.optInt("subbed").takeIf { it > 0 }
                ?: json.optInt("dubbed").takeIf { it > 0 },
            format = json.optString("format").takeIf(String::isNotBlank),
            adult = json.optBoolean("is_adult"),
            latestSubEpisode = json.optInt("subbed"),
            latestDubEpisode = json.optInt("dubbed"),
            malId = json.optString("mal_id").takeIf(String::isNotBlank),
            aniListId = json.optString("anilist_id").takeIf(String::isNotBlank),
            sourceIndex = SourceCatalog.REANIME_INDEX
        )
    }

    fun genreName(genre: String): String? = when (genre.lowercase(Locale.ROOT)) {
        "1", "action" -> "Action"
        "2", "adventure" -> "Adventure"
        "3", "avant_garde", "avant-garde" -> "Avant Garde"
        "4", "award_winning", "award-winning" -> "Award Winning"
        "5", "boys_love", "boys-love" -> "Boys Love"
        "6", "comedy" -> "Comedy"
        "7", "drama" -> "Drama"
        "8", "fantasy" -> "Fantasy"
        "9", "girls_love", "girls-love" -> "Girls Love"
        "10", "gourmet" -> "Gourmet"
        "11", "horror" -> "Horror"
        "12", "mystery" -> "Mystery"
        "13", "romance" -> "Romance"
        "14", "sci_fi", "sci-fi" -> "Sci-Fi"
        "15", "slice_of_life", "slice-of-life" -> "Slice of Life"
        "16", "sports" -> "Sports"
        "17", "supernatural" -> "Supernatural"
        "18", "suspense" -> "Suspense"
        "19", "ecchi" -> "Ecchi"
        "20", "erotica" -> "Erotica"
        "21", "hentai" -> "Hentai"
        "thriller" -> "Thriller"
        "psychological" -> "Psychological"
        "mecha" -> "Mecha"
        else -> null
    }

    fun episodes(items: JSONArray?, animeId: String, metadata: JSONObject): List<Episode> {
        val array = items ?: return emptyList()
        val subbed = metadata.optInt("subbed")
        val dubbed = metadata.optInt("dubbed")
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val number = item.optInt("episode_number", index + 1)
            Episode(
                id = "$animeId|$number",
                animeId = animeId,
                number = number.toString(),
                title = item.optString("title").takeIf(String::isNotBlank),
                hasSub = subbed <= 0 || number <= subbed,
                hasDub = dubbed > 0 && number <= dubbed,
                filler = item.optBoolean("is_filler")
            )
        }
    }

    fun subtitles(items: JSONArray?): List<SubtitleTrack> {
        val array = items ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val label = item.optString("language").ifBlank { "Unknown" }
            SubtitleTrack(
                id = url,
                language = label.toSubtitleLanguageTag(),
                label = label,
                url = url,
                defaultSelected = item.optBoolean("default")
            )
        }
    }
}
