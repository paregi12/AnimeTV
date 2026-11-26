package com.paregi.animetv.features.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvInlineTabs
import com.paregi.animetv.core.ui.AnimeTvShelfPlaceholder
import com.paregi.animetv.core.ui.AnimeTvTitledShelf
import com.paregi.animetv.core.ui.CardDimensions
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.FocusCard
import com.paregi.animetv.core.ui.calculateCardDimensions
import com.paregi.animetv.core.ui.pbMenuEdgeFadeMask
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AccountRepository
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.MyListLayout
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.TrackedAnimeEntry
import com.paregi.animetv.domain.WatchProgress
import com.paregi.animetv.domain.allowedForRating
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable

internal fun myListSectionTitle(
    id: String,
    accountState: AccountState = AccountState(),
    forLayoutDialog: Boolean = false
): String = when (id) {
    "maltab" -> {
        val user = accountState.malUsername?.takeIf { it.isNotBlank() }
        if (forLayoutDialog) listOfNotNull("MAL Tabbed", user).joinToString(" ")
        else listOfNotNull("MAL", user).joinToString(" ")
    }
    "mal" -> listOfNotNull("MAL", accountState.malUsername?.takeIf { it.isNotBlank() }).joinToString(" ")
    "malplan" -> listOfNotNull("MAL Plan to Watch", accountState.malUsername?.takeIf { it.isNotBlank() }).joinToString(" ")
    "anilisttab" -> {
        val user = accountState.aniListUsername?.takeIf { it.isNotBlank() }
        if (forLayoutDialog) listOfNotNull("AniList Tabbed", user).joinToString(" ")
        else listOfNotNull("AniList", user).joinToString(" ")
    }
    "anilist" -> listOfNotNull("AniList", accountState.aniListUsername?.takeIf { it.isNotBlank() }).joinToString(" ")
    "anilistplan" -> listOfNotNull("AniList Plan to Watch", accountState.aniListUsername?.takeIf { it.isNotBlank() }).joinToString(" ")
    "fav", "watchlist" -> "Watchlist"
    "history" -> "History"
    else -> id
}

private val MAL_TAB_STATUSES = listOf(
    "watching" to "Watching",
    "plan_to_watch" to "Planning",
    "completed" to "Completed",
    "dropped" to "Dropped",
    "on_hold" to "On Hold"
)

private val ANILIST_TAB_STATUSES = listOf(
    "CURRENT" to "Current",
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "DROPPED" to "Dropped",
    "PAUSED" to "Paused",
    "REPEATING" to "Repeating"
)

@Composable
internal fun OriginalStyleMyListScreen(
    watchlist: List<Anime>,
    watchHistory: List<WatchProgress>,
    accountRepository: AccountRepository,
    accountState: AccountState,
    profileRating: Int,
    myListLayout: MyListLayout,
    onMyListLayoutChange: (MyListLayout) -> Unit,
    onOpenAnime: (Anime) -> Unit,
    onRemoveFromWatchlist: (Anime) -> Unit,
    onHistorySelected: (WatchProgress) -> Unit,
    onClearWatchHistory: () -> Unit,
    onScrollOffsetChange: (Dp) -> Unit = {},
    selectedSource: Int? = null
) {
    UnifiedMyListScreen(
        watchlist = watchlist,
        watchHistory = watchHistory,
        accountRepository = accountRepository,
        accountState = accountState,
        profileRating = profileRating,
        myListLayout = myListLayout,
        onMyListLayoutChange = onMyListLayoutChange,
        onOpenAnime = onOpenAnime,
        onRemoveFromWatchlist = onRemoveFromWatchlist,
        onHistorySelected = onHistorySelected,
        onClearWatchHistory = onClearWatchHistory,
        onScrollOffsetChange = onScrollOffsetChange,
        selectedSource = selectedSource
    )
}

@Composable
internal fun AccountListsScreen(
    watchlist: List<Anime>,
    watchHistory: List<WatchProgress>,
    accountRepository: AccountRepository,
    accountState: AccountState,
    profileRating: Int,
    myListLayout: MyListLayout,
    onMyListLayoutChange: (MyListLayout) -> Unit,
    onOpenAnime: (Anime) -> Unit,
    onRemoveFromWatchlist: (Anime) -> Unit,
    onHistorySelected: (WatchProgress) -> Unit,
    onClearWatchHistory: () -> Unit,
    onScrollOffsetChange: (Dp) -> Unit = {},
    selectedSource: Int? = null
) {
    UnifiedMyListScreen(
        watchlist = watchlist,
        watchHistory = watchHistory,
        accountRepository = accountRepository,
        accountState = accountState,
        profileRating = profileRating,
        myListLayout = myListLayout,
        onMyListLayoutChange = onMyListLayoutChange,
        onOpenAnime = onOpenAnime,
        onRemoveFromWatchlist = onRemoveFromWatchlist,
        onHistorySelected = onHistorySelected,
        onClearWatchHistory = onClearWatchHistory,
        onScrollOffsetChange = onScrollOffsetChange,
        selectedSource = selectedSource
    )
}

private data class PagedAccountShelfState(
    val entries: List<TrackedAnimeEntry> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val loading: Boolean = false
)

@Composable
private fun UnifiedMyListScreen(
    watchlist: List<Anime>,
    watchHistory: List<WatchProgress>,
    accountRepository: AccountRepository,
    accountState: AccountState,
    profileRating: Int,
    myListLayout: MyListLayout,
    onMyListLayoutChange: (MyListLayout) -> Unit,
    onOpenAnime: (Anime) -> Unit,
    onRemoveFromWatchlist: (Anime) -> Unit,
    onHistorySelected: (WatchProgress) -> Unit,
    onClearWatchHistory: () -> Unit,
    onScrollOffsetChange: (Dp) -> Unit = {},
    selectedSource: Int? = null
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedLayoutCb = onMyListLayoutChange
    @Suppress("UNUSED_VARIABLE")
    val unusedRemoveCb = onRemoveFromWatchlist

    val tokens = LocalAnimeTvTokens.current
    val density = LocalDensity.current
    val maskEnabled = !LocalPerformanceEffects.current.getOrElse(3) { false }

    var clearHistoryStage by remember { mutableIntStateOf(0) }
    var malTabStatus by remember { mutableStateOf("watching") }
    var aniListTabStatus by remember { mutableStateOf("CURRENT") }

    val pagedCache = remember { mutableStateMapOf<String, PagedAccountShelfState>() }

    val visibleSections = remember(myListLayout.sections, accountState.malUsername, accountState.aniListUsername) {
        val seen = mutableSetOf<String>()
        myListLayout.sections.mapNotNull { sec ->
            val normId = if (sec.id == "watchlist") "fav" else sec.id
            if (!sec.enabled || !seen.add(normId)) return@mapNotNull null
            val allowed = when (normId) {
                "maltab", "mal", "malplan" -> accountState.malUsername != null
                "anilisttab", "anilist", "anilistplan" -> accountState.aniListUsername != null
                "fav", "history" -> true
                else -> false
            }
            if (allowed) normId else null
        }
    }

    fun shelfKey(provider: AccountProvider, status: String) = "${provider.name}:$status"

    suspend fun loadShelfPage(provider: AccountProvider, status: String, page: Int) {
        val key = shelfKey(provider, status)
        val prev = pagedCache[key] ?: PagedAccountShelfState()
        if (prev.loading) return
        pagedCache[key] = prev.copy(loading = true)
        val loaded = runCatching {
            accountRepository.loadAccountList(provider, status, page)
        }.getOrNull()
        val current = pagedCache[key] ?: PagedAccountShelfState()
        if (loaded != null) {
            val filtered = loaded.entries.filter { it.anime.allowedForRating(profileRating) }.distinctBy { it.anime.id }
            val merged = if (page == 1) filtered else (current.entries + filtered).distinctBy { it.anime.id }
            pagedCache[key] = PagedAccountShelfState(
                entries = merged,
                page = page,
                hasNextPage = loaded.hasNextPage,
                loading = false
            )
        } else {
            pagedCache[key] = current.copy(loading = false, hasNextPage = false)
        }
    }

    // Trigger initial page loads for active MAL/AniList shelves and active tab statuses.
    val activeProviderRequests = remember(visibleSections, malTabStatus, aniListTabStatus) {
        buildList {
            visibleSections.forEach { id ->
                when (id) {
                    "maltab" -> add(AccountProvider.MAL to malTabStatus)
                    "mal" -> add(AccountProvider.MAL to "watching")
                    "malplan" -> add(AccountProvider.MAL to "plan_to_watch")
                    "anilisttab" -> add(AccountProvider.ANILIST to aniListTabStatus)
                    "anilist" -> add(AccountProvider.ANILIST to "CURRENT")
                    "anilistplan" -> add(AccountProvider.ANILIST to "PLANNING")
                }
            }
        }.distinct()
    }

    activeProviderRequests.forEach { (provider, status) ->
        val key = shelfKey(provider, status)
        LaunchedEffect(key, accountRepository, accountState.malUsername, accountState.aniListUsername, profileRating) {
            if (pagedCache[key] == null) {
                loadShelfPage(provider, status, 1)
            }
        }
    }

    var paginationTrigger by remember { mutableStateOf<Pair<AccountProvider, String>?>(null) }
    LaunchedEffect(paginationTrigger) {
        val (provider, status) = paginationTrigger ?: return@LaunchedEffect
        val key = shelfKey(provider, status)
        val state = pagedCache[key]
        if (state != null && state.hasNextPage && !state.loading) {
            loadShelfPage(provider, status, state.page + 1)
        }
        paginationTrigger = null
    }

    val listState = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = tokens.portrait
        val horizontalInset = if (portrait) tokens.vh * 2f else tokens.vw * 5f
        val topPad = if (portrait) tokens.vh * 2f + tokens.sysStatHeight else tokens.vw * 8f
        val bottomPad = if (portrait) tokens.sysNavHeight + tokens.vh * 8f else tokens.vw * 4f
        val cardDims = calculateCardDimensions(maxWidth, maxHeight, portrait, tokens.largeUi)
        val estimatedRowPx = with(density) { (cardDims.cardHeight + tokens.fs13 + tokens.u * 2.5f).toPx() }

        val scrollOffsetDp by remember(estimatedRowPx) {
            derivedStateOf {
                val px = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    listState.firstVisibleItemIndex * estimatedRowPx + listState.firstVisibleItemScrollOffset
                }
                with(density) { px.toDp() }
            }
        }
        LaunchedEffect(scrollOffsetDp) {
            onScrollOffsetChange(scrollOffsetDp)
        }

        val visibleWatchlist = remember(watchlist, profileRating, selectedSource) {
            watchlist.filter {
                (selectedSource == null || it.sourceIndex == selectedSource) &&
                    it.allowedForRating(profileRating)
            }
        }
        val visibleHistory = remember(watchHistory, profileRating, selectedSource) {
            watchHistory
                .sortedByDescending { it.updatedAtEpochMs }
                .filter {
                    (selectedSource == null || (it.anime?.sourceIndex ?: SourceCatalog.GOTAKU_INDEX) == selectedSource) &&
                        it.anime?.allowedForRating(profileRating) == true
                }
                .distinctBy { it.animeId to (it.anime?.sourceIndex ?: SourceCatalog.GOTAKU_INDEX) }
        }
        val openAccountAnime: (Anime) -> Unit = { anime ->
            onOpenAnime(selectedSource?.let { anime.copy(sourceIndex = it) } ?: anime)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(tokens.u * 1f)
        ) {
            if (portrait) {
                item(key = "mylist_title") {
                    Text(
                        text = "MyList",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs40),
                        lineHeight = fsSp(tokens.fs40) * 1.3f,
                        modifier = Modifier
                            .padding(start = tokens.vw * 5f, end = tokens.vw * 5f, bottom = tokens.vh * 2f)
                    )
                }
            }

            if (visibleSections.isEmpty()) {
                item(key = "mylist_empty") {
                    AnimeTvShelfPlaceholder(
                        loading = false,
                        emptyText = "No lists enabled",
                        cardHeight = cardDims.cardHeight
                    )
                }
            } else {
                visibleSections.forEach { sectionId ->
                    when (sectionId) {
                        "maltab" -> {
                            val state = pagedCache[shelfKey(AccountProvider.MAL, malTabStatus)] ?: PagedAccountShelfState()
                            item(key = "mylist_maltab") {
                                AccountTabbedShelf(
                                    title = myListSectionTitle("maltab", accountState, forLayoutDialog = false),
                                    tabs = MAL_TAB_STATUSES,
                                    selectedStatus = malTabStatus,
                                    onSelectStatus = { malTabStatus = it },
                                    state = state,
                                    isAniList = false,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.MAL to malTabStatus }
                                )
                            }
                        }
                        "mal" -> {
                            val state = pagedCache[shelfKey(AccountProvider.MAL, "watching")] ?: PagedAccountShelfState()
                            item(key = "mylist_mal") {
                                AccountShelf(
                                    title = myListSectionTitle("mal", accountState),
                                    state = state,
                                    isAniList = false,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.MAL to "watching" }
                                )
                            }
                        }
                        "malplan" -> {
                            val state = pagedCache[shelfKey(AccountProvider.MAL, "plan_to_watch")] ?: PagedAccountShelfState()
                            item(key = "mylist_malplan") {
                                AccountShelf(
                                    title = myListSectionTitle("malplan", accountState),
                                    state = state,
                                    isAniList = false,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.MAL to "plan_to_watch" }
                                )
                            }
                        }
                        "anilisttab" -> {
                            val state = pagedCache[shelfKey(AccountProvider.ANILIST, aniListTabStatus)] ?: PagedAccountShelfState()
                            item(key = "mylist_anilisttab") {
                                AccountTabbedShelf(
                                    title = myListSectionTitle("anilisttab", accountState, forLayoutDialog = false),
                                    tabs = ANILIST_TAB_STATUSES,
                                    selectedStatus = aniListTabStatus,
                                    onSelectStatus = { aniListTabStatus = it },
                                    state = state,
                                    isAniList = true,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.ANILIST to aniListTabStatus }
                                )
                            }
                        }
                        "anilist" -> {
                            val state = pagedCache[shelfKey(AccountProvider.ANILIST, "CURRENT")] ?: PagedAccountShelfState()
                            item(key = "mylist_anilist") {
                                AccountShelf(
                                    title = myListSectionTitle("anilist", accountState),
                                    state = state,
                                    isAniList = true,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.ANILIST to "CURRENT" }
                                )
                            }
                        }
                        "anilistplan" -> {
                            val state = pagedCache[shelfKey(AccountProvider.ANILIST, "PLANNING")] ?: PagedAccountShelfState()
                            item(key = "mylist_anilistplan") {
                                AccountShelf(
                                    title = myListSectionTitle("anilistplan", accountState),
                                    state = state,
                                    isAniList = true,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = openAccountAnime,
                                    onNearEnd = { paginationTrigger = AccountProvider.ANILIST to "PLANNING" }
                                )
                            }
                        }
                        "fav" -> {
                            item(key = "mylist_fav") {
                                LocalListShelf(
                                    title = myListSectionTitle("fav"),
                                    emptyText = "Watchlist is empty",
                                    animes = visibleWatchlist,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onOpenAnime = onOpenAnime
                                )
                            }
                        }
                        "history" -> {
                            item(key = "mylist_history") {
                                HistoryListShelf(
                                    title = myListSectionTitle("history"),
                                    history = visibleHistory,
                                    horizontalInset = horizontalInset,
                                    cardDims = cardDims,
                                    largeUi = tokens.largeUi,
                                    maskEnabled = maskEnabled,
                                    onHistorySelected = onHistorySelected,
                                    onRequestClear = { clearHistoryStage = 1 }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearHistoryStage > 0) {
        ConfirmDialog(
            title = if (clearHistoryStage == 1) "Clear Watch History" else "Confirm Watch History",
            titleIcon = IconGlyphs.Warning,
            message = if (clearHistoryStage == 1) {
                "<h6><c>warning</c> WARNING!!!</h6>" +
                    "This action will clear your watch history, and <b>can't be restored</b>.<br>" +
                    "<b>You want to continue?</b>"
            } else {
                "<h6><c>warning</c> WARNING!!!</h6>" +
                    "You Have <b>${visibleHistory.size}</b> anime in your watch history.<br>" +
                    "<b>Clear it now?</b>"
            },
            onConfirm = {
                if (clearHistoryStage == 1) {
                    clearHistoryStage = 2
                } else {
                    clearHistoryStage = 0
                    onClearWatchHistory()
                }
            },
            onDismiss = { clearHistoryStage = 0 }
        )
    }
}

@Composable
private fun AccountShelf(
    title: String,
    state: PagedAccountShelfState,
    isAniList: Boolean,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onOpenAnime: (Anime) -> Unit,
    onNearEnd: () -> Unit
) {
    val rowState = rememberLazyListState()
    val nearEnd by remember(state.entries.size, state.hasNextPage, state.loading) {
        derivedStateOf {
            if (!state.hasNextPage || state.loading || state.entries.isEmpty()) false
            else {
                val lastVisible = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= (state.entries.lastIndex - 3).coerceAtLeast(0)
            }
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onNearEnd()
    }

    AnimeTvTitledShelf(
        title = title,
        horizontalInset = horizontalInset,
        empty = state.entries.isEmpty(),
        loading = state.loading,
        emptyText = "No data...",
        cardHeight = cardDims.cardHeight,
        cardGap = cardDims.cardGap,
        rowState = rowState,
        maskEnabled = maskEnabled
    ) {
        itemsIndexed(state.entries, key = { idx, it -> "${idx}:${it.status}:${it.anime.id}" }) { _, entry ->
            val pct = entry.anime.episodeCount?.takeIf { it > 0 && entry.progress > 0 }?.let { total ->
                (entry.progress.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
            val epLabel = if (entry.progress > 0) "${entry.progress}" else "-"
            FocusCard(
                anime = entry.anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onOpenAnime(entry.anime) },
                progressPercent = pct,
                progressEpisode = epLabel,
                largeUi = largeUi,
                isAniListCard = isAniList
            )
        }
    }
}

@Composable
private fun AccountTabbedShelf(
    title: String,
    tabs: List<Pair<String, String>>,
    selectedStatus: String,
    onSelectStatus: (String) -> Unit,
    state: PagedAccountShelfState,
    isAniList: Boolean,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onOpenAnime: (Anime) -> Unit,
    onNearEnd: () -> Unit
) {
    var contentActive by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (contentActive) 2f else 1f)
    ) {
        AnimeTvInlineTabs(
            title = title,
            tabs = tabs,
            selectedTabId = selectedStatus,
            contentActive = contentActive,
            horizontalInset = horizontalInset,
            onTabSelected = onSelectStatus
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { contentActive = it.hasFocus }
        ) {
            AccountEntriesRow(
                entries = state.entries,
                loading = state.loading,
                hasNextPage = state.hasNextPage,
                isAniList = isAniList,
                horizontalInset = horizontalInset,
                cardDims = cardDims,
                largeUi = largeUi,
                maskEnabled = maskEnabled,
                onOpenAnime = onOpenAnime,
                onNearEnd = onNearEnd
            )
        }
    }
}

@Composable
private fun AccountEntriesRow(
    entries: List<TrackedAnimeEntry>,
    loading: Boolean,
    hasNextPage: Boolean,
    isAniList: Boolean,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onOpenAnime: (Anime) -> Unit,
    onNearEnd: () -> Unit
) {
    if (entries.isEmpty()) {
        AnimeTvShelfPlaceholder(
            loading = loading,
            emptyText = "No data...",
            cardHeight = cardDims.cardHeight
        )
        return
    }
    val rowState = rememberLazyListState()
    val nearEnd by remember(entries.size, hasNextPage, loading) {
        derivedStateOf {
            if (!hasNextPage || loading) false
            else {
                val lastVisible = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= (entries.lastIndex - 3).coerceAtLeast(0)
            }
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onNearEnd()
    }

    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .pbMenuEdgeFadeMask(rowState, enabled = maskEnabled),
        contentPadding = PaddingValues(horizontal = horizontalInset),
        horizontalArrangement = Arrangement.spacedBy(cardDims.cardGap)
    ) {
        itemsIndexed(entries, key = { idx, it -> "${idx}:${it.status}:${it.anime.id}" }) { _, entry ->
            val pct = entry.anime.episodeCount?.takeIf { it > 0 && entry.progress > 0 }?.let { total ->
                (entry.progress.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
            val epLabel = if (entry.progress > 0) "${entry.progress}" else "-"
            FocusCard(
                anime = entry.anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onOpenAnime(entry.anime) },
                progressPercent = pct,
                progressEpisode = epLabel,
                largeUi = largeUi,
                isAniListCard = isAniList
            )
        }
    }
}

@Composable
private fun LocalListShelf(
    title: String,
    emptyText: String,
    animes: List<Anime>,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onOpenAnime: (Anime) -> Unit
) {
    val rowState = rememberLazyListState()
    AnimeTvTitledShelf(
        title = title,
        horizontalInset = horizontalInset,
        empty = animes.isEmpty(),
        loading = false,
        emptyText = emptyText,
        cardHeight = cardDims.cardHeight,
        cardGap = cardDims.cardGap,
        rowState = rowState,
        maskEnabled = maskEnabled
    ) {
        itemsIndexed(animes, key = { idx, it -> "fav:${idx}:${it.sourceIndex}:${it.id}" }) { _, anime ->
            FocusCard(
                anime = anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onOpenAnime(anime) },
                largeUi = largeUi
            )
        }
    }
}

@Composable
private fun HistoryListShelf(
    title: String,
    history: List<WatchProgress>,
    horizontalInset: Dp,
    cardDims: CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onHistorySelected: (WatchProgress) -> Unit,
    onRequestClear: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val rowState = rememberLazyListState()
    var clearFocused by remember { mutableStateOf(false) }

    AnimeTvTitledShelf(
        title = title,
        horizontalInset = horizontalInset,
        empty = history.isEmpty(),
        loading = false,
        emptyText = "Watch history is empty",
        cardHeight = cardDims.cardHeight,
        cardGap = cardDims.cardGap,
        rowState = rowState,
        maskEnabled = maskEnabled,
        trailingHeaderContent = if (history.isNotEmpty()) {
            {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.u * 0.4f))
                        .background(if (clearFocused) Color(0xFF880000) else Color(0x22FF5555))
                        .onFocusChanged { clearFocused = it.isFocused }
                        .navigationClickable(onClick = onRequestClear)
                        .navigationFocusable()
                        .padding(horizontal = tokens.u * 0.8f, vertical = tokens.u * 0.25f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.3f)
                ) {
                    Text(
                        text = IconGlyphs.DeleteForever,
                        fontFamily = LocalIconFont.current,
                        color = if (clearFocused) Color.White else Color(0xFFFF7A7A),
                        fontSize = fsSp(tokens.fs10)
                    )
                    Text(
                        text = "Clear History",
                        color = if (clearFocused) Color.White else Color(0xFFFF7A7A),
                        fontSize = fsSp(tokens.fs10),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else null
    ) {
        itemsIndexed(history, key = { idx, it -> "history:${idx}:${it.anime?.sourceIndex ?: SourceCatalog.GOTAKU_INDEX}:${it.animeId}:${it.episodeId}" }) { _, progress ->
            val anime = progress.anime ?: return@itemsIndexed
            val pct = if (progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else null
            val epLabel = extractEpisodeNumber(progress.episodeId)
            FocusCard(
                anime = anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onHistorySelected(progress) },
                progressPercent = pct,
                progressEpisode = epLabel,
                largeUi = largeUi
            )
        }
    }
}

private fun extractEpisodeNumber(episodeId: String): String? {
    val pipeTail = episodeId.substringAfterLast('|', "")
    if (pipeTail.isNotBlank() && pipeTail.all { it.isDigit() }) return pipeTail
    val dashTail = episodeId.substringAfterLast('-', "")
    if (dashTail.isNotBlank() && dashTail.all { it.isDigit() }) return dashTail
    return null
}
