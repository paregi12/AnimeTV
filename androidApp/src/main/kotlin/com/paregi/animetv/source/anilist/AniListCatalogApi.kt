package com.paregi.animetv.source.anilist

import android.net.Uri
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.shared.toSourceStringList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

internal data class AniListHomeShelves(
    val featured: List<Anime>? = null,
    val recent: List<Anime>? = null,
    val top: List<Anime>? = null,
    val popular: List<Anime>? = null,
    val year: List<Anime>? = null,
    val upcoming: List<Anime>? = null
)

/** AniList home shelves, search filtering, and catalog response mapping. */
internal class AniListCatalogApi(private val client: AniListGraphQlClient) {
    suspend fun fetchHomeShelves(
        highResolutionImages: Boolean,
        includeFeatured: Boolean,
        year: Int,
        source: CatalogSource
    ): AniListHomeShelves {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val startDate = currentFuzzyDateInt()
        val featuredBlock = if (includeFeatured) {
            """
            featured: Page(page: 1, perPage: 12) {
              media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                ...homeMedia
                bannerImage description genres
                trailer { id site thumbnail }
              }
            }
            """.trimIndent()
        } else ""
        val query = """
            fragment homeMedia on Media {
              id idMal
              title { romaji english native }
              coverImage { large extraLarge medium }
              startDate { year month day }
              status duration format seasonYear season isAdult averageScore episodes
              nextAiringEpisode { episode }
            }
            query(${'$'}tm: Int, ${'$'}year: Int, ${'$'}startDate: FuzzyDateInt) {
              $featuredBlock
              alrecent: Page(page: 1, perPage: 50) {
                airingSchedules(airingAt_greater: 0, airingAt_lesser: ${'$'}tm, sort: TIME_DESC, notYetAired: false) {
                  episode
                  media {
                    ...homeMedia
                    type countryOfOrigin
                  }
                }
              }
              altop: Page(page: 1, perPage: 20) {
                media(sort: SCORE_DESC, isAdult: false, type: ANIME, status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]) {
                  ...homeMedia
                }
              }
              alpopular: Page(page: 1, perPage: 20) {
                media(sort: POPULARITY_DESC, isAdult: false, type: ANIME, status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]) {
                  ...homeMedia
                }
              }
              alyear: Page(page: 1, perPage: 20) {
                media(sort: SCORE_DESC, seasonYear: ${'$'}year, isAdult: false, type: ANIME, status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]) {
                  ...homeMedia
                }
              }
              alupcoming: Page(page: 1, perPage: 20) {
                media(sort: START_DATE, isAdult: false, type: ANIME, status: NOT_YET_RELEASED, startDate_greater: ${'$'}startDate) {
                  ...homeMedia
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("tm", nowSeconds)
            .put("year", year)
            .put("startDate", startDate)
        var batchError: Throwable? = null
        val data = runCatching {
            postGraphQl(query, variables).optJSONObject("data")
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            batchError = error
            null
        }
        if (data == null && batchError?.let { err ->
                "HTTP 429" in err.message.orEmpty() || err is java.io.IOException
            } == true
        ) {
            return AniListHomeShelves()
        }

        val featured = if (includeFeatured) {
            data?.optJSONObject("featured")?.optJSONArray("media")
                ?.let { parseTrendingMedia(it, highResolutionImages, source) }
                ?: runCatching { fetchTrending(highResolutionImages, source) }.getOrNull()
        } else null
        val recent = data?.optJSONObject("alrecent")?.optJSONArray("airingSchedules")
            ?.let { parseRecentSchedules(it, highResolutionImages, source) }
            ?: runCatching { fetchRecentReleases(highResolutionImages, source) }.getOrNull()
        val top = data?.optJSONObject("altop")?.optJSONArray("media")
            ?.toHomeAnimeList(highResolutionImages, source)
            ?: runCatching {
                fetchHomeMediaSection(
                    sort = "SCORE_DESC",
                    extraArgs = "status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]",
                    highResolutionImages = highResolutionImages,
                    source = source
                )
            }.getOrNull()
        val popular = data?.optJSONObject("alpopular")?.optJSONArray("media")
            ?.toHomeAnimeList(highResolutionImages, source)
            ?: runCatching {
                fetchHomeMediaSection(
                    sort = "POPULARITY_DESC",
                    extraArgs = "status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]",
                    highResolutionImages = highResolutionImages,
                    source = source
                )
            }.getOrNull()
        val yearList = data?.optJSONObject("alyear")?.optJSONArray("media")
            ?.toHomeAnimeList(highResolutionImages, source)
            ?: runCatching {
                fetchHomeMediaSection(
                    sort = "SCORE_DESC",
                    extraArgs = "seasonYear: $year, status_not_in: [HIATUS, CANCELLED, NOT_YET_RELEASED]",
                    highResolutionImages = highResolutionImages,
                    source = source
                )
            }.getOrNull()
        val upcoming = data?.optJSONObject("alupcoming")?.optJSONArray("media")
            ?.toHomeAnimeList(highResolutionImages, source)
            ?: runCatching {
                fetchHomeMediaSection(
                    sort = "START_DATE",
                    extraArgs = "status: NOT_YET_RELEASED, startDate_greater: $startDate",
                    highResolutionImages = highResolutionImages,
                    source = source
                )
            }.getOrNull()

        return AniListHomeShelves(
            featured = featured,
            recent = recent,
            top = top,
            popular = popular,
            year = yearList,
            upcoming = upcoming
        )
    }

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
        val response = postGraphQl(
            query,
            JSONObject()
                .put("tm", System.currentTimeMillis() / 1_000L)
                .put("page", 1)
                .put("perPage", 50)
        )
        val schedules = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("airingSchedules") ?: return emptyList()
        return (0 until schedules.length()).mapNotNull { index ->
            val media = schedules.optJSONObject(index)?.optJSONObject("media") ?: return@mapNotNull null
            if (media.optBoolean("isAdult")) return@mapNotNull null
            val anime = media.toAniListTvAnime("extraLarge", normalizeTvShort = true, source = source)
                ?: return@mapNotNull null
            media.optInt("popularity") to anime
        }.sortedByDescending { it.first }.map { it.second }.distinctBy { it.id }
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
        val response = postGraphQl(
            query,
            JSONObject().put("page", 1).put("perPage", 50)
        )
        val media = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("media") ?: return emptyList()
        return (0 until media.length()).mapNotNull { index ->
            media.optJSONObject(index)?.toAniListTvAnime("large", normalizeTvShort = false, source = source)
        }
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
        val response = postGraphQl(query)
        val media = response.optJSONObject("data")
            ?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        return parseTrendingMedia(media, highResolutionImages, source)
    }

    suspend fun search(
        filters: SearchFilters,
        page: Int,
        source: CatalogSource
    ): SearchPage {
        val safePage = page.coerceAtLeast(1)
        val genres = filters.genres.mapNotNull(::aniListGenreName).distinct()
        val formats = filters.types.mapNotNull(::aniListFormatName).distinct()
        val rawSeason = filters.season?.trim()?.uppercase(Locale.ROOT)
        val isUpcomingSeason = rawSeason == "UPCOMING"
        val validSeason = rawSeason?.takeIf { it in VALID_SEASONS }
        val statuses = buildList {
            filters.status.mapNotNull(::aniListStatusName).forEach(::add)
            if (isUpcomingSeason) add("NOT_YET_RELEASED")
        }.distinct()
        val sort = aniListSortName(filters.sort, isUpcomingSeason)
        val searchText = filters.query.trim().takeIf { it.isNotBlank() }

        val varDecls = mutableListOf("${'$'}page: Int!", "${'$'}perPage: Int!")
        val args = mutableListOf("sort: $sort", "isAdult: false", "type: ANIME")
        val variables = JSONObject()
            .put("page", safePage)
            .put("perPage", 12)

        if (searchText != null) {
            varDecls += "${'$'}search: String"
            args += "search: ${'$'}search"
            variables.put("search", searchText)
        }
        if (genres.isNotEmpty()) {
            varDecls += "${'$'}genres: [String]"
            args += "genre_in: ${'$'}genres"
            variables.put("genres", JSONArray(genres))
        }
        if (formats.isNotEmpty()) {
            varDecls += "${'$'}formats: [MediaFormat]"
            args += "format_in: ${'$'}formats"
            variables.put("formats", JSONArray(formats))
        }
        if (filters.year != null) {
            varDecls += "${'$'}year: Int"
            args += "seasonYear: ${'$'}year"
            variables.put("year", filters.year)
        }
        if (validSeason != null) {
            varDecls += "${'$'}season: MediaSeason"
            args += "season: ${'$'}season"
            variables.put("season", validSeason)
        }
        if (statuses.isNotEmpty()) {
            varDecls += "${'$'}statuses: [MediaStatus]"
            args += "status_in: ${'$'}statuses"
            variables.put("statuses", JSONArray(statuses))
        }
        if (isUpcomingSeason) {
            varDecls += "${'$'}startDate: FuzzyDateInt"
            args += "startDate_greater: ${'$'}startDate"
            variables.put("startDate", currentFuzzyDateInt())
        }

        val query = """
            query(${varDecls.joinToString(", ")}) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                media(${args.joinToString(", ")}) {
                  id idMal
                  title { english romaji native }
                  coverImage { large extraLarge medium }
                  startDate { year month day }
                  description genres status duration format season seasonYear
                  isAdult averageScore episodes
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        val response = postGraphQl(query, variables)
        val pageData = response.optJSONObject("data")?.optJSONObject("Page")
            ?: error("AniList did not return a search page")
        val media = pageData.optJSONArray("media") ?: JSONArray()
        val results = media.toHomeAnimeList(highResolutionImages = false, source = source, includeDescription = true)
        return SearchPage(
            results = results,
            page = page,
            hasNextPage = pageData.optJSONObject("pageInfo")?.optBoolean("hasNextPage")
                ?: (results.size >= 12)
        )
    }

    private suspend fun fetchRecentReleases(
        highResolutionImages: Boolean,
        source: CatalogSource
    ): List<Anime> {
        val query = """
            query(${'$'}tm: Int) {
              Page(page: 1, perPage: 50) {
                airingSchedules(airingAt_greater: 0, airingAt_lesser: ${'$'}tm, sort: TIME_DESC, notYetAired: false) {
                  episode
                  media {
                    id idMal type countryOfOrigin
                    title { romaji english native }
                    coverImage { large extraLarge medium }
                    startDate { year month day }
                    status duration format seasonYear season isAdult averageScore episodes
                    nextAiringEpisode { episode }
                  }
                }
              }
            }
        """.trimIndent()
        val response = postGraphQl(
            query,
            JSONObject().put("tm", System.currentTimeMillis() / 1_000L)
        )
        val schedules = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("airingSchedules") ?: return emptyList()
        return parseRecentSchedules(schedules, highResolutionImages, source)
    }

    private suspend fun fetchHomeMediaSection(
        sort: String,
        extraArgs: String,
        highResolutionImages: Boolean,
        source: CatalogSource
    ): List<Anime> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(sort: $sort, isAdult: false, type: ANIME, $extraArgs) {
                  id idMal
                  title { romaji english native }
                  coverImage { large extraLarge medium }
                  startDate { year month day }
                  status duration format seasonYear season isAdult averageScore episodes
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        val response = postGraphQl(query)
        val media = response.optJSONObject("data")?.optJSONObject("Page")
            ?.optJSONArray("media") ?: return emptyList()
        return media.toHomeAnimeList(highResolutionImages, source)
    }

    private suspend fun postGraphQl(query: String, variables: JSONObject? = null): JSONObject {
        val payload = JSONObject().put("query", query)
        if (variables != null) payload.put("variables", variables)
        val body = payload.toString()
        for (attempt in 0..2) {
            try {
                return client.requestJson(
                    ANILIST_ENDPOINT,
                    body = body,
                    referer = ANILIST_REFERER,
                    origin = ANILIST_ORIGIN
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attempt == 2) throw error
                val msg = error.message.orEmpty()
                val retryDelayMs = when {
                    "HTTP 429" in msg -> (attempt + 1) * 1_200L
                    "HTTP 5" in msg -> (attempt + 1) * 300L
                    else -> throw error
                }
                delay(retryDelayMs)
            }
        }
        error("AniList request failed")
    }

    private fun parseRecentSchedules(
        schedules: JSONArray,
        highResolutionImages: Boolean,
        source: CatalogSource
    ): List<Anime> {
        val strict = ArrayList<Anime>()
        val relaxed = ArrayList<Anime>()
        for (index in 0 until schedules.length()) {
            val entry = schedules.optJSONObject(index) ?: continue
            val media = entry.optJSONObject("media") ?: continue
            if (media.optBoolean("isAdult")) continue
            val type = media.optNonBlank("type")
            if (type != null && !type.equals("ANIME", ignoreCase = true)) continue
            val country = media.optNonBlank("countryOfOrigin")
            if (country != null && !country.equals("JP", ignoreCase = true)) continue
            val airedEp = entry.optInt("episode").takeIf { it > 0 }
            val anime = media.toAniListHomeAnime(
                highResolutionImages = highResolutionImages,
                source = source,
                fallbackAiredEpisode = airedEp
            ) ?: continue
            relaxed += anime
            val format = media.optNonBlank("format")
            val episodes = media.optInt("episodes")
            if (!format.equals("TV_SHORT", ignoreCase = true) &&
                !format.equals("ONA", ignoreCase = true) &&
                episodes > 0
            ) {
                strict += anime
            }
        }
        val strictDistinct = strict.distinctBy { it.id }
        return if (strictDistinct.size >= 6) strictDistinct else relaxed.distinctBy { it.id }
    }

    private fun parseTrendingMedia(
        media: JSONArray,
        highResolutionImages: Boolean,
        source: CatalogSource
    ): List<Anime> = (0 until media.length()).mapNotNull { index ->
        val item = media.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optInt("id").takeIf { it > 0 }?.toString() ?: return@mapNotNull null
        val titles = item.optJSONObject("title")
        val title = titles?.optNonBlank("english")
            ?: titles?.optNonBlank("romaji")
            ?: titles?.optNonBlank("native")
            ?: id
        val cover = item.optJSONObject("coverImage")
        val trailer = item.optJSONObject("trailer")
            ?.takeIf { it.optNonBlank("site").equals("youtube", ignoreCase = true) }
        val banner = item.optNonBlank("bannerImage")
        if (banner == null && trailer == null) return@mapNotNull null
        val trailerId = trailer?.optNonBlank("id")
        val nextAiredEpisode = item.optJSONObject("nextAiringEpisode")
            ?.optInt("episode")?.minus(1)?.takeIf { it > 0 }
        val poster = if (highResolutionImages) {
            cover?.optNonBlank("extraLarge") ?: cover?.optNonBlank("large")
        } else {
            cover?.optNonBlank("large") ?: cover?.optNonBlank("extraLarge")
        }
        Anime(
            id = "anilist:$id|${Uri.encode(title)}",
            title = title,
            originalTitle = titles?.optNonBlank("romaji"),
            posterUrl = poster,
            bannerUrl = banner ?: trailer?.optNonBlank("thumbnail"),
            trailerId = trailerId,
            synopsis = item.optNonBlank("description"),
            genres = item.optJSONArray("genres").toSourceStringList(),
            year = item.optInt("seasonYear").takeIf { it > 0 },
            score = item.optInt("averageScore").takeIf { it > 0 }?.div(10f),
            status = item.optNonBlank("status"),
            duration = item.optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
            episodeCount = nextAiredEpisode ?: item.optInt("episodes").takeIf { it > 0 },
            format = item.optNonBlank("format"),
            malId = item.optInt("idMal").takeIf { it > 0 }?.toString(),
            aniListId = id,
            sourceIndex = source.index
        )
    }

    private fun JSONArray.toHomeAnimeList(
        highResolutionImages: Boolean,
        source: CatalogSource,
        includeDescription: Boolean = false
    ): List<Anime> = (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.toAniListHomeAnime(
            highResolutionImages = highResolutionImages,
            source = source,
            includeDescription = includeDescription
        )
    }.distinctBy { it.id }

    private fun JSONObject.toAniListTvAnime(
        coverSize: String,
        normalizeTvShort: Boolean,
        source: CatalogSource
    ): Anime? {
        val id = optInt("id").takeIf { it > 0 }?.toString() ?: return null
        val titles = optJSONObject("title")
        val englishTitle = titles?.optNonBlank("english").orEmpty()
        val romajiTitle = titles?.optNonBlank("romaji").orEmpty()
        val title = englishTitle.ifBlank { romajiTitle }.ifBlank { return null }
        val cover = optJSONObject("coverImage")
        val poster = cover?.optNonBlank(coverSize) ?: return null
        val format = optNonBlank("format").orEmpty()
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

    private fun JSONObject.toAniListHomeAnime(
        highResolutionImages: Boolean,
        source: CatalogSource,
        fallbackAiredEpisode: Int? = null,
        includeDescription: Boolean = false
    ): Anime? {
        val id = optInt("id").takeIf { it > 0 }?.toString() ?: return null
        val titles = optJSONObject("title")
        val english = titles?.optNonBlank("english")
        val romaji = titles?.optNonBlank("romaji")
        val native = titles?.optNonBlank("native")
        val title = english ?: romaji ?: native ?: return null
        val cover = optJSONObject("coverImage")
        val poster = if (highResolutionImages) {
            cover?.optNonBlank("extraLarge") ?: cover?.optNonBlank("large") ?: cover?.optNonBlank("medium")
        } else {
            cover?.optNonBlank("large") ?: cover?.optNonBlank("extraLarge") ?: cover?.optNonBlank("medium")
        }
        val nextAiringEpisode = optJSONObject("nextAiringEpisode")?.optInt("episode")?.takeIf { it > 0 }
            ?: fallbackAiredEpisode?.takeIf { it > 0 }?.plus(1)
        val startYear = optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 }
        return Anime(
            id = "anilist:$id|${Uri.encode(title)}",
            title = title,
            originalTitle = romaji ?: native,
            titleAliases = listOfNotNull(english, romaji, native).distinct(),
            posterUrl = poster,
            synopsis = if (includeDescription) optNonBlank("description") else null,
            genres = if (includeDescription) optJSONArray("genres").toSourceStringList() else emptyList(),
            year = optInt("seasonYear").takeIf { it > 0 } ?: startYear,
            score = optInt("averageScore").takeIf { it > 0 }?.div(10f),
            status = optNonBlank("status"),
            duration = optInt("duration").takeIf { it > 0 }?.let { "${it}MIN" },
            episodeCount = optInt("episodes").takeIf { it > 0 },
            format = optNonBlank("format"),
            adult = optBoolean("isAdult"),
            malId = optInt("idMal").takeIf { it > 0 }?.toString(),
            aniListId = id,
            sourceIndex = source.index,
            nextAiringEpisode = nextAiringEpisode
        )
    }

    private fun JSONObject.optNonBlank(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun currentFuzzyDateInt(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 10_000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun aniListSortName(sort: String, upcoming: Boolean): String = when (sort.lowercase(Locale.ROOT)) {
        "score", "score_desc", "top" -> "SCORE_DESC"
        "popularity", "popularity_desc", "popular" -> "POPULARITY_DESC"
        "trending", "trending_day", "trending_desc" -> "TRENDING_DESC"
        "upcoming", "start_date" -> "START_DATE"
        else -> if (upcoming) "START_DATE" else "SEARCH_MATCH"
    }

    private fun aniListStatusName(status: String): String? = when (status.trim().uppercase(Locale.ROOT)) {
        "FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS" -> status.trim().uppercase(Locale.ROOT)
        else -> null
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

    private companion object {
        const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
        const val ANILIST_REFERER = "https://anilist.co/graphiql"
        const val ANILIST_ORIGIN = "https://anilist.co"
        val VALID_SEASONS = setOf("WINTER", "SPRING", "SUMMER", "FALL")
    }
}
