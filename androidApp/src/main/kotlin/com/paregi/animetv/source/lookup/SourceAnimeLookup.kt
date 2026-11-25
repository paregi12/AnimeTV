package com.paregi.animetv.source.lookup

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.SearchFilters

/** Resolves AniList/MAL titles to an exact match from the currently selected playable provider. */
internal class SourceAnimeLookup(private val catalogApi: AndroidCatalogApi) {
    suspend fun resolveSourceId(
        aniListId: String,
        title: String,
        malId: String? = null,
        aliases: List<String> = emptyList(),
        year: Int? = null,
        status: String? = null,
        source: CatalogSource
    ): String {
        require(title.isNotBlank()) { "AniList title could not be resolved to a source title" }
        val reAnime = source.isReAnime
        val externalTitles = (listOf(title) + aliases).mapNotNull(::normalizeAnimeTitle).toSet()
        val searchTitles = (listOf(title) + aliases)
            .distinctBy(::normalizeAnimeTitle)
            .take(5)
        var firstSearchResult: Anime? = null
        for (searchTitle in searchTitles) {
            val candidates = catalogApi.search(
                SearchFilters(
                    query = searchTitle,
                    sort = if (reAnime) "popularity_desc" else "trending_day"
                ),
                page = 1,
                source = source
            ).results
            if (firstSearchResult == null) firstSearchResult = candidates.firstOrNull()
            candidates.firstOrNull {
                (aniListId.isNotBlank() && it.aniListId == aniListId) ||
                    (malId != null && it.malId == malId)
            }?.let { return it.id }

            val exactMatches = candidates.filter { candidate ->
                val matchingNames = listOfNotNull(candidate.title, candidate.originalTitle) + candidate.titleAliases
                matchingNames.mapNotNull(::normalizeAnimeTitle).any { it in externalTitles }
            }
            exactMatches.minByOrNull { candidate ->
                val candidateYear = candidate.year
                if (year == null || candidateYear == null) Int.MAX_VALUE
                else kotlin.math.abs(candidateYear - year)
            }?.let { return it.id }
        }
        if (status != "NOT_YET_RELEASED") firstSearchResult?.let { return it.id }
        error("This title is not available from the selected source")
    }

}
