package com.paregi.animetv.source.gotaku

import android.net.Uri
import com.paregi.animetv.AndroidGotakuCrypto
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

/** Gotaku catalog endpoints, filters, encrypted responses, and pagination. */
internal class GotakuCatalogApi(
    private val requestBytes: SourceRequestBytes
) : SourceCatalogApi {
    override suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage {
        val builder = Uri.parse(source.root + "/api/v1/titles").buildUpon()
            .appendQueryParameter("limit", "28")
            .appendQueryParameter("page", page.coerceAtLeast(1).toString())
            .appendQueryParameter("sort", filters.sort)
        if (filters.query.isNotBlank()) builder.appendQueryParameter("q", filters.query.trim())
        filters.year?.let { builder.appendQueryParameter("year", it.toString()) }
        filters.season?.let { builder.appendQueryParameter("season", it.uppercase(Locale.ROOT)) }
        filters.types.forEach { builder.appendQueryParameter("type", it) }
        filters.genres.mapNotNull(GotakuSource::genreId)
            .forEach { builder.appendQueryParameter("genre[]", it) }
        filters.status.forEach { builder.appendQueryParameter("status", it) }

        val root = json(builder.build().toString(), source)
        val items = root.optJSONArray("data") ?: JSONArray()
        val results = items.toAnimeList()
        return SearchPage(
            results = results,
            page = page,
            hasNextPage = root.optJSONObject("pagination")?.optInt("last_page", page + 1)?.let { page < it }
                ?: (items.length() >= PAGE_SIZE)
        )
    }

    override suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime> {
        val url = Uri.parse(source.root + "/api/v1/titles").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("sort", sort)
            .build().toString()
        return json(url, source).optJSONArray("data").toAnimeList()
    }

    override suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime? {
        val root = json(source.root + "/api/v1/titles/" + Uri.encode(sourceId), source)
        val data = root.optJSONObject("data") ?: root
        return GotakuSource.anime(data.optJSONObject("title") ?: data, sourceId)
    }

    override suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode> {
        val root = json(source.root + "/api/v1/titles/" + animeId + "/episodes?k=10", source)
        return GotakuSource.episodes(root.optJSONArray("data"), animeId)
    }

    override suspend fun json(url: String, source: CatalogSource): JSONObject {
        val bytes = requestBytes(
            url,
            null,
            null,
            mapOf("Accept" to "application/json, text/plain, */*")
        )
        val key = Uri.parse(url).getQueryParameter("k") ?: "10"
        val plain = AndroidGotakuCrypto.decryptApi(bytes, key)
            ?: bytes.toString(StandardCharsets.UTF_8).takeIf { it.trimStart().startsWith("{") }
            ?: error("Could not decrypt source response")
        return JSONObject(plain)
    }

    private fun JSONArray?.toAnimeList(): List<Anime> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { GotakuSource.anime(it) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 28
    }
}
