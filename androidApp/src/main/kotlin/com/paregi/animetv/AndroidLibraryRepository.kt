package com.paregi.animetv

import android.content.Context
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.LibraryRepository
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.WatchProgress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Local Android storage for the watchlist and viewing progress. */
class AndroidLibraryRepository(context: Context, private val namespace: ProfileNamespace) : LibraryRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences("KMP_LIBRARY", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private data class StoredHistoryRecord(
        val progress: WatchProgress,
        val sourceIndex: Int
    )

    override suspend fun loadWatchlist(sourceIndex: Int?): List<Anime> {
        val all = loadWatchlistInternal()
        return if (sourceIndex == null) all else all.filter { it.sourceIndex == sourceIndex }
    }

    override suspend fun addToWatchlist(anime: Anime) {
        lock.withLock {
            val entries = loadWatchlistInternal().associateBy { it.id to it.sourceIndex }.toMutableMap()
            entries[anime.id to anime.sourceIndex] = anime
            saveWatchlistInternal(entries.values)
        }
    }

    override suspend fun removeFromWatchlist(animeId: String, sourceIndex: Int) {
        lock.withLock {
            val remaining = loadWatchlistInternal().filterNot {
                it.id == animeId && it.sourceIndex == sourceIndex
            }
            saveWatchlistInternal(remaining)
        }
    }

    override suspend fun loadHistory(sourceIndex: Int?): List<WatchProgress> {
        val records = loadHistoryRecords()
        val filtered = if (sourceIndex == null) records else records.filter { it.sourceIndex == sourceIndex }
        return filtered.map { it.progress }
    }

    override suspend fun saveProgress(progress: WatchProgress) {
        lock.withLock {
            val now = System.currentTimeMillis()
            val targetSource = progress.anime?.sourceIndex ?: SourceCatalog.GOTAKU_INDEX
            val preserveTimestamp = progress.updatedAtEpochMs > 0L
            val existing = loadHistoryRecords()
            val previous = existing.firstOrNull {
                it.progress.animeId == progress.animeId &&
                    it.progress.episodeId == progress.episodeId &&
                    it.sourceIndex == targetSource
            }
            val updated = progress.copy(
                updatedAtEpochMs = if (preserveTimestamp) progress.updatedAtEpochMs else now,
                episodeNumber = progress.episodeNumber?.takeIf { it.isNotBlank() }
                    ?: previous?.progress?.episodeNumber?.takeIf { it.isNotBlank() }
            )
            val remaining = existing.filterNot {
                it.progress.animeId == updated.animeId &&
                    it.progress.episodeId == updated.episodeId &&
                    it.sourceIndex == targetSource
            }
            val records = if (preserveTimestamp) {
                (remaining + StoredHistoryRecord(updated, targetSource))
                    .sortedByDescending { it.progress.updatedAtEpochMs }
            } else {
                listOf(StoredHistoryRecord(updated, targetSource)) + remaining
            }
            saveHistoryRecords(records.take(500))
        }
    }

    override suspend fun removeAnimeHistory(animeId: String, sourceIndex: Int) {
        lock.withLock {
            val remaining = loadHistoryRecords().filterNot {
                it.progress.animeId == animeId && it.sourceIndex == sourceIndex
            }
            saveHistoryRecords(remaining)
        }
    }

    override suspend fun clearHistory(sourceIndex: Int?) {
        lock.withLock {
            if (sourceIndex == null) {
                preferences.edit().putString(namespace.key(KEY_HISTORY), "[]").apply()
            } else {
                val remaining = loadHistoryRecords().filterNot { it.sourceIndex == sourceIndex }
                saveHistoryRecords(remaining)
            }
        }
    }

    private fun loadWatchlistInternal(): List<Anime> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.key(KEY_WATCHLIST), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.toAnime() }
    }

    private fun saveWatchlistInternal(items: Collection<Anime>) {
        preferences.edit().putString(namespace.key(KEY_WATCHLIST), JSONArray().apply {
            items.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    private fun loadHistoryRecords(): List<StoredHistoryRecord> {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.key(KEY_HISTORY), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        return (0 until data.length()).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            val topSource = item.optSourceIndex(SourceCatalog.GOTAKU_INDEX)
            val anime = item.optJSONObject("anime")?.toAnime(defaultSourceIndex = topSource)
            val recordSource = anime?.sourceIndex ?: topSource
            StoredHistoryRecord(
                progress = WatchProgress(
                    animeId = item.optString("animeId"),
                    episodeId = item.optString("episodeId"),
                    positionMs = item.optLong("positionMs"),
                    durationMs = item.optLong("durationMs"),
                    updatedAtEpochMs = item.optLong("updatedAtEpochMs"),
                    anime = anime,
                    episodeNumber = if (item.isNull("episodeNumber")) null
                    else item.optString("episodeNumber").takeIf { it.isNotBlank() }
                ),
                sourceIndex = recordSource
            )
        }
    }

    private fun saveHistoryRecords(records: List<StoredHistoryRecord>) {
        val json = JSONArray().apply {
            records.forEach { record ->
                val item = record.progress
                put(JSONObject()
                    .put("animeId", item.animeId)
                    .put("episodeId", item.episodeId)
                    .put("episodeNumber", item.episodeNumber?.takeIf { it.isNotBlank() })
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAtEpochMs", item.updatedAtEpochMs)
                    .put("sourceIndex", record.sourceIndex)
                    .put("anime", item.anime?.toJson()))
            }
        }
        preferences.edit().putString(namespace.key(KEY_HISTORY), json.toString()).apply()
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

    private fun JSONObject.toAnime(defaultSourceIndex: Int = SourceCatalog.GOTAKU_INDEX) = Anime(
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
        sourceIndex = optSourceIndex(defaultSourceIndex)
    )

    private fun JSONObject.optSourceIndex(fallback: Int): Int =
        if (has("sourceIndex") && !isNull("sourceIndex")) optInt("sourceIndex", fallback) else fallback

    private companion object {
        const val KEY_WATCHLIST = "watchlist"
        const val KEY_HISTORY = "history"
    }
}
