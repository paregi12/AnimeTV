package com.paregi.animetv.source.gotaku

import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.source.shared.toSourceGenreList
import com.paregi.animetv.source.shared.toSourceStringList
import com.paregi.animetv.source.shared.toSubtitleLanguageTag
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Gotaku-specific response mapping kept outside the repository coordinator. */
internal object GotakuSource {
    fun anime(json: JSONObject, fallbackId: String? = null): Anime? {
        val id = json.optText("id")
            ?: json.optText("slug")
            ?: fallbackId?.takeIf(String::isNotBlank)
            ?: return null
        val episodes = json.optJSONObject("episodes")
        val links = json.optJSONObject("links")
        val durationMinutes = json.optPositiveInt("duration_minutes") ?: 0
        val episodeDurationSeconds = json.optPositiveLong("episode_duration") ?: 0L
        val durationMs = json.optPositiveLong("duration") ?: 0L
        val genres = (json.optJSONArray("genres").toSourceGenreList() +
            json.optJSONArray("themes").toSourceGenreList()).distinct()
        return Anime(
            id = id,
            title = json.optText("name") ?: json.optText("title") ?: id,
            originalTitle = json.optJSONArray("alt_names")?.let { arr ->
                if (arr.length() > 0 && !arr.isNull(0)) arr.optString(0).takeIf(String::isNotBlank) else null
            },
            titleAliases = json.optJSONArray("alt_names").toSourceStringList(),
            posterUrl = json.optText("poster_url") ?: json.optText("poster"),
            bannerUrl = json.optText("backdrop_url") ?: json.optText("banner_url"),
            synopsis = json.optText("synopsis"),
            genres = genres,
            year = json.optPositiveInt("year"),
            score = json.optPositiveFloat("mal_score") ?: json.optPositiveFloat("ratings_avg"),
            status = json.optText("status"),
            duration = when {
                durationMinutes > 0 -> "${durationMinutes}MIN"
                episodeDurationSeconds > 0 -> "${episodeDurationSeconds / 60}MIN"
                durationMs >= 60_000 -> "${durationMs / 60_000}MIN"
                durationMs > 0 -> "${durationMs}MIN"
                else -> null
            },
            rating = json.optText("rating") ?: json.optText("age_rating"),
            episodeCount = episodes?.optPositiveInt("total"),
            format = json.optText("format"),
            adult = json.optBoolean("is_adult"),
            latestSubEpisode = episodes?.optInt("latest_sub") ?: 0,
            latestDubEpisode = episodes?.optInt("latest_dub") ?: 0,
            malId = links?.optText("mal")?.trimEnd('/')?.substringAfterLast('/')?.takeIf(String::isNotBlank),
            aniListId = json.optText("anilist_id")
                ?: links?.optText("al")?.trimEnd('/')?.substringAfterLast('/')?.takeIf(String::isNotBlank),
            sourceIndex = SourceCatalog.GOTAKU_INDEX
        )
    }

    fun relatedItems(data: JSONObject, currentId: String? = null): Triple<List<Anime>, List<Anime>, List<Anime>> {
        val payload = if (data.has("seasons") || data.has("relations") || data.has("recommendations")) {
            data
        } else {
            data.optJSONObject("data") ?: data
        }
        return Triple(
            payload.optJSONArray("seasons").toBriefAnimeList(currentId, preferFullName = true),
            payload.optJSONArray("relations").toBriefAnimeList(currentId, preferFullName = false),
            payload.optJSONArray("recommendations").toBriefAnimeList(currentId, preferFullName = false)
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
            val id = item.optText("id") ?: return@mapNotNull null
            val rawNumber = if (!item.isNull("number")) item.optDouble("number", Double.NaN) else Double.NaN
            val number = if (!rawNumber.isNaN()) {
                if (rawNumber % 1.0 == 0.0) rawNumber.toLong().toString() else rawNumber.toString()
            } else {
                item.optText("label") ?: (index + 1).toString()
            }
            Episode(
                id = id,
                animeId = animeId,
                number = number,
                title = item.optText("name"),
                hasSub = if (item.has("sub") && !item.isNull("sub")) item.optBoolean("sub") else true,
                hasDub = item.optBoolean("dub", false),
                filler = item.optBoolean("is_filler"),
                imageUrl = item.optText("thumbnail_url")
            )
        }
    }

    fun subtitles(items: JSONArray?): List<SubtitleTrack> {
        val array = items ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optText("url") ?: return@mapNotNull null
            val rawLanguage = item.optText("lang") ?: item.optText("language") ?: item.optText("name") ?: ""
            SubtitleTrack(
                id = item.optText("id") ?: url,
                language = rawLanguage.toSubtitleLanguageTag(),
                label = item.optText("label") ?: item.optText("name") ?: rawLanguage.ifBlank { "Subtitle" },
                url = url,
                defaultSelected = item.optBoolean("default")
            )
        }
    }

    private fun JSONArray?.toBriefAnimeList(currentId: String?, preferFullName: Boolean): List<Anime> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optText("id") ?: return@mapNotNull null
            if (!currentId.isNullOrBlank() && id == currentId) return@mapNotNull null
            val title = if (preferFullName) {
                item.optText("full_name") ?: item.optText("name")
            } else {
                item.optText("name")
            } ?: return@mapNotNull null
            Anime(
                id = id,
                title = title,
                posterUrl = item.optText("poster_url"),
                format = item.optText("format"),
                sourceIndex = SourceCatalog.GOTAKU_INDEX
            )
        }.distinctBy { it.id }
    }

    private fun JSONObject.optText(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optPositiveInt(key: String): Int? =
        if (isNull(key)) null else optInt(key).takeIf { it > 0 }

    private fun JSONObject.optPositiveLong(key: String): Long? =
        if (isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun JSONObject.optPositiveFloat(key: String): Float? {
        if (isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return if (!value.isNaN() && value > 0.0) value.toFloat() else null
    }
}
