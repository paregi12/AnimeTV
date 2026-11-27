package com.paregi.animetv.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.paregi.animetv.core.theme.BadgeThemeColors
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.displayRPlusRating
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformImagePainter

@Immutable
data class CardStylePreferences(
    val cardSize: Int = 0,
    val cardRadius: Int = 0,
    val badgeStyle: Int = 0,
    val badgeColors: BadgeThemeColors = BadgeThemeColors.Default
)

internal val LocalCardStyle = compositionLocalOf { CardStylePreferences() }

@Immutable
internal data class CardDimensions(
    val cardWidth: Dp,
    val cardHeight: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,
    val cardGap: Dp,
    val titleSize: TextUnit,
    val titleLineHeight: TextUnit
)

@Composable
internal fun calculateCardDimensions(
    maxWidth: Dp,
    maxHeight: Dp,
    portrait: Boolean,
    largeUi: Boolean = false
): CardDimensions {
    val tokens = LocalAnimeTvTokens.current
    val effectiveLargeUi = largeUi && !portrait
    val u = if (portrait) maxHeight / 100f else maxWidth / 100f
    val cardWidth = if (portrait || effectiveLargeUi) u * 16f else u * 12f
    val posterWidth = if (portrait || effectiveLargeUi) u * 15.2f else u * 11.2f
    val posterHeight = if (portrait || effectiveLargeUi) u * 21f else u * 16f
    val cardHeight = tokens.fs20 + (if (portrait || effectiveLargeUi) u * 23.6f else u * 18.6f)
    val titleSize = if (portrait) fsSp(tokens.fs14) else fsSp(tokens.fs12)
    val titleLineHeight = fsSp(tokens.fs15)

    return CardDimensions(
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        posterWidth = posterWidth,
        posterHeight = posterHeight,
        cardGap = 0.dp,
        titleSize = titleSize,
        titleLineHeight = titleLineHeight
    )
}

@Immutable
private data class BadgeSegment(
    val text: String,
    val background: Color,
    val icon: String? = null,
    val iconTint: Color = Color.White,
    val textColor: Color = Color.White,
    val bold: Boolean = true
)

@Composable
internal fun FocusCard(
    anime: Anime,
    cardWidth: Dp,
    cardHeight: Dp,
    posterWidth: Dp,
    posterHeight: Dp,
    cardTitleSize: TextUnit,
    cardTitleLineHeight: TextUnit = cardTitleSize * 1.22f,
    onClick: () -> Unit,
    progressPercent: Float? = null,
    progressEpisode: String? = null,
    posterCornerLabel: String? = null,
    posterBottomLabel: String? = null,
    posterBottomIcon: String? = null,
    posterBottomAired: Boolean = false,
    largeUi: Boolean = false,
    useScheduleBadges: Boolean = false,
    isAniListCard: Boolean = false,
    badgeMode: CardBadgeMode? = null,
    showBottomBadges: Boolean = false,
    showPlayingRibbon: Boolean = false,
    onFocused: (() -> Unit)? = null
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val performanceEffects = LocalPerformanceEffects.current
    val cardStyle = LocalCardStyle.current
    val density = LocalDensity.current
    val platformContext = LocalPlatformContext.current
    val imageCdn = LocalImageCdn.current
    val japaneseTitles = LocalJapaneseTitles.current

    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val isActive = focused || hovered

    val u = tokens.u
    val isLargeLandscape = !tokens.portrait && (largeUi || cardWidth > tokens.vw * 14f)
    val scaleEnabled = !performanceEffects.getOrElse(1) { false }
    val shadowsDisabled = performanceEffects.getOrElse(2) { true }
    val textShadowDisabled = performanceEffects.getOrElse(4) { false }

    val animScaleState = animateFloatAsState(
        targetValue = if (focused && scaleEnabled) {
            if (isLargeLandscape) 1.03f else 1.06f
        } else 1f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )

    val effectiveBadgeMode = badgeMode ?: when {
        useScheduleBadges -> CardBadgeMode.Schedule
        isAniListCard -> CardBadgeMode.AniListMyList
        else -> CardBadgeMode.Catalog
    }
    val effectiveShowBottomBadges =
        effectiveBadgeMode == CardBadgeMode.Catalog && showBottomBadges && anime.id.startsWith("anilist:")

    val badges = remember(
        anime,
        progressPercent,
        progressEpisode,
        posterBottomAired,
        posterBottomLabel,
        effectiveBadgeMode,
        effectiveShowBottomBadges
    ) {
        resolveCardBadges(
            anime = anime,
            progressPercent = progressPercent,
            progressEpisode = progressEpisode,
            airingAired = posterBottomAired,
            airingTime = posterBottomLabel,
            mode = effectiveBadgeMode,
            showBottomBadges = effectiveShowBottomBadges
        )
    }

    val posterShape = remember(u) { RoundedCornerShape((u * 0.3f).coerceAtLeast(1.dp)) }
    val backplateShape = remember(u) { RoundedCornerShape((u * 0.5f).coerceAtLeast(1.dp)) }
    val badgeFontSize = fsSp(tokens.fs8)
    val badgeLineHeight = badgeFontSize * 1.4f
    val badgeCornerRadius = (u * 0.3f).coerceAtLeast(1.dp)
    val badgeDividerWidth = (u * 0.1f).coerceAtLeast(0.5.dp)
    val badgeHorizontalPadding = u * 0.3f
    val badgeVerticalPadding = (u * 0.06f).coerceAtLeast(0.5.dp)

    val posterInsetX = ((cardWidth - posterWidth) / 2f).coerceAtLeast(0.dp)
    val posterInsetY = u * 0.5f
    val titleTop = posterInsetY + posterHeight + u * 0.3f
    val titleHeight = (cardHeight - titleTop).coerceAtLeast(0.dp)

    val displayTitle = remember(anime.title, anime.originalTitle, japaneseTitles) {
        displayAnimeTitle(anime, japaneseTitles)
    }
    val posterUrl = remember(anime.posterUrl, imageCdn) {
        imageCdnUrl(anime.posterUrl, imageCdn)
    }
    val posterWidthPx = with(density) { posterWidth.roundToPx().coerceAtLeast(1) }
    val posterHeightPx = with(density) { posterHeight.roundToPx().coerceAtLeast(1) }
    val posterRequest = remember(platformContext, posterUrl, posterWidthPx, posterHeightPx) {
        posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(platformContext)
                .data(url)
                .size(posterWidthPx, posterHeightPx)
                .precision(Precision.INEXACT)
                .build()
        }
    }
    var showFallback by remember(posterUrl) { mutableStateOf(posterUrl.isNullOrBlank()) }

    val glassMode = cardStyle.badgeStyle != 0
    val topSegments = remember(
        badges,
        posterCornerLabel,
        effectiveBadgeMode,
        glassMode,
        colors.primaryBg,
        colors.primary1,
        colors.primary2
    ) {
        buildTopBadgeSegments(
            badges = badges,
            posterCornerLabel = posterCornerLabel,
            badgeMode = effectiveBadgeMode,
            glassMode = glassMode,
            primaryBg = colors.primaryBg,
            primary1 = colors.primary1,
            primary2 = colors.primary2
        )
    }
    val bottomSegments = remember(
        badges,
        posterBottomLabel,
        posterBottomIcon,
        posterBottomAired,
        effectiveBadgeMode,
        effectiveShowBottomBadges,
        glassMode,
        colors.primaryBg,
        colors.primary2
    ) {
        buildBottomBadgeSegments(
            badges = badges,
            posterBottomLabel = posterBottomLabel,
            posterBottomIcon = posterBottomIcon,
            posterBottomAired = posterBottomAired,
            badgeMode = effectiveBadgeMode,
            showBottomBadges = effectiveShowBottomBadges,
            glassMode = glassMode,
            primaryBg = colors.primaryBg,
            primary2 = colors.primary2
        )
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .zIndex(if (isActive) 2f else 0f)
            .graphicsLayer {
                val s = animScaleState.value
                scaleX = s
                scaleY = s
            }
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused?.invoke()
            }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
    ) {
        if (isActive) {
            val backplateWidth = if (scaleEnabled) cardWidth else cardWidth + u * 0.2f
            val backplateHeight = if (scaleEnabled) posterHeight + u * 0.8f else posterHeight + u * 1.0f
            val backplateOffsetX = if (scaleEnabled) 0.dp else -(u * 0.1f)
            val backplateOffsetY = if (scaleEnabled) u * 0.1f else 0.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = backplateOffsetX, y = backplateOffsetY)
                    .width(backplateWidth)
                    .height(backplateHeight)
                    .then(
                        if (!shadowsDisabled) {
                            Modifier.shadow(
                                elevation = u * 1f,
                                shape = backplateShape,
                                clip = false,
                                ambientColor = colors.accent1,
                                spotColor = colors.accent1
                            )
                        } else Modifier
                    )
                    .background(Color.White, backplateShape)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = posterInsetX, y = posterInsetY)
                .width(posterWidth)
                .height(posterHeight)
                .clip(posterShape)
                .background(Color.Black)
        ) {
            if (posterRequest != null && !showFallback) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    onSuccess = { showFallback = false },
                    onError = { showFallback = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (showFallback) {
                Image(
                    painter = platformImagePainter("noimg"),
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (topSegments.isNotEmpty()) {
                SegmentedBadgePill(
                    segments = topSegments,
                    glassMode = glassMode,
                    fontSize = badgeFontSize,
                    lineHeight = badgeLineHeight,
                    cornerRadius = badgeCornerRadius,
                    dividerWidth = badgeDividerWidth,
                    horizontalPadding = badgeHorizontalPadding,
                    verticalPadding = badgeVerticalPadding,
                    iconGap = (u * 0.1f).coerceAtLeast(0.5.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = u * 0.7f, start = u * 0.6f, end = u * 0.6f)
                )
            }

            if (effectiveBadgeMode == CardBadgeMode.AniListMyList && badges.hasNextAired) {
                val borderWidth = (u * 0.15f).coerceAtLeast(1.dp)
                val nextPillShape = remember(u) {
                    RoundedCornerShape(topEnd = u * 0.5f, bottomEnd = u * 0.5f)
                }
                val nextPillTop = posterHeight - (if (isLargeLandscape || tokens.portrait) u * 3.5f else u * 2.5f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = nextPillTop.coerceAtLeast(0.dp))
                        .width(u * 3f)
                        .height(u * 2f)
                        .clip(nextPillShape)
                        .background(colors.dimm1)
                        .drawBehind {
                            val strokePx = borderWidth.toPx()
                            val half = strokePx / 2f
                            drawLine(colors.accent1, Offset(0f, half), Offset(size.width, half), strokePx)
                            drawLine(colors.accent1, Offset(size.width - half, 0f), Offset(size.width - half, size.height), strokePx)
                            drawLine(colors.accent1, Offset(0f, size.height - half), Offset(size.width, size.height - half), strokePx)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2728",
                        color = Color.White,
                        fontSize = badgeFontSize,
                        maxLines = 1
                    )
                }
            }

            if (bottomSegments.isNotEmpty()) {
                val bottomInset = if (isLargeLandscape || tokens.portrait) u * 0.5f else u * 0.3f
                SegmentedBadgePill(
                    segments = bottomSegments,
                    glassMode = glassMode,
                    fontSize = badgeFontSize,
                    lineHeight = badgeLineHeight,
                    cornerRadius = badgeCornerRadius,
                    dividerWidth = badgeDividerWidth,
                    horizontalPadding = badgeHorizontalPadding,
                    verticalPadding = badgeVerticalPadding,
                    iconGap = (u * 0.1f).coerceAtLeast(0.5.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = u * 0.6f, end = u * 0.6f, bottom = bottomInset)
                )
            }

            if (showPlayingRibbon) {
                val ribbonShape = remember(u) { RoundedCornerShape(u * 0.4f) }
                val animEnabled = tokens.tim10s > 0
                val playingPulse = if (animEnabled) {
                    val playingTransition = rememberInfiniteTransition(label = "playing-blink")
                    playingTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = (tokens.tim10s / 2).coerceAtLeast(300),
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "playing-color"
                    )
                } else null
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = u * 13f)
                        .padding(horizontal = u * 1f)
                        .fillMaxWidth()
                        .height(u * 3f)
                        .clip(ribbonShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PLAYING",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs10),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (playingPulse != null) {
                        Text(
                            text = "PLAYING",
                            color = Color(0xFF33FF33),
                            fontSize = fsSp(tokens.fs10),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = playingPulse.value }
                        )
                    }
                }
            }

            badges.progressPercent?.let { pct ->
                val trackShape = remember(u) { RoundedCornerShape((u * 0.2f).coerceAtLeast(1.dp)) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = u * 0.2f)
                        .fillMaxWidth()
                        .height((u * 0.3f).coerceAtLeast(2.dp))
                        .clip(trackShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.accent1)
                    )
                }
            }
        }

        val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
        val shadow = remember(textShadowDisabled, u, density) {
            if (!textShadowDisabled) {
                val blurPx = with(density) { (u * 0.2f).toPx() }
                val offsetPx = with(density) { (u * 0.1f).toPx() }
                Shadow(Color.Black, Offset(0f, offsetPx), blurPx)
            } else null
        }
        Text(
            text = displayTitle,
            color = textColor,
            fontSize = cardTitleSize,
            fontWeight = FontWeight.Normal,
            lineHeight = cardTitleLineHeight,
            style = LocalTextStyle.current.copy(shadow = shadow),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = titleTop)
                .fillMaxWidth()
                .height(titleHeight)
                .padding(horizontal = u * 0.3f)
        )
    }
}

private fun buildTopBadgeSegments(
    badges: CardBadgeData,
    posterCornerLabel: String?,
    badgeMode: CardBadgeMode,
    glassMode: Boolean,
    primaryBg: Color,
    primary1: Color,
    primary2: Color
): List<BadgeSegment> {
    if (!posterCornerLabel.isNullOrBlank()) {
        return listOf(
            BadgeSegment(
                text = posterCornerLabel,
                background = if (glassMode) Color.Black.copy(alpha = 0.85f) else primaryBg
            )
        )
    }
    return buildList {
        if (badgeMode == CardBadgeMode.Schedule || badgeMode == CardBadgeMode.AniListMyList) {
            displayRPlusRating(badges.rating, badges.isAdult)?.let { rPlus ->
                add(
                    BadgeSegment(
                        text = rPlus,
                        background = Color(0xFF880000),
                        textColor = if (glassMode) Color(0xFFEF4444) else Color.White
                    )
                )
            }
        }
        badges.format?.takeIf { it.isNotBlank() }?.let { fmt ->
            add(BadgeSegment(text = fmt, background = primaryBg, bold = false))
        }
        when (badgeMode) {
            CardBadgeMode.Schedule -> {
                badges.progressEpisode?.takeIf { it.isNotBlank() }?.let { ep ->
                    add(BadgeSegment(text = ep, background = primary1, icon = IconGlyphs.Movie))
                }
                badges.totalEpisodes?.let { total ->
                    add(BadgeSegment(text = "$total", background = primary2, icon = IconGlyphs.Bookmark))
                }
            }
            CardBadgeMode.AniListMyList -> {
                val aired = badges.airedEpisode
                val total = badges.totalEpisodes
                if (aired != null && aired > 0) {
                    add(BadgeSegment(text = "$aired", background = primary2, icon = IconGlyphs.Movie))
                } else if (total != null && total > 0) {
                    add(BadgeSegment(text = "$total", background = primary2, icon = IconGlyphs.Bookmark))
                }
                val epLabel = badges.progressEpisode?.takeIf { it.isNotBlank() } ?: "-"
                add(BadgeSegment(text = epLabel, background = primary1, icon = IconGlyphs.AvgPace))
            }
            CardBadgeMode.MalMyList -> {
                badges.totalEpisodes?.takeIf { it > 0 }?.let { total ->
                    add(BadgeSegment(text = "$total", background = primary2, icon = IconGlyphs.Bookmark))
                }
                val epLabel = badges.progressEpisode?.takeIf { it.isNotBlank() } ?: "-"
                add(BadgeSegment(text = epLabel, background = primary1, icon = IconGlyphs.AvgPace))
            }
            CardBadgeMode.LocalMyList -> {
                badges.progressEpisode?.takeIf { it.isNotBlank() }?.let { ep ->
                    add(BadgeSegment(text = ep, background = primary1, icon = IconGlyphs.AvgPace))
                }
            }
            CardBadgeMode.Catalog -> {
                val subEp = badges.subEpisode
                val dubEp = badges.dubEpisode
                val haveEpCount = (if (subEp != null) 1 else 0) + (if (dubEp != null) 1 else 0)
                badges.progressEpisode?.takeIf { it.isNotBlank() }?.let { ep ->
                    add(BadgeSegment(text = ep, background = primary1, icon = IconGlyphs.AvgPace))
                }
                subEp?.let {
                    add(BadgeSegment(text = "$it", background = primary1, icon = IconGlyphs.ClosedCaption))
                }
                dubEp?.let {
                    add(BadgeSegment(text = "$it", background = primary1, icon = IconGlyphs.Mic))
                }
                if (subEp == null && dubEp == null && badges.progressEpisode.isNullOrBlank()) {
                    badges.airedEpisode?.let {
                        add(BadgeSegment(text = "$it", background = primary1, icon = IconGlyphs.Movie))
                    }
                }
                if (haveEpCount < 2) {
                    badges.totalEpisodes?.let {
                        add(BadgeSegment(text = "$it", background = primary2, icon = IconGlyphs.Bookmark))
                    }
                }
            }
        }
    }
}

private fun buildBottomBadgeSegments(
    badges: CardBadgeData,
    posterBottomLabel: String?,
    posterBottomIcon: String?,
    posterBottomAired: Boolean,
    badgeMode: CardBadgeMode,
    showBottomBadges: Boolean,
    glassMode: Boolean,
    primaryBg: Color,
    primary2: Color
): List<BadgeSegment> {
    if (!posterBottomLabel.isNullOrBlank()) {
        val icon = posterBottomIcon ?: if (posterBottomAired) IconGlyphs.Done else IconGlyphs.Schedule
        return listOf(
            BadgeSegment(
                text = posterBottomLabel,
                background = if (posterBottomAired) primaryBg else primary2,
                icon = icon,
                iconTint = if (glassMode && !posterBottomAired) Color(0xFF38BDF8) else Color.White
            )
        )
    }
    if (badgeMode != CardBadgeMode.Catalog || !showBottomBadges) return emptyList()

    return buildList {
        val scoreDisplay = if (glassMode) {
            badges.scoreFormatted ?: badges.score?.takeIf { it > 0 }?.toString()
        } else {
            badges.score?.takeIf { it > 0 }?.toString() ?: badges.scoreFormatted
        }
        if (!scoreDisplay.isNullOrBlank()) {
            add(
                BadgeSegment(
                    text = scoreDisplay,
                    background = Color(0xFF006600),
                    icon = IconGlyphs.Star,
                    iconTint = if (glassMode) Color(0xFFFBBF24) else Color.White
                )
            )
        }
        displayRPlusRating(badges.rating, badges.isAdult)?.let { rPlus ->
            add(
                BadgeSegment(
                    text = rPlus,
                    background = Color(0xFF880000),
                    textColor = if (glassMode) Color(0xFFEF4444) else Color.White
                )
            )
        }
        if (badges.isUpcoming) {
            add(
                BadgeSegment(
                    text = badges.year ?: "TBA",
                    background = Color(0xFF0066CC),
                    icon = IconGlyphs.EventUpcoming
                )
            )
        } else if (!badges.year.isNullOrBlank()) {
            add(BadgeSegment(text = badges.year, background = Color(0xFF0066CC)))
        }
        if (!badges.duration.isNullOrBlank()) {
            add(BadgeSegment(text = badges.duration, background = Color.Black))
        }
    }
}

@Composable
private fun SegmentedBadgePill(
    segments: List<BadgeSegment>,
    glassMode: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    cornerRadius: Dp,
    dividerWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    iconGap: Dp,
    modifier: Modifier = Modifier
) {
    val iconFont = LocalIconFont.current
    val shape = RoundedCornerShape(cornerRadius)
    if (!glassMode) {
        Row(
            modifier = modifier
                .height(IntrinsicSize.Min)
                .clip(shape)
                .border(dividerWidth, Color(0xCC000000), shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(dividerWidth)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }
                val startPadding = if (index > 0 && segment.icon != null) 0.dp else horizontalPadding
                Row(
                    modifier = Modifier
                        .background(segment.background)
                        .padding(
                            start = startPadding,
                            end = horizontalPadding,
                            top = verticalPadding,
                            bottom = verticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(iconGap)
                ) {
                    if (segment.icon != null) {
                        Text(
                            text = segment.icon,
                            color = segment.iconTint,
                            fontFamily = iconFont,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                    Text(
                        text = segment.text,
                        color = segment.textColor,
                        fontWeight = if (segment.bold) FontWeight.Bold else FontWeight.Medium,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(dividerWidth, Color.White.copy(alpha = 0.18f), shape)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(iconGap * 2f)
        ) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Text("/", color = Color.White.copy(alpha = 0.25f), fontSize = fontSize, lineHeight = lineHeight)
                }
                if (segment.icon != null) {
                    Text(
                        text = segment.icon,
                        color = if (segment.iconTint == Color.White) Color(0xFFD4D4D8) else segment.iconTint,
                        fontFamily = iconFont,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
                Text(
                    text = segment.text,
                    color = segment.textColor,
                    fontWeight = if (segment.bold) FontWeight.Medium else FontWeight.Normal,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

internal fun Modifier.pbMenuEdgeFadeMask(
    rowState: LazyListState,
    enabled: Boolean
): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeLeft = rowState.canScrollBackward
            val fadeRight = rowState.canScrollForward
            if (fadeLeft || fadeRight) {
                val stops = buildList {
                    if (fadeLeft) {
                        add(0.0f to Color.Transparent)
                        add(0.05f to Color.Black)
                    } else {
                        add(0.0f to Color.Black)
                    }
                    if (fadeRight) {
                        add(0.95f to Color.Black)
                        add(1.0f to Color.Transparent)
                    } else {
                        add(1.0f to Color.Black)
                    }
                }
                drawRect(
                    brush = Brush.horizontalGradient(colorStops = stops.toTypedArray()),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}

@Composable
internal fun animeTvTextShadow(
    blurMultiplier: Float = 0.2f,
    offsetMultiplier: Float = 0.1f
): Shadow? {
    val textShadowDisabled = LocalPerformanceEffects.current.getOrElse(4) { false }
    if (textShadowDisabled) return null
    val tokens = LocalAnimeTvTokens.current
    val density = LocalDensity.current
    return remember(tokens.u, blurMultiplier, offsetMultiplier, density) {
        val blurPx = with(density) { (tokens.u * blurMultiplier).toPx() }
        val offPx = with(density) { (tokens.u * offsetMultiplier).toPx() }
        Shadow(Color.Black, Offset(0f, offPx), blurPx)
    }
}

@Composable
internal fun AnimeTvShelfHeader(
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val headerColor by animateColorAsState(
        targetValue = if (active) Color.White else Color(0xFFAAAAAA),
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )
    Text(
        text = title,
        color = headerColor,
        fontSize = fsSp(tokens.fs13),
        fontWeight = FontWeight.Bold,
        style = LocalTextStyle.current.copy(shadow = animeTvTextShadow()),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun AnimeTvShelfPlaceholder(
    loading: Boolean,
    emptyText: String,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val animEnabled = loading && tokens.tim10s > 0
    val alphaState = if (animEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "loading_text")
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading_alpha"
        )
    } else {
        remember(loading) { mutableFloatStateOf(if (loading) 0.7f else 0.5f) }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (loading) "Loading data..." else emptyText,
            color = Color.White,
            fontSize = fsSp(tokens.fs20),
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = alphaState.value }
        )
    }
}

@Composable
internal fun AnimeTvTitledShelf(
    title: String,
    horizontalInset: Dp,
    empty: Boolean,
    loading: Boolean,
    emptyText: String,
    cardHeight: Dp,
    cardGap: Dp,
    rowState: LazyListState,
    maskEnabled: Boolean,
    trailingHeaderContent: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    var hasFocus by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (hasFocus || hovered) 2f else 1f)
            .hoverable(interactionSource)
            .onFocusChanged { hasFocus = it.hasFocus }
    ) {
        if (trailingHeaderContent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalInset, end = horizontalInset, bottom = tokens.u * 0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimeTvShelfHeader(
                    title = title,
                    active = hasFocus || hovered
                )
                trailingHeaderContent()
            }
        } else {
            AnimeTvShelfHeader(
                title = title,
                active = hasFocus || hovered,
                modifier = Modifier
                    .padding(start = horizontalInset, end = horizontalInset, bottom = tokens.u * 0.5f)
            )
        }
        if (empty) {
            AnimeTvShelfPlaceholder(
                loading = loading,
                emptyText = emptyText,
                cardHeight = cardHeight
            )
        } else {
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .pbMenuEdgeFadeMask(rowState, enabled = maskEnabled),
                contentPadding = PaddingValues(horizontal = horizontalInset),
                horizontalArrangement = Arrangement.spacedBy(cardGap),
                content = content
            )
        }
    }
}

@Composable
internal fun AnimeTvInlineTabs(
    title: String,
    tabs: List<Pair<String, String>>,
    selectedTabId: String,
    contentActive: Boolean,
    horizontalInset: Dp,
    onTabSelected: (String) -> Unit,
    onTabFocused: () -> Unit = {}
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val maskEnabled = !LocalPerformanceEffects.current.getOrElse(3) { true }
    val rowState = rememberLazyListState()
    var anyTabFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { anyTabFocused = it.hasFocus }
            .padding(bottom = tokens.u * 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimeTvShelfHeader(
            title = title,
            active = anyTabFocused || contentActive,
            modifier = Modifier.padding(start = horizontalInset, end = tokens.u * 2f)
        )
        LazyRow(
            state = rowState,
            modifier = Modifier
                .weight(1f)
                .pbMenuEdgeFadeMask(rowState, enabled = maskEnabled),
            contentPadding = PaddingValues(end = horizontalInset),
            horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.first }) { _, (tabId, tabLabel) ->
                var tabFocused by remember { mutableStateOf(false) }
                val isSelected = tabId == selectedTabId
                val bgColor = when {
                    tabFocused || (isSelected && contentActive) -> colors.primary1
                    isSelected -> Color(0xFF333333)
                    else -> Color.Transparent
                }
                val borderColor = when {
                    isSelected && contentActive && !tabFocused -> colors.primary3
                    tabFocused -> Color.White
                    else -> Color.Transparent
                }
                val pillShape = RoundedCornerShape(tokens.u * 4f)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(bgColor)
                        .border((tokens.u * 0.1f).coerceAtLeast(1.dp), borderColor, pillShape)
                        .onFocusChanged { state ->
                            tabFocused = state.isFocused
                            if (state.isFocused) {
                                onTabFocused()
                                onTabSelected(tabId)
                            }
                        }
                        .navigationClickable { onTabSelected(tabId) }
                        .navigationFocusable()
                        .padding(horizontal = tokens.u * 0.8f, vertical = tokens.u * 0.25f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs10),
                        lineHeight = fsSp(tokens.fs10),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
