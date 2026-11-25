package com.paregi.animetv.source.anilist

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import android.net.Uri
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** AniList home shelves, search filtering, and catalog response mapping. */
internal class AniListCatalogApi(private val client: AniListGraphQlClient) {
    suspend fun fetchTvRecent(source: CatalogSource): List<Anime> {
        val query = """
            query(${'$'}tm: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                airingSchedules(airingAt_lesser: ${'$'}tm, sort: TIME_DESC) {
                  media {
                    id isAdult title { romaji english }
                    coverImage { extraLarge }
                    episodes format popularity averageScore
                  }
                }
              }
            }
        """.trimIndent()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).put(
                "variables",
                JSONObject()
                    .put("tm", System.currentTimeMillis() / 1_000L)
                    .put("page", 1)
                    .put("perPage", 50)
            ).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val schedules = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("airingSchedules") ?: return emptyList()
        return (0 until schedules.length()).mapNotNull { index ->
            val media = schedules.optJSONObject(index)?.optJSONObject("media") ?: return@mapNotNull null
            if (media.optBoolean("isAdult")) return@mapNotNull null
            val anime = media.toAniListTvAnime("extraLarge", normalizeTvShort = true, source = source)
                ?: return@mapNotNull null
            media.optInt("popularity") to anime
        }.sortedByDescending { it.first }.map { it.second }
    }

    suspend fun fetchTvRanking(trending: Boolean, source: CatalogSource): List<Anime> {
        val sortAndStatus = if (trending) {
            "sort: TRENDING_DESC, status: RELEASING"
        } else {
            "sort: POPULARITY_DESC, status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]"
        }
        val query = """
            query(${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media($sortAndStatus, isAdult: false, type: ANIME) {
                  id title { romaji english }
                  coverImage { large }
                  episodes format averageScore
                }
              }
            }
        """.trimIndent()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).put(
                "variables",
                JSONObject().put("page", 1).put("perPage", 50)
            ).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("media") ?: return emptyList()
        return (0 until media.length()).mapNotNull { index ->
            media.optJSONObject(index)?.toAniListTvAnime("large", normalizeTvShort = false, source = source)
        }
    }

    private fun JSONObject.toAniListTvAnime(
        coverSize: String,
        normalizeTvShort: Boolean,
        source: CatalogSource
    ): Anime? {
        val id = optInt("id").takeIf { it > 0 }?.toString() ?: return null
        val titles = optJSONObject("title")
        val englishTitle = titles?.takeUnless { it.isNull("english") }?.optString("english").orEmpty()
        val romajiTitle = titles?.takeUnless { it.isNull("romaji") }?.optString("romaji").orEmpty()
        val title = englishTitle
            .ifBlank { romajiTitle }
            .ifBlank { return null }
        val cover = optJSONObject("coverImage")
        val poster = cover?.takeUnless { it.isNull(coverSize) }?.optString(coverSize).orEmpty()
            .takeIf { it.isNotBlank() } ?: return null
        val format = if (isNull("format")) "" else optString("format").takeIf { it.isNotBlank() }.orEmpty()
        val displayFormat = if (normalizeTvShort && format.equals("TV_SHORT", ignoreCase = true)) "TV" else format
        val episodes = optInt("episodes").takeIf { it > 0 }
        val score = optInt("averageScore").takeIf { it > 0 }
        val description = if (displayFormat.equals("MOVIE", ignoreCase = true) || episodes == null) {
            "Score: ${score ?: 0}  |  $displayFormat"
        } else {
            "$episodes Episodes  |  Score: ${score ?: 0}  |  $displayFormat"
        }
        return Anime(
            id = "anilist:$id|${Uri.encode(title)}",
            title = title,
            originalTitle = romajiTitle.takeIf { it.isNotBlank() },
            posterUrl = poster,
            synopsis = description,
            score = score?.div(10f),
            episodeCount = episodes,
            format = displayFormat.takeIf { it.isNotBlank() },
            aniListId = id,
            sourceIndex = source.index
        )
    }

    suspend fun fetchTrending(
        highResolutionImages: Boolean,
        source: CatalogSource
    ): List<Anime> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id idMal title { romaji english native }
                  coverImage { large extraLarge } bannerImage description genres
                  trailer { id site thumbnail }
                  seasonYear averageScore episodes nextAiringEpisode { episode } format status duration
                }
              }
            }
        """.trimIndent()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = response.optJSONObject("data")
            ?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
        return (0 until media.length()).mapNotNull { index ->
            val item = media.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id").takeIf { it > 0 }?.toString() ?: return@mapNotNull null
            val titles = item.optJSONObject("title")
            val title = titles?.optString("english").orEmpty()
                .ifBlank { titles?.optString("romaji").orEmpty() }
                .ifBlank { titles?.optString("native").orEmpty() }
                .ifBlank { id }
            val cover = item.optJSONObject("coverImage")
            val trailer = item.optJSONObject("trailer")
                ?.takeIf { it.optString("site").equals("youtube", ignoreCase = true) }
            val banner = item.optString("bannerImage").takeIf { it.isNotBlank() }
            if (banner == null && trailer == null) return@mapNotNull null
            val trailerId = trailer?.optString("id")?.takeIf { it.isNotBlank() }
            val nextAiredEpisode = item.optJSONObject("nextAiringEpisode")
                ?.optInt("episode")?.minus(1)?.takeIf { it > 0 }
            val poster = if (highResolutionImages) cover?.optString("extraLarge").orEmpty()
            else cover?.optString("large").orEmpty()
            Anime(
                id = "anilist:" + id + "|" + Uri.encode(title),
                title = title,
                originalTitle = titles?.optString("romaji")?.takeIf { it.isNotBlank() },
                posterUrl = poster.ifBlank { cover?.optString("large").orEmpty() }
                    .takeIf { it.isNotBlank() },
                bannerUrl = banner ?: trailer?.optString("thumbnail")?.takeIf { it.isNotBlank() },
                trailerId = trailerId,
                synopsis = item.optString("description").takeIf { it.isNotBlank() },
                genres = item.optJSONArray("genres").toSourceStringList(),
                year = item.optInt("seasonYear").takeIf { it > 0 },
                score = item.optInt("averageScore").takeIf { it > 0 }?.div(10f),
                status = item.optString("status").takeIf { it.isNotBlank() },
                duration = item.optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
                episodeCount = nextAiredEpisode ?: item.optInt("episodes").takeIf { it > 0 },
                format = item.optString("format").takeIf { it.isNotBlank() },
                malId = item.optInt("idMal").takeIf { it > 0 }?.toString(),
                aniListId = id,
                sourceIndex = source.index
            )
        }
    }

    suspend fun search(
        filters: SearchFilters,
        page: Int,
        source: CatalogSource
    ): SearchPage {
        val query = """
            query(${ '$' }page: Int!, ${ '$' }perPage: Int!, ${ '$' }search: String, ${ '$' }genres: [String], ${ '$' }formats: [MediaFormat], ${ '$' }year: Int, ${ '$' }season: MediaSeason) {
              Page(page: ${ '$' }page, perPage: ${ '$' }perPage) {
                pageInfo { hasNextPage }
                media(
                  sort: SEARCH_MATCH,
                  isAdult: false,
                  type: ANIME,
                  search: ${ '$' }search,
                  genre_in: ${ '$' }genres,
                  format_in: ${ '$' }formats,
                  seasonYear: ${ '$' }year,
                  season: ${ '$' }season
                ) {
                  id idMal
                  title { english romaji native }
                  coverImage { large medium }
                  description genres status duration format season seasonYear
                  isAdult averageScore episodes
                }
              }
            }
        """.trimIndent()
        val genres = filters.genres.mapNotNull(::aniListGenreName).distinct()
        val formats = filters.types.mapNotNull(::aniListFormatName).distinct()
        val body = JSONObject().put("query", query).put(
            "variables",
            JSONObject()
                .put("page", page.coerceAtLeast(1))
                .put("perPage", 12)
                .put("search", filters.query.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("genres", genres.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
                .put("formats", formats.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
                .put("year", filters.year ?: JSONObject.NULL)
                .put("season", filters.season?.uppercase(Locale.ROOT) ?: JSONObject.NULL)
        ).toString()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = body,
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val pageData = response.optJSONObject("data")?.optJSONObject("Page")
            ?: error("AniList did not return a search page")
        val media = pageData.optJSONArray("media") ?: JSONArray()
        val results = (0 until media.length()).mapNotNull { index ->
            media.optJSONObject(index)?.toAniListSearchAnime(source)
        }
        return SearchPage(
            results = results,
            page = page,
            hasNextPage = pageData.optJSONObject("pageInfo")?.optBoolean("hasNextPage")
                ?: (results.size >= 12)
        )
    }

    private fun JSONObject.toAniListSearchAnime(source: CatalogSource): Anime? {
        val id = optInt("id").takeIf { it > 0 }?.toString() ?: return null
        val titles = optJSONObject("title")
        val english = titles?.optString("english")?.takeIf { it.isNotBlank() }
        val romaji = titles?.optString("romaji")?.takeIf { it.isNotBlank() }
        val native = titles?.optString("native")?.takeIf { it.isNotBlank() }
        val title = english ?: romaji ?: native ?: return null
        val cover = optJSONObject("coverImage")
        val poster = cover?.optString("large")?.takeIf { it.isNotBlank() }
            ?: cover?.optString("medium")?.takeIf { it.isNotBlank() }
        return Anime(
            id = "anilist:$id|${Uri.encode(title)}",
            title = title,
            originalTitle = romaji ?: native,
            titleAliases = listOfNotNull(english, romaji, native).distinct(),
            posterUrl = poster,
            synopsis = optString("description").takeIf { it.isNotBlank() && it != "null" },
            genres = optJSONArray("genres").toSourceStringList(),
            year = optInt("seasonYear").takeIf { it > 0 },
            score = optInt("averageScore").takeIf { it > 0 }?.div(10f),
            status = optString("status").takeIf { it.isNotBlank() && it != "null" },
            duration = optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
            episodeCount = optInt("episodes").takeIf { it > 0 },
            format = optString("format").takeIf { it.isNotBlank() && it != "null" },
            adult = optBoolean("isAdult"),
            malId = optInt("idMal").takeIf { it > 0 }?.toString(),
            aniListId = id,
            sourceIndex = source.index
        )
    }

    private fun aniListGenreName(genre: String): String? = when (genre.lowercase(Locale.ROOT)) {
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
        "18", "suspense", "thriller" -> "Thriller"
        "19", "ecchi" -> "Ecchi"
        "20", "erotica" -> "Erotica"
        "21", "hentai" -> "Hentai"
        "mahou_shoujo", "mahou-shoujo" -> "Mahou Shoujo"
        "mecha" -> "Mecha"
        "music" -> "Music"
        "psychological" -> "Psychological"
        else -> null
    }

    private fun aniListFormatName(format: String): String? = when (format.uppercase(Locale.ROOT)) {
        "TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC" -> format.uppercase(Locale.ROOT)
        else -> null
    }

}
