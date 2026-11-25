package com.paregi.animetv.features.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.FilterChip
import com.paregi.animetv.core.ui.FocusCard
import com.paregi.animetv.core.ui.calculateCardDimensions
import com.paregi.animetv.core.ui.pbMenuEdgeFadeMask
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AnimeRepository
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchHistoryRepository
import com.paregi.animetv.domain.allowedForRating
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SearchScreen(
    repository: AnimeRepository,
    tvLayout: Boolean,
    selectedSource: Int,
    searchHistoryRepository: SearchHistoryRepository,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onVoiceSearchResult: (String) -> Unit,
    profileRating: Int,
    profileActivation: Int,
    onAnimeSelected: (Anime) -> Unit,
    largeUi: Boolean = false
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val performanceEffects = LocalPerformanceEffects.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playClickSound = LocalNavigationSound.current

    val portrait = tokens.portrait
    val isTouch = tokens.isTouch
    val effectiveLargeUi = (largeUi || tokens.largeUi) && !portrait
    val maskDisabled = performanceEffects.getOrElse(3) { false }
    val textShadowDisabled = performanceEffects.getOrElse(4) { false }

    var history by remember { mutableStateOf(emptyList<String>()) }
    var clearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteHistoryTerm by remember { mutableStateOf<String?>(null) }
    var selectedFilterKeys by remember { mutableStateOf(emptySet<String>()) }
    var page by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Anime>()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var aniListSearch by remember { mutableStateOf(false) }
    var remoteTypingActive by remember { mutableStateOf(false) }
    var searchSubmitNonce by remember { mutableIntStateOf(0) }
    var searchMuted by rememberSaveable { mutableStateOf(SearchRemotePadPrefs.muted) }

    var kwFocused by remember { mutableStateOf(false) }
    var genresFocused by remember { mutableStateOf(false) }
    var resultsFocused by remember { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }
    val remoteKeyboardRequester = remember { FocusRequester() }
    val genreRowRequester = remember { FocusRequester() }
    val resultsRowRequester = remember { FocusRequester() }
    val historyScope = rememberCoroutineScope()
    val genresListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()

    LaunchedEffect(searchHistoryRepository, profileActivation) {
        history = runCatching { searchHistoryRepository.load() }.getOrDefault(emptyList())
        aniListSearch = runCatching { searchHistoryRepository.loadAniListSearchEnabled() }
            .getOrDefault(false)
    }

    LaunchedEffect(query) {
        page = 1
    }

    LaunchedEffect(isTouch, tvLayout) {
        if (!isTouch && query.isBlank()) {
            runCatching { remoteKeyboardRequester.requestFocus() }
        }
    }

    fun recordSearch(value: String) {
        val term = value.trim()
        if (term.isBlank()) return
        historyScope.launch {
            history = runCatching { searchHistoryRepository.record(term) }
                .getOrDefault(history)
        }
    }

    fun triggerSearch(focusResults: Boolean = false, recordQuery: Boolean = false) {
        remoteTypingActive = false
        val cleaned = query.trim()
        if (cleaned != query) onQueryChange(cleaned)
        if (recordQuery && cleaned.isNotBlank()) recordSearch(cleaned)
        page = 1
        searchSubmitNonce += 1
        if (focusResults && !isTouch) {
            runCatching { resultsRowRequester.requestFocus() }
        }
    }

    LaunchedEffect(
        selectedSource,
        query,
        selectedFilterKeys,
        page,
        profileRating,
        aniListSearch,
        searchSubmitNonce,
        remoteTypingActive
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() && selectedFilterKeys.isEmpty()) {
            results = emptyList()
            hasNextPage = false
            loading = false
            errorMessage = null
            return@LaunchedEffect
        }
        if (remoteTypingActive && page == 1) return@LaunchedEffect
        if (page == 1 && searchSubmitNonce == 0) {
            delay(350)
        }
        if (page == 1) {
            results = emptyList()
        }
        loading = true
        errorMessage = null
        val response = runCatching {
            repository.search(
                SearchFilters(
                    query = trimmedQuery,
                    types = selectedFilterKeys.filter { it.startsWith("_") }
                        .map { it.removePrefix("_").uppercase() }.toSet(),
                    genres = selectedFilterKeys.filterNot { it.startsWith("_") }.toSet(),
                    aniList = aniListSearch
                ),
                page = page
            )
        }.onFailure { errorMessage = it.message ?: "Search failed" }.getOrNull()
        if (response != null) {
            results = if (page == 1) response.results else (results + response.results).distinctBy { it.id }
            hasNextPage = response.hasNextPage
            if (page == 1 && trimmedQuery.isNotBlank() && response.results.isNotEmpty()) {
                recordSearch(trimmedQuery)
            }
        } else if (page == 1) {
            results = emptyList()
            hasNextPage = false
        }
        loading = false
    }

    val allowedResults = remember(results, profileRating) {
        results.filter { it.allowedForRating(profileRating) }
    }

    LaunchedEffect(resultsListState, allowedResults.size, hasNextPage, loading) {
        snapshotFlow {
            val info = resultsListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (hasNextPage && !loading && total > 0 && page < 60 && lastVisible >= total - 4) {
                    page += 1
                }
            }
    }

    val matchingHistory = remember(history, query) {
        val fkw = query.trim()
        if (fkw.isEmpty()) {
            history.take(4)
        } else {
            history
                .mapNotNull { item ->
                    val idx = item.indexOf(fkw, ignoreCase = true)
                    if (idx >= 0) item to idx else null
                }
                .sortedBy { it.second }
                .take(5)
                .map { it.first }
        }
    }

    val filterOptions = remember(selectedSource, aniListSearch) {
        searchFilterOptions(selectedSource, aniListSearch)
    }

    val rootBackgroundModifier = if (portrait) {
        Modifier.background(blendColors(colors.primary1, Color.Black, 0.25f))
    } else {
        Modifier.background(Brush.verticalGradient(listOf(Color.Black, colors.primaryBg)))
    }

    val portraitHeaderColor = Color(
        red = colors.primary1.red * 0.4f,
        green = colors.primary1.green * 0.4f,
        blue = colors.primary1.blue * 0.4f,
        alpha = 0.88f
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(rootBackgroundModifier)
            .onPreviewKeyEvent { event ->
                if (isTouch || kwFocused || event.type != KeyEventType.KeyDown ||
                    event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
                ) {
                    return@onPreviewKeyEvent false
                }
                val ch = hardwareKeyChar(event.key)
                when {
                    ch != null -> {
                        playClickSound()
                        remoteTypingActive = true
                        onQueryChange(query + ch)
                        true
                    }
                    event.key == Key.Spacebar -> {
                        playClickSound()
                        remoteTypingActive = true
                        onQueryChange(query + " ")
                        true
                    }
                    event.key == Key.Backspace -> {
                        if (query.isNotEmpty()) {
                            playClickSound()
                            remoteTypingActive = true
                            onQueryChange(query.dropLast(1))
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val cardDims = calculateCardDimensions(maxWidth, maxHeight, portrait, largeUi = effectiveLargeUi)
        val headerShadow = if (!textShadowDisabled) {
            Shadow(Color.Black, Offset(0f, 1.5f), 3f)
        } else null

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (portrait) {
                            Modifier
                                .background(portraitHeaderColor)
                                .padding(
                                    top = tokens.sysStatHeight + tokens.vw * 0.5f,
                                    bottom = tokens.vw * 2f,
                                    start = tokens.vw * 2f,
                                    end = tokens.vw * 2f
                                )
                        } else {
                            Modifier
                                .background(Color.Transparent)
                                .padding(
                                    start = tokens.vw * 5f,
                                    end = tokens.vw * 5f,
                                    top = tokens.vw * 3f
                                )
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchLogoButton(
                        portrait = portrait,
                        textShadow = headerShadow,
                        onClick = onBack
                    )

                    Spacer(Modifier.width(tokens.u * 0.5f))

                    PlatformVoiceSearchButton(
                        onResult = { voiceText ->
                            remoteTypingActive = false
                            val normalized = voiceText.trim()
                            onVoiceSearchResult(normalized)
                            if (normalized.isNotBlank()) {
                                recordSearch(normalized)
                                searchSubmitNonce += 1
                            }
                        },
                        inlineSearchBar = true,
                        iconColor = if (portrait) Color.White.copy(alpha = 0.8f) else Color.White,
                        iconSize = fsSp(if (portrait) tokens.fs25 else tokens.fs17)
                    )

                    if (!portrait) {
                        Spacer(Modifier.width(tokens.vw * 0.4f))
                        SearchHeaderKbdButton(
                            textShadow = headerShadow,
                            onClick = {
                                if (!kwFocused) {
                                    remoteTypingActive = false
                                    runCatching { searchFocusRequester.requestFocus() }
                                    keyboardController?.show()
                                } else {
                                    keyboardController?.hide()
                                    triggerSearch(focusResults = true, recordQuery = true)
                                }
                            }
                        )
                        Spacer(Modifier.width(tokens.vw * 0.5f))
                        SearchQueryField(
                            query = query,
                            showRemoteCursor = remoteTypingActive && !kwFocused && !isTouch,
                            portrait = false,
                            onQueryChange = { raw ->
                                remoteTypingActive = false
                                val next = if (!isTouch) sanitizeRemoteQuery(raw) else raw.lowercase()
                                onQueryChange(next)
                                page = 1
                            },
                            focusRequester = searchFocusRequester,
                            onFocusChange = { focused ->
                                kwFocused = focused
                                if (focused) {
                                    remoteTypingActive = false
                                    if (isTouch) keyboardController?.show()
                                }
                            },
                            onSearch = {
                                keyboardController?.hide()
                                triggerSearch(focusResults = !isTouch, recordQuery = true)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = tokens.vw * 56f)
                                .padding(end = tokens.vw * 0.8f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    SearchAniListToggleButton(
                        enabled = aniListSearch,
                        portrait = portrait,
                        isTouch = isTouch,
                        textShadow = headerShadow,
                        onClick = {
                            val next = !aniListSearch
                            aniListSearch = next
                            selectedFilterKeys = emptySet()
                            page = 1
                            historyScope.launch {
                                searchHistoryRepository.saveAniListSearchEnabled(next)
                            }
                        }
                    )
                }

                if (portrait) {
                    Spacer(Modifier.height(tokens.vh * 1f))
                    SearchQueryField(
                        query = query,
                        showRemoteCursor = false,
                        portrait = true,
                        onQueryChange = { raw ->
                            remoteTypingActive = false
                            val next = if (!isTouch) sanitizeRemoteQuery(raw) else raw.lowercase()
                            onQueryChange(next)
                            page = 1
                        },
                        focusRequester = searchFocusRequester,
                        onFocusChange = { focused ->
                            kwFocused = focused
                            if (focused && isTouch) keyboardController?.show()
                        },
                        onSearch = {
                            keyboardController?.hide()
                            triggerSearch(focusResults = !isTouch, recordQuery = true)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = tokens.vw * 1f)
                    )
                }
            }

            if (!isTouch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = tokens.vw * 5f,
                            end = tokens.vw * 5f,
                            top = tokens.vw * 2f,
                            bottom = tokens.vw * 1f
                        )
                        .focusGroup(),
                    verticalAlignment = Alignment.Top
                ) {
                    SearchHistoryColumn(
                        history = matchingHistory,
                        hasAnyHistory = history.isNotEmpty(),
                        queryBlank = query.isBlank(),
                        modifier = Modifier
                            .width(tokens.vw * 24f)
                            .padding(end = tokens.vw * 2f),
                        onSelect = { term ->
                            remoteTypingActive = false
                            onQueryChange(term)
                            recordSearch(term)
                            page = 1
                            searchSubmitNonce += 1
                            runCatching { resultsRowRequester.requestFocus() }
                        },
                        onRequestDelete = { term -> pendingDeleteHistoryTerm = term },
                        onRequestClearAll = { clearHistoryConfirmation = true }
                    )

                    SearchKeyboardTools(
                        query = query,
                        onRemoteQueryChange = { next ->
                            remoteTypingActive = true
                            onQueryChange(next)
                            page = 1
                        },
                        onSearch = { triggerSearch(focusResults = true, recordQuery = true) },
                        muted = searchMuted,
                        initialFocusRequester = remoteKeyboardRequester,
                        modifier = Modifier.width(tokens.vw * 45f)
                    )

                    Spacer(Modifier.weight(1f))

                    SearchKeypadTools(
                        query = query,
                        onRemoteQueryChange = { next ->
                            remoteTypingActive = true
                            onQueryChange(next)
                            page = 1
                        },
                        muted = searchMuted,
                        onToggleMute = {
                            val next = !searchMuted
                            searchMuted = next
                            SearchRemotePadPrefs.muted = next
                        },
                        modifier = Modifier.width(tokens.vw * 13f)
                    )
                }
            } else {
                TouchHistoryStrip(
                    history = matchingHistory,
                    hasAnyHistory = history.isNotEmpty(),
                    query = query,
                    portrait = portrait,
                    onReset = {
                        onQueryChange("")
                        page = 1
                        runCatching { searchFocusRequester.requestFocus() }
                        keyboardController?.show()
                    },
                    onSelect = { term ->
                        onQueryChange(term)
                        recordSearch(term)
                        page = 1
                        searchSubmitNonce += 1
                    },
                    onRequestDelete = { term -> pendingDeleteHistoryTerm = term },
                    onRequestClearAll = { clearHistoryConfirmation = true }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = if (portrait) tokens.sysNavHeight + tokens.vh * 10f else tokens.vw * 2f)
            ) {
                val hideGenres = effectiveLargeUi && !isTouch && resultsFocused
                val genreAlpha by animateFloatAsState(
                    targetValue = if (hideGenres) 0f else 1f,
                    animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
                )
                val genreCollapseFraction by animateFloatAsState(
                    targetValue = if (hideGenres) 0f else 1f,
                    animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val collapsedHeight = (placeable.height * genreCollapseFraction).roundToInt().coerceAtLeast(1)
                            layout(placeable.width, collapsedHeight) {
                                placeable.placeRelative(0, collapsedHeight - placeable.height)
                            }
                        }
                        .graphicsLayer { alpha = genreAlpha }
                        .padding(bottom = tokens.u * 1f)
                ) {
                    Text(
                        text = "Type & Genres",
                        color = if (genresFocused) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs13),
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = headerShadow),
                        modifier = Modifier.padding(
                            start = tokens.vw * 5f,
                            end = tokens.vw * 5f,
                            bottom = tokens.u * 0.5f
                        )
                    )
                    LazyRow(
                        state = genresListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pbMenuEdgeFadeMask(genresListState, !maskDisabled)
                            .onFocusChanged {
                                genresFocused = it.hasFocus
                                if (it.hasFocus && remoteTypingActive) {
                                    remoteTypingActive = false
                                }
                            }
                            .focusRequester(genreRowRequester),
                        contentPadding = PaddingValues(horizontal = tokens.vw * 5f),
                        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.5f)
                    ) {
                        items(filterOptions, key = { it.key }) { option ->
                            FilterChip(
                                label = option.label,
                                value = "",
                                selected = option.key in selectedFilterKeys,
                                onClick = {
                                    remoteTypingActive = false
                                    selectedFilterKeys = if (option.key in selectedFilterKeys) {
                                        selectedFilterKeys - option.key
                                    } else {
                                        selectedFilterKeys + option.key
                                    }
                                    page = 1
                                    searchSubmitNonce += 1
                                }
                            )
                        }
                    }
                }

                val resultEmptyHeight = if (effectiveLargeUi) tokens.vw * 21f else tokens.u * 20f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = tokens.u * 1f)
                        .onFocusChanged {
                            resultsFocused = it.hasFocus
                            if (it.hasFocus && remoteTypingActive) {
                                remoteTypingActive = false
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (!isTouch && effectiveLargeUi && resultsFocused &&
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
                            ) {
                                runCatching { genreRowRequester.requestFocus() }
                                true
                            } else false
                        }
                ) {
                    Text(
                        text = if (aniListSearch) "AniList Search Result" else "Search Result",
                        color = if (resultsFocused) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs13),
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = headerShadow),
                        modifier = Modifier.padding(
                            start = tokens.vw * 5f,
                            end = tokens.vw * 5f,
                            bottom = tokens.u * 0.5f
                        )
                    )

                    if (allowedResults.isNotEmpty()) {
                        LazyRow(
                            state = resultsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pbMenuEdgeFadeMask(resultsListState, !maskDisabled)
                                .focusRequester(resultsRowRequester),
                            contentPadding = PaddingValues(horizontal = tokens.vw * 5f),
                            horizontalArrangement = Arrangement.spacedBy(cardDims.cardGap)
                        ) {
                            items(allowedResults, key = { it.id }) { anime ->
                                FocusCard(
                                    anime = anime,
                                    cardWidth = cardDims.cardWidth,
                                    cardHeight = cardDims.cardHeight,
                                    posterWidth = cardDims.posterWidth,
                                    posterHeight = cardDims.posterHeight,
                                    cardTitleSize = cardDims.titleSize,
                                    cardTitleLineHeight = cardDims.titleLineHeight,
                                    onClick = { onAnimeSelected(anime) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(resultEmptyHeight)
                                .focusRequester(resultsRowRequester),
                            contentAlignment = Alignment.Center
                        ) {
                            val err = errorMessage
                            when {
                                loading -> {
                                    val pulse = rememberInfiniteTransition(label = "search-loading")
                                    val pulseAlpha by pulse.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 0.8f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "search-loading-alpha"
                                    )
                                    Text(
                                        text = "Searching...",
                                        color = Color.White.copy(alpha = pulseAlpha),
                                        fontSize = fsSp(tokens.fs20)
                                    )
                                }
                                err != null -> Text(
                                    text = err,
                                    color = Color(0xFFFF7A8A),
                                    fontSize = fsSp(tokens.fs14)
                                )
                                else -> Text(
                                    text = "No Result",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = fsSp(tokens.fs20)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteHistoryTerm?.let { term ->
        ConfirmDialog(
            title = "Delete History",
            message = "Delete <b>$term</b> from search history?",
            onConfirm = {
                pendingDeleteHistoryTerm = null
                historyScope.launch {
                    history = runCatching { searchHistoryRepository.remove(term) }
                        .getOrDefault(history)
                }
            },
            onDismiss = { pendingDeleteHistoryTerm = null }
        )
    }

    if (clearHistoryConfirmation) {
        ConfirmDialog(
            title = "Clear Search History",
            message = "Are you sure you want to <b>clear all</b> search history?",
            onConfirm = {
                clearHistoryConfirmation = false
                historyScope.launch {
                    searchHistoryRepository.clear()
                    history = emptyList()
                }
            },
            onDismiss = { clearHistoryConfirmation = false }
        )
    }
}

@Composable
private fun SearchLogoButton(
    portrait: Boolean,
    textShadow: Shadow?,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = focused || hovered

    Box(
        modifier = Modifier
            .then(if (portrait) Modifier.size(tokens.vh * 4f) else Modifier)
            .clip(RoundedCornerShape(tokens.u * 4f))
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(
                horizontal = if (portrait) 0.dp else tokens.vw * 0.2f,
                vertical = if (portrait) 0.dp else tokens.vw * 0.2f
            ),
        contentAlignment = Alignment.Center
    ) {
        if (portrait) {
            Text(
                text = IconGlyphs.Back,
                fontFamily = LocalIconFont.current,
                color = if (active) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = fsSp(tokens.fs25),
                style = TextStyle(shadow = textShadow)
            )
        } else {
            Text(
                text = "AnimeTV",
                color = Color.White.copy(alpha = if (active) 1f else 0.7f),
                fontSize = fsSp(tokens.fs20),
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = textShadow)
            )
        }
    }
}

@Composable
private fun SearchHeaderKbdButton(
    textShadow: Shadow?,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bg = if (focused || hovered) Color.White else Color.Transparent
    val fg = when {
        focused -> colors.primary1
        hovered -> colors.accent1
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.vw * 2f))
            .background(bg)
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.3f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = IconGlyphs.Keyboard,
            fontFamily = LocalIconFont.current,
            color = fg,
            fontSize = fsSp(tokens.fs14),
            style = TextStyle(shadow = if (focused || hovered) null else textShadow)
        )
    }
}

@Composable
private fun SearchAniListToggleButton(
    enabled: Boolean,
    portrait: Boolean,
    isTouch: Boolean,
    textShadow: Shadow?,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = focused || hovered

    val bg = when {
        portrait -> Color.Transparent
        highlighted -> Color.White
        else -> Color.Transparent
    }
    val fg = when {
        portrait -> if (highlighted) Color.White else Color.White.copy(alpha = 0.8f)
        focused -> colors.primary1
        hovered -> colors.accent1
        else -> Color.White
    }
    val showShadow = !highlighted && (!portrait || !isTouch)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.u * 2f))
            .background(bg)
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(
                horizontal = if (portrait) tokens.vh * 1f else tokens.vw * 1f,
                vertical = if (portrait) tokens.vh * 0.5f else tokens.vw * 0.3f
            ),
        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (enabled) IconGlyphs.Check else IconGlyphs.Close,
            fontFamily = LocalIconFont.current,
            color = fg,
            fontSize = fsSp(if (portrait) tokens.fs17 else tokens.fs12),
            style = TextStyle(shadow = if (showShadow) textShadow else null)
        )
        Text(
            text = "Search from AniList",
            color = fg,
            fontSize = fsSp(tokens.fs12),
            maxLines = 1,
            style = TextStyle(shadow = if (showShadow) textShadow else null)
        )
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    showRemoteCursor: Boolean,
    portrait: Boolean,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val cornerRadius = if (portrait) tokens.vh * 5f else tokens.vw * 5f
    val pillHeight = if (portrait) tokens.vh * 4f else tokens.vw * 4f
    val cursorVisual = remember(showRemoteCursor) {
        if (!showRemoteCursor) VisualTransformation.None
        else VisualTransformation { text ->
            val withCursor = AnnotatedString(text.text + "_")
            val mapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, text.length)
                override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, text.length)
            }
            TransformedText(withCursor, mapping)
        }
    }

    Row(
        modifier = modifier
            .height(pillHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White)
            .padding(
                start = if (portrait) tokens.vh * 1f else tokens.vw * 1f,
                end = if (portrait) tokens.vh * 2f else tokens.vw * 2f
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (portrait) Arrangement.Center else Arrangement.Start
    ) {
        Text(
            text = IconGlyphs.Search,
            fontFamily = LocalIconFont.current,
            color = Color(0xFF333333),
            fontSize = fsSp(tokens.fs17)
        )
        Spacer(Modifier.width(if (portrait) tokens.vh * 0.5f else tokens.vw * 0.5f))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = fsSp(tokens.fs17)
            ),
            cursorBrush = SolidColor(Color.Black),
            visualTransformation = cursorVisual,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .then(if (portrait) Modifier.width(tokens.vw * 70f) else Modifier.weight(1f))
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty() && !showRemoteCursor) {
                        Text(
                            text = "Search Anime...",
                            color = Color(0xFF888888),
                            fontSize = fsSp(tokens.fs17),
                            maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun SearchHistoryColumn(
    history: List<String>,
    hasAnyHistory: Boolean,
    queryBlank: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestClearAll: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.vw * 0.4f)
    ) {
        if (history.isEmpty() && !hasAnyHistory) {
            Text(
                text = "No search history...",
                color = Color(0xFF888888),
                fontSize = fsSp(tokens.fs12),
                modifier = Modifier.padding(vertical = tokens.vw * 0.6f)
            )
        } else {
            history.forEach { term ->
                var focused by remember { mutableStateOf(false) }
                var deleteArmed by remember { mutableStateOf(false) }
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.vw * 4f))
                        .background(if (focused || hovered) colors.primary2 else Color(0xFF111111))
                        .hoverable(interactionSource = interactionSource)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (!state.isFocused) deleteArmed = false
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (!deleteArmed) {
                                        playClickSound()
                                        deleteArmed = true
                                        true
                                    } else {
                                        deleteArmed = false
                                        false
                                    }
                                }
                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                                    deleteArmed = false
                                    false
                                }
                                else -> false
                            }
                        }
                        .navigationClickable {
                            if (deleteArmed) onRequestDelete(term) else onSelect(term)
                        }
                        .navigationFocusable()
                        .padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.25f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (deleteArmed) IconGlyphs.Clear else IconGlyphs.Update,
                        fontFamily = LocalIconFont.current,
                        color = if (deleteArmed || focused || hovered) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs12),
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.vw * 4f))
                            .background(if (deleteArmed) Color(0xFF990000) else Color.Transparent)
                            .clickable {
                                playClickSound()
                                onRequestDelete(term)
                            }
                            .padding(horizontal = if (deleteArmed) tokens.vw * 0.35f else 0.dp)
                            .padding(end = if (deleteArmed) 0.dp else tokens.vw * 0.5f)
                    )
                    if (deleteArmed) Spacer(Modifier.width(tokens.vw * 0.4f))
                    Text(
                        text = term,
                        color = if (focused || hovered) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs12),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = tokens.vw * 20f)
                    )
                }
            }

            if (queryBlank && hasAnyHistory) {
                var clearFocused by remember { mutableStateOf(false) }
                val clearInteraction = remember { MutableInteractionSource() }
                val clearHovered by clearInteraction.collectIsHoveredAsState()

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.vw * 4f))
                        .background(if (clearFocused || clearHovered) Color(0xFF660000) else Color.Transparent)
                        .hoverable(interactionSource = clearInteraction)
                        .onFocusChanged { clearFocused = it.isFocused }
                        .navigationClickable(onClick = onRequestClearAll)
                        .navigationFocusable()
                        .padding(horizontal = tokens.vw * 1f, vertical = tokens.vw * 0.25f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = IconGlyphs.DeleteForever,
                        fontFamily = LocalIconFont.current,
                        color = if (clearFocused || clearHovered) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs12),
                        modifier = Modifier.padding(end = tokens.vw * 0.5f)
                    )
                    Text(
                        text = "Clear search history",
                        color = if (clearFocused || clearHovered) Color.White else Color(0xFFAAAAAA),
                        fontSize = fsSp(tokens.fs12),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchHistoryStrip(
    history: List<String>,
    hasAnyHistory: Boolean,
    query: String,
    portrait: Boolean,
    onReset: () -> Unit,
    onSelect: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestClearAll: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    val stripHeight = if (portrait) tokens.vh * 4f else tokens.vh * 7f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .padding(top = tokens.u * 0.3f),
        contentAlignment = Alignment.Center
    ) {
        if (history.isEmpty() && query.isBlank()) {
            Text(
                text = "No search history...",
                color = Color(0xFF888888),
                fontSize = fsSp(tokens.fs12)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = tokens.vw * 2f),
                horizontalArrangement = Arrangement.spacedBy(
                    if (portrait) tokens.vh * 0.4f else tokens.vw * 0.4f,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (query.isNotBlank()) {
                    item(key = "__reset__") {
                        val interactionSource = remember { MutableInteractionSource() }
                        val hovered by interactionSource.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.u * 4f))
                                .background(if (hovered) Color(0xFF660000) else Color.Transparent)
                                .hoverable(interactionSource = interactionSource)
                                .navigationClickable(onClick = onReset)
                                .padding(horizontal = tokens.u * 1.2f, vertical = tokens.u * 0.5f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f)
                        ) {
                            Text(
                                text = IconGlyphs.Clear,
                                fontFamily = LocalIconFont.current,
                                color = Color(0xFFAAAAAA),
                                fontSize = fsSp(tokens.fs12)
                            )
                            Text(
                                text = "Reset",
                                color = Color(0xFFAAAAAA),
                                fontSize = fsSp(tokens.fs12)
                            )
                        }
                    }
                }

                items(history, key = { it }) { term ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.u * 4f))
                            .background(if (hovered) colors.primary1 else Color(0xFF111111))
                            .hoverable(interactionSource = interactionSource)
                            .navigationClickable { onSelect(term) }
                            .padding(horizontal = tokens.u * 1.2f, vertical = tokens.u * 0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.5f)
                    ) {
                        Text(
                            text = IconGlyphs.Update,
                            fontFamily = LocalIconFont.current,
                            color = Color(0xFFAAAAAA),
                            fontSize = fsSp(tokens.fs12),
                            modifier = Modifier.clickable {
                                playClickSound()
                                onRequestDelete(term)
                            }
                        )
                        Text(
                            text = term,
                            color = Color(0xFFAAAAAA),
                            fontSize = fsSp(tokens.fs12),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = tokens.u * 20f)
                        )
                    }
                }

                if (query.isBlank() && hasAnyHistory) {
                    item(key = "__clear_all__") {
                        val interactionSource = remember { MutableInteractionSource() }
                        val hovered by interactionSource.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.u * 4f))
                                .background(if (hovered) Color(0xFF660000) else Color.Transparent)
                                .hoverable(interactionSource = interactionSource)
                                .navigationClickable(onClick = onRequestClearAll)
                                .padding(horizontal = tokens.u * 1.2f, vertical = tokens.u * 0.5f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.5f)
                        ) {
                            Text(
                                text = IconGlyphs.DeleteForever,
                                fontFamily = LocalIconFont.current,
                                color = Color(0xFFAAAAAA),
                                fontSize = fsSp(tokens.fs12)
                            )
                            Text(
                                text = "Clear search history",
                                color = Color(0xFFAAAAAA),
                                fontSize = fsSp(tokens.fs12)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sanitizeRemoteQuery(raw: String): String = raw
    .lowercase()
    .replace(Regex("[^\\w]+"), " ")
    .replace(Regex(" {2,}"), " ")

private fun hardwareKeyChar(key: Key): Char? = when (key) {
    Key.A -> 'a'; Key.B -> 'b'; Key.C -> 'c'; Key.D -> 'd'; Key.E -> 'e'
    Key.F -> 'f'; Key.G -> 'g'; Key.H -> 'h'; Key.I -> 'i'; Key.J -> 'j'
    Key.K -> 'k'; Key.L -> 'l'; Key.M -> 'm'; Key.N -> 'n'; Key.O -> 'o'
    Key.P -> 'p'; Key.Q -> 'q'; Key.R -> 'r'; Key.S -> 's'; Key.T -> 't'
    Key.U -> 'u'; Key.V -> 'v'; Key.W -> 'w'; Key.X -> 'x'; Key.Y -> 'y'
    Key.Z -> 'z'
    Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'; Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'; Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
