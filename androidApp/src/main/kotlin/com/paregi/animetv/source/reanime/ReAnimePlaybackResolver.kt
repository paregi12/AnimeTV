package com.paregi.animetv.source.reanime

import android.net.Uri
import android.util.LruCache
import com.paregi.animetv.AndroidStreamProxy
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import com.paregi.animetv.source.AndroidCatalogApi
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.shared.SourceRequestJson
import com.paregi.animetv.source.shared.SourceRequestText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** Resolves ReAnime's FlixCloud servers, embed data, tokens, and stream playlists. */
internal class ReAnimePlaybackResolver(
    private val streamProxy: AndroidStreamProxy,
    private val catalogApi: AndroidCatalogApi,
    private val requestText: SourceRequestText,
    private val requestJsonCall: SourceRequestJson
) {
    private data class AnimeMeta(
        val anilistId: Int,
        val malId: Int,
        val tmdbId: Int,
        val seasonNumber: Int,
        val subbed: Int,
        val dubbed: Int
    )

    private val metaCache = LruCache<String, AnimeMeta>(64)
    private val maskCache = LruCache<String, ByteArray>(16)

    suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean,
        mirrorIndex: Int,
        requestedStreamType: PlaybackStreamType? = null,
        source: CatalogSource
    ): PlaybackChoice = coroutineScope {
        val parts = episodeId.split('|')
        val animeId = parts.getOrNull(0).orEmpty()
        val episodeNumber = parts.getOrNull(1).orEmpty()
        require(animeId.isNotBlank() && episodeNumber.isNotBlank()) { "Invalid ReAnime episode id" }

        val meta = resolveAnimeMeta(animeId, episodeNumber, source)
        val pathId = if (meta.anilistId > 0) meta.anilistId.toString() else "0"
        val flixUrl = Uri.parse(
            "${source.root}/api/flix/${Uri.encode(pathId)}/${Uri.encode(episodeNumber)}"
        ).buildUpon().apply {
            if (meta.malId > 0) appendQueryParameter("mal", meta.malId.toString())
            if (meta.anilistId <= 0 && meta.tmdbId > 0) {
                appendQueryParameter("tmdb", meta.tmdbId.toString())
                appendQueryParameter("season", meta.seasonNumber.toString())
            }
        }.build().toString()
        val flixReferer = "${source.root}/watch/${Uri.encode(animeId)}?ep=${Uri.encode(episodeNumber)}"
        val flixRoot = requestJson(flixUrl, referer = flixReferer)
        val flixData = flixRoot.optJSONObject("data") ?: flixRoot
        check(flixData.optBoolean("success", flixRoot.optBoolean("success", false))) {
            "ReAnime did not return any stream servers"
        }
        val serversArray = flixData.optJSONArray("servers") ?: flixRoot.optJSONArray("servers") ?: JSONArray()
        val allServers = (0 until serversArray.length()).mapNotNull { serversArray.optJSONObject(it) }
            .filter { !it.isNull("dataLink") && it.optString("dataLink").isNotBlank() }
        val wantDub = when (requestedStreamType) {
            PlaybackStreamType.DUB -> true
            PlaybackStreamType.SUB, PlaybackStreamType.SOFTSUB -> false
            null -> preferDub
        }
        val preferredServers = allServers.filter {
            val type = it.optString("dataType")
            if (wantDub) type.equals("dub", ignoreCase = true)
            else !type.equals("dub", ignoreCase = true)
        }
        val candidates = preferredServers.ifEmpty { allServers }
        val selectedServerIndex = mirrorIndex.coerceIn(0, candidates.lastIndex.coerceAtLeast(0))
        val selected = candidates.getOrNull(selectedServerIndex)
            ?: error("ReAnime did not return any stream servers")
        val serverNames = candidates.mapIndexed { index, server -> formatServerLabel(server, index) }
        val embedUrl = selected.optString("dataLink").takeIf { it.isNotBlank() }
            ?: error("ReAnime stream server did not include an embed URL")

        val html = request(
            embedUrl,
            accept = "text/html,application/xhtml+xml,*/*",
            referer = FLIXCLOUD_REFERER,
            origin = FLIXCLOUD_ORIGIN,
            source = source
        )
        val maskDeferred = async { extractXorMask(html, embedUrl, source) }

        val rawEmbed = EMBED_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: error("FlixCloud embed data was not found")
        val normalizedEmbed = rawEmbed
            .replace(JSON5_KEY_REGEX, "$1\"$2\"$3")
            .replace(JSON5_TRAILING_COMMA_REGEX, "$1")
            .replace(JSON5_UNDEFINED_REGEX, ": null")
        val embedData = JSONObject(normalizedEmbed)
        val subtitles = ReAnimeSource.subtitles(embedData.optJSONArray("subtitles"))
        val skipRanges = mutableListOf<SkipRange>()
        embedData.optJSONObject("intro_chapter")?.let {
            val start = it.optDouble("start", Double.NaN)
            val end = it.optDouble("end", Double.NaN)
            if (!start.isNaN() && !end.isNaN() && end > start) {
                skipRanges += SkipRange(start, end, SkipRange.Kind.INTRO)
            }
        }
        embedData.optJSONObject("outro_chapter")?.let {
            val start = it.optDouble("start", Double.NaN)
            val end = it.optDouble("end", Double.NaN)
            if (!start.isNaN() && !end.isNaN() && end > start) {
                skipRanges += SkipRange(start, end, SkipRange.Kind.OUTRO)
            }
        }
        embedData.remove("subtitles")
        embedData.remove("intro_chapter")
        embedData.remove("outro_chapter")
        embedData.remove("chapters")

        val tokenJson = requestJson(
            "https://enc-dec.app/api/dec-flixcloud?type=token",
            body = JSONObject().put("data", embedData).toString(),
            referer = "https://enc-dec.app/"
        )
        check(tokenJson.optInt("status") == 200) { "FlixCloud token request failed" }
        val tokenResult = tokenJson.optJSONObject("result") ?: error("FlixCloud token response was empty")
        val token = tokenResult.optString("token").takeIf { it.isNotBlank() }
            ?: error("FlixCloud token was missing")
        val context = tokenResult.optJSONObject("context") ?: error("FlixCloud stream context was missing")
        val streamResponse = requestJson(
            "$FLIXCLOUD_ORIGIN/api/m3u8/" + Uri.encode(token),
            referer = FLIXCLOUD_REFERER,
            origin = FLIXCLOUD_ORIGIN
        )
        val decodedStream = requestJson(
            "https://enc-dec.app/api/dec-flixcloud?type=stream",
            body = JSONObject().put(
                "data",
                JSONObject().put("context", context).put("stream_response", streamResponse)
            ).toString(),
            referer = "https://enc-dec.app/"
        )
        check(decodedStream.optInt("status") == 200) { "FlixCloud stream decoding failed" }
        val streamResult = decodedStream.optJSONObject("result") ?: error("FlixCloud stream response was empty")
        val streamUrl = streamResult.optString("stream").takeIf { it.isNotBlank() }
            ?: error("FlixCloud did not return a stream URL")
        val wPayload = streamResult.optJSONObject("context")?.optString("w_payload").orEmpty()
        maskDeferred.await()?.let(streamProxy::updateReAnimeMask)
        PlaybackChoice(
            url = streamProxy.createReAnimePlaylistUrl(streamUrl, wPayload),
            headers = mapOf("Referer" to FLIXCLOUD_REFERER),
            subtitles = subtitles,
            skipRanges = skipRanges,
            selectedStreamType = when {
                selected.optString("dataType").equals("dub", ignoreCase = true) -> PlaybackStreamType.DUB
                subtitles.isNotEmpty() -> PlaybackStreamType.SOFTSUB
                else -> PlaybackStreamType.SUB
            },
            streamServerNames = serverNames,
            selectedStreamServerIndex = selectedServerIndex
        )
    }

    private suspend fun resolveAnimeMeta(
        animeId: String,
        episodeNumber: String,
        source: CatalogSource
    ): AnimeMeta {
        val cacheKey = "${source.root}|$animeId"
        metaCache.get(cacheKey)?.let { return it }

        val encodedId = Uri.encode(animeId)
        val detail = try {
            val root = catalogApi.json("${source.root}/api/v1/anime/$encodedId", source)
            root.optJSONObject("data") ?: root
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val apiAnilistId = detail?.optPositiveInt("anilist_id") ?: 0
        val apiMalId = detail?.optPositiveInt("mal_id") ?: 0
        val apiTmdbId = detail?.optPositiveInt("themoviedb_id") ?: 0
        if (detail != null && (apiAnilistId > 0 || apiMalId > 0 || apiTmdbId > 0)) {
            val isMovie = detail.optString("format").equals("MOVIE", ignoreCase = true)
            val seasonNum = if (isMovie) {
                0
            } else {
                detail.optJSONObject("external_seasons")?.optPositiveInt("tmdb")?.takeIf { it > 0 } ?: 1
            }
            val meta = AnimeMeta(
                anilistId = apiAnilistId,
                malId = apiMalId,
                tmdbId = apiTmdbId,
                seasonNumber = seasonNum,
                subbed = detail.optPositiveInt("subbed"),
                dubbed = detail.optPositiveInt("dubbed")
            )
            metaCache.put(cacheKey, meta)
            return meta
        }

        val html = try {
            request(
                "${source.root}/anime/$encodedId?_ep=${Uri.encode(episodeNumber)}",
                accept = "text/html,application/xhtml+xml,*/*",
                source = source
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
        val anilistId = ANILIST_ID_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val malId = MAL_ID_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val tmdbId = TMDB_ID_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val isMovie = isMovieRecord(html, animeId)
        val seasonNum = if (isMovie) {
            0
        } else {
            TMDB_SEASON_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
                ?: SEASON_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
                ?: 1
        }
        val subbed = SUBBED_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val dubbed = DUBBED_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val meta = AnimeMeta(anilistId, malId, tmdbId, seasonNum, subbed, dubbed)
        if (anilistId > 0 || malId > 0 || tmdbId > 0) {
            metaCache.put(cacheKey, meta)
        }
        return meta
    }

    private fun isMovieRecord(html: String, slug: String): Boolean {
        val index = html.indexOf(slug)
        if (index == -1) return false
        val start = (index - 500).coerceAtLeast(0)
        val end = (index + 500).coerceAtMost(html.length)
        val snippet = html.substring(start, end)
        return FORMAT_MOVIE_REGEX.containsMatchIn(snippet) || snippet.contains("\"format\":\"MOVIE\"", true)
    }

    private fun formatServerLabel(server: JSONObject, fallbackIndex: Int): String {
        val hardsub = server.optBoolean("hardsub", false)
        val softsub = server.optBoolean("softsub", false)
        val dataType = if (server.isNull("dataType")) "" else server.optString("dataType").trim()
        val audioTag = when {
            hardsub -> "[Hard Sub]"
            dataType.equals("sub", ignoreCase = true) -> "[Sub]"
            dataType.equals("dub", ignoreCase = true) -> "[Dub]"
            dataType.isNotEmpty() -> "[$dataType]"
            else -> ""
        }
        val serverName = if (hardsub) {
            "H-SUB"
        } else {
            val rawName = if (server.isNull("serverName")) "" else server.optString("serverName").trim()
            rawName.ifBlank { "HD-${fallbackIndex + 1}" }
        }
        return buildString {
            if (audioTag.isNotEmpty()) append(audioTag)
            if (isNotEmpty()) append(' ')
            append(serverName)
            if (softsub) append(" [Softsub]")
        }.trim()
    }

    private suspend fun extractXorMask(html: String, embedUrl: String, source: CatalogSource): ByteArray? {
        HLS_SCRIPT_REGEX.find(html)?.groupValues?.get(1)?.let { scriptPath ->
            val scriptUrl = runCatching { URL(URL(embedUrl), scriptPath).toString() }.getOrNull()
            if (scriptUrl != null) {
                maskCache.get(scriptUrl)?.let { return it }
                val script = fetchScript(scriptUrl, source)
                if (script != null) {
                    val parsed = XOR_MASK_REGEX.find(script)?.groupValues?.get(1)?.split(',')
                        ?.mapNotNull { it.trim().toIntOrNull()?.toByte() }
                        ?.toByteArray()
                        ?.takeIf { it.size == MASK_SIZE }
                        ?: parseLegacyMask(script)
                    if (parsed != null) {
                        maskCache.put(scriptUrl, parsed)
                        return parsed
                    }
                }
            }
        }
        LEGACY_PLAYER_SCRIPT_REGEX.find(html)?.value?.let { scriptPath ->
            val scriptUrl = runCatching { URL(URL(embedUrl), scriptPath).toString() }.getOrNull()
            if (scriptUrl != null) {
                maskCache.get(scriptUrl)?.let { return it }
                val script = fetchScript(scriptUrl, source)
                if (script != null) {
                    parseLegacyMask(script)?.let {
                        maskCache.put(scriptUrl, it)
                        return it
                    }
                }
            }
        }
        return null
    }

    private suspend fun fetchScript(scriptUrl: String, source: CatalogSource): String? =
        try {
            request(scriptUrl, referer = FLIXCLOUD_REFERER, origin = FLIXCLOUD_ORIGIN, source = source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private fun parseLegacyMask(script: String): ByteArray? =
        LEGACY_MASK_REGEX.find(script)?.groupValues?.get(1)?.split(',')
            ?.mapNotNull { value ->
                val number = value.trim()
                val parsed = if (number.startsWith("0x", ignoreCase = true)) {
                    number.substring(2).toIntOrNull(16)
                } else {
                    number.toIntOrNull()
                }
                parsed?.toByte()
            }
            ?.toByteArray()
            ?.takeIf { it.size == MASK_SIZE }

    private fun JSONObject.optPositiveInt(key: String): Int =
        if (isNull(key)) 0 else opt(key)?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    private suspend fun requestJson(
        url: String,
        body: String? = null,
        referer: String,
        origin: String? = null
    ): JSONObject = requestJsonCall(url, body, referer, origin)

    private suspend fun request(
        url: String,
        accept: String = "*/*",
        source: CatalogSource,
        referer: String = if (source.isReAnime) source.root + "/home" else source.root + "/",
        origin: String? = null
    ): String = requestText(url, null, null, accept, source, referer, origin)

    private companion object {
        const val MASK_SIZE = 16
        const val FLIXCLOUD_ORIGIN = "https://flixcloud.cc"
        const val FLIXCLOUD_REFERER = "https://flixcloud.cc/"

        val ANILIST_ID_REGEX = Regex("""(?<!\w)"?anilist_id"?\s*:\s*(\d+)""")
        val MAL_ID_REGEX = Regex("""(?<!\w)"?mal_id"?\s*:\s*(\d+)""")
        val TMDB_ID_REGEX = Regex("""(?<!\w)"?themoviedb_id"?\s*:\s*(\d+)""")
        val TMDB_SEASON_REGEX = Regex("""(?<!\w)"?tmdb"?\s*:\s*(\d+)""")
        val SEASON_REGEX = Regex("""(?<!\w)"?season"?\s*:\s*(\d+)""")
        val FORMAT_MOVIE_REGEX = Regex("""(?<!\w)"?format"?\s*:\s*"MOVIE"""", RegexOption.IGNORE_CASE)
        val SUBBED_REGEX = Regex(""",\s*subbed:\s*(\d+)""")
        val DUBBED_REGEX = Regex(""",\s*dubbed:\s*(\d+)""")

        val EMBED_DATA_REGEX = Regex("""type:\s*"data",\s*data:\s*(\{.*?\})\s*,\s*uses:""", RegexOption.DOT_MATCHES_ALL)
        val JSON5_KEY_REGEX = Regex("""([{,]\s*)([\w_]+)(\s*:)""")
        val JSON5_TRAILING_COMMA_REGEX = Regex(""",\s*([}\]])""")
        val JSON5_UNDEFINED_REGEX = Regex(""":\s*undefined\b""")

        val HLS_SCRIPT_REGEX = Regex("""href="([^"]*hls\.js[^"]*)""")
        val XOR_MASK_REGEX = Regex("""for\(var f=\[(\d{1,3}(?:,\d{1,3}){15})]""")
        val LEGACY_PLAYER_SCRIPT_REGEX = Regex("""/(?:js/)?player/v\d+/e\d+-player\.min\.js""")
        val LEGACY_MASK_REGEX = Regex("""\[(\s*(?:0x[0-9a-fA-F]+|\d+)\s*(?:,\s*(?:0x[0-9a-fA-F]+|\d+)\s*)*)\]""")
    }
}
