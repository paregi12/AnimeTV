package com.paregi.animetv.source

import com.paregi.animetv.AndroidStreamProxy
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.domain.TranslationRepository
import com.paregi.animetv.source.gotaku.GotakuPlaybackResolver
import com.paregi.animetv.source.reanime.ReAnimePlaybackResolver
import com.paregi.animetv.source.shared.SourceRequestJson
import com.paregi.animetv.source.shared.SourceRequestText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Selects a provider playback resolver and translates subtitles on demand. */
internal class AndroidPlaybackResolver(
    streamProxy: AndroidStreamProxy,
    catalogApi: AndroidCatalogApi,
    requestText: SourceRequestText,
    requestJsonCall: SourceRequestJson,
    private val translationRepository: TranslationRepository
) {
    private val gotakuResolver = GotakuPlaybackResolver(streamProxy, catalogApi, requestText)
    private val reAnimeResolver = ReAnimePlaybackResolver(streamProxy, catalogApi, requestText, requestJsonCall)

    suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean,
        mirrorIndex: Int,
        subtitleLanguage: String,
        hasSub: Boolean,
        requestedStreamType: PlaybackStreamType?,
        source: CatalogSource
    ): PlaybackChoice = withContext(Dispatchers.IO) {
        if (source.isReAnime) {
            reAnimeResolver.resolvePlayback(
                episodeId = episodeId,
                preferDub = preferDub,
                mirrorIndex = mirrorIndex,
                requestedStreamType = requestedStreamType,
                source = source
            )
        } else {
            gotakuResolver.resolvePlayback(
                episodeId = episodeId,
                preferDub = preferDub,
                hasSub = hasSub,
                source = source,
                mirrorIndex = mirrorIndex,
                subtitleLanguage = subtitleLanguage,
                requestedStreamType = requestedStreamType
            )
        }
    }

    suspend fun translateChoiceSubtitle(
        choice: PlaybackChoice,
        targetLanguage: String
    ): SubtitleTrack? = withContext(Dispatchers.IO) {
        if (targetLanguage.isBlank() || targetLanguage in setOf("hard", "nosub", "dub", "en")) return@withContext null
        fun matchesTarget(track: SubtitleTrack): Boolean {
            val trackLanguage = track.language.lowercase(Locale.ROOT)
            val target = targetLanguage.lowercase(Locale.ROOT)
            return trackLanguage == target || trackLanguage.substringBefore('-') == target.substringBefore('-')
        }
        if (choice.subtitles.any(::matchesTarget)) return@withContext null
        val candidates = choice.subtitles.filter {
            it.language.equals("en", ignoreCase = true) || it.language.startsWith("en-", ignoreCase = true)
        } + choice.subtitles.filter { it.defaultSelected } + choice.subtitles
        val sourceTrack = candidates.distinctBy { it.id }.firstOrNull { track ->
            track.url.substringBefore('?').substringAfterLast('.', "").lowercase() in
                setOf("vtt", "srt", "ass", "ssa")
        } ?: return@withContext null
        try {
            translationRepository.translateSubtitle(sourceTrack, choice.headers, targetLanguage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
