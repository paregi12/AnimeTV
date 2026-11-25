package com.paregi.animetv.source.gotaku

import com.paregi.animetv.AndroidGotakuCrypto
import com.paregi.animetv.AndroidStreamProxy
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import com.paregi.animetv.source.AndroidCatalogApi
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.shared.SourceRequestText
import kotlinx.coroutines.CancellationException
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
        source: CatalogSource,
        mirrorIndex: Int = 0
    ): PlaybackChoice {
        val flagsRoot = try {
            catalogApi.json("${source.root}/api/v1/episodes/$episodeId?k=10", source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val flags = flagsRoot?.optJSONObject("data") ?: flagsRoot?.takeIf {
            it.has("soft_sub") || it.has("hard_sub") || it.has("dub")
        }
        val modes = resolveModes(flags, preferDub, hasSub)
        val selectedIndex = mirrorIndex.coerceIn(0, modes.lastIndex.coerceAtLeast(0))
        val candidateIndices = listOf(selectedIndex) + modes.indices.filter { it != selectedIndex }

        var chosenIndex = selectedIndex
        var chosenEmbed: JSONObject? = null
        var chosenEmbedUrl: String? = null
        var lastError: Exception? = null
        for (idx in candidateIndices) {
            val mode = modes[idx]
            val embedData = try {
                catalogApi.json(
                    "${source.root}/api/v1/episodes/$episodeId/embed?type=${mode.type}&k=10",
                    source
                ).optJSONObject("data")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                null
            }
            val url = embedData?.takeIf { !it.isNull("url") }
                ?.optString("url")
                ?.takeIf(String::isNotBlank)
            if (embedData != null && url != null) {
                chosenIndex = idx
                chosenEmbed = embedData
                chosenEmbedUrl = url
                break
            }
        }
        val embedData = chosenEmbed ?: throw (lastError ?: IllegalStateException("Episode has no playable stream"))
        val embedUrl = chosenEmbedUrl ?: error("Episode response did not include an embed URL")
        val selectedMode = modes[chosenIndex]

        val absoluteEmbed = when {
            embedUrl.startsWith("//") -> "https:$embedUrl"
            embedUrl.startsWith("http://") || embedUrl.startsWith("https://") -> embedUrl
            embedUrl.startsWith("/") -> "${source.root}$embedUrl"
            else -> "${source.root}/$embedUrl"
        }
        val html = request(absoluteEmbed, source = source)
        val (base, stamp) = extractManifestAttrs(html)
        val nonce = AndroidGotakuCrypto.newNonce()
        val manifestPath = AndroidGotakuCrypto.encryptManifest(stamp, nonce)
            ?: error("Could not prepare manifest request")
        val manifestUrl = "${base.trimEnd('/')}/$manifestPath"
        val encryptedManifest = request(
            manifestUrl,
            accept = "text/plain",
            referer = absoluteEmbed,
            source = source
        ).trim()
        val manifest = AndroidGotakuCrypto.decryptManifest(encryptedManifest, nonce)
            ?: error("Could not decrypt video manifest")
        val json = JSONObject(manifest)
        val streamSource = json.takeIf { !it.isNull("source") }
            ?.optString("source")
            ?.takeIf(String::isNotBlank)
            ?: error("Video manifest did not include a source")
        val keySeed = json.takeIf { !it.isNull("keySeed") }
            ?.optString("keySeed")
            ?.takeIf(String::isNotBlank)
        val segmentBytes = json.optJSONObject("obf")?.optInt("segmentBytes", 0) ?: 0

        return PlaybackChoice(
            url = streamProxy.createPlaylistUrl(streamSource, keySeed, segmentBytes),
            headers = mapOf("Referer" to "${source.root}/"),
            subtitles = GotakuSource.subtitles(json.optJSONArray("subtitles")),
            skipRanges = embedData.optJSONObject("skip")?.toSkipRanges().orEmpty(),
            selectedStreamType = selectedMode.streamType,
            streamServerNames = modes.map { it.label },
            selectedStreamServerIndex = chosenIndex,
            keySeed = keySeed,
            segmentBytes = segmentBytes
        )
    }

    private fun resolveModes(flags: JSONObject?, preferDub: Boolean, hasSub: Boolean): List<AudioMode> {
        if (flags != null) {
            val available = buildList {
                if (preferDub) {
                    if (flags.optBoolean("dub", false)) add(DUB_MODE)
                    if (flags.optBoolean("soft_sub", false)) add(SOFT_SUB_MODE)
                    if (flags.optBoolean("hard_sub", false)) add(HARD_SUB_MODE)
                } else {
                    if (flags.optBoolean("soft_sub", false)) add(SOFT_SUB_MODE)
                    if (flags.optBoolean("hard_sub", false)) add(HARD_SUB_MODE)
                    if (flags.optBoolean("dub", false)) add(DUB_MODE)
                }
            }
            if (available.isNotEmpty()) return available
        }
        return when {
            preferDub -> listOf(DUB_MODE, HARD_SUB_MODE, SOFT_SUB_MODE)
            hasSub -> listOf(SOFT_SUB_MODE, HARD_SUB_MODE)
            else -> listOf(DUB_MODE, SOFT_SUB_MODE, HARD_SUB_MODE)
        }
    }

    private fun extractManifestAttrs(html: String): Pair<String, String> {
        MANIFEST_ATTRS_REGEX.find(html)?.let { match ->
            val base = match.groupValues[1].trim()
            val stamp = match.groupValues[2].trim()
            if (base.isNotEmpty() && stamp.isNotEmpty()) return base to stamp
        }
        MANIFEST_ATTRS_REVERSE_REGEX.find(html)?.let { match ->
            val stamp = match.groupValues[1].trim()
            val base = match.groupValues[2].trim()
            if (base.isNotEmpty() && stamp.isNotEmpty()) return base to stamp
        }
        val base = MANIFEST_BASE_REGEX.find(html)?.groupValues?.get(1)?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error("Video page did not include a manifest endpoint")
        val stamp = MANIFEST_STAMP_REGEX.find(html)?.groupValues?.get(1)?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error("Video page did not include a manifest stamp")
        return base to stamp
    }

    private suspend fun request(
        url: String,
        accept: String = "*/*",
        source: CatalogSource,
        referer: String = "${source.root}/"
    ): String = requestText(url, null, null, accept, source, referer, null)

    private fun JSONObject.toSkipRanges(): List<SkipRange> {
        val result = mutableListOf<SkipRange>()
        fun add(name: String, kind: SkipRange.Kind) {
            val pair = optJSONArray(name) ?: return
            if (pair.length() >= 2) {
                val start = pair.optDouble(0, Double.NaN)
                val end = pair.optDouble(1, Double.NaN)
                if (!start.isNaN() && !end.isNaN() && end > start) {
                    result += SkipRange(start, end, kind)
                }
            }
        }
        add("intro", SkipRange.Kind.INTRO)
        add("outro", SkipRange.Kind.OUTRO)
        add("filler", SkipRange.Kind.FILLER)
        return result
    }

    private data class AudioMode(
        val type: String,
        val label: String,
        val streamType: PlaybackStreamType
    )

    private companion object {
        val SOFT_SUB_MODE = AudioMode("soft_sub", "Soft Sub", PlaybackStreamType.SUB)
        val HARD_SUB_MODE = AudioMode("hard_sub", "Hard Sub", PlaybackStreamType.SUB)
        val DUB_MODE = AudioMode("dub", "Dub", PlaybackStreamType.DUB)

        val MANIFEST_ATTRS_REGEX =
            Regex("""data-manifest-base=["']([^"']+)["'][^>]*data-manifest-stamp=["']([^"']+)["']""")
        val MANIFEST_ATTRS_REVERSE_REGEX =
            Regex("""data-manifest-stamp=["']([^"']+)["'][^>]*data-manifest-base=["']([^"']+)["']""")
        val MANIFEST_BASE_REGEX = Regex("""data-manifest-base=["']([^"']+)["']""")
        val MANIFEST_STAMP_REGEX = Regex("""data-manifest-stamp=["']([^"']+)["']""")
    }
}
