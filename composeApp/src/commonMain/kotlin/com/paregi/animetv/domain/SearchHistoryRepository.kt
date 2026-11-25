package com.paregi.animetv.domain

interface SearchHistoryRepository {
    suspend fun load(): List<String>
    suspend fun record(query: String): List<String>
    suspend fun remove(query: String): List<String>
    suspend fun clear()
    suspend fun loadAniListSearchEnabled(): Boolean = false
    suspend fun saveAniListSearchEnabled(enabled: Boolean) {}
}
