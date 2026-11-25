package com.paregi.animetv.source

import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.gotaku.GotakuCatalogApi
import com.paregi.animetv.source.reanime.ReAnimeCatalogApi
import org.json.JSONObject

/** Routes catalog operations to the API implementation for the captured source. */
internal class AndroidCatalogApi(requestBytes: SourceRequestBytes) {
    private val gotaku = GotakuCatalogApi(requestBytes)
    private val reAnime = ReAnimeCatalogApi(requestBytes)

    suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage =
        source.apiFor(gotaku, reAnime).search(filters, page, source)

    suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime> =
        source.apiFor(gotaku, reAnime).fetchTitles(sort, page, source)

    suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime? =
        source.apiFor(gotaku, reAnime).loadAnime(sourceId, source)

    suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode> =
        source.apiFor(gotaku, reAnime).loadEpisodes(animeId, source)

    suspend fun json(url: String, source: CatalogSource): JSONObject =
        source.apiFor(gotaku, reAnime).json(url, source)

    private fun CatalogSource.apiFor(
        gotaku: SourceCatalogApi,
        reAnime: SourceCatalogApi
    ): SourceCatalogApi = if (isReAnime) reAnime else gotaku
}
