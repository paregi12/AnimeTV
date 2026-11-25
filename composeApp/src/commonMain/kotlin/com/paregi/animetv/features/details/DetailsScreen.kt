package com.paregi.animetv.features.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformBackHandler
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvSpinner
import com.paregi.animetv.core.ui.displayAnimeSynopsis
import com.paregi.animetv.core.ui.displayAnimeTitle
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AnimeDetails
import com.paregi.animetv.domain.AnimeTrackingState
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.WatchProgress
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DetailsScreen(
    anime: Anime,
    tvLayout: Boolean,
    details: AnimeDetails?,
    synopsis: String?,
    aniListInfo: AniListAnimeInfo?,
    aniListInfoLoading: Boolean,
    aniListInfoLoaded: Boolean,
    aniListInfoError: String?,
    onRequestAniListInfo: (Boolean) -> Unit,
    watchProgress: WatchProgress?,
    inHistory: Boolean,
    skipFiller: Boolean,
    inWatchlist: Boolean,
    tracking: AnimeTrackingState,
    trackingLoading: Boolean,
    trackingBusy: Boolean,
    trackingMessage: String?,
    onTrackingStatusChange: (AccountProvider, String?) -> Unit,
    onConnectAccounts: () -> Unit,
    onWatchlistChange: (Boolean) -> Unit,
    onHistoryChange: (Boolean) -> Unit,
    onEpisodeSelected: (Episode, Long?) -> Unit,
    onDismiss: () -> Unit = {},
    loading: Boolean = false,
    openedFromCard: Boolean = true
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val performanceEffects = LocalPerformanceEffects.current
    val portrait = tokens.portrait
    val isTouch = tokens.isTouch
    val blurEnabled = !performanceEffects.getOrElse(0) { true }
    val shadowDisabled = performanceEffects.getOrElse(2) { true }
    val isBigFont = if (portrait) tokens.fs10 > tokens.u * 1.25f else tokens.fs10 > tokens.u * 1.05f

    var fallbackLoading by remember(anime.id) { mutableStateOf(details == null) }
    LaunchedEffect(anime.id, details, loading) {
        if (details != null) {
            fallbackLoading = false
        } else if (!loading) {
            delay(4500)
            fallbackLoading = false
        }
    }
    val isLoading = loading || (details == null && fallbackLoading)

    val detailAnime = details?.anime ?: anime
    val episodes = details?.episodes.orEmpty()
    val firstPlayableEpisode = episodes.firstOrNull { !skipFiller || !it.filler }
    val recordedResumeEpisode = watchProgress?.let { progress ->
        episodes.firstOrNull { it.id == progress.episodeId }
            ?: progress.episodeId.takeIf { it.startsWith("${anime.id}|") }
                ?.substringAfterLast('|')
                ?.let { number -> episodes.firstOrNull { it.number == number } }
    }
    val resumeEpisode = recordedResumeEpisode?.let { recorded ->
        if (!skipFiller || !recorded.filler) recorded
        else {
            val index = episodes.indexOfFirst { it.id == recorded.id }
            episodes.drop(index + 1).firstOrNull { !it.filler }
        }
    }
    val resumePositionMs = if (resumeEpisode?.id == recordedResumeEpisode?.id) {
        watchProgress?.positionMs ?: 0L
    } else 0L
    val resumePercent = watchProgress?.takeIf {
        resumeEpisode != null && it.durationMs > 0L && resumePositionMs > 0L
    }?.let { (resumePositionMs.toDouble() * 100.0 / it.durationMs).toInt().coerceIn(0, 100) }
    val resumeIndex = resumeEpisode?.let { ep -> episodes.indexOfFirst { it.id == ep.id } } ?: -1
    val nextEpisode = if (resumeIndex >= 0) {
        episodes.drop(resumeIndex + 1).firstOrNull { !skipFiller || !it.filler }
    } else null

    var episodePickerId by remember(anime.id, episodes.map { it.id }, resumeEpisode?.id, skipFiller) {
        mutableStateOf((resumeEpisode ?: firstPlayableEpisode ?: episodes.firstOrNull())?.id)
    }
    val episodePickerIndex = episodes.indexOfFirst { it.id == episodePickerId }
    val episodePickerEpisode = episodes.getOrNull(episodePickerIndex) ?: firstPlayableEpisode
    val previousPickerEpisode = if (episodePickerIndex > 0) episodes.getOrNull(episodePickerIndex - 1) else null
    val nextPickerEpisode = if (episodePickerIndex in 0 until episodes.lastIndex) episodes.getOrNull(episodePickerIndex + 1) else null
    val episodeTotal = detailAnime.episodeCount?.takeIf { it > 0 } ?: episodes.size

    var showAniListInfo by remember(anime.id) { mutableStateOf(false) }
    var selectedTrailerId by remember(anime.id) { mutableStateOf<String?>(null) }
    val initialDetailsFocusRequester = remember(anime.id) { FocusRequester() }
    val aniListInfoFocusRequester = remember(anime.id) { FocusRequester() }

    LaunchedEffect(
        showAniListInfo,
        isTouch,
        isLoading,
        aniListInfoLoaded,
        aniListInfo != null,
        firstPlayableEpisode?.id,
        resumeEpisode?.id
    ) {
        if (!isTouch && !isLoading) {
            val requester = if (showAniListInfo) aniListInfoFocusRequester else initialDetailsFocusRequester
            runCatching { requester.requestFocus() }
        }
    }

    PlatformBackHandler(enabled = showAniListInfo) {
        selectedTrailerId = null
        showAniListInfo = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onDismiss()
            },
        contentAlignment = if (portrait && showAniListInfo) Alignment.BottomCenter else Alignment.Center
    ) {
        val cornerRadius = tokens.u * 0.5f
        val cardShape = RoundedCornerShape(cornerRadius)
        val panelWidth = when {
            showAniListInfo && portrait -> maxWidth
            showAniListInfo -> if (isBigFont) tokens.vw * 72f else tokens.vw * 64f
            portrait -> tokens.vw * 92f
            isBigFont -> tokens.vw * 72f
            else -> tokens.vw * 64f
        }
        val panelHeight = when {
            showAniListInfo && portrait -> tokens.vh * 80f
            showAniListInfo -> tokens.vh * 90f
            portrait -> tokens.vh * 60f
            else -> tokens.vw * 22f
        }
        val surfaceColor = when {
            showAniListInfo && blurEnabled -> Color(0x99181818)
            showAniListInfo -> Color(0xFF181818)
            blurEnabled -> Color(0x99202020)
            else -> Color(0xFF222222)
        }

        val containerModifier = Modifier
            .then(
                if (portrait && showAniListInfo) Modifier.padding(bottom = tokens.vh * 10f)
                else Modifier
            )
            .width(panelWidth)
            .height(panelHeight)
            .then(
                if (!shadowDisabled) Modifier.shadow(tokens.u * 0.56f, cardShape, clip = false)
                else Modifier
            )
            .clip(cardShape)
            .background(surfaceColor)
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}

        if (showAniListInfo) {
            AniListInfoDialog(
                modifier = containerModifier,
                info = aniListInfo,
                loading = aniListInfoLoading,
                loaded = aniListInfoLoaded,
                error = aniListInfoError,
                onRetry = { onRequestAniListInfo(true) },
                tracking = tracking,
                trackingLoading = trackingLoading,
                trackingBusy = trackingBusy,
                trackingMessage = trackingMessage,
                onTrackingStatusChange = onTrackingStatusChange,
                onConnectAccounts = onConnectAccounts,
                selectedTrailerId = selectedTrailerId,
                initialFocusRequester = aniListInfoFocusRequester.takeIf { !isTouch },
                onPlayTrailer = { trailer -> selectedTrailerId = trailer }
            )
        } else if (isLoading) {
            Box(containerModifier, contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.8f)
                ) {
                    AnimeTvSpinner(
                        size = tokens.u * 2f,
                        strokeWidth = tokens.u * 0.25f,
                        color = Color.White
                    )
                    Text(
                        text = "Loading Data...",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs14)
                    )
                }
            }
        } else {
            val infoSurfaceColor = if (blurEnabled) Color(0x80000000) else Color(0xFF121212)
            val posterWidth = if (portrait) tokens.vh * 17f else tokens.vw * 16f
            val posterHeight = if (portrait) tokens.vh * 22f else tokens.vw * 22f
            val actionsWidth = if (isBigFont) tokens.vw * 19f else tokens.vw * 16f
            val infoRightPadding = if (isBigFont) tokens.vw * 20f else tokens.vw * 17f
            val moreInfoRightOffset = if (isBigFont) tokens.vw * 20.8f else tokens.vw * 17.8f

            Box(containerModifier) {
                if (portrait) {
                    DetailInformation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = tokens.vh * 22f)
                            .background(infoSurfaceColor),
                        anime = detailAnime,
                        title = displayAnimeTitle(detailAnime, LocalJapaneseTitles.current),
                        synopsis = synopsis,
                        isPortrait = true,
                        isBigFont = isBigFont
                    )
                    DetailPoster(
                        anime = detailAnime,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(posterWidth)
                            .height(posterHeight)
                            .drawBehind {
                                drawLine(
                                    Color(0xFF333333),
                                    Offset(size.width, 0f),
                                    Offset(size.width, size.height),
                                    strokeWidth = 1f
                                )
                            }
                    )
                    DetailPlaybackActions(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth()
                            .padding(
                                start = tokens.vh * 17.5f,
                                end = tokens.vh * 0.5f,
                                top = tokens.vh * 0.1f,
                                bottom = tokens.vh * 28f
                            ),
                        isPortrait = true,
                        openedFromCard = openedFromCard,
                        firstEpisode = firstPlayableEpisode,
                        resumeEpisode = resumeEpisode,
                        resumePercent = resumePercent,
                        resumePositionMs = resumePositionMs,
                        nextEpisode = nextEpisode,
                        episodes = episodes,
                        episode = episodePickerEpisode,
                        episodeIndex = episodePickerIndex,
                        episodeTotal = episodeTotal,
                        previousEpisode = previousPickerEpisode,
                        nextPickerEpisode = nextPickerEpisode,
                        inWatchlist = inWatchlist,
                        inHistory = inHistory,
                        isTouch = isTouch,
                        onStepEpisode = { episodePickerId = it.id },
                        onPlay = onEpisodeSelected,
                        onWatchlistChange = onWatchlistChange,
                        onHistoryChange = onHistoryChange,
                        initialFocusRequester = initialDetailsFocusRequester.takeIf {
                            !isTouch && firstPlayableEpisode != null
                        }
                    )
                    FloatingMoreInfoButton(
                        portrait = true,
                        onClick = { showAniListInfo = true; onRequestAniListInfo(false) },
                        focusRequester = initialDetailsFocusRequester.takeIf {
                            !isTouch && firstPlayableEpisode == null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = tokens.vh * 23f, end = tokens.vh * 1f)
                    )
                } else {
                    DetailPoster(
                        anime = detailAnime,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(posterWidth)
                            .height(posterHeight)
                            .drawBehind {
                                drawLine(
                                    Color(0xFF333333),
                                    Offset(size.width, 0f),
                                    Offset(size.width, size.height),
                                    strokeWidth = 1f
                                )
                            }
                    )
                    DetailInformation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = posterWidth, end = infoRightPadding)
                            .background(infoSurfaceColor)
                            .drawBehind {
                                drawLine(
                                    Color(0xFF333333),
                                    Offset(size.width, 0f),
                                    Offset(size.width, size.height),
                                    strokeWidth = 1f
                                )
                            },
                        anime = detailAnime,
                        title = displayAnimeTitle(detailAnime, LocalJapaneseTitles.current),
                        synopsis = synopsis,
                        isPortrait = false,
                        isBigFont = isBigFont
                    )
                    DetailPlaybackActions(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .width(actionsWidth)
                            .heightIn(max = panelHeight)
                            .padding(end = tokens.vw * 0.5f, bottom = tokens.vw * 0.5f),
                        isPortrait = false,
                        openedFromCard = openedFromCard,
                        firstEpisode = firstPlayableEpisode,
                        resumeEpisode = resumeEpisode,
                        resumePercent = resumePercent,
                        resumePositionMs = resumePositionMs,
                        nextEpisode = nextEpisode,
                        episodes = episodes,
                        episode = episodePickerEpisode,
                        episodeIndex = episodePickerIndex,
                        episodeTotal = episodeTotal,
                        previousEpisode = previousPickerEpisode,
                        nextPickerEpisode = nextPickerEpisode,
                        inWatchlist = inWatchlist,
                        inHistory = inHistory,
                        isTouch = isTouch,
                        onStepEpisode = { episodePickerId = it.id },
                        onPlay = onEpisodeSelected,
                        onWatchlistChange = onWatchlistChange,
                        onHistoryChange = onHistoryChange,
                        initialFocusRequester = initialDetailsFocusRequester.takeIf {
                            !isTouch && firstPlayableEpisode != null
                        }
                    )
                    FloatingMoreInfoButton(
                        portrait = false,
                        onClick = { showAniListInfo = true; onRequestAniListInfo(false) },
                        focusRequester = initialDetailsFocusRequester.takeIf {
                            !isTouch && firstPlayableEpisode == null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = tokens.vw * 0.8f, end = moreInfoRightOffset)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPoster(anime: Anime, modifier: Modifier) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val rawRating = anime.rating?.takeIf { it.isNotBlank() } ?: if (anime.adult) "R+" else null
    val isAdult = anime.adult || rawRating?.equals("18+", ignoreCase = true) == true ||
        rawRating?.equals("R+", ignoreCase = true) == true

    Box(modifier.background(Color.Black)) {
        AsyncImage(
            model = imageCdnUrl(anime.posterUrl, LocalImageCdn.current),
            contentDescription = displayAnimeTitle(anime, LocalJapaneseTitles.current),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        rawRating?.let { rating ->
            Text(
                text = rating,
                color = Color.White,
                fontSize = fsSp(tokens.fs10),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(tokens.u * 0.5f)
                    .clip(RoundedCornerShape(tokens.u * 0.2f))
                    .background(if (isAdult) Color(0xFFAA0000) else colors.primary2)
                    .padding(horizontal = tokens.u * 0.4f, vertical = tokens.u * 0.2f)
            )
        }
    }
}

@Composable
private fun FloatingMoreInfoButton(
    portrait: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val expanded = portrait || focused || hovered

    val pillWidth by animateDpAsState(
        targetValue = if (expanded) tokens.fs15 * 4.3f else tokens.fs17 + tokens.u * 0.8f,
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )
    val bgColor = when {
        focused -> colors.primary1
        hovered -> colors.primary3
        else -> Color(0xFF333333)
    }

    Row(
        modifier = modifier
            .width(pillWidth)
            .clip(RoundedCornerShape(tokens.u * 4f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = IconGlyphs.Info,
            fontFamily = LocalIconFont.current,
            color = Color.White,
            fontSize = fsSp(tokens.fs15),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(tokens.fs17)
        )
        if (expanded) {
            Spacer(Modifier.width(tokens.u * 0.35f))
            Text(
                text = "More Info",
                color = Color.White,
                fontSize = fsSp(tokens.fs10),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun DetailPlaybackActions(
    modifier: Modifier,
    isPortrait: Boolean,
    openedFromCard: Boolean,
    firstEpisode: Episode?,
    resumeEpisode: Episode?,
    resumePercent: Int?,
    resumePositionMs: Long,
    nextEpisode: Episode?,
    episodes: List<Episode>,
    episode: Episode?,
    episodeIndex: Int,
    episodeTotal: Int,
    previousEpisode: Episode?,
    nextPickerEpisode: Episode?,
    inWatchlist: Boolean,
    inHistory: Boolean,
    isTouch: Boolean,
    onStepEpisode: (Episode) -> Unit,
    onPlay: (Episode, Long?) -> Unit,
    onWatchlistChange: (Boolean) -> Unit,
    onHistoryChange: (Boolean) -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val tokens = LocalAnimeTvTokens.current
    val rowSpacing = tokens.u * 0.45f
    val resumeLabel = if (openedFromCard && resumeEpisode == null) "Play Episode" else "Resume"

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowSpacing, Alignment.Bottom)
    ) {
        DetailPlaybackAction(
            icon = IconGlyphs.Replay,
            title = "Play from Beginning",
            enabled = firstEpisode != null,
            isPortrait = isPortrait,
            focusRequester = initialFocusRequester.takeIf { resumeEpisode == null },
            onClick = { firstEpisode?.let { onPlay(it, null) } }
        )

        DetailPlaybackAction(
            icon = IconGlyphs.Play,
            title = resumeLabel,
            subLabel = resumeEpisode?.let { "EP-${it.number}" },
            progressPercent = resumePercent,
            enabled = resumeEpisode != null,
            isPortrait = isPortrait,
            focusRequester = initialFocusRequester.takeIf { resumeEpisode != null },
            onClick = { resumeEpisode?.let { onPlay(it, resumePositionMs) } }
        )

        DetailPlaybackAction(
            icon = IconGlyphs.NextPlan,
            title = "Play Next",
            subLabel = nextEpisode?.let { "EP-${it.number}" },
            enabled = nextEpisode != null,
            isPortrait = isPortrait,
            onClick = { nextEpisode?.let { onPlay(it, null) } }
        )

        DetailEpisodeStepper(
            episodes = episodes,
            episode = episode,
            episodeIndex = episodeIndex,
            episodeTotal = episodeTotal,
            previousEpisode = previousEpisode,
            nextPickerEpisode = nextPickerEpisode,
            isPortrait = isPortrait,
            isTouch = isTouch,
            onStepEpisode = onStepEpisode,
            onPlay = { ep -> onPlay(ep, null) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.35f)
        ) {
            DetailWatchlistChip(
                inWatchlist = inWatchlist,
                isPortrait = isPortrait,
                onClick = { onWatchlistChange(!inWatchlist) },
                modifier = Modifier.weight(1f)
            )
            DetailHistoryChip(
                inHistory = inHistory,
                isPortrait = isPortrait,
                onClick = { onHistoryChange(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailPlaybackAction(
    icon: String,
    title: String,
    subLabel: String? = null,
    progressPercent: Int? = null,
    enabled: Boolean = true,
    isPortrait: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> Color(0xFF252525)
        focused -> colors.primary1
        hovered -> colors.primary2
        else -> Color(0xFF2A2A2A)
    }
    val contentColor = if (enabled) Color.White else Color(0xFF666666)
    val fontSize = fsSp(tokens.fs12)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.u * if (isPortrait) 3.8f else 3.7f)
            .alpha(if (enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(tokens.u * 0.5f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(enabled = enabled, onClick = onClick)
            .navigationFocusable(enabled = enabled)
            .padding(horizontal = tokens.u * 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontFamily = LocalIconFont.current,
                color = contentColor,
                fontSize = fontSize,
                modifier = Modifier.padding(horizontal = tokens.u * 0.45f)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (enabled && !subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    color = colors.accent1,
                    fontSize = fsSp(tokens.fs11),
                    maxLines = 1,
                    modifier = Modifier.padding(end = tokens.u * 0.3f)
                )
            }
        }

        if (enabled && progressPercent != null && progressPercent > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = tokens.u * 0.28f)
                    .height(tokens.u * 0.36f)
                    .clip(RoundedCornerShape(tokens.u * 0.2f))
                    .background(Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressPercent.coerceIn(0, 100) / 100f)
                        .fillMaxHeight()
                        .background(colors.accent1)
                )
            }
        }
    }
}

@Composable
private fun DetailEpisodeStepper(
    episodes: List<Episode>,
    episode: Episode?,
    episodeIndex: Int,
    episodeTotal: Int,
    previousEpisode: Episode?,
    nextPickerEpisode: Episode?,
    isPortrait: Boolean,
    isTouch: Boolean,
    onStepEpisode: (Episode) -> Unit,
    onPlay: (Episode) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    val enabled = episode != null && episodes.isNotEmpty()
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val prevHover = remember { MutableInteractionSource() }
    val nextHover = remember { MutableInteractionSource() }
    val isPrevHovered by prevHover.collectIsHoveredAsState()
    val isNextHovered by nextHover.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> Color(0xFF252525)
        focused -> colors.primary1
        hovered -> colors.primary2
        else -> Color(0xFF2A2A2A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.u * if (isPortrait) 3.8f else 3.7f)
            .alpha(if (enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(tokens.u * 0.5f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        previousEpisode?.let {
                            playClickSound()
                            onStepEpisode(it)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        nextPickerEpisode?.let {
                            playClickSound()
                            onStepEpisode(it)
                        }
                        true
                    }
                    Key.PageUp -> {
                        if (episodeIndex > 0) {
                            playClickSound()
                            onStepEpisode(episodes[(episodeIndex - 10).coerceAtLeast(0)])
                        }
                        true
                    }
                    Key.PageDown -> {
                        if (episodeIndex in 0 until episodes.lastIndex) {
                            playClickSound()
                            onStepEpisode(episodes[(episodeIndex + 10).coerceAtMost(episodes.lastIndex)])
                        }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(enabled = enabled) { episode?.let(onPlay) }
            .navigationFocusable(enabled = enabled)
            .padding(horizontal = tokens.u * 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = IconGlyphs.Remove,
            fontFamily = LocalIconFont.current,
            color = when {
                !enabled -> Color(0xFF666666)
                isPrevHovered -> colors.accent1
                else -> Color.White
            },
            fontSize = fsSp(tokens.fs13),
            modifier = Modifier
                .hoverable(interactionSource = prevHover, enabled = enabled && previousEpisode != null)
                .then(
                    if (enabled && previousEpisode != null) {
                        Modifier.clickable {
                            playClickSound()
                            onStepEpisode(previousEpisode)
                        }
                    } else Modifier
                )
                .padding(
                    horizontal = if (isTouch) tokens.u * 0.8f else tokens.u * 0.45f,
                    vertical = tokens.u * 0.3f
                )
        )

        Text(
            text = if (enabled && episode != null) {
                "Episode ${episode.number} / ${episodeTotal.takeIf { it > 0 }?.toString() ?: "?"}"
            } else "-",
            color = if (enabled) Color.White else Color(0xFF666666),
            fontSize = fsSp(tokens.fs12),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = IconGlyphs.Add,
            fontFamily = LocalIconFont.current,
            color = when {
                !enabled -> Color(0xFF666666)
                isNextHovered -> colors.accent1
                else -> Color.White
            },
            fontSize = fsSp(tokens.fs13),
            modifier = Modifier
                .hoverable(interactionSource = nextHover, enabled = enabled && nextPickerEpisode != null)
                .then(
                    if (enabled && nextPickerEpisode != null) {
                        Modifier.clickable {
                            playClickSound()
                            onStepEpisode(nextPickerEpisode)
                        }
                    } else Modifier
                )
                .padding(
                    horizontal = if (isTouch) tokens.u * 0.8f else tokens.u * 0.45f,
                    vertical = tokens.u * 0.3f
                )
        )
    }
}

@Composable
private fun DetailWatchlistChip(
    inWatchlist: Boolean,
    isPortrait: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        focused -> if (inWatchlist) Color(0xFF880000) else Color(0xFF006600)
        hovered -> colors.primary2
        else -> Color(0xFF2A2A2A)
    }

    Row(
        modifier = modifier
            .height(tokens.u * if (isPortrait) 3.8f else 3.7f)
            .clip(RoundedCornerShape(tokens.u * 0.5f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(horizontal = tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (inWatchlist) IconGlyphs.Close else IconGlyphs.Bookmark,
            fontFamily = LocalIconFont.current,
            color = Color.White,
            fontSize = fsSp(tokens.fs12)
        )
        Spacer(Modifier.width(tokens.u * 0.35f))
        Text(
            text = "Watchlist",
            color = Color.White,
            fontSize = fsSp(tokens.fs12),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailHistoryChip(
    inHistory: Boolean,
    isPortrait: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !inHistory -> Color(0xFF252525)
        focused -> Color(0xFF880000)
        hovered -> colors.primary2
        else -> Color(0xFF2A2A2A)
    }
    val fgColor = if (inHistory) Color.White else Color(0xFF666666)

    Row(
        modifier = modifier
            .height(tokens.u * if (isPortrait) 3.8f else 3.7f)
            .alpha(if (inHistory) 1f else 0.6f)
            .clip(RoundedCornerShape(tokens.u * 0.5f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource, enabled = inHistory)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(enabled = inHistory, onClick = onClick)
            .navigationFocusable(enabled = inHistory)
            .padding(horizontal = tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = IconGlyphs.Close,
            fontFamily = LocalIconFont.current,
            color = fgColor,
            fontSize = fsSp(tokens.fs12)
        )
        Spacer(Modifier.width(tokens.u * 0.35f))
        Text(
            text = "History",
            color = fgColor,
            fontSize = fsSp(tokens.fs12),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailInformation(
    modifier: Modifier,
    anime: Anime,
    title: String,
    synopsis: String?,
    isPortrait: Boolean,
    isBigFont: Boolean
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val titleFontSize = fsSp(if (isPortrait) tokens.fs17 else tokens.fs14)
    val bodyFontSize = fsSp(tokens.fs11)
    val rightLabel = when {
        !anime.duration.isNullOrBlank() -> "Duration:" to anime.duration!!
        !anime.format.isNullOrBlank() -> "Type:" to anime.format!!
        else -> null
    }
    val statusText = anime.status?.takeIf { it.isNotBlank() } ?: "-"
    val genreText = anime.genres.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "-"

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = tokens.u * 1f,
                end = tokens.u * 0.5f,
                top = tokens.u * 0.5f,
                bottom = tokens.u * 0.5f
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = colors.accent1,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Normal,
                lineHeight = titleFontSize * (if (isPortrait) 1.3f else 1.2f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isPortrait) {
                    Modifier.fillMaxWidth().padding(end = tokens.fs15 * 4.6f)
                } else {
                    Modifier.fillMaxWidth(0.90f)
                }
            )

            Text(
                text = synopsis?.takeIf { it.isNotBlank() }?.let(::displayAnimeSynopsis) ?: " ",
                color = Color(0xFFAAAAAA),
                fontSize = bodyFontSize,
                lineHeight = bodyFontSize * (if (isPortrait) 1.5f else 1.2f),
                maxLines = when {
                    isPortrait -> 5
                    isBigFont && tokens.fs14 >= tokens.vw * 1.9f -> 4
                    else -> 5
                },
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = tokens.u * (if (isPortrait) 1f else 0.8f), bottom = tokens.u * (if (isPortrait) 1f else 0.8f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) { append("Status: ") }
                        withStyle(SpanStyle(color = colors.accent1)) { append(statusText) }
                    },
                    fontSize = bodyFontSize,
                    lineHeight = bodyFontSize * 1.4f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                rightLabel?.let { (label, value) ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) { append("$label ") }
                            withStyle(SpanStyle(color = colors.accent1)) { append(value) }
                        },
                        fontSize = bodyFontSize,
                        lineHeight = bodyFontSize * 1.4f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = tokens.u * 1f)
                    )
                }
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Genre: ") }
                    withStyle(SpanStyle(color = colors.accent1)) { append(genreText) }
                },
                fontSize = bodyFontSize,
                lineHeight = bodyFontSize * (if (isBigFont && !isPortrait) 1.2f else 1.4f),
                maxLines = if (isBigFont && !isPortrait) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
