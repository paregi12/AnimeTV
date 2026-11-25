package com.paregi.animetv.source.reanime

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

/** ReAnime-specific response mapping kept outside the repository coordinator. */
internal object ReAnimeSource {
    private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")

    fun anime(json: JSONObject, fallbackId: String? = null): Anime? {
        val id = json.optText("anime_id")
            ?: json.optText("id")
            ?: fallbackId?.takeIf(String::isNotBlank)
            ?: return null
        val titles = json.optJSONObject("title")
        val cover = json.optJSONObject("cover_image")
        val name = titles?.optText("english")
            ?: titles?.optText("romaji")
            ?: titles?.optText("native")
            ?: titles?.optText("user_preferred")
            ?: id
        val extraLarge = cover?.optText("extra_large")
        val large = cover?.optText("large")
        val medium = cover?.optText("medium")
        val posterUrl = if (extraLarge?.contains("tmdb.org", ignoreCase = true) == true) {
            large ?: medium ?: extraLarge
        } else {
            extraLarge ?: large ?: medium
        }
        val titleAliases = (
            listOf("english", "romaji", "native", "user_preferred")
                .mapNotNull { titles?.optText(it) } +
                json.optJSONArray("synonyms").toSourceStringList()
            ).distinct()
        val combinedGenres = (
            json.optJSONArray("genres").toSourceStringList() +
                json.optJSONArray("tags").toSourceGenreList().sorted()
            ).distinct()
        val trailerId = json.optJSONObject("trailer")
            ?.takeIf { it.optText("site").equals("youtube", ignoreCase = true) }
            ?.optText("id")
        val score = json.optDouble("average_score", Double.NaN)
            .takeIf { !it.isNaN() && it > 0.0 }
            ?.toFloat()
            ?.div(10f)
        val synopsis = json.optText("description")
            ?.replace(BR_REGEX, "\n")
            ?.replace(HTML_TAG_REGEX, "")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return Anime(
            id = id,
            title = name,
            originalTitle = titles?.optText("native"),
            titleAliases = titleAliases,
            posterUrl = posterUrl,
            bannerUrl = json.optText("banner_image") ?: extraLarge,
            trailerId = trailerId,
            synopsis = synopsis,
            genres = combinedGenres,
            year = json.optInt("year").takeIf { it > 0 },
            score = score,
            status = json.optText("status"),
            duration = json.optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
            rating = json.optText("rating"),
            episodeCount = json.optInt("episodes").takeIf { it > 0 }
                ?: json.optInt("subbed").takeIf { it > 0 }
                ?: json.optInt("dubbed").takeIf { it > 0 },
            format = json.optText("format"),
            adult = json.optBoolean("is_adult"),
            latestSubEpisode = json.optInt("subbed"),
            latestDubEpisode = json.optInt("dubbed"),
            malId = json.optIdString("mal_id"),
            aniListId = json.optIdString("anilist_id"),
            sourceIndex = SourceCatalog.REANIME_INDEX,
            nextAiringEpisode = json.optJSONObject("next_airing_episode")?.optInt("episode")?.takeIf { it > 0 }
        )
    }

    fun genreName(genre: String): String? = when (genre.lowercase(Locale.ROOT)) {
        "1", "action" -> "Action"
        "action_&_adventure", "action & adventure", "action-and-adventure" -> "Action & Adventure"
        "2", "adventure" -> "Adventure"
        "animation" -> "Animation"
        "3", "avant_garde", "avant-garde" -> "Avant Garde"
        "4", "award_winning", "award-winning" -> "Award Winning"
        "5", "boys_love", "boys-love" -> "Boys Love"
        "6", "comedy" -> "Comedy"
        "7", "drama" -> "Drama"
        "19", "ecchi" -> "Ecchi"
        "20", "erotica" -> "Erotica"
        "family" -> "Family"
        "8", "fantasy" -> "Fantasy"
        "9", "girls_love", "girls-love" -> "Girls Love"
        "10", "gourmet" -> "Gourmet"
        "21", "hentai" -> "Hentai"
        "11", "horror" -> "Horror"
        "mahou_shoujo", "mahou-shoujo", "mahou shoujo" -> "Mahou Shoujo"
        "mecha" -> "Mecha"
        "music" -> "Music"
        "12", "mystery" -> "Mystery"
        "psychological" -> "Psychological"
        "13", "romance" -> "Romance"
        "14", "sci_fi", "sci-fi" -> "Sci-Fi"
        "sci-fi_&_fantasy", "sci_fi_&_fantasy", "sci-fi & fantasy" -> "Sci-Fi & Fantasy"
        "science_fiction", "science-fiction", "science fiction" -> "Science Fiction"
        "15", "slice_of_life", "slice-of-life" -> "Slice of Life"
        "16", "sports" -> "Sports"
        "17", "supernatural" -> "Supernatural"
        "18", "suspense" -> "Suspense"
        "thriller" -> "Thriller"
        else -> null
    }

    fun episodes(
        items: JSONArray?,
        animeId: String,
        metadata: JSONObject,
        thumbnails: Map<String, String>? = null
    ): List<Episode> {
        val array = items ?: return emptyList()
        val subbed = metadata.optInt("subbed")
        val dubbed = metadata.optInt("dubbed")
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val rawNumber = if (!item.isNull("episode_number")) {
                item.optDouble("episode_number", Double.NaN)
            } else Double.NaN
            val epNum = if (!rawNumber.isNaN()) rawNumber else (index + 1).toDouble()
            val numberStr = if (epNum % 1.0 == 0.0) epNum.toInt().toString() else epNum.toString()
            val rawTitle = item.optText("title")
                ?: item.optText("title_romanji")
                ?: item.optText("title_japanese")
            val isRecap = item.optBoolean("is_recap")
            val formattedTitle = when {
                rawTitle != null && isRecap -> "$rawTitle [Recap]"
                rawTitle != null -> rawTitle
                isRecap -> "Episode $numberStr [Recap]"
                else -> null
            }
            val itemSub = if (item.has("subbed") && !item.isNull("subbed")) item.optBoolean("subbed") else null
            val itemDub = if (item.has("dubbed") && !item.isNull("dubbed")) item.optBoolean("dubbed") else null
            Episode(
                id = "$animeId|$numberStr",
                animeId = animeId,
                number = numberStr,
                title = formattedTitle,
                hasSub = itemSub ?: ((subbed <= 0 && dubbed <= 0) || (subbed > 0 && epNum <= subbed)),
                hasDub = itemDub ?: (dubbed > 0 && epNum <= dubbed),
                filler = item.optBoolean("is_filler"),
                imageUrl = thumbnails?.get(numberStr)
            )
        }
    }

    fun subtitles(items: JSONArray?): List<SubtitleTrack> {
        val array = items ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optText("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val label = item.optText("language") ?: "Unknown"
            SubtitleTrack(
                id = url,
                language = label.toSubtitleLanguageTag(),
                label = label,
                url = url,
                defaultSelected = item.optBoolean("default")
            )
        }
    }

    private fun JSONObject.optText(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optIdString(key: String): String? {
        if (isNull(key)) return null
        val raw = opt(key)?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return null
        return raw.takeIf { it != "0" && !it.equals("null", ignoreCase = true) }
    }
}
