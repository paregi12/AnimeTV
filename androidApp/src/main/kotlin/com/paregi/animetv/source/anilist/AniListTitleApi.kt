package com.paregi.animetv.source.anilist

import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.AniListReview
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.source.shared.normalizeAnimeTitle
import com.paregi.animetv.source.shared.toSourceStringList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class AniListTitleMetadata(
    val aniListId: String?,
    val malId: String?,
    val aliases: List<String>,
    val year: Int?,
    val status: String?,
    val trailerId: String?
)

internal class AniListTitleApi(private val client: AniListGraphQlClient) {
    suspend fun loadInfo(anime: Anime): AniListAnimeInfo? = withContext(Dispatchers.IO) {
        val aniListId = anime.aniListId?.toIntOrNull()
        val malId = anime.malId?.toIntOrNull()
        val searchCandidates = (listOf(anime.title, anime.originalTitle) + anime.titleAliases)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .take(3)
        if (aniListId == null && malId == null && searchCandidates.isEmpty()) return@withContext null

        val names = (listOf(anime.title, anime.originalTitle) + anime.titleAliases)
            .filterNotNull()
            .mapNotNull(::normalizeAnimeTitle)
            .toSet()

        var lastError: Exception? = null
        var mediaItems: List<JSONObject> = emptyList()

        if (aniListId != null) {
            mediaItems = queryInfoPageCatching(
                varDecl = "${'$'}id:Int!",
                mediaArgs = "type: ANIME, id: ${'$'}id",
                varName = "id",
                varValue = aniListId
            ).getOrElse { lastError = it; emptyList() }
        }
        if (mediaItems.isEmpty() && malId != null) {
            mediaItems = queryInfoPageCatching(
                varDecl = "${'$'}idMal:Int!",
                mediaArgs = "type: ANIME, idMal: ${'$'}idMal",
                varName = "idMal",
                varValue = malId
            ).getOrElse { lastError = it; emptyList() }
        }
        if (mediaItems.isEmpty()) {
            val adultFilter = if (anime.adult) "" else "isAdult: false, "
            for (term in searchCandidates) {
                mediaItems = queryInfoPageCatching(
                    varDecl = "${'$'}search:String!",
                    mediaArgs = "sort: SEARCH_MATCH, ${adultFilter}type: ANIME, search: ${'$'}search",
                    varName = "search",
                    varValue = term
                ).getOrElse { lastError = it; emptyList() }
                if (mediaItems.isNotEmpty()) break
            }
        }
        if (mediaItems.isEmpty()) {
            lastError?.let { throw it }
            return@withContext null
        }

        val requestedId = aniListId?.toString()
        val requestedMalId = malId?.toString()
        val match = mediaItems.firstOrNull { item ->
            (requestedId != null && item.optInt("id").toString() == requestedId) ||
                (requestedMalId != null && item.optInt("idMal").toString() == requestedMalId)
        } ?: mediaItems.firstOrNull { item ->
            val title = item.optJSONObject("title")
            val itemNames = buildList {
                listOf("english", "romaji", "native").forEach { key ->
                    title?.optNonBlank(key)?.let(::add)
                }
                item.optJSONArray("synonyms").toSourceStringList().forEach(::add)
            }
            itemNames.mapNotNull(::normalizeAnimeTitle).any { it in names }
        } ?: mediaItems.firstOrNull() ?: return@withContext null

        val title = match.optJSONObject("title")
        val trailer = match.optJSONObject("trailer")
            ?.takeIf { it.optNonBlank("site").equals("youtube", ignoreCase = true) }
        val studios = match.optJSONObject("studios")?.optJSONArray("edges")?.let { edges ->
            (0 until edges.length()).mapNotNull { index ->
                edges.optJSONObject(index)?.optJSONObject("node")?.optNonBlank("name")
            }
        }.orEmpty()
        val reviews = match.optJSONObject("reviews")?.optJSONArray("edges")?.let { edges ->
            (0 until edges.length()).mapNotNull { index ->
                val node = edges.optJSONObject(index)?.optJSONObject("node") ?: return@mapNotNull null
                val summary = node.optNonBlank("summary") ?: return@mapNotNull null
                AniListReview(
                    score = node.optInt("score").takeIf { it > 0 },
                    rating = node.optInt("rating").takeIf { it > 0 },
                    summary = summary
                )
            }
        }.orEmpty()
        AniListAnimeInfo(
            id = match.optInt("id").takeIf { it > 0 }?.toString() ?: return@withContext null,
            malId = match.optInt("idMal").takeIf { it > 0 }?.toString(),
            titleEnglish = title?.optNonBlank("english"),
            titleRomaji = title?.optNonBlank("romaji"),
            titleNative = title?.optNonBlank("native"),
            coverUrl = match.optJSONObject("coverImage")?.optNonBlank("large")
                ?: match.optJSONObject("coverImage")?.optNonBlank("medium"),
            bannerUrl = match.optNonBlank("bannerImage"),
            trailerId = trailer?.optNonBlank("id"),
            description = match.optNonBlank("description"),
            format = match.optNonBlank("format"),
            status = match.optNonBlank("status"),
            adult = match.optBoolean("isAdult"),
            genres = match.optJSONArray("genres").toSourceStringList(),
            startDate = match.optJSONObject("startDate").formatAniListDate(),
            endDate = match.optJSONObject("endDate").formatAniListDate(),
            source = match.optNonBlank("source"),
            countryOfOrigin = match.optNonBlank("countryOfOrigin"),
            season = match.optNonBlank("season"),
            year = match.optInt("seasonYear").takeIf { it > 0 },
            episodeCount = match.optInt("episodes").takeIf { it > 0 },
            nextAiringEpisode = match.optJSONObject("nextAiringEpisode")
                ?.optInt("episode")?.takeIf { it > 0 },
            durationMinutes = match.optInt("duration").takeIf { it > 0 },
            score = match.optInt("averageScore").takeIf { it > 0 },
            popularity = match.optInt("popularity").takeIf { it > 0 },
            favourites = match.optInt("favourites").takeIf { it > 0 },
            trending = match.optInt("trending").takeIf { it > 0 },
            studios = studios,
            synonyms = match.optJSONArray("synonyms").toSourceStringList(),
            reviews = reviews
        )
    }

    suspend fun loadTitleMetadata(aniListId: String?, malId: String?): AniListTitleMetadata? {
        val id = aniListId?.toIntOrNull()
        val idMal = malId?.toIntOrNull()
        val media = (id?.let { queryMetadataMedia("${'$'}id:Int!", "id: ${'$'}id, type: ANIME", "id", it) })
            ?: (idMal?.let { queryMetadataMedia("${'$'}idMal:Int!", "idMal: ${'$'}idMal, type: ANIME", "idMal", it) })
            ?: return null
        val titles = media.optJSONObject("title")
        val aliases = buildList {
            listOf("english", "romaji", "native", "userPreferred").forEach { key ->
                titles?.optNonBlank(key)?.let(::add)
            }
            val synonyms = media.optJSONArray("synonyms") ?: JSONArray()
            for (index in 0 until synonyms.length()) {
                if (!synonyms.isNull(index)) {
                    synonyms.optString(index).takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }?.let(::add)
                }
            }
        }.distinct()
        val trailer = media.optJSONObject("trailer")
            ?.takeIf { it.optNonBlank("site").equals("youtube", ignoreCase = true) }
        return AniListTitleMetadata(
            aniListId = media.optInt("id").takeIf { it > 0 }?.toString(),
            malId = media.optInt("idMal").takeIf { it > 0 }?.toString(),
            aliases = aliases,
            year = media.optInt("seasonYear").takeIf { it > 0 },
            status = media.optNonBlank("status"),
            trailerId = trailer?.optNonBlank("id")
        )
    }

    private suspend fun queryInfoPageCatching(
        varDecl: String,
        mediaArgs: String,
        varName: String,
        varValue: Any
    ): Result<List<JSONObject>> = try {
        val query = """
            query($varDecl) {
              Page(page: 1, perPage: 10) {
                media($mediaArgs) {
                  id idMal
                  title { romaji english native }
                  coverImage { large medium }
                  bannerImage trailer { id site thumbnail }
                  status duration format seasonYear season isAdult
                  nextAiringEpisode { episode }
                  episodes description averageScore favourites popularity trending
                  genres startDate { year month day } endDate { year month day }
                  studios { edges { isMain node { name } } }
                  synonyms source countryOfOrigin
                  reviews { edges { node { id summary rating score } } }
                }
              }
            }
        """.trimIndent()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put(varName, varValue))
                .toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = response.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
        val items = if (media == null) emptyList() else (0 until media.length()).mapNotNull { media.optJSONObject(it) }
        Result.success(items)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun queryMetadataMedia(
        varDecl: String,
        mediaArgs: String,
        varName: String,
        varValue: Int
    ): JSONObject? = try {
        val query = """
            query($varDecl) {
              Media($mediaArgs) {
                id idMal title { romaji english native userPreferred } synonyms seasonYear status
                trailer { id site }
              }
            }
        """.trimIndent()
        val root = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put(varName, varValue))
                .toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        root.optJSONObject("data")?.optJSONObject("Media")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.optNonBlank(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    suspend fun loadTrailer(aniListId: String): String? {
        val id = aniListId.toIntOrNull() ?: return null
        val query = "query { Media(id: $id, type: ANIME) { trailer { id site } } }"
        val root = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val trailer = root.optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("trailer")
            ?.takeIf { it.optNonBlank("site").equals("youtube", ignoreCase = true) }
        return trailer?.optNonBlank("id")
    }

    private fun JSONObject?.formatAniListDate(): String? {
        if (this == null) return null
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val parts = buildList {
            optInt("month").takeIf { it in 1..12 }?.let { add(months[it - 1]) }
            optInt("day").takeIf { it > 0 }?.let { add(it.toString()) }
            optInt("year").takeIf { it > 0 }?.let { add(it.toString()) }
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }
}
