package com.paregi.animetv.domain

interface LibraryRepository {
    suspend fun loadWatchlist(sourceIndex: Int? = null): List<Anime>
    suspend fun addToWatchlist(anime: Anime)
    suspend fun removeFromWatchlist(animeId: String, sourceIndex: Int = SourceCatalog.GOTAKU_INDEX)
    suspend fun loadHistory(sourceIndex: Int? = null): List<WatchProgress>
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun removeAnimeHistory(animeId: String, sourceIndex: Int = SourceCatalog.GOTAKU_INDEX)
    suspend fun clearHistory(sourceIndex: Int? = null)
}
