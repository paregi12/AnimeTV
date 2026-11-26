package com.paregi.animetv.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformBackHandler
import com.paregi.animetv.PlatformPlayer
import com.paregi.animetv.PlatformSubtitlePreviewOverlay
import com.paregi.animetv.core.theme.AnimeTvThemeColors
import com.paregi.animetv.core.theme.AnimeTvTokens
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvSpinner
import com.paregi.animetv.core.ui.AppToastOverlay
import com.paregi.animetv.core.ui.FocusCard
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.calculateCardDimensions
import com.paregi.animetv.core.ui.displayAnimeSynopsis
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AudioLanguageCatalog
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import com.paregi.animetv.domain.SubtitleLanguageCatalog
import com.paregi.animetv.domain.SubtitleStyleCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.domain.isAdultAgeRating
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformSubtitleFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.TimeSource

internal enum class PlaybackMenuZone {
    GENRES,
    SETTINGS,
    TRACKS,
    EPISODES,
    SEASONS,
    RELATED,
    RECS
}

internal data class PlayerOsdHudState(
    val icon: String,
    val text: String,
    val progressPercent: Float
)

private enum class PlaybackQuickDialog {
    SPEED,
    STREAM_TYPE,
    STREAM_SERVER,
    AUDIO_LANGUAGE,
    QUALITY,
    SUBTITLES
}

@Composable
internal fun PlaybackScreen(
    title: String,
    description: String,
    genres: List<String>,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    related: List<Anime>,
    recommendations: List<Anime>,
    onEpisodeSelected: (Episode) -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    episodeLabel: String,
    posterUrl: String?,
    canPrevious: Boolean,
    canNext: Boolean,
    choice: PlaybackChoice,
    hasSubStream: Boolean,
    hasDubStream: Boolean,
    subStreamLabel: String,
    onStreamTypeChange: (PlaybackStreamType) -> Unit,
    streamServerNames: List<String>,
    selectedStreamServerIndex: Int,
    onStreamServerChange: (Int) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    subtitleLanguage: String,
    subtitleVariant: String,
    onSubtitleVariantSelected: (String) -> Unit,
    audioLanguage: String,
    onAudioLanguageChange: (String) -> Unit,
    subtitleStyle: Int,
    onSubtitleStyleChange: (Int) -> Unit,
    playbackQuality: Int,
    onPlaybackQualityChange: (Int) -> Unit,
    autoSkipIntro: Boolean,
    skipFiller: Boolean,
    startPositionMs: Long,
    seekSeconds: Int,
    videoScale: Int,
    @Suppress("UNUSED_PARAMETER") onVideoScaleChange: (Int) -> Unit = {},
    videoBuffer: Int,
    progressiveCache: Boolean,
    cacheSizeMb: Int,
    disableTouchGestures: Boolean,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    showBackHint: Boolean,
    seasons: List<Anime> = emptyList(),
    onOpenSettings: () -> Unit = {},
    inWatchlist: Boolean = false,
    onToggleWatchlist: (() -> Unit)? = null,
    isTracked: Boolean = false,
    onTrackingClick: (() -> Unit)? = null,
    onSearchGenre: ((String) -> Unit)? = null,
    currentAnimeId: String = "",
    animeFormat: String? = null,
    animeQuality: String? = null,
    animeRating: String? = null,
    settingsOverlayOpen: Boolean = false
) {
    var activeDialog by remember { mutableStateOf<PlaybackQuickDialog?>(null) }
    var subtitleStyleDialogOpen by remember { mutableStateOf(false) }
    var dialogSubtitleIndex by remember(choice.url) { mutableIntStateOf(-1) }
    var selectedSubtitleOverride by remember(choice.url, subtitleLanguage, choice.subtitles) {
        mutableStateOf<Int?>(null)
    }
    var availableAudioLanguages by remember(choice.url) { mutableStateOf<Set<String>>(emptySet()) }
    var detectedVideoHeights by remember(choice.url) { mutableStateOf<List<Int>>(emptyList()) }

    val selectedAudioLanguage = AudioLanguageCatalog.all.firstOrNull { it.code == audioLanguage }
        ?: AudioLanguageCatalog.all.first()
    val effectiveAudioLabel = if (isAudioLanguageAvailable(audioLanguage, availableAudioLanguages)) {
        selectedAudioLanguage.label
    } else {
        AudioLanguageCatalog.all.first().label
    }
    val effectiveStreamServerNames = if (streamServerNames.isNotEmpty()) streamServerNames else listOf("Main", "Mirror")
    val selectedStreamServer = effectiveStreamServerNames.getOrNull(selectedStreamServerIndex)
        ?: effectiveStreamServerNames.firstOrNull()
    val episodePickerLabel = when {
        episodes.isNotEmpty() -> "Episodes"
        seasons.isNotEmpty() -> "Seasons"
        related.isNotEmpty() -> "Related"
        recommendations.isNotEmpty() -> "Recommendation"
        else -> null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PlatformPlayer(
            url = choice.url,
            metadataTitle = title,
            metadataArtist = episodeLabel,
            metadataDescription = displayAnimeSynopsis(description),
            metadataGenres = genres,
            artworkUrl = posterUrl,
            hasNext = canNext,
            hasPrevious = canPrevious,
            episodePickerLabel = episodePickerLabel,
            onEpisodePickerRequest = null,
            onNextEpisode = onNext,
            onPreviousEpisode = onPrevious,
            headers = choice.headers,
            subtitles = choice.subtitles,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedDialogRequest = { activeDialog = PlaybackQuickDialog.SPEED },
            currentStreamType = choice.selectedStreamType,
            hasSubStream = hasSubStream,
            hasDubStream = hasDubStream,
            subStreamLabel = subStreamLabel,
            onStreamTypeDialogRequest = { activeDialog = PlaybackQuickDialog.STREAM_TYPE },
            streamServerLabel = selectedStreamServer,
            onStreamServerDialogRequest = { activeDialog = PlaybackQuickDialog.STREAM_SERVER },
            subtitleLanguage = subtitleLanguage,
            subtitleVariant = subtitleVariant,
            onSubtitleVariantSelected = onSubtitleVariantSelected,
            audioLanguage = audioLanguage,
            audioLanguageLabel = effectiveAudioLabel,
            onAudioLanguageDialogRequest = { activeDialog = PlaybackQuickDialog.AUDIO_LANGUAGE },
            subtitleStyle = subtitleStyle,
            onSubtitleStyleDialogRequest = { subtitleStyleDialogOpen = true },
            playbackQuality = playbackQuality,
            onPlaybackQualityChange = onPlaybackQualityChange,
            onPlaybackQualityDialogRequest = { activeDialog = PlaybackQuickDialog.QUALITY },
            skipRanges = choice.skipRanges,
            autoSkipIntro = autoSkipIntro,
            skipFiller = skipFiller,
            startPositionMs = startPositionMs,
            seekSeconds = seekSeconds,
            videoScale = videoScale,
            onVideoScaleDialogRequest = null,
            videoBuffer = videoBuffer,
            progressiveCache = progressiveCache,
            cacheSizeMb = cacheSizeMb,
            disableTouchGestures = disableTouchGestures,
            onProgress = onProgress,
            onEnded = onEnded,
            onBack = onBack,
            episodes = episodes,
            selectedEpisodeId = selectedEpisodeId,
            onEpisodeSelected = onEpisodeSelected,
            seasons = seasons,
            related = related,
            recommendations = recommendations,
            onAnimeSelected = onAnimeSelected,
            onOpenSettings = onOpenSettings,
            inWatchlist = inWatchlist,
            onToggleWatchlist = onToggleWatchlist,
            isTracked = isTracked,
            onTrackingClick = onTrackingClick,
            onSearchGenre = onSearchGenre,
            onSubtitlesDialogRequest = { activeIndex ->
                dialogSubtitleIndex = activeIndex
                activeDialog = PlaybackQuickDialog.SUBTITLES
            },
            selectedSubtitleIndexOverride = selectedSubtitleOverride,
            onAvailableAudioLanguagesChanged = { availableAudioLanguages = it },
            onDetectedVideoHeightsChanged = { _, heights -> detectedVideoHeights = heights },
            showBackHint = showBackHint,
            currentAnimeId = currentAnimeId,
            metadataFormat = animeFormat,
            metadataQuality = animeQuality,
            metadataRating = animeRating,
            settingsOverlayOpen = settingsOverlayOpen
        )

        if (showBackHint) {
            AppToastOverlay(message = "Press back again to close playback...")
        }

        PlaybackQuickOptionDialogs(
            activeDialog = activeDialog,
            onDismiss = { activeDialog = null },
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            currentStreamType = choice.selectedStreamType,
            hasSubStream = hasSubStream,
            hasDubStream = hasDubStream,
            subStreamLabel = subStreamLabel,
            onStreamTypeChange = onStreamTypeChange,
            streamServerNames = effectiveStreamServerNames,
            selectedStreamServerIndex = selectedStreamServerIndex,
            onStreamServerChange = onStreamServerChange,
            audioLanguage = audioLanguage,
            availableAudioLanguages = availableAudioLanguages,
            onAudioLanguageChange = onAudioLanguageChange,
            playbackQuality = playbackQuality,
            detectedVideoHeights = detectedVideoHeights,
            onPlaybackQualityChange = onPlaybackQualityChange,
            subtitles = choice.subtitles,
            subtitleLanguage = subtitleLanguage,
            dialogSubtitleIndex = dialogSubtitleIndex,
            onSubtitleTrackSelect = { trackIndex, formattedVariant ->
                selectedSubtitleOverride = trackIndex
                onSubtitleVariantSelected(formattedVariant)
            }
        )

        if (subtitleStyleDialogOpen) {
            SubtitleStylePickerDialog(
                style = subtitleStyle,
                onChange = onSubtitleStyleChange,
                onDismiss = { subtitleStyleDialogOpen = false }
            )
        }
    }
}

@Composable
private fun PlaybackQuickOptionDialogs(
    activeDialog: PlaybackQuickDialog?,
    onDismiss: () -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    currentStreamType: PlaybackStreamType,
    hasSubStream: Boolean,
    hasDubStream: Boolean,
    subStreamLabel: String,
    onStreamTypeChange: (PlaybackStreamType) -> Unit,
    streamServerNames: List<String>,
    selectedStreamServerIndex: Int,
    onStreamServerChange: (Int) -> Unit,
    audioLanguage: String,
    availableAudioLanguages: Set<String>,
    onAudioLanguageChange: (String) -> Unit,
    playbackQuality: Int,
    detectedVideoHeights: List<Int>,
    onPlaybackQualityChange: (Int) -> Unit,
    subtitles: List<SubtitleTrack> = emptyList(),
    subtitleLanguage: String = "",
    dialogSubtitleIndex: Int = -1,
    onSubtitleTrackSelect: ((Int, String) -> Unit)? = null
) {
    val playbackSpeeds = remember { (1..10).map { it * 0.25f } }
    val streamTypes = buildList {
        if (hasSubStream) {
            if (subStreamLabel.equals("Sub", ignoreCase = true)) {
                add(if (currentStreamType == PlaybackStreamType.SOFTSUB) PlaybackStreamType.SOFTSUB else PlaybackStreamType.SUB)
            } else {
                add(PlaybackStreamType.SUB)
                add(PlaybackStreamType.SOFTSUB)
            }
        } else if (currentStreamType == PlaybackStreamType.SUB || currentStreamType == PlaybackStreamType.SOFTSUB) {
            add(currentStreamType)
        }
        if (hasDubStream || currentStreamType == PlaybackStreamType.DUB) {
            add(PlaybackStreamType.DUB)
        }
    }
    val effectiveServers = if (streamServerNames.isNotEmpty()) streamServerNames else listOf("Main", "Mirror")

    when (activeDialog) {
        PlaybackQuickDialog.SPEED -> {
            val selectedIndex = playbackSpeeds.indexOfFirst { abs(it - playbackSpeed) < 0.01f }.coerceAtLeast(0)
            ListOrderDialog(
                title = "Playback Speed",
                items = playbackSpeeds.map { speed ->
                    ListOrderItem(
                        id = speed.toString(),
                        title = playbackSpeedLabel(speed),
                        selected = abs(speed - playbackSpeed) < 0.01f
                    )
                },
                mode = ListOrderMode.LIST,
                initialSelectedIndex = selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index, _ ->
                    onPlaybackSpeedChange(playbackSpeeds[index])
                    onDismiss()
                }
            )
        }
        PlaybackQuickDialog.STREAM_TYPE -> {
            val selectedIndex = streamTypes.indexOf(currentStreamType).coerceAtLeast(0)
            ListOrderDialog(
                title = "Stream Type",
                items = streamTypes.map { streamType ->
                    ListOrderItem(
                        id = streamType.name,
                        title = streamTypeDisplayLabel(streamType, subStreamLabel),
                        selected = streamType == currentStreamType
                    )
                },
                mode = ListOrderMode.LIST,
                initialSelectedIndex = selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index, _ ->
                    val chosen = streamTypes[index]
                    onDismiss()
                    if (chosen != currentStreamType) onStreamTypeChange(chosen)
                }
            )
        }
        PlaybackQuickDialog.STREAM_SERVER -> {
            val selectedIndex = selectedStreamServerIndex.coerceIn(0, effectiveServers.lastIndex.coerceAtLeast(0))
            ListOrderDialog(
                title = "Stream Server",
                items = effectiveServers.mapIndexed { index, name ->
                    ListOrderItem(
                        id = "server_$index",
                        title = name,
                        selected = index == selectedIndex
                    )
                },
                mode = ListOrderMode.LIST,
                initialSelectedIndex = selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index, _ ->
                    onDismiss()
                    if (index != selectedStreamServerIndex) onStreamServerChange(index)
                }
            )
        }
        PlaybackQuickDialog.AUDIO_LANGUAGE -> {
            val selectedIndex = AudioLanguageCatalog.all.indexOfFirst { it.code == audioLanguage }.coerceAtLeast(0)
            ListOrderDialog(
                title = "Audio Language",
                items = AudioLanguageCatalog.all.map { language ->
                    val available = isAudioLanguageAvailable(
                        code = language.code,
                        availableTracks = availableAudioLanguages
                    )
                    ListOrderItem(
                        id = language.code.ifBlank { "original" },
                        title = language.label,
                        selected = language.code == audioLanguage,
                        rightBadge = if (available) "✓ Available" else null
                    )
                },
                mode = ListOrderMode.LIST,
                initialSelectedIndex = selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index, _ ->
                    onAudioLanguageChange(AudioLanguageCatalog.all[index].code)
                    onDismiss()
                }
            )
        }
        PlaybackQuickDialog.QUALITY -> {
            val qualityNames = listOf("Auto", "High", "Medium", "Low")
            val selectedIndex = playbackQuality.coerceIn(0, qualityNames.lastIndex)
            ListOrderDialog(
                title = "Video Quality",
                items = qualityNames.mapIndexed { index, name ->
                    val heightHint = if (index > 0) detectedVideoHeights.getOrNull(index - 1) else null
                    ListOrderItem(
                        id = "quality_$index",
                        title = name,
                        value = heightHint?.let { "${it}p" },
                        selected = index == selectedIndex
                    )
                },
                mode = ListOrderMode.LIST,
                initialSelectedIndex = selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index, _ ->
                    onPlaybackQualityChange(index)
                    onDismiss()
                }
            )
        }
        PlaybackQuickDialog.SUBTITLES -> {
            val variants = resolveSubVariants(subtitles, subtitleLanguage)
            if (variants.size > 1) {
                val langName = targetSubtitleLanguageName(subtitleLanguage)
                val subtitleRows = variants.map { (origIdx, track) ->
                    val rawName = track.label.ifBlank { track.language.uppercase().ifBlank { "Track ${origIdx + 1}" } }
                    val formatted = formatVariantName(rawName, langName)
                    ListOrderItem(
                        id = "cc_${track.id}_$origIdx",
                        title = formatted,
                        selected = origIdx == dialogSubtitleIndex
                    )
                }
                val initialRowIndex = variants.indexOfFirst { it.first == dialogSubtitleIndex }.coerceAtLeast(0)
                ListOrderDialog(
                    title = "Subtitles",
                    items = subtitleRows,
                    mode = ListOrderMode.LIST,
                    initialSelectedIndex = initialRowIndex,
                    onDismiss = onDismiss,
                    onSelect = { rowIndex, _ ->
                        val (origIdx, track) = variants[rowIndex]
                        val formatted = formatVariantName(
                            track.label.ifBlank { track.language },
                            langName
                        )
                        onSubtitleTrackSelect?.invoke(origIdx, formatted)
                        onDismiss()
                    }
                )
            } else {
                onDismiss()
            }
        }
        null -> Unit
    }
}

private val SUBTITLE_MATCH_LANG_ALIASES = mapOf(
    "it" to listOf("ita"),
    "es" to listOf("esp", "spa"),
    "ar" to listOf("ara"),
    "fr" to listOf("fra", "fre"),
    "ru" to listOf("rus"),
    "de" to listOf("deu", "ger"),
    "pt" to listOf("por", "bra")
)

private fun targetSubtitleLanguageCode(subtitleLanguage: String): String =
    if (subtitleLanguage.isBlank() || subtitleLanguage.equals("hard", ignoreCase = true)) "en"
    else subtitleLanguage

private fun targetSubtitleLanguageName(subtitleLanguage: String): String? {
    val code = targetSubtitleLanguageCode(subtitleLanguage)
    return SubtitleLanguageCatalog.all.firstOrNull { it.code.equals(code, ignoreCase = true) }?.label
}

private fun resolveSubVariants(
    subtitles: List<SubtitleTrack>,
    subtitleLanguage: String
): List<Pair<Int, SubtitleTrack>> {
    if (subtitles.size <= 1 || subtitleLanguage.equals("nosub", ignoreCase = true)) return emptyList()
    val targetLang = targetSubtitleLanguageCode(subtitleLanguage).lowercase()
    val aliases = SUBTITLE_MATCH_LANG_ALIASES[targetLang]
    val matches = mutableListOf<Pair<Int, SubtitleTrack>>()
    if (aliases != null) {
        subtitles.forEachIndexed { idx, track ->
            val labelLower = track.label.lowercase()
            val langLower = track.language.lowercase()
            if (aliases.any { alias -> labelLower.contains(alias) || langLower.contains(alias) }) {
                matches += idx to track
            }
        }
    }
    if (matches.isEmpty()) {
        val langName = targetSubtitleLanguageName(subtitleLanguage)?.lowercase()
        subtitles.forEachIndexed { idx, track ->
            val labelLower = track.label.lowercase()
            val langLower = track.language.lowercase()
            val isMatch = (langName != null && (labelLower == langName || labelLower.contains(langName))) ||
                langLower == targetLang ||
                langLower.substringBefore('-') == targetLang.substringBefore('-') ||
                (targetLang == "en" && (labelLower.contains("eng") || langLower.startsWith("en")))
            if (isMatch) matches += idx to track
        }
    }
    return matches
}

private fun formatVariantName(name: String, langName: String?): String {
    val clean = name.trim()
    if (!langName.isNullOrBlank()) {
        val reg = Regex("^${Regex.escape(langName)}\\s*\\((.*)\\)$", RegexOption.IGNORE_CASE)
        reg.matchEntire(clean)?.groupValues?.getOrNull(1)?.let { return it }
    }
    return clean
}

private fun streamTypeDisplayLabel(type: PlaybackStreamType, subStreamLabel: String): String = when (type) {
    PlaybackStreamType.SUB -> subStreamLabel
    PlaybackStreamType.SOFTSUB -> "Softsub"
    PlaybackStreamType.DUB -> "Dub"
}

private val ISO_2_TO_3 = mapOf(
    "en" to "eng",
    "fr" to "fre",
    "fra" to "fre",
    "de" to "ger",
    "deu" to "ger",
    "hi" to "hin",
    "id" to "ind",
    "it" to "ita",
    "pt" to "por",
    "es" to "spa",
    "ta" to "tam",
    "te" to "tel",
    "ja" to "jpn"
)

private fun isAudioLanguageAvailable(
    code: String,
    availableTracks: Set<String>
): Boolean {
    if (code.isEmpty()) return true
    if (availableTracks.isEmpty()) return false
    val target = code.lowercase()
    return availableTracks.any { raw ->
        val norm = raw.substringBefore('-').lowercase()
        val mapped = ISO_2_TO_3[norm] ?: norm
        norm == target || mapped == target || raw.startsWith(target.take(3))
    }
}

internal fun playbackSpeedLabel(speed: Float): String {
    val value = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "${value}x"
}

internal fun playbackSpeedStatusLabel(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    val fraction = (hundredths % 100).toString().padStart(2, '0')
    return "${hundredths / 100}.${fraction}x"
}

internal fun formatPlaybackTime(positionMs: Long, durationMs: Long = 0L): String {
    val totalSec = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
    val durSec = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val remaining = totalSec % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = remaining.toString().padStart(2, '0')
    return if (durSec >= 3600 || hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackOsdContent(
    metadataTitle: String,
    episodeLabel: String,
    metadataDescription: String,
    metadataGenres: List<String>,
    artworkUrl: String?,
    firstFrameRendered: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    statusTextOverride: String?,
    errorMessage: String?,
    skipRanges: List<SkipRange>,
    autoSkipIntro: Boolean,
    seekSeconds: Int,
    playbackSpeed: Float,
    currentStreamType: PlaybackStreamType,
    hasSubStream: Boolean,
    hasDubStream: Boolean,
    subStreamLabel: String,
    streamServerLabel: String?,
    subtitleLanguage: String,
    selectedSubtitleLabel: String?,
    subtitlesCount: Int,
    audioLanguageLabel: String,
    playbackQuality: Int,
    detectedVideoHeight: Int,
    disableTouchGestures: Boolean,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    seasons: List<Anime>,
    related: List<Anime>,
    recommendations: List<Anime>,
    inWatchlist: Boolean,
    isTracked: Boolean,
    osdHudState: PlayerOsdHudState?,
    seekIndicatorSide: Int?,
    onShowOsdHud: (PlayerOsdHudState) -> Unit,
    onShowSeekIndicator: (Int) -> Unit,
    onDebouncedSeek: (Long) -> Unit,
    onImmediateSeek: (Long) -> Unit,
    onTogglePlay: (hideOsdOnPlay: Boolean) -> Unit,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onEpisodeSelected: ((Episode) -> Unit)?,
    onAnimeSelected: ((Anime) -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onPlaybackSpeedDialogRequest: () -> Unit,
    onStreamServerDialogRequest: () -> Unit,
    onPlaybackQualityDialogRequest: (() -> Unit)?,
    onStreamTypeDialogRequest: () -> Unit,
    onSubtitlesDialogRequest: (() -> Unit)?,
    onAudioLanguageDialogRequest: () -> Unit,
    onToggleWatchlist: (() -> Unit)?,
    onTrackingClick: (() -> Unit)?,
    onSearchGenre: ((String) -> Unit)?,
    onAdjustBrightness: (Int) -> Unit,
    onAdjustVolume: (Int) -> Unit,
    onBack: () -> Unit,
    videoContent: @Composable BoxScope.() -> Unit = {},
    currentAnimeId: String = "",
    metadataFormat: String? = null,
    metadataQuality: String? = null,
    metadataRating: String? = null,
    settingsOverlayOpen: Boolean = false,
    subtitles: List<SubtitleTrack> = emptyList()
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val performanceEffects = LocalPerformanceEffects.current
    val textShadowDisabled = performanceEffects.getOrElse(4) { false }
    val shadowsDisabled = performanceEffects.getOrElse(2) { false }
    val maskDisabled = performanceEffects.getOrElse(3) { false }
    val playClickSound = LocalNavigationSound.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val hasError = !errorMessage.isNullOrBlank()
    val menuZones = remember(episodes.isNotEmpty(), seasons.isNotEmpty(), related.isNotEmpty(), recommendations.isNotEmpty()) {
        buildList {
            add(PlaybackMenuZone.GENRES)
            add(PlaybackMenuZone.SETTINGS)
            add(PlaybackMenuZone.TRACKS)
            if (episodes.isNotEmpty()) add(PlaybackMenuZone.EPISODES)
            if (seasons.isNotEmpty()) add(PlaybackMenuZone.SEASONS)
            if (related.isNotEmpty()) add(PlaybackMenuZone.RELATED)
            if (recommendations.isNotEmpty()) add(PlaybackMenuZone.RECS)
        }
    }

    var osdVisible by remember(metadataTitle, episodeLabel) { mutableStateOf(true) }
    var activeZone by remember(metadataTitle, episodeLabel) {
        mutableStateOf(
            if (hasError) PlaybackMenuZone.TRACKS else PlaybackMenuZone.SETTINGS
        )
    }
    if (activeZone !in menuZones) {
        activeZone = PlaybackMenuZone.TRACKS
    }
    LaunchedEffect(hasError) {
        if (hasError) {
            osdVisible = true
            if (activeZone < PlaybackMenuZone.TRACKS) {
                activeZone = PlaybackMenuZone.TRACKS
            }
        }
    }
    LaunchedEffect(osdVisible, hasError) {
        if (!osdVisible && !hasError) {
            activeZone = PlaybackMenuZone.GENRES
        }
    }

    var lastInteractionTick by remember { mutableStateOf(0L) }
    fun bumpInteraction() {
        lastInteractionTick += 1L
    }

    // Pause auto-hide on pause, loading, or error so OSD stays responsive.
    LaunchedEffect(osdVisible, isPlaying, isLoading, hasError, lastInteractionTick) {
        if (osdVisible && isPlaying && !isLoading && !hasError) {
            delay(2_500L)
            osdVisible = false
        }
    }

    // Intercept Back only when OSD is open (on normal playback) and Settings drawer is closed.
    PlatformBackHandler(enabled = osdVisible && !hasError && !settingsOverlayOpen) {
        playClickSound()
        osdVisible = false
    }

    val rootFocusRequester = remember { FocusRequester() }
    val genresFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val tracksFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val seasonsFocusRequester = remember { FocusRequester() }
    val relatedFocusRequester = remember { FocusRequester() }
    val recsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(osdVisible, activeZone, settingsOverlayOpen) {
        if (settingsOverlayOpen) return@LaunchedEffect
        yield()
        val requester = if (!osdVisible) {
            rootFocusRequester
        } else {
            when (activeZone) {
                PlaybackMenuZone.GENRES -> genresFocusRequester
                PlaybackMenuZone.SETTINGS -> settingsFocusRequester
                PlaybackMenuZone.TRACKS -> tracksFocusRequester
                PlaybackMenuZone.EPISODES -> episodesFocusRequester
                PlaybackMenuZone.SEASONS -> seasonsFocusRequester
                PlaybackMenuZone.RELATED -> relatedFocusRequester
                PlaybackMenuZone.RECS -> recsFocusRequester
            }
        }
        runCatching { requester.requestFocus() }
    }

    val currentMenuZones by rememberUpdatedState(menuZones)
    val currentActiveZone by rememberUpdatedState(activeZone)

    fun moveZone(delta: Int, preventCloseOnTop: Boolean = false): Boolean {
        if (!osdVisible) return false
        val zones = currentMenuZones
        val currentIdx = zones.indexOf(activeZone).coerceAtLeast(0)
        val targetIdx = currentIdx + delta
        if (targetIdx < 0) {
            if (!preventCloseOnTop && !hasError) {
                playClickSound()
                osdVisible = false
            }
            return true
        }
        if (targetIdx in zones.indices) {
            playClickSound()
            activeZone = zones[targetIdx]
            bumpInteraction()
            return true
        }
        return false
    }

    val activeSkip = if (hasError) null else skipRanges.firstOrNull { range ->
        (range.kind == SkipRange.Kind.INTRO || range.kind == SkipRange.Kind.OUTRO) &&
            positionMs > 3_000L &&
            positionMs / 1000.0 >= range.startSeconds &&
            positionMs / 1000.0 < range.endSeconds
    }

    LaunchedEffect(activeSkip, osdVisible, autoSkipIntro) {
        val range = activeSkip ?: return@LaunchedEffect
        if (!osdVisible && autoSkipIntro) {
            delay(1_800L)
            onImmediateSeek(((range.endSeconds + 1.0) * 1000.0).toLong())
        }
    }

    // Track accumulated gesture seek position via fresh state refs so pointerInput never sees stale times.
    val currentPosState by rememberUpdatedState(positionMs)
    val currentDurState by rememberUpdatedState(durationMs)
    val currentSeekSeconds by rememberUpdatedState(seekSeconds)
    val currentOsdVisible by rememberUpdatedState(osdVisible)
    val currentSettingsOverlayOpen by rememberUpdatedState(settingsOverlayOpen)
    val currentMoveZone by rememberUpdatedState(::moveZone)
    val currentAdjustBrightness by rememberUpdatedState(onAdjustBrightness)
    val currentAdjustVolume by rememberUpdatedState(onAdjustVolume)
    val currentShowSeekIndicator by rememberUpdatedState(onShowSeekIndicator)
    val currentShowOsdHud by rememberUpdatedState(onShowOsdHud)
    val currentDebouncedSeek by rememberUpdatedState(onDebouncedSeek)
    val currentReadOsdVisible by rememberUpdatedState { osdVisible }
    val currentOpenOsdZone by rememberUpdatedState { zone: PlaybackMenuZone ->
        activeZone = zone
        osdVisible = true
    }
    val currentDismissOsd by rememberUpdatedState {
        if (!hasError) osdVisible = false
    }
    val gestureClock = remember { TimeSource.Monotonic.markNow() }
    var gestureSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(positionMs) {
        if (gestureSeekMs != null && abs(positionMs - (gestureSeekMs ?: positionMs)) < 1_000L) {
            gestureSeekMs = null
        }
    }
    fun performDeltaSeek(direction: Int) {
        val end = currentDurState.coerceAtLeast(0L)
        val base = gestureSeekMs ?: currentPosState
        val deltaMs = direction * currentSeekSeconds.coerceAtLeast(1) * 1_000L
        val target = (base + deltaMs).let { if (end > 0L) it.coerceIn(0L, end) else it.coerceAtLeast(0L) }
        gestureSeekMs = target
        currentDebouncedSeek(target)
        if (!currentOsdVisible) {
            val pct = if (end > 0L) (target.toFloat() / end * 100f).coerceIn(0f, 100f) else 0f
            currentShowOsdHud(
                PlayerOsdHudState(
                    icon = if (direction > 0) IconGlyphs.FastForward else IconGlyphs.FastRewind,
                    text = "${formatPlaybackTime(target, end)} of ${formatPlaybackTime(end, end)}",
                    progressPercent = pct
                )
            )
        }
    }
    val currentPerformDeltaSeek by rememberUpdatedState(::performDeltaSeek)

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapSide by remember { mutableIntStateOf(0) }
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }

    var localRippleSide by remember { mutableStateOf<Int?>(null) }
    var localRippleNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekIndicatorSide) {
        if (seekIndicatorSide != null && seekIndicatorSide != localRippleSide) {
            localRippleSide = seekIndicatorSide
            localRippleNonce += 1
        }
    }
    fun triggerLocalSeekRipple(direction: Int) {
        localRippleSide = direction
        localRippleNonce += 1
    }

    val epPageSize = 50
    val currentEpIdx = remember(episodes, selectedEpisodeId) {
        episodes.indexOfFirst { it.id == selectedEpisodeId }.coerceAtLeast(0)
    }
    var episodePage by remember(episodes.size, selectedEpisodeId) {
        mutableIntStateOf(currentEpIdx / epPageSize)
    }
    var episodePageFocusTarget by remember { mutableStateOf<Int?>(null) }
    var shelfJumpTarget by remember { mutableStateOf<Pair<PlaybackMenuZone, Boolean>?>(null) }

    fun changeEpisodePage(newPage: Int, focusLast: Boolean) {
        episodePageFocusTarget = if (focusLast) Int.MAX_VALUE else 0
        episodePage = newPage
        bumpInteraction()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (currentSettingsOverlayOpen || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                bumpInteraction()
                when (event.key) {
                    Key.Spacebar, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (!hasError) {
                            if (!isPlaying) {
                                onTogglePlay(false)
                            } else {
                                onTogglePlay(false)
                                if (!currentOsdVisible) {
                                    activeZone = PlaybackMenuZone.TRACKS
                                    osdVisible = true
                                }
                            }
                        }
                        true
                    }
                    Key.MediaNext, Key.MediaPrevious, Key.PageUp, Key.PageDown, Key.ChannelUp, Key.ChannelDown -> {
                        val isNextKey = event.key == Key.MediaNext
                        val isPrevKey = event.key == Key.MediaPrevious
                        val isPgUpKey = event.key == Key.PageUp || event.key == Key.ChannelUp
                        val isPgDownKey = event.key == Key.PageDown || event.key == Key.ChannelDown
                        if (!currentOsdVisible || currentActiveZone == PlaybackMenuZone.TRACKS) {
                            if (episodes.isNotEmpty()) {
                                activeZone = PlaybackMenuZone.EPISODES
                                osdVisible = true
                            }
                            if (isNextKey || isPgUpKey) {
                                if (hasNext) onNextEpisode()
                            } else if (isPrevKey || isPgDownKey) {
                                if (hasPrevious) onPreviousEpisode()
                            }
                            true
                        } else if (currentActiveZone == PlaybackMenuZone.EPISODES) {
                            val maxPage = (episodes.size - 1).coerceAtLeast(0) / epPageSize
                            if (isPgUpKey || isPrevKey) {
                                if (episodes.size > epPageSize && episodePage > 0) {
                                    playClickSound()
                                    changeEpisodePage(episodePage - 1, focusLast = true)
                                } else {
                                    playClickSound()
                                    shelfJumpTarget = PlaybackMenuZone.EPISODES to false
                                }
                            } else {
                                if (episodes.size > epPageSize && episodePage < maxPage) {
                                    playClickSound()
                                    changeEpisodePage(episodePage + 1, focusLast = false)
                                } else {
                                    playClickSound()
                                    shelfJumpTarget = PlaybackMenuZone.EPISODES to true
                                }
                            }
                            true
                        } else if (currentActiveZone in setOf(
                                PlaybackMenuZone.GENRES,
                                PlaybackMenuZone.SETTINGS,
                                PlaybackMenuZone.SEASONS,
                                PlaybackMenuZone.RELATED,
                                PlaybackMenuZone.RECS
                            )
                        ) {
                            playClickSound()
                            shelfJumpTarget = currentActiveZone to (isPgDownKey || isNextKey)
                            true
                        } else {
                            false
                        }
                    }
                    Key.Back, Key.Escape -> {
                        if (hasError) {
                            playClickSound()
                            onBack()
                            true
                        } else if (currentOsdVisible) {
                            playClickSound()
                            osdVisible = false
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (!currentOsdVisible && !hasError) {
                            performDeltaSeek(-1)
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (!currentOsdVisible && !hasError) {
                            performDeltaSeek(1)
                            true
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (!currentOsdVisible) {
                            val skip = activeSkip
                            if (skip != null) {
                                playClickSound()
                                onImmediateSeek(((skip.endSeconds + 1.0) * 1000.0).toLong())
                            } else if (!hasError) {
                                playClickSound()
                                if (!isPlaying) {
                                    onTogglePlay(false)
                                } else {
                                    onTogglePlay(false)
                                    activeZone = PlaybackMenuZone.TRACKS
                                    osdVisible = true
                                }
                            }
                            true
                        } else false
                    }
                    Key.DirectionUp -> {
                        if (!currentOsdVisible) {
                            playClickSound()
                            activeZone = PlaybackMenuZone.SETTINGS
                            osdVisible = true
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (!currentOsdVisible) {
                            playClickSound()
                            activeZone = PlaybackMenuZone.TRACKS
                            osdVisible = true
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(hasError) {
                var wheelAccum = 0f
                var wheelExpireMs = 0L
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (currentSettingsOverlayOpen || event.type != PointerEventType.Scroll) continue
                        val rawDelta = event.changes.fold(0f) { acc, change -> acc + change.scrollDelta.y }
                        if (rawDelta == 0f) continue
                        val nowMs = gestureClock.elapsedNow().inWholeMilliseconds
                        if (wheelExpireMs < nowMs) {
                            wheelAccum = 0f
                        }
                        wheelExpireMs = nowMs + 1_000L
                        val scaledDelta = if (abs(rawDelta) in 0.01f..10f) rawDelta * 100f else rawDelta
                        wheelAccum += scaledDelta
                        if (wheelAccum <= -100f) {
                            wheelAccum = 0f
                            if (!currentOsdVisible && !hasError) {
                                playClickSound()
                                activeZone = PlaybackMenuZone.SETTINGS
                                osdVisible = true
                                bumpInteraction()
                            } else if (currentOsdVisible) {
                                currentMoveZone(-1, true)
                            }
                        } else if (wheelAccum >= 100f) {
                            wheelAccum = 0f
                            if (!currentOsdVisible && !hasError) {
                                playClickSound()
                                activeZone = PlaybackMenuZone.TRACKS
                                osdVisible = true
                                bumpInteraction()
                            } else if (currentOsdVisible) {
                                currentMoveZone(1, true)
                            }
                        }
                    }
                }
            }
            .pointerInput(disableTouchGestures, seekSeconds, hasError) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed || currentSettingsOverlayOpen) {
                        return@awaitEachGesture
                    }

                    val downX = down.position.x
                    val downY = down.position.y
                    val downTime = gestureClock.elapsedNow().inWholeMilliseconds
                    val touchSlop = viewConfiguration.touchSlop
                    val lockSlopPx = maxOf(touchSlop * 1.5f, 14.dp.toPx())
                    val fallbackSlopPx = lockSlopPx * 2.5f
                    val seekStepPx = (size.width * 0.06f).coerceIn(36.dp.toPx(), 56.dp.toPx())
                    val firstSeekThreshold = (seekStepPx * 0.6f).coerceAtLeast(lockSlopPx)
                    val verticalRangePx = (size.height * 0.55f).coerceIn(180.dp.toPx(), 320.dp.toPx())
                    val levelStepPx = verticalRangePx / 50f
                    val zoneStepPx = (size.height * 0.14f).coerceIn(44.dp.toPx(), 72.dp.toPx())
                    val firstZoneThreshold = (zoneStepPx * 0.6f).coerceAtLeast(lockSlopPx)

                    var isDrag = false
                    var movedPastSlop = false
                    var childClaimed = false
                    var multiTouch = false
                    var released = false
                    var upConsumed = false
                    // 1=horiz seek, 2=left brightness, 3=right volume, 4=OSD zone swipe, 5=closed vert open
                    var mode = 0
                    var horizontalCarry = 0f
                    var verticalCarry = 0f
                    var nextSeekThreshold = firstSeekThreshold
                    var nextZoneThreshold = firstZoneThreshold
                    var currentPos = down.position

                    while (true) {
                        val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val change = initialEvent.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            awaitPointerEvent(PointerEventPass.Final)
                            break
                        }
                        if (initialEvent.changes.count { it.pressed } > 1) {
                            multiTouch = true
                        }
                        if (!change.pressed) {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            val finalChange = finalEvent.changes.firstOrNull { it.id == down.id }
                            upConsumed = finalChange?.isConsumed == true
                            released = true
                            break
                        }

                        val prevPos = currentPos
                        if (!change.isConsumed && !childClaimed && !multiTouch && !currentSettingsOverlayOpen) {
                            val dragAmount = change.position - currentPos
                            currentPos = change.position
                            var justStartedDrag = false

                            if (!isDrag) {
                                val totalDx = abs(change.position.x - downX)
                                val totalDy = abs(change.position.y - downY)
                                if (totalDx > touchSlop || totalDy > touchSlop) {
                                    movedPastSlop = true
                                }
                                val osdOpen = currentReadOsdVisible()
                                // Match child LazyRow/SeekBar touchSlop when OSD is open so vertical swipes lock before horizontal children claim them.
                                val activeLockSlop = if (osdOpen) touchSlop else lockSlopPx
                                val activeFallbackSlop = if (osdOpen) touchSlop * 2f else fallbackSlopPx
                                val axisMargin = if (osdOpen) 1.2f else 1.35f
                                val horizLock = totalDx >= activeLockSlop && totalDx > totalDy * axisMargin
                                val vertLock = totalDy >= activeLockSlop && totalDy > totalDx * axisMargin
                                val diagDist = hypot(totalDx, totalDy)
                                val diagVertFallback = diagDist >= activeFallbackSlop &&
                                    totalDy > totalDx * if (osdOpen) 1.15f else 1f
                                val diagHorizFallback = !osdOpen && diagDist >= fallbackSlopPx && totalDx > totalDy

                                if (horizLock || vertLock || diagVertFallback || diagHorizFallback) {
                                    val isHorizontal = horizLock || diagHorizFallback
                                    val resolvedMode = when {
                                        isHorizontal && !osdOpen && !disableTouchGestures && !hasError -> 1
                                        !isHorizontal && !osdOpen && !disableTouchGestures && !hasError && downX < size.width * 0.2f -> 2
                                        !isHorizontal && !osdOpen && !disableTouchGestures && !hasError && downX > size.width * 0.8f -> 3
                                        !isHorizontal && osdOpen -> 4
                                        !isHorizontal && !osdOpen && !hasError -> 5
                                        else -> 0
                                    }
                                    if (resolvedMode != 0) {
                                        isDrag = true
                                        justStartedDrag = true
                                        mode = resolvedMode
                                        pendingTapJob?.cancel()
                                        pendingTapJob = null
                                    }
                                }
                            }

                            if (isDrag && mode != 0) {
                                change.consume()
                                when (mode) {
                                    1 -> {
                                        horizontalCarry += if (justStartedDrag) (change.position.x - downX) else dragAmount.x
                                        while (nextSeekThreshold > 0f && abs(horizontalCarry) >= nextSeekThreshold) {
                                            val direction = if (horizontalCarry > 0f) 1 else -1
                                            horizontalCarry -= direction * nextSeekThreshold
                                            nextSeekThreshold = seekStepPx
                                            currentPerformDeltaSeek(direction)
                                        }
                                    }
                                    2, 3 -> {
                                        verticalCarry += if (justStartedDrag) (change.position.y - downY) else dragAmount.y
                                        while (levelStepPx > 0f && abs(verticalCarry) >= levelStepPx) {
                                            val carrySign = if (verticalCarry > 0f) 1 else -1
                                            verticalCarry -= carrySign * levelStepPx
                                            val direction = -carrySign
                                            if (mode == 2) currentAdjustBrightness(direction) else currentAdjustVolume(direction)
                                        }
                                    }
                                    5 -> {
                                        val openingTracks = (change.position.y - downY) < 0f
                                        verticalCarry = 0f
                                        nextZoneThreshold = zoneStepPx
                                        playClickSound()
                                        bumpInteraction()
                                        currentOpenOsdZone(
                                            if (openingTracks) PlaybackMenuZone.TRACKS else PlaybackMenuZone.SETTINGS
                                        )
                                        mode = 4
                                    }
                                    4 -> {
                                        verticalCarry += if (justStartedDrag) (change.position.y - downY) else dragAmount.y
                                        while (nextZoneThreshold > 0f && abs(verticalCarry) >= nextZoneThreshold) {
                                            val carrySign = if (verticalCarry > 0f) 1 else -1
                                            verticalCarry -= carrySign * nextZoneThreshold
                                            nextZoneThreshold = zoneStepPx
                                            val delta = -carrySign
                                            currentMoveZone(delta, false)
                                            if (!currentReadOsdVisible()) {
                                                mode = 0
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            currentPos = change.position
                        }

                        val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                        val finalChange = finalEvent.changes.firstOrNull { it.id == down.id }
                        if (!isDrag &&
                            finalChange != null &&
                            finalChange.isConsumed &&
                            finalChange.previousPressed &&
                            finalChange.position != prevPos
                        ) {
                            childClaimed = true
                        }
                    }

                    if (!isDrag && !movedPastSlop && released && !childClaimed && !multiTouch && !upConsumed && !currentSettingsOverlayOpen) {
                        if (currentReadOsdVisible()) {
                            currentDismissOsd()
                        } else {
                            val tapSide = when {
                                downX < size.width * 0.2f -> 1
                                downX > size.width * 0.8f -> 2
                                else -> 0
                            }
                            if (tapSide == 0 || hasError) {
                                pendingTapJob?.cancel()
                                pendingTapJob = null
                                lastTapTime = 0L
                                lastTapSide = 0
                                bumpInteraction()
                                currentOpenOsdZone(PlaybackMenuZone.TRACKS)
                            } else {
                                val isDouble = (downTime - lastTapTime < 280L) && (lastTapSide == tapSide)
                                if (isDouble) {
                                    pendingTapJob?.cancel()
                                    pendingTapJob = null
                                    lastTapTime = downTime
                                    lastTapSide = tapSide
                                    val direction = if (tapSide == 2) 1 else -1
                                    currentPerformDeltaSeek(direction)
                                    triggerLocalSeekRipple(direction)
                                    currentShowSeekIndicator(direction)
                                } else {
                                    lastTapTime = downTime
                                    lastTapSide = tapSide
                                    pendingTapJob?.cancel()
                                    pendingTapJob = coroutineScope.launch {
                                        delay(300L)
                                        bumpInteraction()
                                        currentOpenOsdZone(PlaybackMenuZone.TRACKS)
                                        lastTapTime = 0L
                                        lastTapSide = 0
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)

        // Video player surface sits at the bottom of the stack.
        videoContent()

        // Banner/poster backdrop stays visible until the first video frame renders (or on error).
        if (!firstFrameRendered || hasError) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                val model = imageCdnUrl(artworkUrl, LocalImageCdn.current)
                if (!model.isNullOrBlank()) {
                    AsyncImage(
                        model = model,
                        contentDescription = metadataTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Prevent native PlayerView from claiming raw pointer events.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                }
        )

        // #pb_loading sits behind #pb_meta & #pb_actions (z-index: 10 vs 30/50).
        AnimatedVisibility(
            visible = isLoading && !hasError,
            enter = if (tokens.tim5s == 0) EnterTransition.None else fadeIn(
                tween(durationMillis = tokens.tim5s, easing = tokens.easing)
            ),
            exit = if (tokens.tim5s == 0) ExitTransition.None else fadeOut(
                tween(durationMillis = tokens.tim5s, easing = tokens.easing)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            PlaybackLoadingScreen()
        }

        // #pb_meta: Zone 1 (active when osdVisible && activeZone <= SETTINGS)
        val isMetaVisible = osdVisible && (activeZone == PlaybackMenuZone.GENRES || activeZone == PlaybackMenuZone.SETTINGS)
        PlaybackMetaLayer(
            visible = isMetaVisible,
            hasError = hasError,
            title = metadataTitle,
            description = metadataDescription,
            genres = metadataGenres,
            metadataFormat = metadataFormat,
            metadataQuality = metadataQuality,
            metadataRating = metadataRating,
            episodesCount = episodes.size,
            seasonsCount = seasons.size,
            currentStreamType = currentStreamType,
            hasSubStream = hasSubStream,
            hasDubStream = hasDubStream,
            subStreamLabel = subStreamLabel,
            streamServerLabel = streamServerLabel,
            playbackSpeed = playbackSpeed,
            playbackQuality = playbackQuality,
            detectedVideoHeight = detectedVideoHeight,
            subtitleLanguage = subtitleLanguage,
            subtitles = subtitles,
            subtitlesCount = subtitlesCount,
            selectedSubtitleLabel = selectedSubtitleLabel,
            audioLanguageLabel = audioLanguageLabel,
            inWatchlist = inWatchlist,
            isTracked = isTracked,
            activeZone = activeZone,
            shelfJumpTarget = shelfJumpTarget,
            onShelfJumpHandled = { shelfJumpTarget = null },
            genresFocusRequester = genresFocusRequester,
            settingsFocusRequester = settingsFocusRequester,
            onMoveZone = { d -> moveZone(d) },
            onBumpInteraction = ::bumpInteraction,
            onToggleWatchlist = onToggleWatchlist,
            onTrackingClick = onTrackingClick,
            onSearchGenre = onSearchGenre,
            onOpenSettings = onOpenSettings,
            onPlaybackSpeedDialogRequest = onPlaybackSpeedDialogRequest,
            onStreamServerDialogRequest = onStreamServerDialogRequest,
            onPlaybackQualityDialogRequest = onPlaybackQualityDialogRequest,
            onStreamTypeDialogRequest = onStreamTypeDialogRequest,
            onSubtitlesDialogRequest = onSubtitlesDialogRequest,
            onAudioLanguageDialogRequest = onAudioLanguageDialogRequest,
            onBack = onBack
        )

        // #pb_actions / #pb_iactions: Zone 2+ (tracks + inline episodes, seasons, related, recommendations)
        PlaybackActionsLayer(
            osdVisible = osdVisible,
            activeZone = activeZone,
            menuZones = menuZones,
            screenHeightPx = screenHeightPx,
            episodeLabel = episodeLabel,
            positionMs = gestureSeekMs ?: positionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            isPlaying = isPlaying,
            isLoading = isLoading,
            isEnded = isEnded,
            statusTextOverride = statusTextOverride,
            errorMessage = errorMessage,
            skipRanges = skipRanges,
            episodes = episodes,
            selectedEpisodeId = selectedEpisodeId,
            episodePage = episodePage,
            episodePageFocusTarget = episodePageFocusTarget,
            onEpisodePageFocusHandled = { episodePageFocusTarget = null },
            onEpisodePageChange = { newPage, focusLast ->
                changeEpisodePage(newPage, focusLast)
            },
            currentAnimeId = currentAnimeId,
            seasons = seasons,
            related = related,
            recommendations = recommendations,
            shelfJumpTarget = shelfJumpTarget,
            onShelfJumpHandled = { shelfJumpTarget = null },
            tracksFocusRequester = tracksFocusRequester,
            episodesFocusRequester = episodesFocusRequester,
            seasonsFocusRequester = seasonsFocusRequester,
            relatedFocusRequester = relatedFocusRequester,
            recsFocusRequester = recsFocusRequester,
            onActivateZone = { zone ->
                activeZone = zone
                bumpInteraction()
            },
            onMoveZone = { d -> moveZone(d) },
            onSeekDelta = ::performDeltaSeek,
            onSeekTarget = { target ->
                gestureSeekMs = target
                onDebouncedSeek(target)
                bumpInteraction()
            },
            onTogglePlay = {
                onTogglePlay(false)
                bumpInteraction()
            },
            onEpisodeSelected = { ep ->
                onEpisodeSelected?.invoke(ep)
                bumpInteraction()
            },
            onAnimeSelected = { anime ->
                onAnimeSelected?.invoke(anime)
                bumpInteraction()
            },
            tokens = tokens,
            colors = colors,
            maskDisabled = maskDisabled,
            textShadowDisabled = textShadowDisabled
        )

        // #pb_touch_actions: visible ONLY in touch mode when OSD is open at Zone 2 (#pb_tracks).
        val showTouchActions = tokens.isTouch && osdVisible && activeZone == PlaybackMenuZone.TRACKS
        val touchActionsAlpha by animateFloatAsState(
            targetValue = if (showTouchActions) 1f else 0f,
            animationSpec = tween(durationMillis = tokens.tim5s, easing = tokens.easing),
            label = "pb-touch-actions-alpha"
        )
        if (showTouchActions || touchActionsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = touchActionsAlpha }
            ) {
                if (showTouchActions) {
                    PlaybackTouchCircleButton(
                        icon = IconGlyphs.Back,
                        buttonSize = tokens.fs25 + tokens.vw * 2f,
                        iconSize = fsSp(tokens.fs25),
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = tokens.vw * 3f, top = tokens.vw * 3f)
                    )
                    val showCenterTouchControls = !(isLoading && durationMs <= 0L) && !hasError
                    if (showCenterTouchControls) {
                        val sideButtonSize = tokens.fs30 + tokens.vw * 3f
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 8f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasPrevious) {
                                PlaybackTouchCircleButton(
                                    icon = IconGlyphs.Previous,
                                    buttonSize = sideButtonSize,
                                    iconSize = fsSp(tokens.fs30),
                                    onClick = {
                                        onPreviousEpisode()
                                        bumpInteraction()
                                    }
                                )
                            } else {
                                Spacer(Modifier.size(sideButtonSize))
                            }
                            PlaybackTouchCircleButton(
                                icon = when {
                                    isEnded -> IconGlyphs.Replay
                                    isPlaying -> IconGlyphs.Pause
                                    else -> IconGlyphs.Play
                                },
                                buttonSize = tokens.fs60 + tokens.vw * 6f,
                                iconSize = fsSp(tokens.fs60),
                                onClick = {
                                    onTogglePlay(true)
                                    if (!isPlaying) osdVisible = false
                                }
                            )
                            if (hasNext) {
                                PlaybackTouchCircleButton(
                                    icon = IconGlyphs.Next,
                                    buttonSize = sideButtonSize,
                                    iconSize = fsSp(tokens.fs30),
                                    onClick = {
                                        onNextEpisode()
                                        bumpInteraction()
                                    }
                                )
                            } else {
                                Spacer(Modifier.size(sideButtonSize))
                            }
                        }
                    }
                }
            }
        }

        // #pb_action_streamtype: top-right badge visible whenever OSD is shown (hidden on error).
        val showStreamType = osdVisible && !hasError
        val streamTypeAlpha by animateFloatAsState(
            targetValue = if (showStreamType) 1f else 0f,
            animationSpec = tween(durationMillis = tokens.tim5s, easing = tokens.easing),
            label = "pb-streamtype-alpha"
        )
        if (showStreamType || streamTypeAlpha > 0.01f) {
            StreamTypeTopBadge(
                currentStreamType = currentStreamType,
                subStreamLabel = subStreamLabel,
                subtitleLanguage = subtitleLanguage,
                selectedSubtitleLabel = selectedSubtitleLabel,
                subtitlesCount = subtitlesCount,
                tokens = tokens,
                colors = colors,
                shadowsDisabled = shadowsDisabled,
                textShadowDisabled = textShadowDisabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = tokens.vw * 1f, end = tokens.vw * 1f)
                    .graphicsLayer { alpha = streamTypeAlpha }
            )
        }

        // #pb_events: Skip Intro / Outro pill, fades over 200ms when OSD opens/closes.
        var lastSkipForFade by remember { mutableStateOf<SkipRange?>(null) }
        if (activeSkip != null) {
            lastSkipForFade = activeSkip
        }
        AnimatedVisibility(
            visible = !osdVisible && activeSkip != null,
            enter = if (tokens.tim2s == 0) EnterTransition.None else fadeIn(
                tween(durationMillis = tokens.tim2s, easing = tokens.easing)
            ),
            exit = if (tokens.tim2s == 0) ExitTransition.None else fadeOut(
                tween(durationMillis = tokens.tim2s, easing = tokens.easing)
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = tokens.vw * 5f, bottom = tokens.vw * 5f)
        ) {
            val skipToRender = activeSkip ?: lastSkipForFade
            if (skipToRender != null) {
                PlaybackSkipEventPill(
                    skipRange = skipToRender,
                    autoSkip = autoSkipIntro,
                    tokens = tokens,
                    colors = colors,
                    shadowsDisabled = shadowsDisabled,
                    textShadowDisabled = textShadowDisabled,
                    onSkip = {
                        onImmediateSeek(((skipToRender.endSeconds + 1.0) * 1000.0).toLong())
                    }
                )
            }
        }

        // Blank double-tap seek edge indicator semicircle (edge-anchored scaleX 0.8 -> 0.1).
        val rippleAlpha = remember { Animatable(0f) }
        val rippleScaleX = remember { Animatable(0.8f) }
        LaunchedEffect(localRippleNonce) {
            val side = localRippleSide
            if (localRippleNonce > 0 && side != null) {
                rippleAlpha.snapTo(1f)
                rippleScaleX.snapTo(0.8f)
                delay(50L)
                coroutineScope.launch {
                    rippleScaleX.animateTo(
                        targetValue = 0.1f,
                        animationSpec = tween(durationMillis = tokens.tim4s, easing = EaseOut)
                    )
                }
                rippleAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = tokens.tim4s, easing = EaseOut)
                )
                localRippleSide = null
            }
        }
        localRippleSide?.let { side ->
            if (rippleAlpha.value > 0.005f) {
                val isRight = side > 0
                val circleSize = maxHeight
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(circleSize)
                            .offset(x = if (isRight) circleSize * 0.5f else -circleSize * 0.5f)
                            .graphicsLayer {
                                scaleX = rippleScaleX.value
                                alpha = rippleAlpha.value
                                transformOrigin = TransformOrigin.Center
                            }
                            .clip(CircleShape)
                            .background(Color(230, 230, 230).copy(alpha = 0.20f))
                    )
                }
            }
        }

        // #pb_osd: Top-center floating HUD for seek, volume, and brightness.
        var lastOsdForFade by remember { mutableStateOf<PlayerOsdHudState?>(null) }
        if (osdHudState != null) {
            lastOsdForFade = osdHudState
        }
        val osdSlidePx = with(density) { (tokens.vw * 2f).roundToPx() }
        AnimatedVisibility(
            visible = osdHudState != null,
            enter = if (tokens.tim3s == 0) EnterTransition.None else {
                fadeIn(tween(tokens.tim3s, easing = tokens.easing)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(tokens.tim3s, easing = tokens.easing)) +
                    slideInVertically(tween(tokens.tim3s, easing = tokens.easing)) { -osdSlidePx }
            },
            exit = if (tokens.tim3s == 0) ExitTransition.None else {
                fadeOut(tween(tokens.tim3s, easing = tokens.easing)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(tokens.tim3s, easing = tokens.easing)) +
                    slideOutVertically(tween(tokens.tim3s, easing = tokens.easing)) { -osdSlidePx }
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = tokens.vw * 3f)
        ) {
            val osd = osdHudState ?: lastOsdForFade
            if (osd != null) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.vw * 1f))
                        .background(Color(0x66191919))
                        .padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.5f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.6f)
                    ) {
                        Text(
                            text = osd.icon,
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = LocalIconFont.current,
                            fontSize = fsSp(tokens.fs14)
                        )
                        Text(
                            text = osd.text,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = fsSp(tokens.fs14)
                        )
                    }
                    Spacer(Modifier.height(tokens.vw * 0.5f))
                    val osdBorderWidth = (tokens.vw * 0.1f).coerceAtLeast(0.5.dp)
                    val osdPad = (tokens.vw * 0.1f).coerceAtLeast(1.dp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.vw * 1f))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .border(osdBorderWidth, Color.White.copy(alpha = 0.5f), RoundedCornerShape(tokens.vw * 1f))
                            .padding(osdBorderWidth + osdPad)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(tokens.vw * 20f)
                                .height(tokens.vw * 1f)
                                .clip(RoundedCornerShape(tokens.vw * 1f))
                        ) {
                            val osdCategory = when (osd.text) {
                                "Brightness" -> "brightness"
                                "Volume" -> "volume"
                                else -> "other"
                            }
                            val animatedWidth by key(osdCategory) {
                                animateFloatAsState(
                                    targetValue = (osd.progressPercent / 100f).coerceIn(0f, 1f),
                                    animationSpec = if (tokens.tim1s == 0) snap() else tween(
                                        durationMillis = tokens.tim1s,
                                        easing = tokens.easing
                                    ),
                                    label = "pb-osd-progress"
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedWidth)
                                    .clip(RoundedCornerShape(tokens.vw * 1f))
                                    .background(colors.accent1)
                            )
                        }
                    }
                    Spacer(Modifier.height(tokens.vw * 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackMetaLayer(
    visible: Boolean,
    hasError: Boolean,
    title: String,
    description: String,
    genres: List<String>,
    metadataFormat: String?,
    metadataQuality: String?,
    metadataRating: String?,
    episodesCount: Int,
    seasonsCount: Int,
    currentStreamType: PlaybackStreamType,
    hasSubStream: Boolean,
    hasDubStream: Boolean,
    subStreamLabel: String,
    streamServerLabel: String?,
    playbackSpeed: Float,
    playbackQuality: Int,
    detectedVideoHeight: Int,
    subtitleLanguage: String,
    subtitles: List<SubtitleTrack>,
    subtitlesCount: Int,
    selectedSubtitleLabel: String?,
    audioLanguageLabel: String,
    inWatchlist: Boolean,
    isTracked: Boolean,
    activeZone: PlaybackMenuZone,
    shelfJumpTarget: Pair<PlaybackMenuZone, Boolean>?,
    onShelfJumpHandled: () -> Unit,
    genresFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onMoveZone: (Int) -> Boolean,
    onBumpInteraction: () -> Unit,
    onToggleWatchlist: (() -> Unit)?,
    onTrackingClick: (() -> Unit)?,
    onSearchGenre: ((String) -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onPlaybackSpeedDialogRequest: () -> Unit,
    onStreamServerDialogRequest: () -> Unit,
    onPlaybackQualityDialogRequest: (() -> Unit)?,
    onStreamTypeDialogRequest: () -> Unit,
    onSubtitlesDialogRequest: (() -> Unit)?,
    onAudioLanguageDialogRequest: () -> Unit,
    onBack: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val performanceEffects = LocalPerformanceEffects.current
    val textShadowDisabled = performanceEffects.getOrElse(4) { false }
    val shadowsDisabled = performanceEffects.getOrElse(2) { false }
    val maskDisabled = performanceEffects.getOrElse(3) { false }

    val metaAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim5s, easing = tokens.easing),
        label = "pb-meta-alpha"
    )
    if (!visible && metaAlpha <= 0.01f) return

    val titleOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing),
        label = "pb-title-slide"
    )
    val descOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = tokens.tim5s, easing = tokens.easing),
        label = "pb-desc-slide"
    )
    val genresOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing),
        label = "pb-genres-slide"
    )
    val settingsOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing),
        label = "pb-settings-slide"
    )

    val metaTextShadow = if (!textShadowDisabled) {
        Shadow(Color.Black, Offset(1f, 1f), 6f)
    } else null

    val genresState = rememberLazyListState()
    val settingsState = rememberLazyListState()
    var genresSelectedIdx by remember(genres) { mutableIntStateOf(0) }
    var settingsSelectedIdx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = metaAlpha }
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.9f),
                    0.65f to Color.Black.copy(alpha = 0.4f),
                    1f to Color.Transparent
                )
            )
            .padding(tokens.vw * 5f)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            // #pb_title: 40vw width, unrestricted lines like CSS.
            Text(
                text = title,
                color = Color.White,
                fontSize = fsSp(tokens.vw * 3f),
                fontWeight = FontWeight.Bold,
                lineHeight = fsSp(tokens.vw * 3.4f),
                style = LocalTextStyle.current.copy(shadow = metaTextShadow),
                modifier = Modifier
                    .width(tokens.vw * 40f)
                    .padding(bottom = tokens.fs15)
                    .graphicsLayer { translationX = (tokens.vw * 5f).toPx() * titleOffset }
            )

            // #pb_genres: Watchlist, Tracking, optional format/type pill, and all genres.
            val genreItems = buildList {
                if (onToggleWatchlist != null) {
                    add(
                        PlaybackChipSpec(
                            id = "fav",
                            icon = if (inWatchlist) IconGlyphs.Clear else IconGlyphs.Bookmark,
                            label = "Watchlist",
                            onClick = onToggleWatchlist
                        )
                    )
                }
                if (onTrackingClick != null) {
                    add(
                        PlaybackChipSpec(
                            id = "tracking",
                            icon = IconGlyphs.DataExploration,
                            label = "Tracking",
                            onClick = onTrackingClick
                        )
                    )
                }
                metadataFormat?.trim()?.takeIf { it.isNotEmpty() }?.let { formatLabel ->
                    add(
                        PlaybackChipSpec(
                            id = "format_$formatLabel",
                            icon = null,
                            label = formatLabel,
                            onClick = { onSearchGenre?.invoke(formatLabel) }
                        )
                    )
                }
                genres.forEachIndexed { idx, genre ->
                    add(
                        PlaybackChipSpec(
                            id = "genre_${idx}_$genre",
                            icon = null,
                            label = genre,
                            onClick = { onSearchGenre?.invoke(genre) }
                        )
                    )
                }
            }
            if (genreItems.isNotEmpty()) {
                val genresRowActive = visible && activeZone == PlaybackMenuZone.GENRES
                val safeGenresSel = genresSelectedIdx.coerceIn(0, genreItems.lastIndex)
                val genresFadeLeft by remember(genresState) {
                    derivedStateOf {
                        genresState.firstVisibleItemIndex > 0 || genresState.firstVisibleItemScrollOffset > 0
                    }
                }
                LaunchedEffect(shelfJumpTarget, genreItems.size, genresRowActive) {
                    if (genresRowActive && shelfJumpTarget?.first == PlaybackMenuZone.GENRES) {
                        val target = if (shelfJumpTarget.second) genreItems.lastIndex else 0
                        genresSelectedIdx = target
                        genresState.scrollToItem(target)
                        yield()
                        runCatching { genresFocusRequester.requestFocus() }
                        onShelfJumpHandled()
                    }
                }
                LazyRow(
                    state = genresState,
                    userScrollEnabled = visible,
                    horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.5f),
                    modifier = Modifier
                        .width(tokens.vw * 50f)
                        .padding(bottom = tokens.vw * 1f)
                        .graphicsLayer { translationX = (tokens.vw * 20f).toPx() * genresOffset }
                        .then(
                            if (!maskDisabled) {
                                Modifier.horizontalEdgeFadeMask(fadeLeft = genresFadeLeft, fadeRight = true)
                            } else Modifier
                        )
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionUp -> onMoveZone(-1)
                                Key.DirectionDown -> onMoveZone(1)
                                else -> false
                            }
                        }
                ) {
                    itemsIndexed(genreItems, key = { _, item -> item.id }) { idx, item ->
                        PbMenuChip(
                            icon = item.icon,
                            label = item.label,
                            rowActive = genresRowActive,
                            focusEnabled = visible,
                            shadowsDisabled = shadowsDisabled,
                            textShadowDisabled = textShadowDisabled,
                            tokens = tokens,
                            colors = colors,
                            modifier = if (idx == safeGenresSel) Modifier.focusRequester(genresFocusRequester) else Modifier,
                            onFocused = {
                                genresSelectedIdx = idx
                                onBumpInteraction()
                            },
                            onClick = {
                                genresSelectedIdx = idx
                                onBumpInteraction()
                                item.onClick()
                            }
                        )
                    }
                }
            }

            // #pb_desc: 50vw width with type/quality/rating/seasons/episodes pills and synopsis.
            val infoPills = buildList {
                metadataFormat?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
                metadataQuality?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
                metadataRating?.trim()?.takeIf { isAdultAgeRating(it) }?.let {
                    add("R+")
                }
                if (seasonsCount > 0) add("$seasonsCount SEASONS")
                if (episodesCount > 1) add("$episodesCount EPISODES")
            }
            Column(
                modifier = Modifier
                    .width(tokens.vw * 50f)
                    .padding(bottom = tokens.vw * 1.5f)
                    .heightIn(max = (tokens.screenHeight - tokens.vw * 26f).coerceAtLeast(tokens.vw * 10f))
                    .graphicsLayer { translationX = (tokens.vw * 15f).toPx() * descOffset }
            ) {
                if (infoPills.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.2f),
                        verticalArrangement = Arrangement.spacedBy(tokens.vw * 0.2f),
                        modifier = Modifier.padding(bottom = tokens.vw * 0.5f)
                    ) {
                        infoPills.forEach { pill ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(tokens.vw * 0.25f))
                                    .background(Color(0xD90A0A0A))
                                    .border(
                                        width = (tokens.vw * 0.1f).coerceAtLeast(1.dp),
                                        color = Color(0xFF060606),
                                        shape = RoundedCornerShape(tokens.vw * 0.25f)
                                    )
                                    .padding(
                                        start = tokens.vw * 0.4f,
                                        end = tokens.vw * 0.4f,
                                        top = tokens.vw * 0.4f,
                                        bottom = tokens.vw * 0.1f
                                    )
                            ) {
                                Text(
                                    text = pill,
                                    color = Color(0xFFCCCCCC),
                                    fontSize = fsSp(tokens.fs8),
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = fsSp(tokens.fs8)
                                )
                            }
                        }
                    }
                }
                if (description.isNotBlank()) {
                    val maxDescLines = if (tokens.fs13 > tokens.u * 1.35f) 8 else 10
                    Text(
                        text = description,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        lineHeight = fsSp(tokens.fs13) * 1.5f,
                        maxLines = maxDescLines,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(shadow = metaTextShadow)
                    )
                }
            }
        }

        // #pb_settings: anchored at bottom of #pb_meta (bottom: 5vw, width: 80vw).
        val qualityNames = listOf("Auto", "High", "Medium", "Low")
        val qualityIcon = when {
            detectedVideoHeight == 0 || detectedVideoHeight == 720 -> IconGlyphs.Hd
            detectedVideoHeight == 1080 -> IconGlyphs.FullHd
            detectedVideoHeight > 1080 -> IconGlyphs.FourK
            else -> IconGlyphs.Sd
        }
        val qualityLabel = buildString {
            append(qualityNames[playbackQuality.coerceIn(0, qualityNames.lastIndex)])
            if (detectedVideoHeight > 0) append(" ${detectedVideoHeight}p")
        }
        val subVariants = resolveSubVariants(subtitles, subtitleLanguage)
        val langNameForVariant = targetSubtitleLanguageName(subtitleLanguage)
        val subTrackChipLabel = selectedSubtitleLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { formatVariantName(it, langNameForVariant) }
            ?: "Subtitles"
        val settingsChips = buildList {
            if (onOpenSettings != null) {
                add(PlaybackChipSpec("settings", IconGlyphs.Settings, "Settings") { onOpenSettings() })
            } else if (hasError) {
                add(PlaybackChipSpec("close", IconGlyphs.Back, "Close Playback") { onBack() })
            }
            add(
                PlaybackChipSpec(
                    "speed",
                    IconGlyphs.Speed,
                    "Speed ${playbackSpeedStatusLabel(playbackSpeed)}"
                ) { onPlaybackSpeedDialogRequest() }
            )
            add(
                PlaybackChipSpec(
                    "mirror",
                    IconGlyphs.Cloud,
                    streamServerLabel ?: "Main"
                ) { onStreamServerDialogRequest() }
            )
            if (onPlaybackQualityDialogRequest != null) {
                add(PlaybackChipSpec("quality", qualityIcon, qualityLabel) { onPlaybackQualityDialogRequest() })
            }
            if (hasSubStream || hasDubStream) {
                val isDub = currentStreamType == PlaybackStreamType.DUB
                add(
                    PlaybackChipSpec(
                        "streamtype",
                        if (isDub) IconGlyphs.Mic else IconGlyphs.ClosedCaption,
                        streamTypeDisplayLabel(currentStreamType, subStreamLabel)
                    ) { onStreamTypeDialogRequest() }
                )
            }
            if ((subVariants.size > 1 && subtitlesCount > 1) && onSubtitlesDialogRequest != null) {
                add(
                    PlaybackChipSpec(
                        "subtrack",
                        IconGlyphs.ClosedCaption,
                        subTrackChipLabel
                    ) { onSubtitlesDialogRequest() }
                )
            }
            add(
                PlaybackChipSpec(
                    "alang",
                    IconGlyphs.TextToSpeech,
                    audioLanguageLabel
                ) { onAudioLanguageDialogRequest() }
            )
        }
        val settingsRowActive = visible && activeZone == PlaybackMenuZone.SETTINGS
        val safeSettingsSel = settingsSelectedIdx.coerceIn(0, settingsChips.lastIndex.coerceAtLeast(0))
        val settingsFadeLeft by remember(settingsState) {
            derivedStateOf {
                settingsState.firstVisibleItemIndex > 0 || settingsState.firstVisibleItemScrollOffset > 0
            }
        }
        LaunchedEffect(shelfJumpTarget, settingsChips.size, settingsRowActive) {
            if (settingsRowActive && shelfJumpTarget?.first == PlaybackMenuZone.SETTINGS && settingsChips.isNotEmpty()) {
                val target = if (shelfJumpTarget.second) settingsChips.lastIndex else 0
                settingsSelectedIdx = target
                settingsState.scrollToItem(target)
                yield()
                runCatching { settingsFocusRequester.requestFocus() }
                onShelfJumpHandled()
            }
        }
        LazyRow(
            state = settingsState,
            userScrollEnabled = visible,
            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.5f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(tokens.vw * 80f)
                .padding(bottom = tokens.vw * 1f)
                .graphicsLayer { translationX = (tokens.vw * 25f).toPx() * settingsOffset }
                .then(
                    if (!maskDisabled) {
                        Modifier.horizontalEdgeFadeMask(fadeLeft = settingsFadeLeft, fadeRight = true)
                    } else Modifier
                )
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionUp -> onMoveZone(-1)
                        Key.DirectionDown -> onMoveZone(1)
                        else -> false
                    }
                }
        ) {
            itemsIndexed(settingsChips, key = { _, item -> item.id }) { idx, item ->
                PbMenuChip(
                    icon = item.icon,
                    label = item.label,
                    rowActive = settingsRowActive,
                    focusEnabled = visible,
                    shadowsDisabled = shadowsDisabled,
                    textShadowDisabled = textShadowDisabled,
                    tokens = tokens,
                    colors = colors,
                    modifier = if (idx == safeSettingsSel) Modifier.focusRequester(settingsFocusRequester) else Modifier,
                    onFocused = {
                        settingsSelectedIdx = idx
                        onBumpInteraction()
                    },
                    onClick = {
                        settingsSelectedIdx = idx
                        onBumpInteraction()
                        item.onClick()
                    }
                )
            }
        }
    }
}

private data class PlaybackChipSpec(
    val id: String,
    val icon: String?,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun PbMenuChip(
    icon: String?,
    label: String,
    rowActive: Boolean,
    focusEnabled: Boolean,
    shadowsDisabled: Boolean,
    textShadowDisabled: Boolean,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val isFocusedActive = rowActive && focused
    val bgColor = when {
        pressed || isFocusedActive -> colors.primary1
        hovered -> colors.primary3
        rowActive -> Color(0x70FAFDFF)
        else -> Color(0x59A0A5A7)
    }

    Row(
        modifier = modifier
            .then(
                if (!shadowsDisabled && (pressed || isFocusedActive)) {
                    Modifier.shadow(tokens.vw * 0.4f, RoundedCornerShape(tokens.u * 0.2f), clip = false)
                } else Modifier
            )
            .clip(RoundedCornerShape(tokens.u * 0.2f))
            .background(bgColor)
            .hoverable(interactionSource)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .navigationClickable(enabled = focusEnabled, onClick = onClick)
            .navigationFocusable(enabled = focusEnabled)
            .padding(horizontal = tokens.u * 0.8f, vertical = tokens.u * 0.2f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f)
    ) {
        if (!icon.isNullOrBlank()) {
            Text(
                text = icon,
                color = Color.White,
                fontFamily = LocalIconFont.current,
                fontSize = fsSp(tokens.fs10),
                style = LocalTextStyle.current.copy(
                    shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
                )
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = fsSp(tokens.fs10),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(
                shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
            )
        )
    }
}

@Composable
private fun PlaybackActionsLayer(
    osdVisible: Boolean,
    activeZone: PlaybackMenuZone,
    menuZones: List<PlaybackMenuZone>,
    screenHeightPx: Int,
    episodeLabel: String,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    statusTextOverride: String?,
    errorMessage: String?,
    skipRanges: List<SkipRange>,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    episodePage: Int,
    episodePageFocusTarget: Int?,
    onEpisodePageFocusHandled: () -> Unit,
    onEpisodePageChange: (Int, Boolean) -> Unit,
    currentAnimeId: String,
    seasons: List<Anime>,
    related: List<Anime>,
    recommendations: List<Anime>,
    shelfJumpTarget: Pair<PlaybackMenuZone, Boolean>?,
    onShelfJumpHandled: () -> Unit,
    tracksFocusRequester: FocusRequester,
    episodesFocusRequester: FocusRequester,
    seasonsFocusRequester: FocusRequester,
    relatedFocusRequester: FocusRequester,
    recsFocusRequester: FocusRequester,
    onActivateZone: (PlaybackMenuZone) -> Unit,
    onMoveZone: (Int) -> Boolean,
    onSeekDelta: (Int) -> Unit,
    onSeekTarget: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    maskDisabled: Boolean,
    textShadowDisabled: Boolean
) {
    val density = LocalDensity.current
    val hasError = !errorMessage.isNullOrBlank()
    val isActionsZoneVisible = osdVisible

    var tracksHeightPx by remember { mutableIntStateOf(0) }
    var episodesHeightPx by remember { mutableIntStateOf(0) }
    var seasonsHeightPx by remember { mutableIntStateOf(0) }
    var relatedHeightPx by remember { mutableIntStateOf(0) }
    var recsHeightPx by remember { mutableIntStateOf(0) }
    var totalIactionsHeightPx by remember { mutableIntStateOf(0) }

    val peekHeightPx = with(density) { (tokens.vw * 4f).roundToPx() }
    val listsPeekPx = screenHeightPx / 13

    val targetOffsetY = remember(
        osdVisible,
        activeZone,
        menuZones,
        screenHeightPx,
        tracksHeightPx,
        episodesHeightPx,
        seasonsHeightPx,
        relatedHeightPx,
        recsHeightPx,
        totalIactionsHeightPx
    ) {
        if (!osdVisible || activeZone == PlaybackMenuZone.GENRES || activeZone == PlaybackMenuZone.SETTINGS) {
            screenHeightPx - peekHeightPx
        } else {
            val activeIdx = menuZones.indexOf(activeZone)
            val tracksIdx = menuZones.indexOf(PlaybackMenuZone.TRACKS)
            val isLastZone = activeIdx == menuZones.lastIndex
            val hasLists = menuZones.size > tracksIdx + 1
            val visibleHeight = when {
                isLastZone && activeZone != PlaybackMenuZone.TRACKS -> totalIactionsHeightPx
                else -> {
                    var sum = 0
                    for (i in tracksIdx..activeIdx) {
                        sum += when (menuZones[i]) {
                            PlaybackMenuZone.TRACKS -> tracksHeightPx
                            PlaybackMenuZone.EPISODES -> episodesHeightPx
                            PlaybackMenuZone.SEASONS -> seasonsHeightPx
                            PlaybackMenuZone.RELATED -> relatedHeightPx
                            PlaybackMenuZone.RECS -> recsHeightPx
                            else -> 0
                        }
                    }
                    sum + if (!isLastZone && hasLists) listsPeekPx else 0
                }
            }
            screenHeightPx - visibleHeight
        }
    }

    val animatedOffsetY by animateIntAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing),
        label = "pb-iactions-y"
    )
    val actionsAlpha by animateFloatAsState(
        targetValue = if (isActionsZoneVisible) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim5s, easing = tokens.easing),
        label = "pb-actions-alpha"
    )

    if (!isActionsZoneVisible && actionsAlpha <= 0.01f) return

    val listFocusEnabled = osdVisible && activeZone >= PlaybackMenuZone.TRACKS

    val currentActiveZone by rememberUpdatedState(activeZone)
    val currentOnActivateZone by rememberUpdatedState(onActivateZone)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = actionsAlpha }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .offset { IntOffset(0, animatedOffsetY) }
                .onSizeChanged { totalIactionsHeightPx = it.height }
                .pointerInput(osdVisible) {
                    if (!osdVisible) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1) {
                                cancelled = true
                                break
                            }
                            if (!change.pressed) {
                                if (!cancelled && !change.isConsumed) {
                                    change.consume()
                                    currentOnActivateZone(currentActiveZone)
                                }
                                break
                            }
                            if (change.isConsumed ||
                                abs(change.position.x - down.position.x) > slop ||
                                abs(change.position.y - down.position.y) > slop
                            ) {
                                cancelled = true
                                break
                            }
                        }
                    }
                }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        ) {
            // #pb_tracks
            val tracksActive = osdVisible && activeZone == PlaybackMenuZone.TRACKS
            PlaybackTracksSection(
                osdVisible = osdVisible,
                active = tracksActive,
                focusEnabled = listFocusEnabled,
                episodeLabel = episodeLabel,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                isPlaying = isPlaying,
                isLoading = isLoading,
                isEnded = isEnded,
                statusTextOverride = statusTextOverride,
                hasError = hasError,
                skipRanges = skipRanges,
                tracksFocusRequester = tracksFocusRequester,
                onActivate = { onActivateZone(PlaybackMenuZone.TRACKS) },
                onMoveZone = onMoveZone,
                onSeekDelta = onSeekDelta,
                onSeekTarget = onSeekTarget,
                onTogglePlay = onTogglePlay,
                tokens = tokens,
                colors = colors,
                textShadowDisabled = textShadowDisabled,
                modifier = Modifier.onSizeChanged { tracksHeightPx = it.height }
            )

            // #pb_lists
            Column(modifier = Modifier.fillMaxWidth()) {
                if (episodes.isNotEmpty()) {
                    PlaybackEpisodesSection(
                        osdVisible = osdVisible,
                        active = osdVisible && activeZone == PlaybackMenuZone.EPISODES,
                        focusEnabled = listFocusEnabled,
                        episodes = episodes,
                        selectedEpisodeId = selectedEpisodeId,
                        episodePage = episodePage,
                        episodePageFocusTarget = episodePageFocusTarget,
                        onEpisodePageFocusHandled = onEpisodePageFocusHandled,
                        onEpisodePageChange = onEpisodePageChange,
                        shelfJumpTarget = shelfJumpTarget,
                        onShelfJumpHandled = onShelfJumpHandled,
                        focusRequester = episodesFocusRequester,
                        onActivate = { onActivateZone(PlaybackMenuZone.EPISODES) },
                        onMoveZone = onMoveZone,
                        onEpisodeSelected = onEpisodeSelected,
                        tokens = tokens,
                        colors = colors,
                        maskDisabled = maskDisabled,
                        textShadowDisabled = textShadowDisabled,
                        modifier = Modifier.onSizeChanged { episodesHeightPx = it.height }
                    )
                }

                if (seasons.isNotEmpty()) {
                    PlaybackPosterShelf(
                        title = "Seasons",
                        zone = PlaybackMenuZone.SEASONS,
                        osdVisible = osdVisible,
                        active = osdVisible && activeZone == PlaybackMenuZone.SEASONS,
                        focusEnabled = listFocusEnabled,
                        animes = seasons,
                        playingAnimeId = currentAnimeId,
                        shelfJumpTarget = shelfJumpTarget,
                        onShelfJumpHandled = onShelfJumpHandled,
                        focusRequester = seasonsFocusRequester,
                        onActivate = { onActivateZone(PlaybackMenuZone.SEASONS) },
                        onMoveZone = onMoveZone,
                        onAnimeSelected = onAnimeSelected,
                        tokens = tokens,
                        maskDisabled = maskDisabled,
                        textShadowDisabled = textShadowDisabled,
                        modifier = Modifier.onSizeChanged { seasonsHeightPx = it.height }
                    )
                }

                if (related.isNotEmpty()) {
                    PlaybackPosterShelf(
                        title = "Related",
                        zone = PlaybackMenuZone.RELATED,
                        osdVisible = osdVisible,
                        active = osdVisible && activeZone == PlaybackMenuZone.RELATED,
                        focusEnabled = listFocusEnabled,
                        animes = related,
                        shelfJumpTarget = shelfJumpTarget,
                        onShelfJumpHandled = onShelfJumpHandled,
                        focusRequester = relatedFocusRequester,
                        onActivate = { onActivateZone(PlaybackMenuZone.RELATED) },
                        onMoveZone = onMoveZone,
                        onAnimeSelected = onAnimeSelected,
                        tokens = tokens,
                        maskDisabled = maskDisabled,
                        textShadowDisabled = textShadowDisabled,
                        modifier = Modifier.onSizeChanged { relatedHeightPx = it.height }
                    )
                }

                if (recommendations.isNotEmpty()) {
                    PlaybackPosterShelf(
                        title = "Recommendation",
                        zone = PlaybackMenuZone.RECS,
                        osdVisible = osdVisible,
                        active = osdVisible && activeZone == PlaybackMenuZone.RECS,
                        focusEnabled = listFocusEnabled,
                        animes = recommendations,
                        shelfJumpTarget = shelfJumpTarget,
                        onShelfJumpHandled = onShelfJumpHandled,
                        focusRequester = recsFocusRequester,
                        onActivate = { onActivateZone(PlaybackMenuZone.RECS) },
                        onMoveZone = onMoveZone,
                        onAnimeSelected = onAnimeSelected,
                        tokens = tokens,
                        maskDisabled = maskDisabled,
                        textShadowDisabled = textShadowDisabled,
                        modifier = Modifier.onSizeChanged { recsHeightPx = it.height }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackTracksSection(
    osdVisible: Boolean,
    active: Boolean,
    focusEnabled: Boolean,
    episodeLabel: String,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    isEnded: Boolean,
    statusTextOverride: String?,
    hasError: Boolean,
    skipRanges: List<SkipRange>,
    tracksFocusRequester: FocusRequester,
    onActivate: () -> Unit,
    onMoveZone: (Int) -> Boolean,
    onSeekDelta: (Int) -> Unit,
    onSeekTarget: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    textShadowDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val trackTextShadow = if (!textShadowDisabled) {
        Shadow(Color.Black, Offset(1f, 1f), 4f)
    } else null

    val infiniteSpin = rememberInfiniteTransition(label = "pb-track-spin")
    val spinAngle by infiniteSpin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pb-track-spin-deg"
    )

    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnSeekTarget by rememberUpdatedState(onSeekTarget)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val displayPosText = when {
        hasError -> "PLAYBACK ERROR"
        !statusTextOverride.isNullOrBlank() -> statusTextOverride
        durationMs <= 0L -> if (isLoading) "LOADING SERVER" else "-"
        else -> formatPlaybackTime(positionMs, durationMs)
    }
    val displayDurText = if (durationMs > 0L) formatPlaybackTime(durationMs, durationMs) else ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (active) 1f else 0.8f }
            .padding(horizontal = tokens.vw * 5f)
            .padding(bottom = tokens.vw * 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .pointerInput(osdVisible, hasError) {
                    if (!osdVisible || hasError) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        val downX = down.position.x
                        val downY = down.position.y
                        val slop = viewConfiguration.touchSlop
                        var horizontalDrag = false
                        var cancelled = false

                        fun seekAt(x: Float) {
                            val dur = currentDurationMs
                            if (dur > 0L && size.width > 0) {
                                val pct = (x / size.width).coerceIn(0f, 1f)
                                currentOnSeekTarget((pct * dur).toLong())
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) {
                                cancelled = true
                                break
                            }
                            if (!change.pressed) {
                                if (!horizontalDrag && !cancelled) {
                                    change.consume()
                                    currentOnActivate()
                                    seekAt(change.position.x)
                                }
                                break
                            }
                            val dx = abs(change.position.x - downX)
                            val dy = abs(change.position.y - downY)
                            if (!horizontalDrag) {
                                if (dx > slop && dx >= dy) {
                                    horizontalDrag = true
                                    change.consume()
                                    currentOnActivate()
                                    seekAt(change.position.x)
                                } else if (dy > slop && dy > dx * 1.35f) {
                                    cancelled = true
                                    break
                                }
                            } else {
                                change.consume()
                                seekAt(change.position.x)
                            }
                        }
                    }
                }
        ) {
            // Header row (#pb_track_left, #pb_track_title, #pb_track_dur) pinned at top (2vw).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.vw * 2f)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = osdVisible && !hasError
                        ) {
                            onActivate()
                            onTogglePlay()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ctlIcon = when {
                        hasError -> IconGlyphs.Warning
                        isLoading && durationMs <= 0L -> IconGlyphs.ProgressActivity
                        isEnded -> IconGlyphs.Replay
                        isPlaying -> IconGlyphs.Pause
                        else -> IconGlyphs.Play
                    }
                    val isSpinning = !hasError && isLoading && durationMs <= 0L
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .size(tokens.vw * 2f)
                            .graphicsLayer { if (isSpinning) rotationZ = spinAngle },
                        contentAlignment = Alignment.Center
                    ) {
                        if (active && !textShadowDisabled) {
                            Text(
                                text = ctlIcon,
                                color = colors.primary2,
                                fontFamily = LocalIconFont.current,
                                fontSize = fsSp(tokens.vw * 2f),
                                lineHeight = fsSp(tokens.vw * 2f),
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(
                                    shadow = Shadow(
                                        Color.White,
                                        Offset.Zero,
                                        with(density) { (tokens.vw * 2f).toPx() }.coerceAtLeast(1f)
                                    )
                                )
                            )
                            Text(
                                text = ctlIcon,
                                color = colors.primary1,
                                fontFamily = LocalIconFont.current,
                                fontSize = fsSp(tokens.vw * 2f),
                                lineHeight = fsSp(tokens.vw * 2f),
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(
                                    shadow = Shadow(
                                        colors.primary2,
                                        Offset.Zero,
                                        with(density) { (tokens.vw * 1f).toPx() }.coerceAtLeast(1f)
                                    )
                                )
                            )
                        }
                        Text(
                            text = ctlIcon,
                            color = Color.White,
                            fontFamily = LocalIconFont.current,
                            fontSize = fsSp(tokens.vw * 2f),
                            lineHeight = fsSp(tokens.vw * 2f),
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                shadow = when {
                                    active && !textShadowDisabled -> Shadow(
                                        colors.primary1,
                                        Offset.Zero,
                                        with(density) { (tokens.vw * 0.4f).toPx() }.coerceAtLeast(1f)
                                    )
                                    !textShadowDisabled -> Shadow(Color.Black, Offset.Zero, 4f)
                                    else -> null
                                }
                            )
                        )
                    }
                    Text(
                        text = displayPosText,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        fontWeight = FontWeight.Bold,
                        lineHeight = fsSp(tokens.vw * 2f),
                        style = LocalTextStyle.current.copy(shadow = trackTextShadow),
                        modifier = Modifier.padding(start = tokens.vw * 0.5f)
                    )
                }

                Text(
                    text = episodeLabel.uppercase(),
                    color = Color.White,
                    fontSize = fsSp(tokens.fs10),
                    fontWeight = FontWeight.Bold,
                    lineHeight = fsSp(tokens.vw * 2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(shadow = trackTextShadow),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = tokens.vw * 15f)
                )

                if (displayDurText.isNotEmpty()) {
                    Text(
                        text = displayDurText,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        fontWeight = FontWeight.Bold,
                        lineHeight = fsSp(tokens.vw * 2f),
                        style = LocalTextStyle.current.copy(shadow = trackTextShadow),
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            // #pb_track seek bar at padding(top = 2.5vw), sitting cleanly inside 4vw collapsed peek.
            val outlineWidth = (tokens.vw * if (active) 0.1f else 0.05f).coerceAtLeast(0.5.dp)
            val outlineShape = RoundedCornerShape(tokens.vw * 0.6f)
            val barShape = RoundedCornerShape(tokens.vw * 0.5f)
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = (tokens.vw * 2.5f - outlineWidth).coerceAtLeast(0.dp))
                    .focusRequester(tracksFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) onActivate()
                    }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionUp -> onMoveZone(-1)
                            Key.DirectionDown -> onMoveZone(1)
                            Key.DirectionLeft -> {
                                if (!hasError) onSeekDelta(-1)
                                true
                            }
                            Key.DirectionRight -> {
                                if (!hasError) onSeekDelta(1)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (!hasError) onTogglePlay()
                                true
                            }
                            else -> false
                        }
                    }
                    .navigationFocusable(enabled = focusEnabled)
                    .then(
                        if (active) Modifier.shadow(tokens.vw * 0.56f, outlineShape, clip = false)
                        else Modifier.shadow(tokens.vw * 0.03f, outlineShape, clip = false)
                    )
                    .border(
                        width = outlineWidth,
                        color = Color.White.copy(alpha = if (active) 0.8f else 0.4f),
                        shape = outlineShape
                    )
                    .padding(outlineWidth)
            ) {
                val trackWidth = maxWidth
                val safeDuration = durationMs.coerceAtLeast(1L)
                val progressPct = if (durationMs > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
                val bufferPct = if (durationMs > 0L) (bufferedPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.vw * 0.6f)
                        .clip(barShape)
                        .background(Color.White.copy(alpha = if (active) 0.9f else 0.5f))
                ) {
                    // z = 1: buffer bar
                    if (bufferPct > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bufferPct)
                                .clip(barShape)
                                .background(colors.accent1.copy(alpha = 0.5f))
                        )
                    }
                    // z = 2: progress fill
                    if (progressPct > 0f) {
                        val fillBrush = if (active) {
                            Brush.verticalGradient(listOf(colors.primary3, colors.primary1))
                        } else {
                            Brush.verticalGradient(listOf(colors.dimm1, colors.dimm1))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPct)
                                .clip(barShape)
                                .background(fillBrush)
                        )
                    }
                    // z = 3: skip markers rendered ON TOP of progress fill
                    if (durationMs > 0L) {
                        skipRanges.filter { it.kind == SkipRange.Kind.INTRO || it.kind == SkipRange.Kind.OUTRO }
                            .take(2)
                            .forEach { range ->
                                val startPct = (range.startSeconds * 1000.0 / safeDuration).toFloat().coerceIn(0f, 1f)
                                val endPct = (range.endSeconds * 1000.0 / safeDuration).toFloat().coerceIn(0f, 1f)
                                val widthPct = (endPct - startPct).coerceAtLeast(0f)
                                if (widthPct > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = trackWidth * startPct)
                                            .width(trackWidth * widthPct)
                                            .fillMaxHeight()
                                            .background(colors.primaryBg.copy(alpha = 0.6f))
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackEpisodesSection(
    osdVisible: Boolean,
    active: Boolean,
    focusEnabled: Boolean,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    episodePage: Int,
    episodePageFocusTarget: Int?,
    onEpisodePageFocusHandled: () -> Unit,
    onEpisodePageChange: (Int, Boolean) -> Unit,
    shelfJumpTarget: Pair<PlaybackMenuZone, Boolean>?,
    onShelfJumpHandled: () -> Unit,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    onMoveZone: (Int) -> Boolean,
    onEpisodeSelected: (Episode) -> Unit,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    maskDisabled: Boolean,
    textShadowDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val pageSize = 50
    val totalPages = ((episodes.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = episodePage.coerceIn(0, totalPages - 1)
    val pageStart = safePage * pageSize
    val pageEnd = minOf(pageStart + pageSize, episodes.size)
    val pageEpisodes = episodes.subList(pageStart, pageEnd)
    val hasPaging = episodes.size > pageSize
    val firstLabel = pageEpisodes.firstOrNull()?.number.orEmpty()
    val lastLabel = pageEpisodes.lastOrNull()?.number.orEmpty()
    val headerTitle = if (hasPaging && firstLabel != lastLabel) {
        "Episodes: $firstLabel - $lastLabel / ${episodes.size}"
    } else "Episodes"

    val listState = rememberLazyListState()
    val selectedInPageIdx = pageEpisodes.indexOfFirst { it.id == selectedEpisodeId }
    val initialFocusTileIdx = (episodePageFocusTarget ?: if (selectedInPageIdx >= 0) selectedInPageIdx else 0)
        .coerceIn(0, pageEpisodes.lastIndex.coerceAtLeast(0))

    LaunchedEffect(safePage, selectedEpisodeId, episodePageFocusTarget, active, focusEnabled) {
        val target = initialFocusTileIdx
        if (target in pageEpisodes.indices) {
            listState.scrollToItem(target)
        }
        if (episodePageFocusTarget != null && active && focusEnabled && pageEpisodes.isNotEmpty()) {
            yield()
            runCatching { focusRequester.requestFocus() }
            onEpisodePageFocusHandled()
        }
    }

    LaunchedEffect(shelfJumpTarget, pageEpisodes.size, active, focusEnabled) {
        if (active && focusEnabled && shelfJumpTarget?.first == PlaybackMenuZone.EPISODES && pageEpisodes.isNotEmpty()) {
            val target = if (shelfJumpTarget.second) pageEpisodes.lastIndex else 0
            listState.scrollToItem(target)
            onEpisodePageChange(safePage, shelfJumpTarget.second)
            onShelfJumpHandled()
        }
    }

    val fadeLeft by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    val playingTransition = rememberInfiniteTransition(label = "pb-ep-playing")
    val playingColor by playingTransition.animateColor(
        initialValue = Color.White,
        targetValue = Color(0xFF33FF33),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tokens.tim10s.coerceAtLeast(300), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pb-ep-playing-color"
    )
    val shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
    val density = LocalDensity.current
    val hintSlidePx = with(density) { (tokens.vw * 10f).roundToPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = tokens.vw * 0.2f, bottom = tokens.vw * 2.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.vw * 5f)
                .padding(bottom = tokens.vw * 0.5f)
        ) {
            Text(
                text = headerTitle,
                color = if (active) Color.White else Color(0xFFCCCCCC),
                fontSize = fsSp(tokens.fs12),
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(shadow = shadow),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = hasPaging && active,
                enter = if (tokens.tim3s == 0) EnterTransition.None else fadeIn(
                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                ) + slideInHorizontally(
                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                ) { -hintSlidePx },
                exit = if (tokens.tim3s == 0) ExitTransition.None else fadeOut(
                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                ) + slideOutHorizontally(
                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                ) { -hintSlidePx },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.vw * 1f))
                        .background(colors.primary2)
                        .padding(horizontal = tokens.vw * 0.5f, vertical = tokens.vw * 0.1f)
                ) {
                    Text(
                        text = "USE PROG+ / PROG- FOR PAGING",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs8),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            userScrollEnabled = osdVisible,
            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.5f),
            contentPadding = PaddingValues(horizontal = tokens.vw * 5f),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!maskDisabled) {
                        Modifier.horizontalEdgeFadeMask(fadeLeft = fadeLeft, fadeRight = true)
                    } else Modifier
                )
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionUp -> onMoveZone(-1)
                        Key.DirectionDown -> onMoveZone(1)
                        else -> false
                    }
                }
        ) {
            itemsIndexed(pageEpisodes, key = { _, ep -> ep.id }) { idx, ep ->
                val isPlaying = ep.id == selectedEpisodeId
                var focused by remember(ep.id) { mutableStateOf(false) }
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()

                val tileBg = when {
                    focused -> colors.primary1
                    hovered -> colors.primary3
                    active -> Color(0x70FAFDFF)
                    else -> Color(0x59A0A5A7)
                }

                Box(
                    modifier = Modifier.wrapContentHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .width(tokens.vw * 10f)
                            .height(tokens.vw * 6f)
                            .clip(RoundedCornerShape(tokens.vw * 0.2f))
                            .background(tileBg)
                            .then(
                                if (!ep.imageUrl.isNullOrBlank() && focused) {
                                    Modifier.border(tokens.vw * 0.15f, Color.White, RoundedCornerShape(tokens.vw * 0.2f))
                                } else Modifier
                            )
                            .hoverable(interactionSource)
                            .then(if (idx == initialFocusTileIdx) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onActivate()
                            }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown || !hasPaging) return@onPreviewKeyEvent false
                                when {
                                    ev.key == Key.DirectionLeft && idx == 0 && safePage > 0 -> {
                                        onEpisodePageChange(safePage - 1, true)
                                        true
                                    }
                                    ev.key == Key.DirectionRight && idx == pageEpisodes.lastIndex && safePage < totalPages - 1 -> {
                                        onEpisodePageChange(safePage + 1, false)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .navigationClickable(enabled = focusEnabled) {
                                onEpisodeSelected(ep)
                            }
                            .navigationFocusable(enabled = focusEnabled),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!ep.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imageCdnUrl(ep.imageUrl, LocalImageCdn.current),
                                contentDescription = ep.title ?: "Episode ${ep.number}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.1f),
                                        1f to Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            )
                        }

                        Text(
                            text = ep.number,
                            color = if (ep.filler) Color(0xFFFFDDAA) else Color.White,
                            fontSize = fsSp(tokens.fs30),
                            fontWeight = FontWeight.Normal,
                            style = LocalTextStyle.current.copy(shadow = shadow)
                        )

                        if (ep.hasDub) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(tokens.vw * 0.3f)
                                    .clip(RoundedCornerShape(tokens.vw * 0.4f))
                                    .background(colors.primaryBg)
                                    .padding(horizontal = tokens.vw * 0.25f)
                            ) {
                                Text(
                                    text = IconGlyphs.Mic,
                                    color = Color.White,
                                    fontFamily = LocalIconFont.current,
                                    fontSize = fsSp(tokens.fs10)
                                )
                            }
                        }

                        if (ep.filler) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(tokens.vw * 0.2f)
                                    .clip(RoundedCornerShape(tokens.vw * 1f))
                                    .background(Color(0xFFAA5500))
                                    .padding(horizontal = tokens.vw * 0.5f)
                            ) {
                                Text(
                                    text = "FILLER",
                                    color = Color.White,
                                    fontSize = fsSp(tokens.fs7),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        ep.title?.takeIf { it.isNotBlank() }?.let { episodeTitle ->
                            Text(
                                text = episodeTitle.uppercase(),
                                color = Color.White,
                                fontSize = fsSp(tokens.fs8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(shadow = shadow),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = tokens.vw * 0.3f, vertical = tokens.vw * 0.1f)
                            )
                        }
                    }

                    if (isPlaying) {
                        Text(
                            text = "PLAYING",
                            color = playingColor,
                            fontSize = fsSp(tokens.fs10),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(shadow = shadow),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = tokens.vw * 0.5f)
                                .width(tokens.vw * 10f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackPosterShelf(
    title: String,
    zone: PlaybackMenuZone,
    osdVisible: Boolean,
    active: Boolean,
    focusEnabled: Boolean,
    animes: List<Anime>,
    playingAnimeId: String? = null,
    shelfJumpTarget: Pair<PlaybackMenuZone, Boolean>?,
    onShelfJumpHandled: () -> Unit,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    onMoveZone: (Int) -> Boolean,
    onAnimeSelected: (Anime) -> Unit,
    tokens: AnimeTvTokens,
    maskDisabled: Boolean,
    textShadowDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val dims = calculateCardDimensions(
        maxWidth = tokens.screenWidth,
        maxHeight = tokens.screenHeight,
        portrait = tokens.portrait,
        largeUi = tokens.largeUi
    )
    val shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(0f, tokens.vw.value * 0.1f), 4f) else null
    val listState = rememberLazyListState()
    val playingIdx = remember(animes, playingAnimeId) {
        if (!playingAnimeId.isNullOrBlank()) {
            animes.indexOfFirst { it.id == playingAnimeId }.takeIf { it >= 0 } ?: 0
        } else 0
    }
    var selectedIdx by remember(animes, playingIdx) { mutableIntStateOf(playingIdx) }
    val safeSelectedIdx = selectedIdx.coerceIn(0, animes.lastIndex.coerceAtLeast(0))

    LaunchedEffect(shelfJumpTarget, animes.size, active, focusEnabled) {
        if (active && focusEnabled && shelfJumpTarget?.first == zone && animes.isNotEmpty()) {
            val target = if (shelfJumpTarget.second) animes.lastIndex else 0
            selectedIdx = target
            listState.scrollToItem(target)
            yield()
            runCatching { focusRequester.requestFocus() }
            onShelfJumpHandled()
        }
    }

    val fadeLeft by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = tokens.vw * 1f)
    ) {
        Text(
            text = title,
            color = if (active) Color.White else Color(0xFFAAAAAA),
            fontSize = fsSp(tokens.fs13),
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(shadow = shadow),
            modifier = Modifier
                .padding(horizontal = tokens.vw * 5f)
                .padding(bottom = tokens.vw * 0.5f)
        )
        LazyRow(
            state = listState,
            userScrollEnabled = osdVisible,
            horizontalArrangement = Arrangement.spacedBy(dims.cardGap),
            contentPadding = PaddingValues(horizontal = tokens.vw * 5f, vertical = tokens.vw * 0.3f),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!maskDisabled) {
                        Modifier.horizontalEdgeFadeMask(fadeLeft = fadeLeft, fadeRight = true)
                    } else Modifier
                )
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionUp -> onMoveZone(-1)
                        Key.DirectionDown -> onMoveZone(1)
                        else -> false
                    }
                }
        ) {
            itemsIndexed(animes, key = { idx, anime -> "${anime.id}_$idx" }) { idx, anime ->
                Box(
                    modifier = Modifier
                        .then(if (idx == safeSelectedIdx) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused) {
                                selectedIdx = idx
                                onActivate()
                            }
                        }
                ) {
                    FocusCard(
                        anime = anime,
                        cardWidth = dims.cardWidth,
                        cardHeight = dims.cardHeight,
                        posterWidth = dims.posterWidth,
                        posterHeight = dims.posterHeight,
                        cardTitleSize = dims.titleSize,
                        cardTitleLineHeight = dims.titleLineHeight,
                        showBottomBadges = false,
                        showPlayingRibbon = !playingAnimeId.isNullOrBlank() && anime.id == playingAnimeId,
                        onClick = {
                            if (focusEnabled) {
                                selectedIdx = idx
                                onAnimeSelected(anime)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackTouchCircleButton(
    icon: String,
    buttonSize: Dp,
    iconSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        pressed -> colors.primary2
        hovered -> colors.primary1
        else -> Color(0x66191919)
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                playClickSound()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = Color.White,
            fontFamily = LocalIconFont.current,
            fontSize = iconSize,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StreamTypeTopBadge(
    currentStreamType: PlaybackStreamType,
    subStreamLabel: String,
    subtitleLanguage: String,
    selectedSubtitleLabel: String?,
    subtitlesCount: Int,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    shadowsDisabled: Boolean,
    textShadowDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDub = currentStreamType == PlaybackStreamType.DUB
    val isSoftsub = currentStreamType == PlaybackStreamType.SOFTSUB
    val isTranslated = isSoftsub &&
        subtitleLanguage.isNotBlank() &&
        subtitleLanguage !in setOf("hard", "nosub", "dub", "en")

    val icon = when {
        isTranslated -> IconGlyphs.GTranslate
        isDub -> IconGlyphs.Mic
        else -> IconGlyphs.ClosedCaption
    }
    val mainLabel = when {
        isTranslated -> "TRANSLATE"
        isDub -> "DUB"
        isSoftsub -> "SOFTSUB"
        else -> subStreamLabel.uppercase()
    }
    val langTag = if (isSoftsub) {
        subtitleLanguage.takeIf { it.isNotBlank() && it != "hard" && it != "nosub" }?.uppercase()
            ?: if (subtitlesCount > 0 && !selectedSubtitleLabel.isNullOrBlank()) {
                selectedSubtitleLabel.take(3).uppercase()
            } else ""
    } else ""

    val shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
    val shape = RoundedCornerShape(tokens.vw * 0.3f)

    Row(
        modifier = modifier
            .then(if (!shadowsDisabled) Modifier.shadow(tokens.vw * 0.1f, shape, clip = false) else Modifier)
            .clip(shape)
            .background(colors.primary2)
            .border((tokens.vw * 0.02f).coerceAtLeast(0.5.dp), colors.primary1, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.4f)
        ) {
            Text(
                text = icon,
                color = Color.White,
                fontFamily = LocalIconFont.current,
                fontSize = fsSp(tokens.fs13),
                style = LocalTextStyle.current.copy(shadow = shadow)
            )
            Text(
                text = mainLabel,
                color = Color.White,
                fontSize = fsSp(tokens.fs13),
                style = LocalTextStyle.current.copy(shadow = shadow)
            )
        }
        if (langTag.isNotEmpty()) {
            val leftBorderWidth = (tokens.vw * 0.15f).coerceAtLeast(1.dp)
            Box(
                modifier = Modifier
                    .padding(start = tokens.vw * 0.2f)
                    .background(colors.primaryBg)
                    .drawBehind {
                        val strokePx = leftBorderWidth.toPx()
                        drawLine(
                            color = colors.primary3,
                            start = Offset(strokePx * 0.5f, 0f),
                            end = Offset(strokePx * 0.5f, size.height),
                            strokeWidth = strokePx
                        )
                    }
                    .padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.2f)
            ) {
                Text(
                    text = langTag,
                    color = Color.White,
                    fontSize = fsSp(tokens.fs13),
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = shadow)
                )
            }
        }
    }
}

@Composable
private fun PlaybackSkipEventPill(
    skipRange: SkipRange,
    autoSkip: Boolean,
    tokens: AnimeTvTokens,
    colors: AnimeTvThemeColors,
    shadowsDisabled: Boolean,
    textShadowDisabled: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pillAlpha by remember(skipRange) { mutableFloatStateOf(1f) }
    var rippleProgress by remember(skipRange, autoSkip) { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(skipRange, tokens.isTouch) {
        pillAlpha = 1f
        delay(3_200L)
        val endAlpha = if (tokens.isTouch) 0.3f else 0f
        val steps = 16
        repeat(steps) { idx ->
            delay(50L)
            val t = (idx + 1).toFloat() / steps
            pillAlpha = 1f + (endAlpha - 1f) * t
        }
    }

    LaunchedEffect(skipRange, autoSkip) {
        if (autoSkip) {
            rippleProgress = 0f
            val steps = 40
            repeat(steps) { idx ->
                delay(50L)
                rippleProgress = (idx + 1).toFloat() / steps
            }
        } else {
            rippleProgress = 0f
        }
    }

    val interactive = hovered || pillAlpha > 0.02f || tokens.isTouch
    val displayAlpha = if (hovered) 1f else pillAlpha
    val pillBg = if (hovered) Color(200, 200, 200).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(tokens.vw * 0.3f)
    val shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
    val label = if (skipRange.kind == SkipRange.Kind.INTRO) "SKIP INTRO" else "SKIP OUTRO"

    Box(
        modifier = modifier
            .graphicsLayer { alpha = displayAlpha }
            .then(if (!shadowsDisabled) Modifier.shadow(tokens.vw * 0.3f, shape, clip = false) else Modifier)
            .clip(shape)
            .background(pillBg)
            .border((tokens.vw * 0.05f).coerceAtLeast(0.5.dp), Color.White, shape)
            .hoverable(interactionSource)
            .navigationClickable(enabled = interactive, onClick = onSkip)
            .navigationFocusable(enabled = interactive)
    ) {
        if (autoSkip && rippleProgress > 0f) {
            val circleScale = 3f * rippleProgress
            val circleAlpha = if (rippleProgress < 0.5f) rippleProgress * 2f else 1f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawCircle(
                            color = colors.primary2.copy(alpha = circleAlpha),
                            radius = (tokens.vw * 5f).toPx() * circleScale,
                            center = Offset.Zero
                        )
                        drawContent()
                    }
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = tokens.vw * 1.5f, vertical = tokens.vw * 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.5f)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = fsSp(tokens.fs14),
                fontWeight = FontWeight.Normal,
                style = LocalTextStyle.current.copy(shadow = shadow)
            )
            Text(
                text = IconGlyphs.FastForward,
                color = Color.White,
                fontFamily = LocalIconFont.current,
                fontSize = fsSp(tokens.fs14),
                style = LocalTextStyle.current.copy(shadow = shadow)
            )
        }
    }
}

private fun Modifier.horizontalEdgeFadeMask(
    fadeLeft: Boolean = false,
    fadeRight: Boolean = true
): Modifier {
    if (!fadeLeft && !fadeRight) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val stops = buildList {
                if (fadeLeft) {
                    add(0f to Color.Transparent)
                    add(0.05f to Color.Black)
                } else {
                    add(0f to Color.Black)
                }
                if (fadeRight) {
                    add(0.95f to Color.Black)
                    add(1f to Color.Transparent)
                } else {
                    add(1f to Color.Black)
                }
            }
            drawRect(
                brush = Brush.horizontalGradient(*stops.toTypedArray()),
                blendMode = BlendMode.DstIn
            )
        }
}

@Composable
internal fun PlaybackErrorScreen(
    title: String,
    episodeLabel: String,
    message: String,
    onBack: () -> Unit,
    description: String = message,
    genres: List<String> = emptyList(),
    artworkUrl: String? = null,
    canPrevious: Boolean = false,
    canNext: Boolean = false,
    hasSubStream: Boolean = false,
    hasDubStream: Boolean = false,
    subStreamLabel: String = "Sub",
    currentStreamType: PlaybackStreamType = PlaybackStreamType.SUB,
    streamServerNames: List<String> = emptyList(),
    selectedStreamServerIndex: Int = 0,
    onStreamServerChange: (Int) -> Unit = {},
    onStreamTypeChange: (PlaybackStreamType) -> Unit = {},
    playbackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    playbackQuality: Int = 0,
    onPlaybackQualityChange: (Int) -> Unit = {},
    audioLanguage: String = "",
    onAudioLanguageChange: (String) -> Unit = {},
    episodes: List<Episode> = emptyList(),
    selectedEpisodeId: String = "",
    currentAnimeId: String = "",
    metadataFormat: String? = null,
    metadataQuality: String? = null,
    metadataRating: String? = null,
    seasons: List<Anime> = emptyList(),
    related: List<Anime> = emptyList(),
    recommendations: List<Anime> = emptyList(),
    inWatchlist: Boolean = false,
    isTracked: Boolean = false,
    settingsOverlayOpen: Boolean = false,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onEpisodeSelected: ((Episode) -> Unit)? = null,
    onAnimeSelected: ((Anime) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onToggleWatchlist: (() -> Unit)? = null,
    onTrackingClick: (() -> Unit)? = null,
    onSearchGenre: ((String) -> Unit)? = null
) {
    var activeDialog by remember { mutableStateOf<PlaybackQuickDialog?>(null) }
    val effectiveServers = if (streamServerNames.isNotEmpty()) streamServerNames else listOf("Main", "Mirror")
    val serverLabel = effectiveServers.getOrNull(selectedStreamServerIndex) ?: effectiveServers.firstOrNull()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PlaybackOsdContent(
            metadataTitle = title,
            episodeLabel = episodeLabel,
            metadataDescription = description.ifBlank { message },
            metadataGenres = genres,
            artworkUrl = artworkUrl,
            firstFrameRendered = false,
            hasNext = canNext,
            hasPrevious = canPrevious,
            positionMs = 0L,
            durationMs = 0L,
            bufferedPositionMs = 0L,
            isPlaying = false,
            isLoading = false,
            isEnded = false,
            statusTextOverride = "PLAYBACK ERROR",
            errorMessage = message,
            skipRanges = emptyList(),
            autoSkipIntro = false,
            seekSeconds = 10,
            playbackSpeed = playbackSpeed,
            currentStreamType = currentStreamType,
            hasSubStream = hasSubStream,
            hasDubStream = hasDubStream,
            subStreamLabel = subStreamLabel,
            streamServerLabel = serverLabel,
            subtitleLanguage = "",
            selectedSubtitleLabel = null,
            subtitlesCount = 0,
            audioLanguageLabel = AudioLanguageCatalog.all.first().label,
            playbackQuality = playbackQuality,
            detectedVideoHeight = 0,
            disableTouchGestures = false,
            episodes = episodes,
            selectedEpisodeId = selectedEpisodeId,
            seasons = seasons,
            related = related,
            recommendations = recommendations,
            inWatchlist = inWatchlist,
            isTracked = isTracked,
            osdHudState = null,
            seekIndicatorSide = null,
            onShowOsdHud = {},
            onShowSeekIndicator = {},
            onDebouncedSeek = {},
            onImmediateSeek = {},
            onTogglePlay = {},
            onNextEpisode = onNext,
            onPreviousEpisode = onPrevious,
            onEpisodeSelected = onEpisodeSelected,
            onAnimeSelected = onAnimeSelected,
            onOpenSettings = onOpenSettings,
            onPlaybackSpeedDialogRequest = { activeDialog = PlaybackQuickDialog.SPEED },
            onStreamServerDialogRequest = { activeDialog = PlaybackQuickDialog.STREAM_SERVER },
            onPlaybackQualityDialogRequest = { activeDialog = PlaybackQuickDialog.QUALITY },
            onStreamTypeDialogRequest = { activeDialog = PlaybackQuickDialog.STREAM_TYPE },
            onSubtitlesDialogRequest = null,
            onAudioLanguageDialogRequest = { activeDialog = PlaybackQuickDialog.AUDIO_LANGUAGE },
            onToggleWatchlist = onToggleWatchlist,
            onTrackingClick = onTrackingClick,
            onSearchGenre = onSearchGenre,
            onAdjustBrightness = {},
            onAdjustVolume = {},
            onBack = onBack,
            currentAnimeId = currentAnimeId,
            metadataFormat = metadataFormat,
            metadataQuality = metadataQuality,
            metadataRating = metadataRating,
            settingsOverlayOpen = settingsOverlayOpen
        )

        PlaybackQuickOptionDialogs(
            activeDialog = activeDialog,
            onDismiss = { activeDialog = null },
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            currentStreamType = currentStreamType,
            hasSubStream = hasSubStream,
            hasDubStream = hasDubStream,
            subStreamLabel = subStreamLabel,
            onStreamTypeChange = onStreamTypeChange,
            streamServerNames = effectiveServers,
            selectedStreamServerIndex = selectedStreamServerIndex,
            onStreamServerChange = onStreamServerChange,
            audioLanguage = audioLanguage,
            availableAudioLanguages = emptySet(),
            onAudioLanguageChange = onAudioLanguageChange,
            playbackQuality = playbackQuality,
            detectedVideoHeights = emptyList(),
            onPlaybackQualityChange = onPlaybackQualityChange
        )
    }
}

@Composable
internal fun PlaybackLoadingScreen() {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val textShadowDisabled = LocalPerformanceEffects.current.getOrElse(4) { false }
    val density = LocalDensity.current
    val blurOuter = with(density) { (tokens.vw * 0.2f).toPx().coerceAtLeast(1f) }
    val blurInner = with(density) { (tokens.vw * 0.1f).toPx().coerceAtLeast(1f) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = tokens.vh * 40f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimeTvSpinner(
                size = tokens.u * 8f,
                strokeWidth = tokens.u * 0.8f,
                color = colors.primary2
            )
            Box(modifier = Modifier.padding(top = tokens.vw * 1f), contentAlignment = Alignment.Center) {
                if (!textShadowDisabled) {
                    Text(
                        text = "LOADING DATA",
                        color = Color.Black,
                        fontSize = fsSp(tokens.fs10),
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(Color.Black, Offset.Zero, blurRadius = blurOuter)
                        )
                    )
                }
                Text(
                    text = "LOADING DATA",
                    color = Color.White,
                    fontSize = fsSp(tokens.fs10),
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(
                        shadow = if (!textShadowDisabled) Shadow(Color.Black, Offset.Zero, blurRadius = blurInner) else null
                    )
                )
            }
        }
    }
}

@Composable
internal fun SubtitleStylePickerDialog(
    style: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStyle by remember(style) { mutableIntStateOf(style) }
    var selectedAxis by remember { mutableStateOf<Int?>(null) }
    var lastAxisIndex by remember { mutableIntStateOf(0) }

    val axis = selectedAxis
    if (axis == null) {
        ListOrderDialog(
            title = "Subtitle Style",
            items = SubtitleStyleCatalog.axes.mapIndexed { index, definition ->
                val value = definition.options[SubtitleStyleCatalog.selectedIndex(currentStyle, index)]
                ListOrderItem(
                    id = "axis_$index",
                    title = definition.title,
                    value = value,
                    icon = SubtitleStyleCatalog.axisIcons.getOrNull(index)
                )
            },
            mode = ListOrderMode.MENU,
            initialSelectedIndex = lastAxisIndex,
            onDismiss = onDismiss,
            onSelect = { index, _ ->
                lastAxisIndex = index
                selectedAxis = index
            }
        )
    } else {
        val definition = SubtitleStyleCatalog.axes[axis]
        val selectedOption = SubtitleStyleCatalog.selectedIndex(currentStyle, axis)
        ListOrderDialog(
            title = definition.title,
            items = definition.options.mapIndexed { optionIndex, option ->
                ListOrderItem(
                    id = "opt_${axis}_$optionIndex",
                    title = option,
                    selected = optionIndex == selectedOption
                )
            },
            mode = ListOrderMode.LIST,
            initialSelectedIndex = selectedOption,
            onDismiss = { selectedAxis = null },
            onSelect = { optionIndex, _ ->
                val updated = SubtitleStyleCatalog.select(currentStyle, axis, optionIndex)
                currentStyle = updated
                onChange(updated)
                selectedAxis = null
            }
        )
    }

    PlatformSubtitlePreviewOverlay(recreateKey = axis) {
        VttSubtitlePreviewOverlay(style = currentStyle)
    }
}

internal fun subtitleBoldSpanWeight(style: Int): FontWeight {
    return when (SubtitleStyleCatalog.selectedIndex(style, 2)) {
        3 -> FontWeight.Medium
        0 -> FontWeight.Bold
        else -> FontWeight.Black
    }
}

@Composable
internal fun VttSubtitlePreviewOverlay(style: Int) {
    val boldSpanWeight = subtitleBoldSpanWeight(style)
    val previewText = remember(boldSpanWeight) {
        buildAnnotatedString {
            append("Your subtitles will look like this.\nSubtitle ")
            withStyle(SpanStyle(fontWeight = boldSpanWeight)) {
                append("support bold")
            }
            append(", ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append("italic")
            }
            append(" and ")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append("underline.")
            }
        }
    }
    VttSubtitleCueOverlay(cueLines = listOf(previewText), style = style)
}

@Composable
internal fun VttSubtitleCueOverlay(
    cueLines: List<AnnotatedString>,
    style: Int,
    modifier: Modifier = Modifier
) {
    if (cueLines.isEmpty()) return

    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current

    val fontIndex = SubtitleStyleCatalog.selectedIndex(style, 0)
    val sizeIndex = SubtitleStyleCatalog.selectedIndex(style, 1)
    val weightIndex = SubtitleStyleCatalog.selectedIndex(style, 2)
    val backgroundIndex = SubtitleStyleCatalog.selectedIndex(style, 3)
    val colorIndex = SubtitleStyleCatalog.selectedIndex(style, 4)
    val opacityIndex = SubtitleStyleCatalog.selectedIndex(style, 5)
    val outlineIndex = SubtitleStyleCatalog.selectedIndex(style, 6)

    val fontFamily = platformSubtitleFontFamily(fontIndex, weightIndex)
    val fontSizeDp = when (sizeIndex) {
        0 -> tokens.fs30
        1 -> tokens.fs25
        2 -> tokens.fs20
        else -> tokens.fs30 + (tokens.fs7 / 3f)
    }
    val fontSize = fsSp(fontSizeDp)
    val fontWeight = when (weightIndex) {
        1 -> FontWeight.Bold
        2 -> FontWeight.Black
        3 -> FontWeight.Light
        else -> FontWeight.Normal
    }
    val lineHeightMultiplier = when (fontIndex) {
        3, 4, 6, 7, 8 -> 1.1f
        9 -> 1.2f
        else -> 1.15f
    }

    val textColor = SubtitleStyleCatalog.resolveColor(colorIndex, colors.primary1, colors.accent1)
    val textOpacity = SubtitleStyleCatalog.resolveTextOpacity(opacityIndex)
    val bgAlpha = SubtitleStyleCatalog.resolveBackgroundOpacity(backgroundIndex)
    val outlineColor = SubtitleStyleCatalog.resolveOutlineColor(colorIndex)
    val outlineVw = SubtitleStyleCatalog.resolveOutlineWidthVw(outlineIndex)

    val density = LocalDensity.current
    val outlineStrokePx = with(density) { (tokens.vw * outlineVw).toPx() }
    val dropShadow = Shadow(
        color = if (outlineIndex == 0) outlineColor else Color(0x800A0A0A),
        offset = if (outlineIndex == 0) Offset.Zero else with(density) {
            val d = tokens.vw * (0.1f + outlineIndex * 0.0375f)
            Offset(d.toPx(), d.toPx())
        },
        blurRadius = with(density) { (tokens.vw * 0.1f).toPx() }.coerceAtLeast(1f)
    )

    val baseStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        lineHeight = fontSize * lineHeightMultiplier
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = tokens.vw * 3f, end = tokens.vw * 3f, bottom = tokens.vw * 3f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.graphicsLayer { alpha = textOpacity },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.vw * 0.2f)
        ) {
            cueLines.forEach { line ->
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = line,
                        style = baseStyle.copy(
                            color = outlineColor,
                            background = Color.Black.copy(alpha = bgAlpha),
                            shadow = dropShadow,
                            drawStyle = if (outlineStrokePx > 0f) {
                                Stroke(width = outlineStrokePx, join = StrokeJoin.Round)
                            } else null
                        )
                    )
                    if (outlineStrokePx > 0f) {
                        Text(
                            text = line,
                            style = baseStyle.copy(color = textColor)
                        )
                    } else {
                        Text(
                            text = line,
                            style = baseStyle.copy(
                                color = textColor,
                                background = Color.Black.copy(alpha = bgAlpha),
                                shadow = dropShadow
                            )
                        )
                    }
                }
            }
        }
    }
}
