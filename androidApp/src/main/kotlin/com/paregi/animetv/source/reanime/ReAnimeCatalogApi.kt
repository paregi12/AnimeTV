package com.paregi.animetv.source.reanime

import android.net.Uri
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.SourceCatalogApi
import com.paregi.animetv.source.SourceRequestBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/** ReAnime catalog endpoints, filters, response decoding, and offset pagination. */
internal class ReAnimeCatalogApi(
    private val requestBytes: SourceRequestBytes
) : SourceCatalogApi {
    override suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage {
        val safePage = page.coerceAtLeast(1)
        val offset = (safePage - 1) * PAGE_SIZE
        val builder = Uri.parse(source.root + "/api/v1/search").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("offset", offset.toString())
            .appendQueryParameter("sort", when (filters.sort.lowercase(Locale.ROOT)) {
                "year", "year_desc" -> "year_desc"
                "score", "score_desc" -> "score_desc"
                else -> "popularity_desc"
            })
        if (filters.query.isNotBlank()) builder.appendQueryParameter("q", filters.query.trim())
        filters.year?.let { builder.appendQueryParameter("year", it.toString()) }
        filters.season?.let { builder.appendQueryParameter("season", it.uppercase(Locale.ROOT)) }
        filters.types.forEach { builder.appendQueryParameter("format", it) }
        filters.status.mapNotNull(::mapStatus).forEach { builder.appendQueryParameter("status", it) }
        val genres = filters.genres.mapNotNull(ReAnimeSource::genreName)
        if (genres.isNotEmpty()) builder.appendQueryParameter("genre", genres.joinToString(","))

        val root = json(builder.build().toString(), source)
        val items = root.optJSONArray("results") ?: root.optJSONArray("data") ?: JSONArray()
        val respOffset = root.optInt("offset", offset)
        val respLimit = root.optInt("limit", PAGE_SIZE).takeIf { it > 0 } ?: PAGE_SIZE
        val total = root.optInt("total", -1)
        val hasNextPage = if (total >= 0) {
            (respOffset + respLimit) < total
        } else {
            root.optBoolean("has_more", items.length() >= PAGE_SIZE)
        }
        return SearchPage(
            results = items.toAnimeList(),
            page = page,
            hasNextPage = hasNextPage
        )
    }

    override suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime> {
        val safePage = page.coerceAtLeast(1)
        if (sort == "latest_aired" || sort == "latest") {
            val latest = try {
                json(source.root + "/api/v1/home/latest-aired?limit=24", source)
                    .optJSONArray("data")
                    .toAnimeList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (latest.isNotEmpty()) return latest
        }
        val effectiveSort = if (sort == "latest_aired" || sort == "latest") "popularity_desc" else sort
        val url = Uri.parse(source.root + "/api/v1/search").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("offset", ((safePage - 1) * PAGE_SIZE).toString())
            .appendQueryParameter("sort", effectiveSort)
            .build().toString()
        val root = json(url, source)
        return (root.optJSONArray("results") ?: root.optJSONArray("data")).toAnimeList()
    }

    override suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime? {
        val root = json(source.root + "/api/v1/anime/" + Uri.encode(sourceId), source)
        val data = root.optJSONObject("data") ?: root
        return ReAnimeSource.anime(data, sourceId)
    }

    override suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode> = coroutineScope {
        val encodedId = Uri.encode(animeId)
        val detailsDeferred = async {
            try {
                val root = json(source.root + "/api/v1/anime/$encodedId", source)
                root.optJSONObject("data") ?: root
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        val thumbnailsDeferred = async {
            val detail = detailsDeferred.await() ?: return@async null
            val anilistId = detail.optPositiveInt("anilist_id")
            val malId = detail.optPositiveInt("mal_id")
            if (anilistId <= 0 && malId <= 0) return@async null
            fetchThumbnails(anilistId, malId, source)
        }
        val episodesRoot = json(source.root + "/api/v1/anime/$encodedId/episodes?limit=2000", source)
        val detail = detailsDeferred.await() ?: episodesRoot
        val thumbnails = thumbnailsDeferred.await()
        ReAnimeSource.episodes(episodesRoot.optJSONArray("data"), animeId, detail, thumbnails)
    }

    override suspend fun json(url: String, source: CatalogSource): JSONObject {
        val bytes = requestBytes(
            url,
            null,
            null,
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "${source.root}/home"
            )
        )
        return JSONObject(bytes.toString(StandardCharsets.UTF_8))
    }

    private suspend fun fetchThumbnails(
        anilistId: Int,
        malId: Int,
        source: CatalogSource
    ): Map<String, String>? {
        val pathId = if (anilistId > 0) anilistId.toString() else "0"
        val url = Uri.parse(source.root + "/api/thumbnails/" + pathId).buildUpon().apply {
            if (malId > 0) appendQueryParameter("mal", malId.toString())
        }.build().toString()
        return try {
            val root = json(url, source)
            val thumbs = root.optJSONObject("thumbnails") ?: return null
            buildMap {
                val keys = thumbs.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!thumbs.isNull(key)) {
                        thumbs.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                    }
                }
            }.takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun mapStatus(status: String): String? = when (status.trim().uppercase(Locale.ROOT)) {
        "FINISHED" -> "Finished"
        "RELEASING" -> "Releasing"
        "NOT_YET_RELEASED" -> "Not Yet Released"
        "CANCELLED" -> "Cancelled"
        else -> status.trim().takeIf(String::isNotBlank)
    }

    private fun JSONObject.optPositiveInt(key: String): Int =
        if (isNull(key)) 0 else opt(key)?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    private fun JSONArray?.toAnimeList(): List<Anime> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { ReAnimeSource.anime(it) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 36
    }
}
