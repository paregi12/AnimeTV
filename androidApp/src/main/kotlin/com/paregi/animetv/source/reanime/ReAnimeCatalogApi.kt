package com.paregi.animetv.source.reanime

import android.net.Uri
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.SourceCatalogApi
import com.paregi.animetv.source.SourceRequestBytes
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/** ReAnime catalog endpoints, filters, response decoding, and offset pagination. */
internal class ReAnimeCatalogApi(
    private val requestBytes: SourceRequestBytes
) : SourceCatalogApi {
    override suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage {
        val builder = Uri.parse(source.root + "/api/v1/search").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("offset", ((page.coerceAtLeast(1) - 1) * PAGE_SIZE).toString())
            .appendQueryParameter("sort", when (filters.sort) {
                "latest", "latest_aired" -> "latest_aired"
                "score", "score_desc" -> "score_desc"
                else -> "popularity_desc"
            })
        if (filters.query.isNotBlank()) builder.appendQueryParameter("q", filters.query.trim())
        filters.year?.let { builder.appendQueryParameter("year", it.toString()) }
        filters.season?.let { builder.appendQueryParameter("season", it.uppercase(Locale.ROOT)) }
        filters.types.forEach { builder.appendQueryParameter("format", it) }
        val genres = filters.genres.mapNotNull(ReAnimeSource::genreName)
        if (genres.isNotEmpty()) builder.appendQueryParameter("genre", genres.joinToString(","))

        val root = json(builder.build().toString(), source)
        val items = root.optJSONArray("results") ?: root.optJSONArray("data") ?: JSONArray()
        return SearchPage(
            results = items.toAnimeList(),
            page = page,
            hasNextPage = root.optBoolean("has_more", items.length() >= PAGE_SIZE)
        )
    }

    override suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime> {
        val url = Uri.parse(source.root + "/api/v1/search").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .appendQueryParameter("sort", sort)
            .build().toString()
        val root = json(url, source)
        return (root.optJSONArray("results") ?: root.optJSONArray("data")).toAnimeList()
    }

    override suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime? {
        val root = json(source.root + "/api/v1/anime/" + Uri.encode(sourceId), source)
        val data = root.optJSONObject("data") ?: root
        return ReAnimeSource.anime(data, sourceId)
    }

    override suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode> {
        val root = json(source.root + "/api/v1/anime/" + animeId + "/episodes?limit=2000", source)
        return ReAnimeSource.episodes(root.optJSONArray("data"), animeId, root)
    }

    override suspend fun json(url: String, source: CatalogSource): JSONObject {
        val bytes = requestBytes(
            url,
            null,
            null,
            mapOf("Accept" to "application/json, text/plain, */*")
        )
        return JSONObject(bytes.toString(StandardCharsets.UTF_8))
    }

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
