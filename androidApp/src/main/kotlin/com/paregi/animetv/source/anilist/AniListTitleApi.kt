package com.paregi.animetv.source.anilist

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.AniListReview
import com.paregi.animetv.domain.Anime
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
        val malId = anime.malId?.toIntOrNull()?.takeIf { aniListId == null }
        val searchTitle = if (aniListId == null && malId == null) anime.title else null
        if (aniListId == null && malId == null && searchTitle.isNullOrBlank()) return@withContext null
        val query = """
            query(${'$'}id:Int, ${'$'}idMal:Int, ${'$'}search:String) {
              Page(page: 1, perPage: 10) {
                media(sort: SEARCH_MATCH, isAdult: false, type: ANIME, id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search) {
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
        val variables = JSONObject()
            .put("id", aniListId ?: JSONObject.NULL)
            .put("idMal", malId ?: JSONObject.NULL)
            .put("search", searchTitle ?: JSONObject.NULL)
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).put("variables", variables).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = response.optJSONObject("data")
            ?.optJSONObject("Page")?.optJSONArray("media") ?: return@withContext null
        val mediaItems = (0 until media.length()).mapNotNull { media.optJSONObject(it) }
        val requestedId = aniListId?.toString()
        val requestedMalId = malId?.toString()
        val names = (listOf(anime.title, anime.originalTitle) + anime.titleAliases)
            .filterNotNull().mapNotNull(::normalizeAnimeTitle).toSet()
        val match = mediaItems.firstOrNull { item ->
            (requestedId != null && item.optInt("id").toString() == requestedId) ||
                (requestedMalId != null && item.optInt("idMal").toString() == requestedMalId)
        } ?: mediaItems.firstOrNull { item ->
            val title = item.optJSONObject("title")
            val itemNames = buildList {
                listOf("english", "romaji", "native").forEach { key ->
                    title?.optString(key)?.takeIf { it.isNotBlank() }?.let(::add)
                }
                item.optJSONArray("synonyms").toSourceStringList().forEach(::add)
            }
            itemNames.mapNotNull(::normalizeAnimeTitle).any { it in names }
        } ?: mediaItems.firstOrNull() ?: return@withContext null

        val title = match.optJSONObject("title")
        val trailer = match.optJSONObject("trailer")
            ?.takeIf { it.optString("site").equals("youtube", ignoreCase = true) }
        val studios = match.optJSONObject("studios")?.optJSONArray("edges")?.let { edges ->
            (0 until edges.length()).mapNotNull { index ->
                edges.optJSONObject(index)?.optJSONObject("node")?.optString("name")
                    ?.takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        val reviews = match.optJSONObject("reviews")?.optJSONArray("edges")?.let { edges ->
            (0 until edges.length()).mapNotNull { index ->
                val node = edges.optJSONObject(index)?.optJSONObject("node") ?: return@mapNotNull null
                val summary = node.optString("summary")
                if (summary.isBlank()) null else AniListReview(
                    score = node.optInt("score").takeIf { it > 0 },
                    rating = node.optInt("rating").takeIf { it > 0 },
                    summary = summary
                )
            }
        }.orEmpty()
        AniListAnimeInfo(
            id = match.optInt("id").takeIf { it > 0 }?.toString() ?: return@withContext null,
            malId = match.optInt("idMal").takeIf { it > 0 }?.toString(),
            titleEnglish = title?.optString("english")?.takeIf { it.isNotBlank() },
            titleRomaji = title?.optString("romaji")?.takeIf { it.isNotBlank() },
            titleNative = title?.optString("native")?.takeIf { it.isNotBlank() },
            coverUrl = match.optJSONObject("coverImage")?.optString("large")
                ?.takeIf { it.isNotBlank() }
                ?: match.optJSONObject("coverImage")?.optString("medium")?.takeIf { it.isNotBlank() },
            bannerUrl = match.optString("bannerImage").takeIf { it.isNotBlank() },
            trailerId = trailer?.optString("id")?.takeIf { it.isNotBlank() },
            description = match.optString("description").takeIf { it.isNotBlank() },
            format = match.optString("format").takeIf { it.isNotBlank() },
            status = match.optString("status").takeIf { it.isNotBlank() },
            adult = match.optBoolean("isAdult"),
            genres = match.optJSONArray("genres").toSourceStringList(),
            startDate = match.optJSONObject("startDate").formatAniListDate(),
            endDate = match.optJSONObject("endDate").formatAniListDate(),
            source = match.optString("source").takeIf { it.isNotBlank() },
            countryOfOrigin = match.optString("countryOfOrigin").takeIf { it.isNotBlank() },
            season = match.optString("season").takeIf { it.isNotBlank() },
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
        if (id == null && idMal == null) return null
        val query = """
            query(${'$'}id:Int,${'$'}idMal:Int) {
              Media(id: ${'$'}id, idMal: ${'$'}idMal, type: ANIME) {
                id idMal title { romaji english native userPreferred } synonyms seasonYear status
                trailer { id site }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("id", id ?: JSONObject.NULL)
            .put("idMal", idMal ?: JSONObject.NULL)
        val root = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject().put("query", query).put("variables", variables).toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = root.optJSONObject("data")?.optJSONObject("Media") ?: return null
        val titles = media.optJSONObject("title")
        val aliases = buildList {
            listOf("english", "romaji", "native", "userPreferred").forEach { key ->
                titles?.optString(key)?.takeIf { it.isNotBlank() }?.let(::add)
            }
            val synonyms = media.optJSONArray("synonyms") ?: JSONArray()
            for (index in 0 until synonyms.length()) {
                synonyms.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
        val trailer = media.optJSONObject("trailer")
            ?.takeIf { it.optString("site").equals("youtube", ignoreCase = true) }
        return AniListTitleMetadata(
            aniListId = media.optInt("id").takeIf { it > 0 }?.toString(),
            malId = media.optInt("idMal").takeIf { it > 0 }?.toString(),
            aliases = aliases,
            year = media.optInt("seasonYear").takeIf { it > 0 },
            status = media.optString("status").takeIf { it.isNotBlank() },
            trailerId = trailer?.optString("id")?.takeIf { it.isNotBlank() }
        )
    }

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
            ?.takeIf { it.optString("site").equals("youtube", ignoreCase = true) }
        return trailer?.optString("id")?.takeIf { it.isNotBlank() }
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
