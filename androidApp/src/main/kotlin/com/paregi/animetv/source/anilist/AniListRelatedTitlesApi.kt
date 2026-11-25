package com.paregi.animetv.source.anilist

import android.net.Uri
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.source.CatalogSource
import org.json.JSONArray
import org.json.JSONObject

internal data class AniListRelatedTitles(
    val related: List<Anime> = emptyList(),
    val recommendations: List<Anime> = emptyList()
)

/** Loads AniList relations and recommendations with the same query used by the original player. */
internal class AniListRelatedTitlesApi(private val client: AniListGraphQlClient) {
    suspend fun load(aniListId: String, source: CatalogSource): AniListRelatedTitles {
        val id = aniListId.toIntOrNull() ?: return AniListRelatedTitles()
        val query = """
            query(${'$'}id:Int) {
              Media(isAdult:false, type:ANIME, id:${'$'}id) {
                relations {
                  nodes {
                    id idMal type isAdult
                    title { romaji english }
                    coverImage { large medium }
                    episodes status duration format averageScore
                  }
                }
                recommendations {
                  nodes {
                    mediaRecommendation {
                      id idMal type isAdult
                      title { romaji english }
                      coverImage { large medium }
                      episodes status duration format averageScore
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val response = client.requestJson(
            "https://graphql.anilist.co",
            body = JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put("id", id))
                .toString(),
            referer = "https://anilist.co/graphiql",
            origin = "https://anilist.co"
        )
        val media = response.optJSONObject("data")?.optJSONObject("Media")
            ?: return AniListRelatedTitles()
        val related = media.optJSONObject("relations")?.optJSONArray("nodes")
            .toAnimeList(source) { it }
        val recommendations = media.optJSONObject("recommendations")?.optJSONArray("nodes")
            .toAnimeList(source) { it.optJSONObject("mediaRecommendation") }
        return AniListRelatedTitles(related = related, recommendations = recommendations)
    }

    private fun JSONArray?.toAnimeList(
        source: CatalogSource,
        unwrap: (JSONObject) -> JSONObject?
    ): List<Anime> {
        val nodes = this ?: return emptyList()
        return (0 until nodes.length()).mapNotNull { index ->
            val item = nodes.optJSONObject(index)?.let(unwrap) ?: return@mapNotNull null
            if (item.optString("type") != "ANIME") return@mapNotNull null
            item.toAnime(source)
        }
    }

    private fun JSONObject.toAnime(source: CatalogSource): Anime? {
        val id = optInt("id").takeIf { it > 0 }?.toString() ?: return null
        val titleData = optJSONObject("title")
        val title = titleData?.optString("english")?.takeIf { it.isNotBlank() }
            ?: titleData?.optString("romaji")?.takeIf { it.isNotBlank() }
            ?: id
        val cover = optJSONObject("coverImage")
        return Anime(
            id = "anilist:$id|${Uri.encode(title)}",
            title = title,
            originalTitle = titleData?.optString("romaji")?.takeIf { it.isNotBlank() },
            posterUrl = cover?.optString("large")?.takeIf { it.isNotBlank() }
                ?: cover?.optString("medium")?.takeIf { it.isNotBlank() },
            score = optInt("averageScore").takeIf { it > 0 }?.div(10f),
            status = optString("status").takeIf { it.isNotBlank() },
            duration = optInt("duration").takeIf { it > 0 }?.let { "$it min" },
            episodeCount = optInt("episodes").takeIf { it > 0 },
            format = optString("format").takeIf { it.isNotBlank() },
            adult = optBoolean("isAdult"),
            malId = optInt("idMal").takeIf { it > 0 }?.toString(),
            aniListId = id,
            sourceIndex = source.index
        )
    }
}
