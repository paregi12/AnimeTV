package com.paregi.animetv.core.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformVoiceSearchButton
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.blendColors
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.domain.AppSettings
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.UserProfile
import com.paregi.animetv.features.profile.ProfileAvatar
import com.paregi.animetv.features.profile.parentalRatingShortNames
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformClockLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

internal enum class Section(val label: String, val icon: String) {
    Home("Home", IconGlyphs.Home),
    Search("Search", IconGlyphs.Search),
    MyList("MyList", IconGlyphs.MyList),
    Schedule("Schedule", IconGlyphs.Schedule),
    Settings("Settings", IconGlyphs.Settings)
}

@Composable
internal fun AppHeader(
    selected: Section,
    tvLayout: Boolean,
    portraitLayout: Boolean,
    headerScrolled: Boolean,
    showClock: Boolean,
    directPageSwitch: Boolean,
    directSidebar: Boolean,
    sidebarOpen: Boolean,
    showVoiceSearch: Boolean,
    onOpenSidebar: () -> Unit,
    onVoiceSearchResult: (String) -> Unit,
    onSelect: (Section) -> Unit,
    onSectionActionRequest: (Section) -> Unit = {},
    onRefreshSchedule: () -> Unit = {}
) {
    if (portraitLayout) {
        PortraitNavCapsule(
            selected = selected,
            headerScrolled = headerScrolled,
            onOpenSidebar = onOpenSidebar,
            onSelect = onSelect,
            onSectionActionRequest = onSectionActionRequest,
            onRefreshSchedule = onRefreshSchedule
        )
    } else {
        TvHeader(
            selected = selected,
            tvLayout = tvLayout,
            headerScrolled = headerScrolled,
            showClock = showClock,
            directPageSwitch = directPageSwitch,
            directSidebar = directSidebar,
            sidebarOpen = sidebarOpen,
            showVoiceSearch = showVoiceSearch,
            onOpenSidebar = onOpenSidebar,
            onVoiceSearchResult = onVoiceSearchResult,
            onSelect = onSelect,
            onSectionActionRequest = onSectionActionRequest,
            onRefreshSchedule = onRefreshSchedule
        )
    }
}

@Composable
private fun PortraitNavCapsule(
    selected: Section,
    headerScrolled: Boolean,
    onOpenSidebar: () -> Unit,
    onSelect: (Section) -> Unit,
    onSectionActionRequest: (Section) -> Unit,
    onRefreshSchedule: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val hideDistance = tokens.vh * 12f + tokens.sysNavHeight
    val scrollOffsetY by animateDpAsState(
        targetValue = if (headerScrolled) hideDistance else 0.dp,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )
    val density = LocalDensity.current
    val capsuleShape = RoundedCornerShape(tokens.vh * 10f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.sysNavHeight + tokens.vh * 2f)
            .graphicsLayer {
                translationY = with(density) { scrollOffsetY.toPx() }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (!shadowsDisabled) {
                        Modifier.shadow(tokens.vh * 0.08f, capsuleShape, clip = false)
                    } else Modifier
                )
                .clip(capsuleShape)
                .background(Color(0xFF222222))
                .padding(
                    start = tokens.vh * 0.3f,
                    top = tokens.vh * 0.9f,
                    end = tokens.vh * 0.7f,
                    bottom = tokens.vh * 0.6f
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortraitNavIcon(
                icon = IconGlyphs.Menu,
                selected = false,
                onClick = onOpenSidebar
            )

            Spacer(Modifier.width(tokens.vh * 1.5f))

            val tabs = remember {
                listOf(
                    Section.Home,
                    Section.MyList,
                    Section.Search,
                    Section.Schedule,
                    Section.Settings
                )
            }
            tabs.forEach { tab ->
                val selectable = tab == Section.Home || tab == Section.MyList || tab == Section.Schedule
                PortraitNavIcon(
                    icon = tab.icon,
                    selected = selectable && selected == tab,
                    onClick = {
                        when {
                            tab == Section.Schedule && selected == Section.Schedule -> onRefreshSchedule()
                            (tab == Section.Home || tab == Section.MyList) && selected == tab -> {
                                onSectionActionRequest(tab)
                            }
                            else -> onSelect(tab)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PortraitNavIcon(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val playClickSound = LocalNavigationSound.current

    val iconColor = if (hovered || pressed) Color.White else Color.White.copy(alpha = 0.8f)
    val bgColor = if (pressed) Color.Black.copy(alpha = 0.4f) else Color.Transparent

    Box(
        modifier = Modifier
            .padding(start = tokens.vh * 0.5f)
            .drawWithContent {
                drawContent()
                if (selected) {
                    val lineWidth = size.width * 0.60f
                    val lineHeight = (tokens.vh * 0.25f).toPx()
                    val left = (size.width - lineWidth) / 2f
                    val top = size.height + (tokens.vh * 0.25f).toPx()
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(lineWidth, lineHeight)
                    )
                }
            }
            .size(tokens.vh * 5f)
            .clip(RoundedCornerShape(tokens.vh * 4f))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                playClickSound()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontFamily = LocalIconFont.current,
            color = iconColor,
            fontSize = fsSp(tokens.fs25)
        )
    }
}

@Composable
private fun TvHeader(
    selected: Section,
    tvLayout: Boolean,
    headerScrolled: Boolean,
    showClock: Boolean,
    directPageSwitch: Boolean,
    directSidebar: Boolean,
    sidebarOpen: Boolean,
    showVoiceSearch: Boolean,
    onOpenSidebar: () -> Unit,
    onVoiceSearchResult: (String) -> Unit,
    onSelect: (Section) -> Unit,
    onSectionActionRequest: (Section) -> Unit,
    onRefreshSchedule: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val density = LocalDensity.current
    val textShadowDisabled = LocalPerformanceEffects.current.getOrElse(4) { false }
    val headerShadow = remember(textShadowDisabled, tokens.vw) {
        if (textShadowDisabled) null
        else Shadow(
            color = Color.Black,
            offset = Offset(0f, with(density) { (tokens.vw * 0.1f).toPx() }),
            blurRadius = with(density) { (tokens.vw * 0.2f).toPx() }
        )
    }

    val scrollOffsetY by animateDpAsState(
        targetValue = if (headerScrolled) -tokens.vw * 10f else 0.dp,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )

    var logoFocused by remember { mutableStateOf(false) }
    val logoInteraction = remember { MutableInteractionSource() }
    val logoHovered by logoInteraction.collectIsHoveredAsState()
    var autoOpenedSidebar by remember { mutableStateOf(false) }

    LaunchedEffect(sidebarOpen, logoFocused) {
        if (!sidebarOpen && !logoFocused) {
            autoOpenedSidebar = false
        }
    }

    val logoActive = logoFocused || logoHovered
    val logoOpacity by animateFloatAsState(
        targetValue = if (logoActive) 1f else 0.7f,
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )
    val menuIconAlpha by animateFloatAsState(
        targetValue = if (logoActive) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )
    val menuIconRotation by animateFloatAsState(
        targetValue = if (logoActive) 0f else -90f,
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = with(density) { scrollOffsetY.toPx() }
            }
            .padding(
                start = tokens.vw * 5f,
                end = tokens.vw * 5f,
                top = tokens.vw * 3f,
                bottom = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = tokens.vw * 0.4f)
                .hoverable(logoInteraction)
                .navigationClickable(onClick = onOpenSidebar)
                .onFocusChanged { focusState ->
                    logoFocused = focusState.isFocused
                    if (focusState.isFocused && tvLayout && directSidebar && !sidebarOpen && !autoOpenedSidebar) {
                        autoOpenedSidebar = true
                        onOpenSidebar()
                    }
                }
                .navigationFocusable(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (menuIconAlpha > 0.01f) {
                Text(
                    text = IconGlyphs.Menu,
                    fontFamily = LocalIconFont.current,
                    color = Color.White,
                    fontSize = fsSp(tokens.fs12),
                    style = TextStyle(shadow = headerShadow),
                    modifier = Modifier
                        .offset(x = -(tokens.vw * 0.2f + tokens.fs20))
                        .graphicsLayer {
                            alpha = menuIconAlpha
                            rotationZ = menuIconRotation
                        }
                )
            }
            Text(
                text = "AnimeTV",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(tokens.fs20),
                lineHeight = fsSp(tokens.fs20),
                style = TextStyle(shadow = headerShadow),
                modifier = Modifier.graphicsLayer { alpha = logoOpacity }
            )
        }

        if (showVoiceSearch) {
            Spacer(Modifier.width(tokens.vw * 0.5f))
            PlatformVoiceSearchButton(
                onResult = onVoiceSearchResult,
                inlineSearchBar = true,
                iconColor = Color.White,
                iconSize = fsSp(tokens.fs17)
            )
        }

        listOf(Section.Search, Section.Home, Section.MyList).forEach { item ->
            val isSelected = selected == item
            TvHeaderItem(
                section = item,
                selected = isSelected,
                directPageSwitch = directPageSwitch,
                headerShadow = headerShadow,
                onFocusSelect = {
                    if (item != Section.Search && directPageSwitch && !isSelected) {
                        onSelect(item)
                    }
                },
                onClick = {
                    when {
                        item == Section.Search -> onSelect(Section.Search)
                        isSelected -> onSectionActionRequest(item)
                        else -> onSelect(item)
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f))

        TvHeaderIconItem(
            section = Section.Schedule,
            tooltip = "Airing Schedule",
            selected = selected == Section.Schedule,
            directPageSwitch = directPageSwitch,
            headerShadow = headerShadow,
            onFocusSelect = {
                if (directPageSwitch && selected != Section.Schedule) {
                    onSelect(Section.Schedule)
                }
            },
            onClick = {
                if (selected == Section.Schedule) onRefreshSchedule()
                else onSelect(Section.Schedule)
            }
        )

        Spacer(Modifier.width(tokens.vw * 1f))

        TvHeaderIconItem(
            section = Section.Settings,
            tooltip = "Settings",
            selected = false,
            directPageSwitch = false,
            headerShadow = headerShadow,
            onFocusSelect = {},
            onClick = { onSelect(Section.Settings) }
        )

        if (showClock) {
            Spacer(Modifier.width(tokens.vw * 1f))
            ClockLabel(headerShadow = headerShadow)
        }
    }
}

@Composable
private fun ClockLabel(headerShadow: Shadow?) {
    val tokens = LocalAnimeTvTokens.current
    var time by remember { mutableStateOf(platformClockLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            time = platformClockLabel()
        }
    }
    Text(
        text = time,
        color = Color.White,
        fontSize = fsSp(tokens.fs20),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        textAlign = TextAlign.End,
        style = TextStyle(shadow = headerShadow),
        modifier = Modifier.widthIn(min = tokens.vw * 8f)
    )
}

@Composable
private fun TvHeaderItem(
    section: Section,
    selected: Boolean,
    directPageSwitch: Boolean,
    headerShadow: Shadow?,
    onFocusSelect: () -> Unit,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val isActive = focused || hovered
    val underlineProgress by animateFloatAsState(
        targetValue = if (selected && !isActive) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )

    val pillBg = if (isActive) Color.White else Color.Transparent
    val fgColor = when {
        focused -> colors.primary1
        hovered -> colors.accent1
        else -> Color.White
    }
    val textShadow = if (isActive) null else headerShadow

    Box(
        modifier = Modifier
            .padding(end = tokens.vw * 0.4f)
            .drawWithContent {
                drawContent()
                if (underlineProgress > 0.01f) {
                    val lineW = size.width * 0.60f * underlineProgress
                    val lineH = (tokens.vw * 0.25f).toPx()
                    val left = (size.width - lineW) / 2f
                    val top = size.height + (tokens.vw * 0.25f).toPx()
                    drawRect(
                        color = Color.White.copy(alpha = underlineProgress),
                        topLeft = Offset(left, top),
                        size = Size(lineW, lineH)
                    )
                }
            }
            .clip(RoundedCornerShape(tokens.vw * 2f))
            .background(pillBg)
            .hoverable(interactionSource)
            .navigationClickable(onClick = onClick)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused && directPageSwitch) {
                    onFocusSelect()
                }
            }
            .navigationFocusable()
            .padding(horizontal = tokens.vw * 1f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.vw * 0.35f)
        ) {
            if (section == Section.Search) {
                Text(
                    text = section.icon,
                    color = fgColor,
                    fontFamily = LocalIconFont.current,
                    fontSize = fsSp(tokens.fs12),
                    lineHeight = fsSp(tokens.fs20),
                    style = TextStyle(shadow = textShadow)
                )
            }
            Text(
                text = section.label,
                color = fgColor,
                fontSize = fsSp(tokens.fs12),
                lineHeight = fsSp(tokens.fs20),
                maxLines = 1,
                style = TextStyle(shadow = textShadow)
            )
        }
    }
}

@Composable
private fun TvHeaderIconItem(
    section: Section,
    tooltip: String,
    selected: Boolean,
    directPageSwitch: Boolean,
    headerShadow: Shadow?,
    onFocusSelect: () -> Unit,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val isActive = focused || hovered
    val underlineProgress by animateFloatAsState(
        targetValue = if (selected && !isActive) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )
    val tooltipProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )

    val pillBg = if (isActive) Color.White else Color.Transparent
    val fgColor = when {
        focused -> colors.primary1
        hovered -> colors.accent1
        else -> Color.White
    }
    val iconShadow = if (isActive) null else headerShadow

    Box(
        modifier = Modifier
            .size(tokens.vw * 4f)
            .drawWithContent {
                drawContent()
                if (underlineProgress > 0.01f) {
                    val lineW = size.width * 0.60f * underlineProgress
                    val lineH = (tokens.vw * 0.25f).toPx()
                    val left = (size.width - lineW) / 2f
                    val top = size.height + (tokens.vw * 0.25f).toPx()
                    drawRect(
                        color = Color.White.copy(alpha = underlineProgress),
                        topLeft = Offset(left, top),
                        size = Size(lineW, lineH)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(tokens.vw * 2f))
                .background(pillBg)
                .hoverable(interactionSource)
                .navigationClickable(onClick = onClick)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused && directPageSwitch) {
                        onFocusSelect()
                    }
                }
                .navigationFocusable(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = section.icon,
                color = fgColor,
                fontFamily = LocalIconFont.current,
                fontSize = fsSp(tokens.fs22),
                lineHeight = fsSp(tokens.vw * 4f),
                style = TextStyle(shadow = iconShadow)
            )
        }

        if (tooltipProgress > 0.01f) {
            val scale = 0.5f + 0.5f * tooltipProgress
            Text(
                text = tooltip,
                color = Color.White,
                fontSize = fsSp(tokens.fs11),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(shadow = headerShadow),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = tokens.vw * 2.5f)
                    .requiredWidth(tokens.vw * 10f)
                    .graphicsLayer {
                        alpha = tooltipProgress
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

@Composable
internal fun SidebarDialog(
    visible: Boolean,
    settings: AppSettings,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onDisarmExit: () -> Unit = {},
    onOpenProfileMenu: () -> Unit,
    onSourceSelected: (Int) -> Unit,
    onConfigureSource: (Int) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val density = LocalDensity.current
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val maskDisabled = LocalPerformanceEffects.current.getOrElse(3) { false }

    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )
    if (!visible && animProgress <= 0.001f) return

    var activeIndex by remember(visible) { mutableIntStateOf(0) }
    val profileRequester = remember { FocusRequester() }
    val sourceRequesters = remember(SourceCatalog.sources.size) {
        SourceCatalog.sources.map { FocusRequester() }
    }

    LaunchedEffect(visible) {
        if (visible) {
            activeIndex = 0
            yield()
            runCatching { profileRequester.requestFocus() }
        }
    }

    val scrimBrush = if (tokens.portrait) {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.05f to Color.Black.copy(alpha = animProgress),
                0.35f to Color.Black.copy(alpha = 0.80f * animProgress),
                0.70f to Color.Black.copy(alpha = 0.60f * animProgress),
                1.00f to Color.Black.copy(alpha = 0.30f * animProgress)
            )
        )
    } else {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.05f to Color.Black.copy(alpha = animProgress),
                0.20f to Color.Black.copy(alpha = 0.95f * animProgress),
                0.50f to Color.Black.copy(alpha = 0.60f * animProgress),
                1.00f to Color.Transparent
            )
        )
    }

    val drawerWidth = if (tokens.portrait) tokens.vw * 50f + tokens.vh * 4f else tokens.vw * 22f
    val sidePad = if (tokens.portrait) tokens.vh * 2f else tokens.vw * 2f
    val topPad = if (tokens.portrait) tokens.vh * 4f + tokens.sysStatHeight else tokens.vw * 2f
    val slideOffsetPx = with(density) { (-tokens.u * 5f).toPx() } * (1f - animProgress)

    val maskModifier = if (!tokens.portrait && !maskDisabled) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadeHeight = (tokens.vw * 2f).toPx()
                val fraction = (fadeHeight / size.height).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        fraction to Color.Black,
                        1f to Color.Black
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimBrush)
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                } else Modifier
            )
            .onPreviewKeyEvent { event ->
                if (!visible || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        onDisarmExit()
                        onDismiss()
                        true
                    }
                    Key.DirectionDown -> {
                        val next = (activeIndex + 1).coerceAtMost(SourceCatalog.sources.size)
                        if (next != activeIndex) {
                            onDisarmExit()
                            activeIndex = next
                            val target = if (next == 0) profileRequester else sourceRequesters[next - 1]
                            runCatching { target.requestFocus() }
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        val prev = (activeIndex - 1).coerceAtLeast(0)
                        if (prev != activeIndex) {
                            onDisarmExit()
                            activeIndex = prev
                            val target = if (prev == 0) profileRequester else sourceRequesters[prev - 1]
                            runCatching { target.requestFocus() }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(drawerWidth)
                .graphicsLayer {
                    translationX = slideOffsetPx
                    alpha = animProgress
                }
                .then(maskModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .pointerInput(visible) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (dragAmount < -15f) {
                            change.consume()
                            onDisarmExit()
                            onDismiss()
                        }
                    }
                }
                .padding(start = sidePad, end = sidePad, top = topPad)
        ) {
            val profileInteraction = remember { MutableInteractionSource() }
            val profileHovered by profileInteraction.collectIsHoveredAsState()
            val profilePressed by profileInteraction.collectIsPressedAsState()
            var profileFocused by remember { mutableStateOf(false) }
            val profileActive = activeIndex == 0 || profileFocused

            val profileBg = when {
                profileActive -> Color.White
                profilePressed -> blendColors(colors.primary1, Color.Black, 0.25f)
                profileHovered -> blendColors(colors.primary1, Color.Black, 0.50f)
                else -> Color.Transparent
            }
            val profileNameColor = when {
                profileActive && profilePressed -> colors.primary1
                profileActive -> Color.Black
                else -> Color.White
            }
            val profileSub = when {
                profile.prefix.isEmpty() -> "admin"
                profile.parentalRating in 0..5 -> "user - ${parentalRatingShortNames[profile.parentalRating]}"
                else -> "user"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = tokens.u * 2f)
                    .clip(RoundedCornerShape(tokens.u * 8f))
                    .background(profileBg)
                    .hoverable(profileInteraction)
                    .focusRequester(profileRequester)
                    .onFocusChanged { state ->
                        profileFocused = state.isFocused
                        if (state.isFocused) activeIndex = 0
                    }
                    .navigationClickable {
                        activeIndex = 0
                        onDisarmExit()
                        onOpenProfileMenu()
                    }
                    .navigationFocusable()
                    .padding(tokens.u * 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = tokens.u * 1f)
                        .size(tokens.u * 4f)
                        .then(
                            if (!shadowsDisabled) {
                                Modifier.shadow(tokens.u * 0.28f, CircleShape, clip = false)
                            } else Modifier
                        )
                        .clip(CircleShape)
                ) {
                    ProfileAvatar(profile, tokens.u * 4f)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        color = profileNameColor,
                        fontSize = fsSp(tokens.fs15),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profileSub,
                        color = profileNameColor,
                        fontSize = fsSp(tokens.fs10),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(modifier = Modifier.padding(bottom = tokens.u * 2f)) {
                Text(
                    text = "Sources",
                    color = Color(0xFF999999),
                    fontSize = fsSp(tokens.fs11),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(
                        start = tokens.u * 1f,
                        bottom = tokens.u * 0.5f
                    )
                )

                SourceCatalog.sources.forEachIndexed { idx, source ->
                    val itemIdx = idx + 1
                    val isCurrentSource = settings.selectedSource == source.index
                    val activeDomain = SourceCatalog.activeDomain(
                        source.index,
                        settings.sourceDomains[source.index]
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val pressed by interactionSource.collectIsPressedAsState()
                    var rowFocused by remember { mutableStateOf(false) }
                    val isRowActive = activeIndex == itemIdx || rowFocused

                    val rowBg = when {
                        isRowActive -> Color.White
                        pressed -> blendColors(colors.primary1, Color.Black, 0.25f)
                        hovered -> blendColors(colors.primary1, Color.Black, 0.50f)
                        else -> Color.Transparent
                    }
                    val titleColor = when {
                        isRowActive -> Color.Black
                        isCurrentSource -> colors.accent1
                        else -> Color.White
                    }
                    val itemPad = if (tokens.portrait) tokens.vh * 2f else tokens.vw * 1f
                    val itemRadius = if (tokens.portrait) tokens.vh * 8f else tokens.vw * 5f
                    val itemBottomGap = if (tokens.portrait) tokens.vh * 0.4f else tokens.vw * 0.2f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = itemBottomGap)
                            .clip(RoundedCornerShape(itemRadius))
                            .background(rowBg)
                            .hoverable(interactionSource)
                            .focusRequester(sourceRequesters[idx])
                            .onFocusChanged { state ->
                                rowFocused = state.isFocused
                                if (state.isFocused) {
                                    activeIndex = itemIdx
                                    onDisarmExit()
                                }
                            }
                            .navigationClickable {
                                onDisarmExit()
                                if (activeIndex != itemIdx) {
                                    activeIndex = itemIdx
                                    runCatching { sourceRequesters[idx].requestFocus() }
                                } else if (isCurrentSource) {
                                    onConfigureSource(source.index)
                                } else {
                                    onSourceSelected(source.index)
                                }
                            }
                            .navigationFocusable()
                            .padding(itemPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconBadgeBg = if (isRowActive && isCurrentSource) colors.primary3 else Color.Transparent
                        val iconFg = when {
                            isRowActive && isCurrentSource -> Color.White
                            isRowActive -> colors.primary3
                            isCurrentSource -> colors.accent1
                            else -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = tokens.u * 0.5f)
                                .size(tokens.fs14 + tokens.u * 1f)
                                .clip(RoundedCornerShape(tokens.u * 5f))
                                .background(iconBadgeBg)
                                .then(
                                    if (isRowActive) {
                                        Modifier.navigationClickable {
                                            onDisarmExit()
                                            onConfigureSource(source.index)
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isRowActive) IconGlyphs.Settings else IconGlyphs.Cloud,
                                fontFamily = LocalIconFont.current,
                                color = iconFg,
                                fontSize = fsSp(tokens.fs14)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.displayName,
                                color = titleColor,
                                fontSize = fsSp(tokens.fs14),
                                lineHeight = fsSp(tokens.fs14),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isRowActive) {
                                Text(
                                    text = "@$activeDomain",
                                    color = colors.primary1,
                                    fontSize = fsSp(tokens.fs7),
                                    lineHeight = fsSp(tokens.fs7),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
