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
        @Suppress("UNUSED_PARAMETER") mirrorIndex: Int = 0,
        subtitleLanguage: String = "",
        requestedStreamType: PlaybackStreamType? = null
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
        val modes = resolveModes(flags, preferDub, hasSub, subtitleLanguage, requestedStreamType)

        var chosenMode: AudioMode? = null
        var chosenEmbed: JSONObject? = null
        var chosenEmbedUrl: String? = null
        var lastError: Exception? = null
        for (mode in modes) {
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
                chosenMode = mode
                chosenEmbed = embedData
                chosenEmbedUrl = url
                break
            }
        }
        val embedData = chosenEmbed ?: throw (lastError ?: IllegalStateException("Episode has no playable stream"))
        val embedUrl = chosenEmbedUrl ?: error("Episode response did not include an embed URL")
        val selectedMode = chosenMode ?: error("Episode has no playable stream")

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
            streamServerNames = listOf(
                if (selectedMode.streamType == PlaybackStreamType.DUB) "Gotaku Dub" else "Gotaku Sub"
            ),
            selectedStreamServerIndex = 0,
            keySeed = keySeed,
            segmentBytes = segmentBytes
        )
    }

    private fun resolveModes(
        flags: JSONObject?,
        preferDub: Boolean,
        hasSub: Boolean,
        subtitleLanguage: String,
        requestedStreamType: PlaybackStreamType?
    ): List<AudioMode> {
        val effectiveType = requestedStreamType ?: when {
            preferDub -> PlaybackStreamType.DUB
            subtitleLanguage.equals("hard", ignoreCase = true) && hasSub -> PlaybackStreamType.SUB
            else -> null
        }
        if (flags != null) {
            val hasDub = flags.optBoolean("dub", false)
            val hasSoft = flags.optBoolean("soft_sub", false)
            val hasHard = flags.optBoolean("hard_sub", false)
            val available = buildList {
                when (effectiveType) {
                    PlaybackStreamType.SUB -> {
                        if (hasHard) add(HARD_SUB_MODE)
                        if (hasSoft) add(SOFT_SUB_MODE)
                        if (hasDub) add(DUB_MODE)
                    }
                    PlaybackStreamType.SOFTSUB -> {
                        if (hasSoft) add(SOFT_SUB_MODE)
                        if (hasHard) add(HARD_SUB_MODE)
                        if (hasDub) add(DUB_MODE)
                    }
                    PlaybackStreamType.DUB -> {
                        if (hasDub) add(DUB_MODE)
                        if (hasSoft) add(SOFT_SUB_MODE)
                        if (hasHard) add(HARD_SUB_MODE)
                    }
                    null -> {
                        if (hasSoft) add(SOFT_SUB_MODE)
                        if (hasHard) add(HARD_SUB_MODE)
                        if (hasDub) add(DUB_MODE)
                    }
                }
            }
            if (available.isNotEmpty()) return available
        }
        return when (effectiveType) {
            PlaybackStreamType.DUB -> listOf(DUB_MODE, HARD_SUB_MODE, SOFT_SUB_MODE)
            PlaybackStreamType.SUB -> listOf(HARD_SUB_MODE, SOFT_SUB_MODE, DUB_MODE)
            PlaybackStreamType.SOFTSUB -> listOf(SOFT_SUB_MODE, HARD_SUB_MODE, DUB_MODE)
            null -> if (hasSub) listOf(SOFT_SUB_MODE, HARD_SUB_MODE)
            else listOf(DUB_MODE, SOFT_SUB_MODE, HARD_SUB_MODE)
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
        val streamType: PlaybackStreamType
    )

    private companion object {
        val SOFT_SUB_MODE = AudioMode("soft_sub", PlaybackStreamType.SOFTSUB)
        val HARD_SUB_MODE = AudioMode("hard_sub", PlaybackStreamType.SUB)
        val DUB_MODE = AudioMode("dub", PlaybackStreamType.DUB)

        val MANIFEST_ATTRS_REGEX =
            Regex("""data-manifest-base=["']([^"']+)["'][^>]*data-manifest-stamp=["']([^"']+)["']""")
        val MANIFEST_ATTRS_REVERSE_REGEX =
            Regex("""data-manifest-stamp=["']([^"']+)["'][^>]*data-manifest-base=["']([^"']+)["']""")
        val MANIFEST_BASE_REGEX = Regex("""data-manifest-base=["']([^"']+)["']""")
        val MANIFEST_STAMP_REGEX = Regex("""data-manifest-stamp=["']([^"']+)["']""")
    }
}
