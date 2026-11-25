package com.paregi.animetv.domain

interface LibraryRepository {
    suspend fun loadWatchlist(): List<Anime>
    suspend fun addToWatchlist(anime: Anime)
    suspend fun removeFromWatchlist(animeId: String)
    suspend fun loadHistory(): List<WatchProgress>
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun removeAnimeHistory(animeId: String)
    suspend fun clearHistory()
}
