package com.paregi.animetv.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paregi.animetv.PlatformTrailer
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalAppToast
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.TextSecondary
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvSpinner
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.displayAnimeSynopsis
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.AniListReview
import com.paregi.animetv.domain.AnimeTrackingState
import com.paregi.animetv.domain.ProviderTrackingState
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun AniListInfoDialog(
    modifier: Modifier,
    info: AniListAnimeInfo?,
    loading: Boolean,
    loaded: Boolean,
    error: String?,
    onRetry: () -> Unit,
    tracking: AnimeTrackingState,
    trackingLoading: Boolean,
    trackingBusy: Boolean,
    trackingMessage: String?,
    onTrackingStatusChange: (AccountProvider, String?) -> Unit,
    onConnectAccounts: () -> Unit,
    selectedTrailerId: String?,
    initialFocusRequester: FocusRequester?,
    onPlayTrailer: (String?) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val toast = LocalAppToast.current
    val japaneseTitles = LocalJapaneseTitles.current
    val textShadowDisabled = LocalPerformanceEffects.current.getOrElse(4) { false }
    val portrait = tokens.portrait
    val isBigFont = if (portrait) tokens.fs10 > tokens.u * 1.25f else tokens.fs10 > tokens.u * 1.05f

    var statusProvider by remember { mutableStateOf<AccountProvider?>(null) }
    var pendingRemovalProvider by remember { mutableStateOf<AccountProvider?>(null) }
    var trailerState by remember(selectedTrailerId) { mutableStateOf<String?>(null) }
    var trailerReplayKey by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val titleShadow = if (!textShadowDisabled) Shadow(Color.Black, Offset(0f, 1.5f), 3f) else null

    Column(
        modifier = modifier
            .focusGroup()
            .focusProperties { onExit = { cancelFocusChange() } }
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0x66080808))
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.8f)
                    ) {
                        AnimeTvSpinner(
                            size = tokens.u * 2f,
                            strokeWidth = tokens.u * 0.25f,
                            color = Color.White
                        )
                        Text("Loading Data...", color = Color.White, fontSize = fsSp(tokens.fs14))
                    }
                }
                info == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(tokens.u * 2f),
                    verticalArrangement = Arrangement.spacedBy(tokens.u * 1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: if (loaded) "Matching AniList Not Found." else "AniList details are unavailable.",
                        color = if (error != null) Color(0xFFFF7A8A) else TextSecondary,
                        fontSize = fsSp(tokens.fs14),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Retry",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.u * 0.5f))
                            .background(colors.primary1)
                            .then(initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .navigationClickable(onClick = onRetry)
                            .navigationFocusable()
                            .padding(horizontal = tokens.u * 1.4f, vertical = tokens.u * 0.5f)
                    )
                }
                else -> {
                    val primaryTitle = if (japaneseTitles) {
                        info.titleRomaji ?: info.titleEnglish ?: info.titleNative.orEmpty()
                    } else {
                        info.titleEnglish ?: info.titleRomaji ?: info.titleNative.orEmpty()
                    }
                    val otherTitle = if (japaneseTitles) {
                        info.titleEnglish ?: info.titleRomaji
                    } else {
                        info.titleRomaji ?: info.titleEnglish
                    }
                    val bannerUrl = info.bannerUrl?.takeIf { it.isNotBlank() }
                        ?: info.trailerId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                    val aniListStatusText = tracking.aniList.status
                        ?.replace('_', ' ')
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() }
                        ?: "Not on your list"
                    val formattedSource = info.source
                        ?.replace('_', ' ')
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() }
                        ?: "-"
                    val formattedSeason = info.season
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() }
                        ?: "-"
                    val genresValue = info.genres.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-"
                    val synonymsValue = info.synonyms.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-"
                    val studiosValue = info.studios.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-"

                    if (portrait) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            val activeTrailerId = selectedTrailerId
                            if (activeTrailerId != null) {
                                key(activeTrailerId, trailerReplayKey) {
                                    PlatformTrailer(
                                        youtubeId = activeTrailerId,
                                        muted = false,
                                        onPlaybackState = { state -> trailerState = state },
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .height(tokens.vh * 26.8f)
                                    )
                                }
                            } else if (bannerUrl != null) {
                                AsyncImage(
                                    model = imageCdnUrl(bannerUrl, LocalImageCdn.current),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .height(tokens.vh * 26.8f)
                                        .background(Color.Black)
                                        .alpha(0.35f)
                                )
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color.Black,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = tokens.vh * 26.8f)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(tokens.vh * 1.5f)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    AsyncImage(
                                        model = imageCdnUrl(info.coverUrl, LocalImageCdn.current),
                                        contentDescription = primaryTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(tokens.vh * 16f)
                                            .height(tokens.vh * 22f)
                                            .clip(RoundedCornerShape(tokens.vh * 0.8f))
                                            .border(tokens.vw * 0.2f, Color.White, RoundedCornerShape(tokens.vh * 0.8f))
                                            .background(Color.Black)
                                    )
                                    Text(
                                        text = primaryTitle,
                                        color = Color.White,
                                        fontSize = fsSp(tokens.fs22),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = fsSp(tokens.fs22) * 1.15f,
                                        style = TextStyle(shadow = titleShadow),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = tokens.vh * 17.5f, top = tokens.vh * 1.5f)
                                    )
                                }

                                Spacer(Modifier.height(tokens.vh * 3f))

                                AniListKeyValCell(
                                    icon = IconGlyphs.Tune,
                                    label = "Genres",
                                    value = genresValue,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    AniListKeyValCell(
                                        icon = IconGlyphs.EventUpcoming,
                                        label = "Start Date",
                                        value = info.startDate ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                    AniListKeyValCell(
                                        icon = IconGlyphs.FactCheck,
                                        label = "End Date",
                                        value = info.endDate ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(Modifier.fillMaxWidth()) {
                                    AniListKeyValCell(
                                        icon = IconGlyphs.LiveTv,
                                        label = "Source",
                                        value = formattedSource,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AniListKeyValCell(
                                        icon = IconGlyphs.Public,
                                        label = "Country",
                                        value = info.countryOfOrigin ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(Modifier.fillMaxWidth()) {
                                    AniListKeyValCell(
                                        icon = IconGlyphs.PartlyCloudyNight,
                                        label = "Season",
                                        value = formattedSeason,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.weight(1f))
                                }

                                Spacer(Modifier.height(tokens.vh * 0.8f))

                                AniListInfoboxRow(info = info, textShadow = titleShadow)

                                info.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = displayAnimeSynopsis(description),
                                        color = Color.White,
                                        fontSize = fsSp(tokens.fs13),
                                        lineHeight = fsSp(tokens.fs13) * 1.2f,
                                        modifier = Modifier.padding(tokens.vh * 0.5f)
                                    )
                                }

                                Spacer(Modifier.height(tokens.vh * 1.5f))

                                AniListMetricsAndDetails(
                                    info = info,
                                    otherTitle = otherTitle,
                                    synonymsValue = synonymsValue,
                                    studiosValue = studiosValue
                                )

                                AniListReviewsBlock(reviews = info.reviews)
                            }
                        }
                    } else {
                        val mediaWidth = if (isBigFont) tokens.vw * 50f else tokens.vw * 42f
                        val trailerHeight = if (isBigFont) tokens.vw * 28f else tokens.vw * 22f
                        val bannerHeight = tokens.vw * 22f

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(tokens.vw * 1.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(tokens.vw * 2f),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.width(tokens.vw * 16f)) {
                                    AsyncImage(
                                        model = imageCdnUrl(info.coverUrl, LocalImageCdn.current),
                                        contentDescription = primaryTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(tokens.vw * 16f)
                                            .height(tokens.vw * 22f)
                                            .clip(RoundedCornerShape(tokens.vw * 0.8f))
                                            .background(Color.Black)
                                    )
                                    Spacer(Modifier.height(tokens.vw * 1f))
                                    Text(
                                        text = aniListStatusText,
                                        color = Color.White,
                                        fontSize = fsSp(tokens.fs13),
                                        lineHeight = fsSp(tokens.fs13) * 1.6f,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(tokens.vw * 0.5f))
                                            .background(Color(0x66282828))
                                            .padding(horizontal = tokens.vw * 0.8f, vertical = tokens.vw * 0.25f)
                                    )
                                    Spacer(Modifier.height(tokens.vw * 1f))
                                    AniListKeyValCell(IconGlyphs.Tune, "Genres", genresValue, Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.EventUpcoming, "Start Date", info.startDate ?: "-", Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.FactCheck, "End Date", info.endDate ?: "-", Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.LiveTv, "Source", formattedSource, Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.Public, "Country", info.countryOfOrigin ?: "-", Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.PartlyCloudyNight, "Season", formattedSeason, Modifier.fillMaxWidth())
                                    AniListKeyValCell(IconGlyphs.Schedule, "Year", info.year?.toString() ?: "-", Modifier.fillMaxWidth())
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    val activeTrailerId = selectedTrailerId
                                    if (activeTrailerId != null) {
                                        Box(
                                            modifier = Modifier
                                                .width(mediaWidth)
                                                .height(trailerHeight)
                                                .clip(RoundedCornerShape(tokens.vw * 0.8f))
                                                .background(Color.Black)
                                        ) {
                                            key(activeTrailerId, trailerReplayKey) {
                                                PlatformTrailer(
                                                    youtubeId = activeTrailerId,
                                                    muted = false,
                                                    onPlaybackState = { state -> trailerState = state },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(tokens.vw * 1f))
                                    } else if (bannerUrl != null) {
                                        AsyncImage(
                                            model = imageCdnUrl(bannerUrl, LocalImageCdn.current),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(mediaWidth)
                                                .height(bannerHeight)
                                                .clip(RoundedCornerShape(tokens.vw * 0.8f))
                                                .background(Color.Black)
                                        )
                                        Spacer(Modifier.height(tokens.vw * 1f))
                                    }

                                    Text(
                                        text = primaryTitle,
                                        color = colors.accent1,
                                        fontSize = fsSp(tokens.fs22),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = fsSp(tokens.fs22) * 1.15f,
                                        modifier = Modifier.padding(bottom = tokens.vw * 0.8f)
                                    )

                                    AniListInfoboxRow(info = info, textShadow = null)

                                    info.description?.takeIf { it.isNotBlank() }?.let { description ->
                                        Text(
                                            text = displayAnimeSynopsis(description),
                                            color = Color.White,
                                            fontSize = fsSp(tokens.fs13),
                                            lineHeight = fsSp(tokens.fs13) * 1.25f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = tokens.vw * 5f)
                                                .padding(bottom = tokens.vw * 0.5f)
                                        )
                                    }

                                    Spacer(Modifier.height(tokens.vw * 1.5f))

                                    AniListMetricsAndDetails(
                                        info = info,
                                        otherTitle = otherTitle,
                                        synonymsValue = synonymsValue,
                                        studiosValue = studiosValue
                                    )

                                    Spacer(Modifier.height(tokens.vw * 1.5f))

                                    AniListReviewsBlock(reviews = info.reviews)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp),
            color = Color(0x1AFFFFFF)
        )

        trackingMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF7A8A),
                fontSize = fsSp(tokens.fs11),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.u * 1f, vertical = tokens.u * 0.25f)
            )
        }

        if (info != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.u * 0.6f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step = tokens.screenHeight.value * 0.20f
                        when (event.key) {
                            Key.DirectionUp -> {
                                coroutineScope.launch { scrollState.animateScrollTo((scrollState.value - step.toInt()).coerceAtLeast(0)) }
                                true
                            }
                            Key.DirectionDown -> {
                                coroutineScope.launch { scrollState.animateScrollTo((scrollState.value + step.toInt()).coerceAtMost(scrollState.maxValue)) }
                                true
                            }
                            Key.PageUp -> {
                                coroutineScope.launch { scrollState.animateScrollTo((scrollState.value - (step * 2).toInt()).coerceAtLeast(0)) }
                                true
                            }
                            Key.PageDown -> {
                                coroutineScope.launch { scrollState.animateScrollTo((scrollState.value + (step * 2).toInt()).coerceAtMost(scrollState.maxValue)) }
                                true
                            }
                            else -> false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                info.trailerId?.let { trailerId ->
                    val (trailerIcon, trailerLabel) = when {
                        selectedTrailerId == null -> IconGlyphs.Play to if (portrait) "Trailer" else "Play Trailer"
                        trailerState == "yt-error" -> IconGlyphs.Close to if (portrait) "Close" else "Close Trailer"
                        trailerState == "yt-play" -> IconGlyphs.Pause to if (portrait) "Pause" else "Pause Trailer"
                        else -> IconGlyphs.Play to if (portrait) "Trailer" else "Play Trailer"
                    }
                    AniListToolButton(
                        icon = trailerIcon,
                        text = trailerLabel,
                        focusRequester = initialFocusRequester,
                        onClick = {
                            when {
                                selectedTrailerId == null -> {
                                    trailerState = null
                                    onPlayTrailer(trailerId)
                                }
                                trailerState == "yt-pause" || trailerState == "yt-end" -> {
                                    trailerState = null
                                    trailerReplayKey += 1
                                }
                                else -> {
                                    trailerState = null
                                    onPlayTrailer(null)
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                AniListToolButton(
                    icon = if (tracking.aniList.isTracked) IconGlyphs.Bookmark else IconGlyphs.Add,
                    text = aniListButtonLabel(tracking.aniList, trackingLoading, trackingBusy, portrait),
                    focusRequester = initialFocusRequester.takeIf { info.trailerId == null },
                    onClick = {
                        when {
                            trackingLoading || trackingBusy -> Unit
                            !tracking.aniList.connected -> toast("Please login to AniList first...")
                            tracking.aniList.mediaId == null -> toast("AniList entry unavailable")
                            else -> statusProvider = AccountProvider.ANILIST
                        }
                    }
                )

                Spacer(Modifier.width(tokens.u * 0.5f))

                AniListToolButton(
                    icon = if (tracking.mal.isTracked) IconGlyphs.Bookmark else IconGlyphs.Add,
                    text = malButtonLabel(tracking.mal, trackingLoading, trackingBusy, portrait),
                    onClick = {
                        when {
                            trackingLoading || trackingBusy -> Unit
                            !tracking.mal.connected -> toast("Please login to MAL first...")
                            tracking.mal.mediaId == null -> toast("MAL entry unavailable")
                            else -> statusProvider = AccountProvider.MAL
                        }
                    }
                )
            }
        }
    }

    statusProvider?.let { provider ->
        val providerName = if (provider == AccountProvider.ANILIST) "AniList" else "MAL"
        val state = if (provider == AccountProvider.ANILIST) tracking.aniList else tracking.mal
        val statusOptions = if (provider == AccountProvider.ANILIST) {
            listOf(
                "CURRENT" to "Current",
                "PLANNING" to "Planning",
                "COMPLETED" to "Completed",
                "DROPPED" to "Dropped",
                "PAUSED" to "Paused",
                "REPEATING" to "Repeating"
            )
        } else {
            listOf(
                "watching" to "Watching",
                "completed" to "Completed",
                "on_hold" to "On Hold",
                "dropped" to "Dropped",
                "plan_to_watch" to "Plan to Watch"
            )
        }
        val selectedIndex = statusOptions.indexOfFirst { it.first.equals(state.status, ignoreCase = true) }
        val currentLabel = statusOptions.getOrNull(selectedIndex)?.second
        val dialogItems = buildList {
            statusOptions.forEach { (status, label) ->
                add(
                    ListOrderItem(
                        id = status,
                        title = "Add to $label",
                        selected = status.equals(state.status, ignoreCase = true)
                    )
                )
            }
            if (state.isTracked && currentLabel != null) {
                add(
                    ListOrderItem(
                        id = "__remove__",
                        title = "Remove from $currentLabel",
                        icon = IconGlyphs.DeleteForever
                    )
                )
            }
        }

        ListOrderDialog(
            title = providerName,
            items = dialogItems,
            mode = ListOrderMode.LIST,
            initialSelectedIndex = selectedIndex.coerceAtLeast(0),
            onDismiss = { statusProvider = null },
            onSelect = { _, item ->
                statusProvider = null
                if (item.id == "__remove__") {
                    pendingRemovalProvider = provider
                } else {
                    val label = statusOptions.firstOrNull { it.first == item.id }?.second ?: item.id
                    onTrackingStatusChange(provider, item.id)
                    toast("Saved to $providerName $label")
                }
            }
        )
    }

    pendingRemovalProvider?.let { provider ->
        val providerName = if (provider == AccountProvider.ANILIST) "AniList" else "MAL"
        ConfirmDialog(
            title = "Remove from $providerName",
            message = "Are you sure you want to remove it from $providerName?",
            onConfirm = {
                pendingRemovalProvider = null
                onTrackingStatusChange(provider, null)
                toast("Deleted from $providerName...")
            },
            onDismiss = { pendingRemovalProvider = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AniListInfoboxRow(info: AniListAnimeInfo, textShadow: Shadow?) {
    val tokens = LocalAnimeTvTokens.current
    val items = buildList<@Composable () -> Unit> {
        info.score?.let { score ->
            add {
                Box {
                    Text("★★★★★", color = Color(0xFF666666), fontSize = fsSp(tokens.fs13))
                    Text(
                        text = "★★★★★",
                        color = Color(0xFFFFD76A),
                        fontSize = fsSp(tokens.fs13),
                        modifier = Modifier.drawWithContent {
                            clipRect(right = size.width * (score.coerceIn(0, 100) / 100f)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    )
                }
            }
        }
        if (info.adult) {
            add {
                Text(
                    text = "18+",
                    color = Color(0xFFFF3333),
                    fontSize = fsSp(tokens.fs13),
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
        val fmtYear = listOfNotNull(
            info.format?.replace("TV_SHORT", "TV")?.takeIf { it.isNotBlank() },
            info.year?.toString()
        ).joinToString(" · ")
        if (fmtYear.isNotBlank()) {
            add {
                Text(
                    text = fmtYear,
                    color = Color.White,
                    fontSize = fsSp(tokens.fs13),
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
        info.status?.takeIf { it.isNotBlank() }?.let { st ->
            add {
                Text(
                    text = st.replace('_', ' '),
                    color = Color(0xFF11AA11),
                    fontSize = fsSp(tokens.fs13),
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
        info.durationMinutes?.let { dur ->
            add {
                Text(
                    text = "$dur min",
                    color = Color(0xFF66BBDD),
                    fontSize = fsSp(tokens.fs13),
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
        val airedEp = info.nextAiringEpisode?.minus(1)?.takeIf { it > 0 }
        val episodeText = when {
            airedEp != null && info.episodeCount != null -> "$airedEp of ${info.episodeCount} Episodes"
            airedEp != null -> "$airedEp Episodes"
            info.episodeCount != null -> "${info.episodeCount} Episodes"
            else -> null
        }
        episodeText?.let { epTxt ->
            add {
                Text(
                    text = epTxt,
                    color = Color.White,
                    fontSize = fsSp(tokens.fs13),
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
    }

    if (items.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.u * 0.5f, bottom = tokens.u * 1.5f),
            verticalArrangement = Arrangement.spacedBy(tokens.u * 0.35f)
        ) {
            items.forEachIndexed { index, renderItem ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = tokens.u * 0.7f)
                                .width((tokens.u * 0.1f).coerceAtLeast(1.dp))
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }
                    renderItem()
                }
            }
        }
    }
}

@Composable
private fun AniListMetricsAndDetails(
    info: AniListAnimeInfo,
    otherTitle: String?,
    synonymsValue: String,
    studiosValue: String
) {
    val tokens = LocalAnimeTvTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            AniListKeyValCell(
                icon = IconGlyphs.Star,
                label = "Score",
                value = "${info.score ?: "-"} / 100",
                modifier = Modifier.weight(1f)
            )
            AniListKeyValCell(
                icon = IconGlyphs.Bolt,
                label = "Popularity",
                value = info.popularity?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            AniListKeyValCell(
                icon = IconGlyphs.VolunteerActivism,
                label = "Favorites",
                value = info.favourites?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            AniListKeyValCell(
                icon = IconGlyphs.AvgPace,
                label = "Trend",
                value = info.trending?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
        }
        AniListKeyValCell(
            icon = IconGlyphs.JapaneseKana,
            label = "Other Name",
            value = otherTitle?.takeIf { it.isNotBlank() } ?: "-",
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            AniListKeyValCell(
                icon = IconGlyphs.ListAlt,
                label = "Synonyms",
                value = synonymsValue,
                modifier = Modifier.weight(1f)
            )
            AniListKeyValCell(
                icon = IconGlyphs.Movie,
                label = "Studios",
                value = studiosValue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AniListKeyValCell(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current

    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp),
            color = Color(0xFF222222)
        )
        Column(
            modifier = Modifier.padding(top = tokens.u * 0.5f, bottom = tokens.u * 0.5f, start = tokens.u * 0.5f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    fontFamily = LocalIconFont.current,
                    color = Color(0xFF999999),
                    fontSize = fsSp(tokens.fs12),
                    modifier = Modifier.padding(end = tokens.u * 0.6f)
                )
                Text(
                    text = label,
                    color = Color(0xFF999999),
                    fontSize = fsSp(tokens.fs12),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = colors.accent1,
                fontSize = fsSp(tokens.fs13),
                lineHeight = fsSp(tokens.fs13) * 1.25f,
                modifier = Modifier.padding(horizontal = tokens.u * 1f, vertical = tokens.u * 0.15f)
            )
        }
    }
}

@Composable
private fun AniListReviewsBlock(reviews: List<AniListReview>) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current

    if (reviews.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(tokens.u * 2f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No review available...",
                color = Color(0xFF777777),
                fontSize = fsSp(tokens.fs14)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tokens.u * 0.3f)
        ) {
            Text(
                text = "Reviews",
                color = colors.primary3,
                fontSize = fsSp(tokens.fs17),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = tokens.u * 0.2f)
            )
            reviews.forEach { review ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = tokens.u * 0.8f, bottom = tokens.u * 0.3f),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        modifier = Modifier.padding(end = tokens.u * 0.6f)
                    )
                    Text(
                        text = review.summary,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs13),
                        lineHeight = fsSp(tokens.fs13) * 1.4f,
                        modifier = Modifier.weight(1f)
                    )
                    review.score?.let { score ->
                        Spacer(Modifier.width(tokens.u * 1f))
                        Row(
                            modifier = Modifier
                                .width(tokens.u * 4.4f)
                                .clip(RoundedCornerShape(tokens.u * 0.4f))
                                .background(Color(0xFF008800))
                                .padding(horizontal = tokens.u * 0.3f, vertical = tokens.u * 0.1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = score.toString(),
                                color = Color.White,
                                fontSize = fsSp(tokens.fs13),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/100",
                                color = Color.White,
                                fontSize = fsSp(tokens.fs7),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AniListToolButton(
    text: String,
    icon: String? = null,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        focused -> colors.primary1
        hovered -> colors.primary3
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.u * 0.5f))
            .background(bg)
            .hoverable(interactionSource = interactionSource)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(
                start = if (tokens.portrait) tokens.vh * 0.5f else tokens.vw * 1f,
                end = if (tokens.portrait) tokens.vh * 0.5f else tokens.vw * 1.5f,
                top = tokens.u * 0.25f,
                bottom = tokens.u * 0.25f
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Text(
                text = icon,
                fontFamily = LocalIconFont.current,
                color = Color.White,
                fontSize = fsSp(tokens.fs14)
            )
            Spacer(Modifier.width(tokens.u * 0.4f))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = fsSp(tokens.fs13),
            lineHeight = fsSp(tokens.fs13) * 1.6f,
            maxLines = 1
        )
    }
}

private fun aniListButtonLabel(
    state: ProviderTrackingState,
    loading: Boolean,
    busy: Boolean,
    portrait: Boolean
): String = when {
    loading || busy -> if (portrait) "AniList" else "AniList..."
    state.isTracked -> {
        val status = state.status?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Current"
        if (portrait) status else "AniList $status"
    }
    else -> if (portrait) "AniList" else "Add to AniList"
}

private fun malButtonLabel(
    state: ProviderTrackingState,
    loading: Boolean,
    busy: Boolean,
    portrait: Boolean
): String = when {
    loading || busy -> if (portrait) "MAL" else "MAL..."
    state.isTracked -> {
        val status = state.status?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Watching"
        "MAL $status"
    }
    else -> if (portrait) "MAL" else "Add to MAL"
}
