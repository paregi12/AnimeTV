package com.paregi.animetv

import android.content.Context
import com.paregi.animetv.domain.SearchHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Persists the same recent-query list used by the original search screen. */
class AndroidSearchHistoryRepository(context: Context, private val namespace: ProfileNamespace) : SearchHistoryRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences("SERVER", Context.MODE_PRIVATE)
    private val lock = Mutex()

    override suspend fun load(): List<String> = withContext(Dispatchers.IO) {
        lock.withLock { loadInternal() }
    }

    override suspend fun record(query: String): List<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            val normalized = query.trim().lowercase()
            val current = loadInternal()
            if (normalized.isBlank()) return@withLock current
            val updated = (listOf(normalized) + current.filterNot { it.equals(normalized, ignoreCase = true) })
                .take(MAX_HISTORY)
            save(updated)
            updated
        }
    }

    override suspend fun remove(query: String): List<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            val updated = loadInternal().filterNot { it.equals(query, ignoreCase = true) }
            save(updated)
            updated
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            lock.withLock { save(emptyList()) }
        }
    }

    override suspend fun loadAniListSearchEnabled(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(preferences.getString(namespace.storeKey("search_config"), "{}") ?: "{}")
                .optBoolean("anilist", false)
        }.getOrDefault(false)
    }

    override suspend fun saveAniListSearchEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val config = runCatching {
                    JSONObject(preferences.getString(namespace.storeKey("search_config"), "{}") ?: "{}")
                }.getOrDefault(JSONObject())
                config.put("anilist", enabled)
                preferences.edit()
                    .putString(namespace.storeKey("search_config"), config.toString())
                    .apply()
            }
        }
    }

    private fun loadInternal(): List<String> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.storeKey("search_history"), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { index ->
            data.optString(index).takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun save(entries: List<String>) {
        preferences.edit().putString(namespace.storeKey("search_history"), JSONArray(entries).toString()).apply()
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
