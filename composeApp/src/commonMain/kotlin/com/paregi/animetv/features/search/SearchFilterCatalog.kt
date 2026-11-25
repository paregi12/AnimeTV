package com.paregi.animetv.features.search

import com.paregi.animetv.domain.SourceCatalog

internal data class SearchFilterOption(val key: String, val label: String)

private val sourceFormatOptions = listOf(
    SearchFilterOption("_tv", "TV"),
    SearchFilterOption("_movie", "MOVIE"),
    SearchFilterOption("_ova", "OVA"),
    SearchFilterOption("_ona", "ONA"),
    SearchFilterOption("_special", "SPECIAL"),
    SearchFilterOption("_music", "MUSIC")
)

private val aniListFormatOptions = listOf(
    SearchFilterOption("_tv", "TV"),
    SearchFilterOption("_tv_short", "TV SHORT"),
    SearchFilterOption("_movie", "MOVIE"),
    SearchFilterOption("_special", "SPECIAL"),
    SearchFilterOption("_ova", "OVA"),
    SearchFilterOption("_ona", "ONA")
)

private val gotakuGenreOptions = listOf(
    "action", "adventure", "avant_garde", "award_winning", "boys_love", "comedy", "drama",
    "ecchi", "erotica", "fantasy", "girls_love", "gourmet", "hentai", "horror", "mystery",
    "romance", "sci_fi", "slice_of_life", "sports", "supernatural", "suspense"
).map { SearchFilterOption(it, it.replace('_', ' ').uppercase()) }

private val reAnimeGenreOptions = (
    gotakuGenreOptions + listOf(
        "action_&_adventure",
        "animation",
        "family",
        "mahou_shoujo",
        "mecha",
        "music",
        "psychological",
        "sci-fi_&_fantasy",
        "science_fiction",
        "thriller"
    ).map { SearchFilterOption(it, it.replace('_', ' ').uppercase()) }
).sortedBy { it.label }

private val aniListGenreOptions = listOf(
    "action", "adventure", "comedy", "drama", "ecchi", "fantasy", "horror",
    "mahou_shoujo", "mecha", "music", "mystery", "psychological", "romance", "sci_fi",
    "slice_of_life", "sports", "supernatural", "thriller"
).map { SearchFilterOption(it, it.replace('_', ' ').uppercase()) }

internal fun searchFilterOptions(sourceIndex: Int, aniListSearch: Boolean): List<SearchFilterOption> {
    val formats = if (aniListSearch) aniListFormatOptions else sourceFormatOptions
    val genres = when {
        aniListSearch -> aniListGenreOptions
        sourceIndex == SourceCatalog.REANIME_INDEX -> reAnimeGenreOptions
        else -> gotakuGenreOptions
    }
    return formats + genres
}
