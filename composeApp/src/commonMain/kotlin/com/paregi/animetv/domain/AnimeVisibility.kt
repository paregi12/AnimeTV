package com.paregi.animetv.domain

internal fun Anime.allowedForRating(rating: Int): Boolean = isAllowedForParentalRating(rating)

internal fun HomeFeed.filteredForRating(rating: Int): HomeFeed = copy(
    featured = featured.filter { it.allowedForRating(rating) },
    continueWatching = continueWatching.filter { it.allowedForRating(rating) },
    sections = sections.map { section -> section.copy(items = section.items.filter { it.allowedForRating(rating) }) }
)

