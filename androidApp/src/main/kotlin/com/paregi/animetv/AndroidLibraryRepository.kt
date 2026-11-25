package com.paregi.animetv

import android.content.Context
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.LibraryRepository
import com.paregi.animetv.domain.WatchProgress
import org.json.JSONArray
import org.json.JSONObject

/** Local Android storage for the watchlist and viewing progress. */
class AndroidLibraryRepository(context: Context, private val namespace: ProfileNamespace) : LibraryRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences("KMP_LIBRARY", Context.MODE_PRIVATE)

    override suspend fun loadWatchlist(): List<Anime> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.key(KEY_WATCHLIST), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.toAnime() }
    }

    override suspend fun addToWatchlist(anime: Anime) {
        val entries = loadWatchlist().associateBy { it.id }.toMutableMap()
        entries[anime.id] = anime
        preferences.edit().putString(namespace.key(KEY_WATCHLIST), JSONArray().apply {
            entries.values.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    override suspend fun removeFromWatchlist(animeId: String) {
        val remaining = loadWatchlist().filterNot { it.id == animeId }
        preferences.edit().putString(namespace.key(KEY_WATCHLIST), JSONArray().apply {
            remaining.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    override suspend fun loadHistory(): List<WatchProgress> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.key(KEY_HISTORY), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            WatchProgress(
                animeId = item.optString("animeId"),
                episodeId = item.optString("episodeId"),
                positionMs = item.optLong("positionMs"),
                durationMs = item.optLong("durationMs"),
                updatedAtEpochMs = item.optLong("updatedAtEpochMs"),
                anime = item.optJSONObject("anime")?.toAnime()
            )
        }
    }

    override suspend fun saveProgress(progress: WatchProgress) {
        val records = loadHistory().filterNot { it.episodeId == progress.episodeId }.toMutableList()
        records.add(0, progress)
        val json = JSONArray().apply {
            records.take(500).forEach { item ->
                put(JSONObject()
                    .put("animeId", item.animeId)
                    .put("episodeId", item.episodeId)
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAtEpochMs", System.currentTimeMillis())
                    .put("anime", item.anime?.toJson()))
            }
        }
        preferences.edit().putString(namespace.key(KEY_HISTORY), json.toString()).apply()
    }

    override suspend fun removeAnimeHistory(animeId: String) {
        val remaining = loadHistory().filterNot { it.animeId == animeId }
        preferences.edit().putString(namespace.key(KEY_HISTORY), JSONArray().apply {
            remaining.forEach { item ->
                put(JSONObject()
                    .put("animeId", item.animeId)
                    .put("episodeId", item.episodeId)
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAtEpochMs", item.updatedAtEpochMs)
                    .put("anime", item.anime?.toJson()))
            }
        }.toString()).apply()
    }

    override suspend fun clearHistory() {
        preferences.edit().putString(namespace.key(KEY_HISTORY), "[]").apply()
    }

    private fun Anime.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("originalTitle", originalTitle)
        put("titleAliases", JSONArray(titleAliases))
        put("posterUrl", posterUrl)
        put("bannerUrl", bannerUrl)
        put("trailerId", trailerId)
        put("synopsis", synopsis)
        put("genres", JSONArray(genres))
        put("year", year)
        put("score", score)
        put("status", status)
        put("duration", duration)
        put("rating", rating)
        put("episodeCount", episodeCount)
        put("format", format)
        put("adult", adult)
        put("latestSubEpisode", latestSubEpisode)
        put("latestDubEpisode", latestDubEpisode)
        put("malId", malId)
        put("aniListId", aniListId)
        put("sourceIndex", sourceIndex)
    }

    private fun JSONObject.toAnime() = Anime(
        id = optString("id"),
        title = optString("title"),
        originalTitle = optString("originalTitle").takeIf { it.isNotBlank() },
        titleAliases = optJSONArray("titleAliases")?.let { values ->
            (0 until values.length()).map { values.optString(it) }
        }.orEmpty(),
        posterUrl = optString("posterUrl").takeIf { it.isNotBlank() },
        bannerUrl = optString("bannerUrl").takeIf { it.isNotBlank() },
        trailerId = optString("trailerId").takeIf { it.isNotBlank() },
        synopsis = optString("synopsis").takeIf { it.isNotBlank() },
        genres = optJSONArray("genres")?.let { values ->
            (0 until values.length()).map { values.optString(it) }
        }.orEmpty(),
        year = optInt("year").takeIf { it > 0 },
        score = if (isNull("score")) null else optDouble("score").toFloat(),
        status = optString("status").takeIf { it.isNotBlank() },
        duration = optString("duration").takeIf { it.isNotBlank() },
        rating = optString("rating").takeIf { it.isNotBlank() },
        episodeCount = optInt("episodeCount").takeIf { it > 0 },
        format = optString("format").takeIf { it.isNotBlank() },
        adult = optBoolean("adult"),
        latestSubEpisode = optInt("latestSubEpisode"),
        latestDubEpisode = optInt("latestDubEpisode"),
        malId = optString("malId").takeIf { it.isNotBlank() },
        aniListId = optString("aniListId").takeIf { it.isNotBlank() },
        sourceIndex = optInt("sourceIndex", 9)
    )

    private companion object {
        const val KEY_WATCHLIST = "watchlist"
        const val KEY_HISTORY = "history"
    }
}
