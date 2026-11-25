package com.paregi.animetv

import android.content.Context
import com.paregi.animetv.domain.SearchHistoryRepository
import org.json.JSONArray
import org.json.JSONObject

/** Persists the same recent-query list used by the original search screen. */
class AndroidSearchHistoryRepository(context: Context, private val namespace: ProfileNamespace) : SearchHistoryRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences("SERVER", Context.MODE_PRIVATE)

    override suspend fun load(): List<String> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.storeKey("search_history"), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { index ->
            data.optString(index).takeIf { it.isNotBlank() }
        }.distinct()
    }

    override suspend fun record(query: String): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return load()
        val updated = (listOf(normalized) + load().filterNot { it.equals(normalized, ignoreCase = true) })
            .take(MAX_HISTORY)
        save(updated)
        return updated
    }

    override suspend fun remove(query: String): List<String> {
        val updated = load().filterNot { it.equals(query, ignoreCase = true) }
        save(updated)
        return updated
    }

    override suspend fun clear() {
        save(emptyList())
    }

    override suspend fun loadAniListSearchEnabled(): Boolean = runCatching {
        JSONObject(preferences.getString(namespace.storeKey("search_config"), "{}") ?: "{}")
            .optBoolean("anilist", false)
    }.getOrDefault(false)

    override suspend fun saveAniListSearchEnabled(enabled: Boolean) {
        val config = runCatching {
            JSONObject(preferences.getString(namespace.storeKey("search_config"), "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        config.put("anilist", enabled)
        preferences.edit()
            .putString(namespace.storeKey("search_config"), config.toString())
            .apply()
    }

    private fun save(entries: List<String>) {
        preferences.edit().putString(namespace.storeKey("search_history"), JSONArray(entries).toString()).apply()
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
