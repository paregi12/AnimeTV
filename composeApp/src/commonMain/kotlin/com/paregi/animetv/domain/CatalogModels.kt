package com.paregi.animetv.domain

import androidx.compose.runtime.Immutable

@Immutable
data class HomeFeed(
    val featured: List<Anime> = emptyList(),
    val continueWatching: List<Anime> = emptyList(),
    val sections: List<AnimeSection> = emptyList()
)

@Immutable
data class AnimeSection(
    val id: String,
    val title: String,
    val items: List<Anime>
)

@Immutable
data class SearchPage(
    val results: List<Anime>,
    val page: Int,
    val hasNextPage: Boolean
)

@Immutable
data class AnimeDetails(
    val anime: Anime,
    val episodes: List<Episode>,
    val related: List<Anime> = emptyList(),
    val recommendations: List<Anime> = emptyList(),
    val seasons: List<Anime> = emptyList()
)
