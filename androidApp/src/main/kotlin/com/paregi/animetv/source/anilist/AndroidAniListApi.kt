package com.paregi.animetv.source.anilist

import com.paregi.animetv.domain.AiringEntry
import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.CatalogSource

/** Composes AniList catalog, title, related-title, and schedule APIs behind one repository-facing API. */
internal class AndroidAniListApi(
    requestJsonCall: AniListRequestJson,
    requestTextCall: AniListRequestText
) {
    private val client = AniListGraphQlClient(requestJsonCall, requestTextCall)
    private val catalog = AniListCatalogApi(client)
    private val titles = AniListTitleApi(client)
    private val relatedTitles = AniListRelatedTitlesApi(client)
    private val schedule = AniListScheduleApi(client)

    suspend fun fetchTvRecent(source: CatalogSource): List<Anime> = catalog.fetchTvRecent(source)
    suspend fun fetchTvRanking(trending: Boolean, source: CatalogSource): List<Anime> =
        catalog.fetchTvRanking(trending, source)
    suspend fun fetchTrending(highResolutionImages: Boolean, source: CatalogSource): List<Anime> =
        catalog.fetchTrending(highResolutionImages, source)
    suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage =
        catalog.search(filters, page, source)
    suspend fun loadInfo(anime: Anime): AniListAnimeInfo? = titles.loadInfo(anime)
    suspend fun loadTitleMetadata(aniListId: String?, malId: String?): AniListTitleMetadata? =
        titles.loadTitleMetadata(aniListId, malId)
    suspend fun loadTrailer(aniListId: String): String? = titles.loadTrailer(aniListId)
    suspend fun loadRelatedTitles(aniListId: String, source: CatalogSource): AniListRelatedTitles =
        relatedTitles.load(aniListId, source)
    suspend fun loadSchedule(day: Int?, includeNonJapan: Boolean, source: CatalogSource): List<AiringEntry> =
        schedule.loadSchedule(day, includeNonJapan, source)
}
