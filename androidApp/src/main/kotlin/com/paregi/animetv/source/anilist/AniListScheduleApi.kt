package com.paregi.animetv.source.anilist

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import android.net.Uri
import com.paregi.animetv.domain.AiringEntry
import com.paregi.animetv.domain.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Loads AniList airing schedules and maps them to the shared schedule model. */
internal class AniListScheduleApi(private val client: AniListGraphQlClient) {
    suspend fun loadSchedule(day: Int?, includeNonJapan: Boolean, source: CatalogSource): List<AiringEntry> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis / 1000
        val end = start + 604_800

        val allEntries = mutableListOf<AiringEntry>()
        var page = 1
        var hasNext = true
        while (hasNext && page < 10) {
            val query = """
                query {
                  Page(page: $page, perPage: 50) {
                    pageInfo { hasNextPage }
                    airingSchedules(airingAt_greater: $start, airingAt_lesser: $end, sort: TIME) {
                      airingAt episode
                      media {
                        id isAdult countryOfOrigin title { romaji english native }
                        coverImage { large } bannerImage description genres
                        seasonYear averageScore episodes format
                      }
                    }
                  }
                }
            """.trimIndent()
            val body = JSONObject().put("query", query).toString()
            val response = runCatching {
                client.request(
                    "https://graphql.anilist.co",
                    body = body,
                    contentType = "application/json",
                    referer = "https://anilist.co/graphiql"
                )
            }.getOrNull() ?: break
            val pageObj = JSONObject(response).optJSONObject("data")?.optJSONObject("Page") ?: break
            hasNext = pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage") ?: false
            val data = pageObj.optJSONArray("airingSchedules") ?: JSONArray()
            (0 until data.length()).forEach { index ->
                val entry = data.optJSONObject(index) ?: return@forEach
                val media = entry.optJSONObject("media") ?: return@forEach
                if (media.optBoolean("isAdult")) return@forEach
                if (!includeNonJapan && media.optString("countryOfOrigin") != "JP") return@forEach
                val airingAt = entry.optLong("airingAt")
                if (day != null && weekdayIndex(airingAt) != day) return@forEach
                val title = media.optJSONObject("title")
                val id = media.optInt("id")
                val displayTitle = title?.optString("english").orEmpty()
                    .ifBlank { title?.optString("romaji").orEmpty() }
                    .ifBlank { title?.optString("native").orEmpty() }
                    .ifBlank { id.toString() }
                val anime = Anime(
                    id = "anilist:" + id + "|" + Uri.encode(displayTitle),
                    title = displayTitle,
                    originalTitle = title?.optString("romaji")?.takeIf { it.isNotBlank() },
                    posterUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                    bannerUrl = media.optString("bannerImage").takeIf { it.isNotBlank() },
                    synopsis = media.optString("description").takeIf { it.isNotBlank() },
                    genres = media.optJSONArray("genres").toSourceStringList(),
                    year = media.optInt("seasonYear").takeIf { it > 0 },
                    score = media.optInt("averageScore").takeIf { it > 0 }?.div(10f),
                    episodeCount = media.optInt("episodes").takeIf { it > 0 },
                    format = media.optString("format").takeIf { it.isNotBlank() },
                    aniListId = id.takeIf { it > 0 }?.toString(),
                    sourceIndex = source.index
                )
                allEntries += AiringEntry(anime, entry.optInt("episode").toString(), airingAt)
            }
            page++
        }
        allEntries
    }


    private fun weekdayIndex(epochSeconds: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochSeconds * 1000 }
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }}
