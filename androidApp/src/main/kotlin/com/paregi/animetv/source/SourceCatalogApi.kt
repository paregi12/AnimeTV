package com.paregi.animetv.source

import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import org.json.JSONObject

/** Operations implemented by each selectable catalog provider. */
internal interface SourceCatalogApi {
    suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage
    suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime>
    suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime?
    suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode>
    suspend fun json(url: String, source: CatalogSource): JSONObject
}

/** Byte-level request adapter supplied by the Android repository's network stack. */
internal typealias SourceRequestBytes = suspend (
    url: String,
    body: String?,
    contentType: String?,
    headers: Map<String, String>
) -> ByteArray
