package com.paregi.animetv.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformTrailer
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvInlineTabs
import com.paregi.animetv.core.ui.AnimeTvShelfPlaceholder
import com.paregi.animetv.core.ui.AnimeTvSpinner
import com.paregi.animetv.core.ui.AnimeTvTitledShelf
import com.paregi.animetv.core.ui.CardDimensions
import com.paregi.animetv.core.ui.FocusCard
import com.paregi.animetv.core.ui.animeTvTextShadow
import com.paregi.animetv.core.ui.calculateCardDimensions
import com.paregi.animetv.core.ui.displayAnimeSynopsis
import com.paregi.animetv.core.ui.displayAnimeTitle
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.core.ui.pbMenuEdgeFadeMask
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.HomeLayout
import com.paregi.animetv.domain.WatchProgress
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformScheduleDays
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun HomeScreen(
    feed: HomeFeed,
    loading: Boolean,
    largeUi: Boolean,
    trailerMode: Int,
    onAnimeSelected: (Anime) -> Unit,
    onContinueWatching: (Anime) -> Unit,
    watchHistory: List<WatchProgress> = emptyList(),
    homeLayout: HomeLayout = HomeLayout.default(),
    onScrollOffsetChange: (Dp) -> Unit = {},
    onRefreshRequested: () -> Unit = {},
    onAboutFooterClick: (() -> Unit)? = null,
    wallpaperUrl: String = "",
    sourceName: String = "Gotaku"
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val density = LocalDensity.current
    val playClickSound = LocalNavigationSound.current
    val maskEnabled = !LocalPerformanceEffects.current.getOrElse(3) { false }

    val featuredCount = feed.featured.size
    var currentSlideIndex by remember(featuredCount) { mutableIntStateOf(0) }
    var featuredFocused by remember { mutableStateOf(false) }
    var lastHeroTouchMs by remember { mutableStateOf(0L) }
    var heroDragOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(featuredCount, featuredFocused) {
        if (featuredCount > 1) {
            while (true) {
                delay(10_000)
                if (!featuredFocused && heroDragOffsetPx == 0f &&
                    System.currentTimeMillis() - lastHeroTouchMs >= 5_000L
                ) {
                    currentSlideIndex = (currentSlideIndex + 1) % featuredCount
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val portrait = tokens.portrait
    val effectiveLargeUi = largeUi && !portrait
    val horizontalInset = if (portrait) tokens.vh * 2f else tokens.vw * 5f
    val shelfSpacing = tokens.u * 1f
    val heroHeight = when {
        portrait -> tokens.vh * 60f
        effectiveLargeUi -> tokens.vw * 55f
        else -> tokens.vw * 40f
    }
    val overlapAmount = if (portrait) tokens.vh * 7.5f else tokens.vw * 7.5f
    val firstShelfOverlap = overlapAmount + shelfSpacing

    // Pull-to-refresh state for portrait / touch screens (#home_pull_refresh).
    val pullEnabled = portrait || tokens.isTouch
    val maxPullPx = with(density) { (tokens.vh * 24f).toPx().coerceAtLeast(120f) }
    val triggerPullPx = with(density) { (tokens.vh * 14f).toPx().coerceAtLeast(80f) }
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    var isRefreshingAnim by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshingAnim) {
        if (isRefreshingAnim) {
            delay(1300)
            isRefreshingAnim = false
        }
    }

    val pullNestedScroll = remember(pullEnabled, maxPullPx, triggerPullPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!pullEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                if (pullDistancePx > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullDistancePx)
                    pullDistancePx = (pullDistancePx + consumedY).coerceAtLeast(0f)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!pullEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f && !isRefreshingAnim) {
                    pullDistancePx = (pullDistancePx + available.y * 0.6f).coerceIn(0f, maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullEnabled && pullDistancePx > 0f) {
                    if (pullDistancePx >= triggerPullPx && !isRefreshingAnim) {
                        isRefreshingAnim = true
                        onRefreshRequested()
                    }
                    pullDistancePx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val estimatedHeroPx = with(density) { heroHeight.toPx() }
    val scrollOffsetDp by remember(estimatedHeroPx, featuredCount) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            val off = listState.firstVisibleItemScrollOffset.toFloat()
            val px = if (idx == 0) off else (if (featuredCount > 0) estimatedHeroPx else 0f) + off + idx * 120f
            with(density) { px.toDp() }
        }
    }
    LaunchedEffect(scrollOffsetDp) {
        onScrollOffsetChange(scrollOffsetDp)
    }

    val alYearLabel = remember(feed.sections) {
        val fromFeed = feed.sections.firstOrNull { it.id == "alyear" }?.title
            ?.let { Regex("\\d{4}").find(it)?.value }
        fromFeed ?: currentYearFallback()
    }
    var alTabSelected by remember { mutableStateOf("altop") }

    @Suppress("UNUSED_VARIABLE")
    val unusedWatchHistoryRef = watchHistory
    @Suppress("UNUSED_VARIABLE")
    val unusedContinueRef = onContinueWatching

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (pullEnabled) Modifier.nestedScroll(pullNestedScroll) else Modifier)
    ) {
        val cardDims = calculateCardDimensions(maxWidth, maxHeight, portrait, largeUi)
        val screenWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val enabledHomeSections = remember(homeLayout.sections) {
            homeLayout.sections.filter { it.enabled && it.id != "continue_watching" }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(shelfSpacing)
        ) {
            if (featuredCount > 0) {
                item(key = "home_featured") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .onPreviewKeyEvent { event ->
                                if (featuredCount > 1 && event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            playClickSound()
                                            lastHeroTouchMs = System.currentTimeMillis()
                                            currentSlideIndex = (currentSlideIndex - 1 + featuredCount) % featuredCount
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            playClickSound()
                                            lastHeroTouchMs = System.currentTimeMillis()
                                            currentSlideIndex = (currentSlideIndex + 1) % featuredCount
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .pointerInput(featuredCount, screenWidthPx) {
                                if (featuredCount <= 1) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = { heroDragOffsetPx = 0f },
                                    onDragCancel = {
                                        heroDragOffsetPx = 0f
                                        lastHeroTouchMs = System.currentTimeMillis()
                                    },
                                    onDragEnd = {
                                        if (abs(heroDragOffsetPx) > screenWidthPx * 0.15f) {
                                            val delta = if (heroDragOffsetPx < 0f) 1 else -1
                                            currentSlideIndex = (currentSlideIndex + delta + featuredCount) % featuredCount
                                        }
                                        heroDragOffsetPx = 0f
                                        lastHeroTouchMs = System.currentTimeMillis()
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        heroDragOffsetPx += dragAmount
                                    }
                                )
                            }
                    ) {
                        val safeIndex = currentSlideIndex.coerceIn(0, featuredCount - 1)
                        Crossfade(
                            targetState = safeIndex,
                            animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing),
                            label = "hero_crossfade",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = heroDragOffsetPx * 0.35f }
                        ) { pageIndex ->
                            val anime = feed.featured[pageIndex]
                            FeaturedHero(
                                anime = anime,
                                trailerMode = trailerMode,
                                portrait = portrait,
                                heroHeight = heroHeight,
                                isActivePage = pageIndex == safeIndex,
                                wallpaperActive = wallpaperUrl.isNotBlank(),
                                onFocusChanged = { featuredFocused = it },
                                onClick = { onAnimeSelected(anime) }
                            )
                        }

                        if (featuredCount > 1) {
                            val pagerModifier = if (portrait) {
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = tokens.vh * 50f, end = tokens.vw * 5f)
                            } else {
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = tokens.vw * 5f, bottom = tokens.vw * 10f)
                            }
                            Row(
                                modifier = pagerModifier.padding(tokens.u * 0.3f),
                                horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(featuredCount) { index ->
                                    val isSelected = index == safeIndex
                                    val dotWidth by animateDpAsState(
                                        targetValue = if (isSelected) tokens.u * 2.5f else tokens.u * 0.7f,
                                        animationSpec = tween(durationMillis = tokens.tim2s, easing = LinearEasing)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(dotWidth)
                                            .height(tokens.u * 0.7f)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = if (isSelected) 1f else 0.5f))
                                            .clickable {
                                                lastHeroTouchMs = System.currentTimeMillis()
                                                currentSlideIndex = index
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (loading) {
                item(key = "home_featured_loader") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimeTvSpinner(
                            size = tokens.u * 8f,
                            strokeWidth = tokens.u * 0.8f,
                            color = colors.primary2
                        )
                    }
                }
            }

            var firstShelfAfterFeatured = true
            enabledHomeSections.forEach { pref ->
                val shelfOverlap = if (featuredCount > 0 && firstShelfAfterFeatured) firstShelfOverlap else 0.dp
                if (pref.id == "altab") {
                    val tabItems = feed.sections.firstOrNull { it.id == alTabSelected }?.items.orEmpty()
                    item(key = "home_shelf_altab") {
                        Box(modifier = Modifier.zIndex(if (shelfOverlap > 0.dp) 2f else 1f).overlapTopBy(shelfOverlap)) {
                            HomeTabbedAniListShelf(
                                yearLabel = alYearLabel,
                                selectedTabId = alTabSelected,
                                onSelectTab = { alTabSelected = it },
                                items = tabItems,
                                loading = loading,
                                horizontalInset = horizontalInset,
                                cardDims = cardDims,
                                largeUi = effectiveLargeUi,
                                maskEnabled = maskEnabled,
                                onAnimeSelected = onAnimeSelected
                            )
                        }
                    }
                    firstShelfAfterFeatured = false
                } else {
                    val shelf = feed.sections.firstOrNull { it.id == pref.id }
                    val title = shelf?.title ?: homeSectionTitle(pref.id, sourceName)
                    val items = shelf?.items.orEmpty()
                    item(key = "home_shelf_${pref.id}") {
                        Box(modifier = Modifier.zIndex(if (shelfOverlap > 0.dp) 2f else 1f).overlapTopBy(shelfOverlap)) {
                            HomeStandardShelf(
                                title = title,
                                animes = items,
                                loading = loading,
                                horizontalInset = horizontalInset,
                                cardDims = cardDims,
                                largeUi = effectiveLargeUi,
                                maskEnabled = maskEnabled,
                                onAnimeSelected = onAnimeSelected
                            )
                        }
                    }
                    firstShelfAfterFeatured = false
                }
            }

            item(key = "home_about") {
                val resolvedSource = sourceName.ifBlank { "Gotaku" }
                val aboutShadow = animeTvTextShadow(blurMultiplier = 0.2f, offsetMultiplier = 0.2f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onAboutFooterClick != null) {
                                Modifier.clickable(onClick = onAboutFooterClick)
                            } else Modifier
                        )
                        .padding(
                            top = tokens.u * 0.5f,
                            bottom = if (portrait) tokens.sysNavHeight + tokens.vh * 3f else tokens.vw * 2f
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AnimeTV 6.6.7 © 2023-2024 amarullz.com",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = fsSp(tokens.fs12),
                        fontWeight = FontWeight.Normal,
                        style = LocalTextStyle.current.copy(shadow = aboutShadow),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Build 666 - Source $resolvedSource",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = fsSp(tokens.fs10),
                        style = LocalTextStyle.current.copy(shadow = aboutShadow),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (pullEnabled && (pullDistancePx > 0f || isRefreshingAnim)) {
            PullRefreshIndicator(
                pullDistancePx = pullDistancePx,
                maxPullPx = maxPullPx,
                triggerPullPx = triggerPullPx,
                refreshing = isRefreshingAnim,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun PullRefreshIndicator(
    pullDistancePx: Float,
    maxPullPx: Float,
    triggerPullPx: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val density = LocalDensity.current

    val spinTransition = rememberInfiniteTransition(label = "pull_spin")
    val spinDeg by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "pull_deg"
    )

    val minOffsetPx = with(density) { (tokens.vh * 10f).toPx() }
    val restingOffsetPx = with(density) { (tokens.vh * 10f + tokens.sysStatHeight).toPx() }
    val progress = (pullDistancePx / maxPullPx).coerceIn(0f, 1f)
    val yOffsetPx = if (refreshing) restingOffsetPx else (pullDistancePx - minOffsetPx)
    val rotation = if (refreshing) spinDeg else (progress * 360f - 360f)
    val alpha = if (refreshing) 1f else progress
    val reached = pullDistancePx >= triggerPullPx

    val circleSize = tokens.fs25 + tokens.fs10 * 2f
    Box(
        modifier = modifier
            .offset { IntOffset(0, yOffsetPx.roundToInt()) }
            .size(circleSize)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = rotation
            }
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (refreshing) IconGlyphs.ProgressActivity else IconGlyphs.Refresh,
            fontFamily = LocalIconFont.current,
            color = if (refreshing || reached) colors.accent1 else Color.Black,
            fontSize = fsSp(tokens.fs25)
        )
    }
}

private fun Modifier.overlapTopBy(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overlapPx = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - overlapPx) {
        placeable.place(0, -overlapPx)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturedHero(
    anime: Anime,
    trailerMode: Int,
    portrait: Boolean,
    heroHeight: Dp,
    isActivePage: Boolean,
    wallpaperActive: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val performanceEffects = LocalPerformanceEffects.current
    val maskEnabled = !performanceEffects.getOrElse(3) { false }
    val textShadowDisabled = performanceEffects.getOrElse(4) { false }
    val density = LocalDensity.current

    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val heroActive = focused || hovered

    var mountTrailer by remember { mutableStateOf(false) }
    var ytPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(focused, anime.id, anime.trailerId, trailerMode, isActivePage) {
        ytPlaying = false
        mountTrailer = false
        if (trailerMode > 0 && !anime.trailerId.isNullOrEmpty() && isActivePage && focused) {
            delay(600)
            mountTrailer = true
        }
    }

    val imageUrl = anime.bannerUrl ?: anime.posterUrl
    var imageLoaded by remember(imageUrl) { mutableStateOf(false) }
    val imageLoadAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = if (imageLoaded) 600 else 0, easing = LinearEasing)
    )
    val trailerCrossfadeAlpha by animateFloatAsState(
        targetValue = if (ytPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim10s, easing = tokens.easing)
    )

    // Portrait hero horizontal pan (30% -> 90% over 30s).
    var panTarget by remember(anime.id, isActivePage, portrait) { mutableFloatStateOf(-0.4f) }
    LaunchedEffect(anime.id, isActivePage, portrait) {
        if (portrait && isActivePage) {
            panTarget = -0.4f
            panTarget = 0.8f
        } else {
            panTarget = -0.4f
        }
    }
    val panBias by animateFloatAsState(
        targetValue = if (portrait) panTarget else 0f,
        animationSpec = tween(
            durationMillis = if (portrait && isActivePage) 30_000 else 300,
            easing = LinearEasing
        )
    )

    val useAlphaMask = wallpaperActive && maskEnabled

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(Color.Black)
            .hoverable(interactionSource)
            .navigationClickable(onClick = onClick)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .navigationFocusable()
    ) {
        val visualModifier = if (useAlphaMask) {
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            0.0f to Color.Transparent,
                            0.5f to Color.Black,
                            1.0f to Color.Black,
                            start = Offset(0f, size.height * 0.54f),
                            end = Offset(size.width, size.height * 0.45f)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black,
                            0.90f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = visualModifier) {
            AsyncImage(
                model = imageCdnUrl(imageUrl, LocalImageCdn.current),
                contentDescription = displayAnimeTitle(anime, LocalJapaneseTitles.current),
                contentScale = ContentScale.Crop,
                alignment = if (portrait) BiasAlignment(panBias, 0f) else Alignment.Center,
                onSuccess = { imageLoaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = imageLoadAlpha * (1f - trailerCrossfadeAlpha)
                    }
            )
            if (mountTrailer && !anime.trailerId.isNullOrEmpty()) {
                PlatformTrailer(
                    youtubeId = anime.trailerId,
                    muted = trailerMode == 1,
                    onPlaybackState = { state ->
                        when (state) {
                            "yt-play" -> ytPlaying = true
                            "yt-pause", "yt-end" -> {
                                ytPlaying = false
                                mountTrailer = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = trailerCrossfadeAlpha }
                )
            }
        }

        if (!useAlphaMask) {
            val leftFadeEndPx = with(density) { (tokens.vw * 40f).toPx() }
            val heroHeightPx = with(density) { heroHeight.toPx() }
            val bottomFadeStartPx = (heroHeightPx - with(density) { (tokens.vw * 20f).toPx() }).coerceAtLeast(0f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent),
                            endX = leftFadeEndPx
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black),
                            startY = bottomFadeStartPx,
                            endY = heroHeightPx
                        )
                    )
            )
        }

        val infoWidth = tokens.vw * (if (portrait) 80f else 55f)
        val titleColor by animateColorAsState(
            targetValue = if (heroActive) Color.White else Color.White.copy(alpha = if (portrait) 0.95f else 0.85f),
            animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
        )
        val descColor by animateColorAsState(
            targetValue = if (heroActive) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = if (portrait) 0.8f else 0.7f),
            animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
        )
        val titleShadow = if (!textShadowDisabled) {
            val offsetPx = with(density) { (tokens.u * 0.2f).toPx() }
            Shadow(Color.Black, Offset(0f, offsetPx), offsetPx)
        } else null
        val descShadow = if (!textShadowDisabled) {
            val offsetPx = with(density) { (tokens.u * 0.1f).toPx() }
            val blurPx = with(density) { (tokens.u * 0.2f).toPx() }
            Shadow(Color.Black, Offset(offsetPx, offsetPx), blurPx)
        } else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = tokens.vw * 5f, top = tokens.u * 10f, bottom = tokens.u * 12f),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.width(infoWidth)) {
                Text(
                    text = displayAnimeTitle(anime, LocalJapaneseTitles.current),
                    color = titleColor,
                    fontSize = fsSp(tokens.fs30),
                    lineHeight = fsSp(tokens.fs30) * 1.2f,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = titleShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                anime.synopsis?.takeIf { it.isNotBlank() }?.let { rawSynopsis ->
                    Text(
                        text = displayAnimeSynopsis(rawSynopsis),
                        color = descColor,
                        fontSize = fsSp(tokens.fs15),
                        lineHeight = fsSp(tokens.fs15) * 1.3f,
                        style = LocalTextStyle.current.copy(shadow = descShadow),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.u * 0.8f)
                    )
                }

                val metaItems = buildHeroMetaItems(anime)
                Column(modifier = Modifier.padding(top = tokens.u * 1.5f)) {
                    if (portrait) {
                        HeroWatchNowButton(active = heroActive)
                        if (metaItems.isNotEmpty() || (anime.score != null && anime.score > 0f)) {
                            Spacer(Modifier.height(tokens.vh * 0.5f))
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(tokens.vh * 0.3f),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                HeroMetaContent(anime = anime, metaItems = metaItems, textShadowDisabled = textShadowDisabled)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroWatchNowButton(active = heroActive)
                            Spacer(Modifier.width(tokens.vw * 1.5f))
                            HeroMetaContent(anime = anime, metaItems = metaItems, textShadowDisabled = textShadowDisabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroWatchNowButton(active: Boolean) {
    val tokens = LocalAnimeTvTokens.current
    val bgColor by animateColorAsState(
        targetValue = if (active) Color(0xFF006600) else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )
    val borderColor = if (active) {
        val pulse = rememberInfiniteTransition(label = "home_play_ani")
        val animatedBorder by pulse.animateColor(
            initialValue = Color(0xFF004400),
            targetValue = Color.White,
            animationSpec = infiniteRepeatable(tween(700, easing = tokens.easing), RepeatMode.Reverse),
            label = "home_play_border"
        )
        animatedBorder
    } else {
        Color.Transparent
    }
    val shape = RoundedCornerShape(tokens.u * 3f)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border((tokens.u * 0.2f).coerceAtLeast(1.dp), borderColor, shape)
            .padding(horizontal = tokens.u * 1.4f, vertical = tokens.u * 0.3f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Watch Now ▶",
            color = Color.White,
            fontSize = fsSp(tokens.fs13),
            lineHeight = fsSp(tokens.fs13) * 1.3f,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private data class HeroMetaText(val text: String, val color: Color)

private fun buildHeroMetaItems(anime: Anime): List<HeroMetaText> = buildList {
    if (anime.adult) {
        add(HeroMetaText("R+", Color(0xFFFF3333)))
    }
    val fmt = anime.format
        ?.replace("TV_SHORT", "TV", ignoreCase = true)
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    val typeYear = listOfNotNull(fmt, anime.year?.toString()).joinToString(" · ")
    if (typeYear.isNotBlank()) {
        add(HeroMetaText(typeYear, Color.White.copy(alpha = 0.7f)))
    }
    anime.duration?.let { dur ->
        val label = Regex("\\d+").find(dur)?.value?.let { "$it min" } ?: dur
        add(HeroMetaText(label, Color(0xFF66BBDD)))
    }
    anime.episodeCount?.takeIf { it > 0 }?.let { eps ->
        add(HeroMetaText("$eps Episodes", Color.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun HeroMetaContent(
    anime: Anime,
    metaItems: List<HeroMetaText>,
    textShadowDisabled: Boolean
) {
    val tokens = LocalAnimeTvTokens.current
    val density = LocalDensity.current
    val metaShadow = if (!textShadowDisabled) {
        val offPx = with(density) { (tokens.u * 0.1f).toPx() }
        val blurPx = with(density) { (tokens.u * 0.2f).toPx() }
        Shadow(Color.Black.copy(alpha = 0.8f), Offset(offPx, offPx), blurPx)
    } else null

    var hasPreceding = false
    val score = anime.score?.takeIf { it > 0f }
    if (score != null) {
        val fraction = (if (score <= 10f) score / 10f else score / 100f).coerceIn(0f, 1f)
        Box(modifier = Modifier.padding(end = tokens.u * 0.8f)) {
            Text(
                text = "⭐⭐⭐⭐⭐",
                fontSize = fsSp(tokens.fs7),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = 0.25f }
            )
            Text(
                text = "⭐⭐⭐⭐⭐",
                fontSize = fsSp(tokens.fs7),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                }
            )
        }
        hasPreceding = true
    }

    metaItems.forEach { item ->
        if (hasPreceding) {
            Box(
                modifier = Modifier
                    .width((tokens.u * 0.1f).coerceAtLeast(1.dp))
                    .height(tokens.fs13)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        Text(
            text = item.text,
            color = item.color,
            fontSize = fsSp(tokens.fs13),
            lineHeight = fsSp(tokens.fs13) * 1.1f,
            style = LocalTextStyle.current.copy(shadow = metaShadow),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = tokens.u * 0.8f)
        )
        hasPreceding = true
    }
}

@Composable
private fun HomeStandardShelf(
    title: String,
    animes: List<Anime>,
    loading: Boolean,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onAnimeSelected: (Anime) -> Unit
) {
    val rowState = rememberLazyListState()
    AnimeTvTitledShelf(
        title = title,
        horizontalInset = horizontalInset,
        empty = animes.isEmpty(),
        loading = loading,
        emptyText = "No data...",
        cardHeight = cardDims.cardHeight,
        cardGap = cardDims.cardGap,
        rowState = rowState,
        maskEnabled = maskEnabled
    ) {
        itemsIndexed(animes, key = { idx, anime -> "${idx}:${anime.id}" }) { _, anime ->
            FocusCard(
                anime = anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onAnimeSelected(anime) },
                largeUi = largeUi
            )
        }
    }
}

@Composable
private fun HomeTabbedAniListShelf(
    yearLabel: String,
    selectedTabId: String,
    onSelectTab: (String) -> Unit,
    items: List<Anime>,
    loading: Boolean,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onAnimeSelected: (Anime) -> Unit
) {
    val rowState = rememberLazyListState()
    var contentActive by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTabId) {
        rowState.scrollToItem(0)
    }
    val tabs = remember(yearLabel) {
        listOf(
            "altop" to "Top Anime",
            "alpopular" to "All Time Popular",
            "alyear" to "Top $yearLabel",
            "alupcoming" to "Upcoming"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (contentActive) 2f else 1f)
    ) {
        AnimeTvInlineTabs(
            title = "AniList",
            tabs = tabs,
            selectedTabId = selectedTabId,
            contentActive = contentActive,
            horizontalInset = horizontalInset,
            onTabSelected = onSelectTab
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { contentActive = it.hasFocus }
        ) {
            if (items.isEmpty()) {
                AnimeTvShelfPlaceholder(
                    loading = loading,
                    emptyText = "No data...",
                    cardHeight = cardDims.cardHeight
                )
            } else {
                LazyRow(
                    state = rowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pbMenuEdgeFadeMask(rowState, enabled = maskEnabled),
                    contentPadding = PaddingValues(horizontal = horizontalInset),
                    horizontalArrangement = Arrangement.spacedBy(cardDims.cardGap)
                ) {
                    itemsIndexed(items, key = { idx, anime -> "${selectedTabId}:${idx}:${anime.id}" }) { _, anime ->
                        FocusCard(
                            anime = anime,
                            cardWidth = cardDims.cardWidth,
                            cardHeight = cardDims.cardHeight,
                            posterWidth = cardDims.posterWidth,
                            posterHeight = cardDims.posterHeight,
                            cardTitleSize = cardDims.titleSize,
                            cardTitleLineHeight = cardDims.titleLineHeight,
                            onClick = { onAnimeSelected(anime) },
                            largeUi = largeUi
                        )
                    }
                }
            }
        }
    }
}

private fun currentYearFallback(): String {
    val firstDate = platformScheduleDays().firstOrNull()?.dateLabel.orEmpty()
    val match = Regex("\\d{4}").find(firstDate)?.value
    return match ?: "Year"
}
