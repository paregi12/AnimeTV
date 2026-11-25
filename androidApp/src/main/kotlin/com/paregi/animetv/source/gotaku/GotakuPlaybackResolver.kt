package com.paregi.animetv.source.gotaku

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import com.paregi.animetv.AndroidGotakuCrypto
import com.paregi.animetv.AndroidStreamProxy
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import org.json.JSONObject

/** Resolves Gotaku episode embeds, encrypted manifests, and stream playlists. */
internal class GotakuPlaybackResolver(
    private val streamProxy: AndroidStreamProxy,
    private val catalogApi: AndroidCatalogApi,
    private val requestText: SourceRequestText
) {
    suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean,
        hasSub: Boolean,
        source: CatalogSource
    ): PlaybackChoice {
        val requestedType = if (preferDub) "dub" else "hard_sub"
        var selectedStreamType = if (preferDub) PlaybackStreamType.DUB else PlaybackStreamType.SUB
        var embed = catalogApi.json(source.root + "/api/v1/episodes/" + episodeId + "/embed?type=" + requestedType + "&k=10", source)
            .optJSONObject("data")
        if (embed?.optString("url").isNullOrBlank() && preferDub && hasSub) {
            embed = catalogApi.json(source.root + "/api/v1/episodes/" + episodeId + "/embed?type=hard_sub&k=10", source)
                .optJSONObject("data")
            selectedStreamType = PlaybackStreamType.SUB
        }
        val embedData = embed ?: error("Episode has no playable stream")
        val embedUrl = embedData.optString("url").takeIf { it.isNotBlank() }
            ?: error("Episode response did not include an embed URL")
        val absoluteEmbed = when {
            embedUrl.startsWith("//") -> "https:" + embedUrl
            embedUrl.startsWith("/") -> source.root + embedUrl
            else -> embedUrl
        }
        val html = request(absoluteEmbed, source = source)
        val base = Regex("data-manifest-base=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: error("Video page did not include a manifest endpoint")
        val stamp = Regex("data-manifest-stamp=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: error("Video page did not include a manifest stamp")
        val nonce = AndroidGotakuCrypto.newNonce()
        val manifestPath = AndroidGotakuCrypto.encryptManifest(stamp, nonce)
            ?: error("Could not prepare manifest request")
        val manifestUrl = base.trimEnd('/') + "/" + manifestPath
        val encryptedManifest = request(
            manifestUrl,
            accept = "text/plain",
            referer = absoluteEmbed,
            source = source
        ).trim()
        val manifest = AndroidGotakuCrypto.decryptManifest(encryptedManifest, nonce)
            ?: error("Could not decrypt video manifest")
        val json = JSONObject(manifest)
        val streamSource = json.optString("source").takeIf { it.isNotBlank() }
            ?: error("Video manifest did not include a source")
        val keySeed = json.optString("keySeed").takeIf { it.isNotBlank() }
        val segmentBytes = json.optJSONObject("obf")?.optInt("segmentBytes", 0) ?: 0
        val choice = PlaybackChoice(
            url = streamProxy.createPlaylistUrl(streamSource, keySeed, segmentBytes),
            headers = mapOf("Referer" to source.root + "/"),
            subtitles = GotakuSource.subtitles(json.optJSONArray("subtitles")),
            skipRanges = embedData.optJSONObject("skip")?.toSkipRanges().orEmpty(),
            selectedStreamType = selectedStreamType,
            keySeed = keySeed,
            segmentBytes = segmentBytes
        )
        return choice
    }

    private suspend fun request(
        url: String,
        body: String? = null,
        contentType: String? = null,
        accept: String = "*/*",
        source: CatalogSource,
        referer: String = source.root + "/",
        origin: String? = null
    ): String = requestText(url, body, contentType, accept, source, referer, origin)

    private fun JSONObject.toSkipRanges(): List<SkipRange> {
        val result = mutableListOf<SkipRange>()
        fun add(name: String, kind: SkipRange.Kind) {
            val pair = optJSONArray(name) ?: return
            if (pair.length() >= 2) result += SkipRange(pair.optDouble(0), pair.optDouble(1), kind)
        }
        add("intro", SkipRange.Kind.INTRO)
        add("outro", SkipRange.Kind.OUTRO)
        add("filler", SkipRange.Kind.FILLER)
        return result
    }
}
