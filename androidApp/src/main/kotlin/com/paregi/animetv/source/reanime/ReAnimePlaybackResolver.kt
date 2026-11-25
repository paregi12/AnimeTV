package com.paregi.animetv.source.reanime

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import android.net.Uri
import com.paregi.animetv.AndroidStreamProxy
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
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
    suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean,
        mirrorIndex: Int,
        source: CatalogSource
    ): PlaybackChoice {
        val parts = episodeId.split('|')
        val animeId = parts.getOrNull(0).orEmpty()
        val episodeNumber = parts.getOrNull(1).orEmpty()
        require(animeId.isNotBlank() && episodeNumber.isNotBlank()) { "Invalid ReAnime episode id" }
        val detailRoot = catalogApi.json(source.root + "/api/v1/anime/" + Uri.encode(animeId), source)
        val detail = detailRoot.optJSONObject("data") ?: detailRoot
        val aniListId = detail.optString("anilist_id").takeIf { it.isNotBlank() } ?: "0"
        val malId = detail.optString("mal_id").takeIf { it.isNotBlank() }
        val flixUrl = Uri.parse(source.root + "/api/flix/" + Uri.encode(aniListId) + "/" + Uri.encode(episodeNumber))
            .buildUpon().apply { malId?.let { appendQueryParameter("mal", it) } }.build().toString()
        val flixRoot = requestJson(flixUrl, referer = source.root + "/home")
        val flixData = flixRoot.optJSONObject("data") ?: flixRoot
        check(flixData.optBoolean("success", flixRoot.optBoolean("success", false))) {
            "ReAnime did not return any stream servers"
        }
        val servers = flixData.optJSONArray("servers") ?: flixRoot.optJSONArray("servers") ?: JSONArray()
        val preferredType = if (preferDub) "dub" else "sub"
        val preferredServers = (0 until servers.length()).mapNotNull { servers.optJSONObject(it) }
            .filter {
                val type = it.optString("dataType")
                if (preferDub) type.equals(preferredType, ignoreCase = true)
                else !type.equals("dub", ignoreCase = true)
            }
        val candidates = preferredServers.ifEmpty {
            (0 until servers.length()).mapNotNull { servers.optJSONObject(it) }
        }
        val selectedServerIndex = mirrorIndex.coerceIn(0, candidates.lastIndex.coerceAtLeast(0))
        val selected = candidates.getOrNull(selectedServerIndex)
            ?: error("ReAnime did not return any stream servers")
        val serverNames = candidates.mapIndexed { index, server ->
            server.optString("serverName").takeIf { it.isNotBlank() } ?: "HD-${index + 1}"
        }
        val embedUrl = selected.optString("dataLink").takeIf { it.isNotBlank() }
            ?: error("ReAnime stream server did not include an embed URL")

        val html = request(
            embedUrl,
            accept = "text/html,application/xhtml+xml,*/*",
            referer = "https://flixcloud.cc/",
            origin = "https://flixcloud.cc",
            source = source
        )
        Regex("/(?:js/)?player/v\\d+/e\\d+-player\\.min\\.js").find(html)?.value?.let { scriptPath ->
            runCatching {
                val scriptUrl = URL(URL(embedUrl), scriptPath).toString()
                val script = request(scriptUrl, referer = "https://flixcloud.cc/", source = source)
                Regex("\\[(\\s*(?:0x[0-9a-fA-F]+|\\d+)\\s*(?:,\\s*(?:0x[0-9a-fA-F]+|\\d+)\\s*)*)\\]")
                    .find(script)?.groupValues?.get(1)?.split(',')
                    ?.map { value ->
                        val number = value.trim()
                        (if (number.startsWith("0x", ignoreCase = true)) number.substring(2).toInt(16) else number.toInt()).toByte()
                    }?.toByteArray()?.let(streamProxy::updateReAnimeMask)
            }
        }

        val rawEmbed = Regex("type:\\s*\"data\",\\s*data:\\s*(\\{.*?\\})\\s*,\\s*uses:", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("FlixCloud embed data was not found")
        val normalizedEmbed = rawEmbed
            .replace(Regex("([{,]\\s*)([\\w_]+)(\\s*:)"), "$1\"$2\"$3")
            .replace(Regex(",\\s*([}\\]])"), "$1")
            .replace(Regex(":\\s*undefined\\b"), ": null")
        val embedData = JSONObject(normalizedEmbed)
        val subtitles = ReAnimeSource.subtitles(embedData.optJSONArray("subtitles"))
        val skipRanges = mutableListOf<SkipRange>()
        embedData.optJSONObject("intro_chapter")?.let {
            skipRanges += SkipRange(it.optDouble("start", 0.0), it.optDouble("end", 0.0), SkipRange.Kind.INTRO)
        }
        embedData.optJSONObject("outro_chapter")?.let {
            skipRanges += SkipRange(it.optDouble("start", 0.0), it.optDouble("end", 0.0), SkipRange.Kind.OUTRO)
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
            "https://flixcloud.cc/api/m3u8/" + Uri.encode(token),
            referer = "https://flixcloud.cc/",
            origin = "https://flixcloud.cc"
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
        return PlaybackChoice(
            url = streamProxy.createReAnimePlaylistUrl(streamUrl, wPayload),
            headers = mapOf("Referer" to "https://flixcloud.cc/"),
            subtitles = subtitles,
            skipRanges = skipRanges,
            selectedStreamType = if (selected.optString("dataType").equals("dub", ignoreCase = true)) {
                PlaybackStreamType.DUB
            } else {
                PlaybackStreamType.SUB
            },
            streamServerNames = serverNames,
            selectedStreamServerIndex = selectedServerIndex
        )
    }

    private suspend fun requestJson(
        url: String,
        body: String? = null,
        referer: String,
        origin: String? = null
    ): JSONObject = requestJsonCall(url, body, referer, origin)

    private suspend fun request(
        url: String,
        body: String? = null,
        contentType: String? = null,
        accept: String = "*/*",
        source: CatalogSource,
        referer: String = if (source.isReAnime) source.root + "/home" else source.root + "/",
        origin: String? = null
    ): String = requestText(url, body, contentType, accept, source, referer, origin)
}
