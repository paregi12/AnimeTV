package com.paregi.animetv.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.paregi.animetv.core.navigation.Section
import com.paregi.animetv.domain.*
import com.paregi.animetv.features.home.HomeScreen
import com.paregi.animetv.features.mylist.AccountListsScreen
import com.paregi.animetv.features.mylist.OriginalStyleMyListScreen
import com.paregi.animetv.features.schedule.ScheduleScreen
import com.paregi.animetv.features.search.SearchScreen
import com.paregi.animetv.features.settings.SettingsScreen

@Composable
internal fun SectionScreen(
    section: Section,
    tvLayout: Boolean,
    settings: AppSettings,
    profileRating: Int,
    profileActivation: Int,
    myListLayout: MyListLayout,
    onMyListLayoutChange: (MyListLayout) -> Unit,
    homeLayout: HomeLayout = HomeLayout.default(),
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackToHome: () -> Unit,
    onVoiceSearchResult: (String) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    homeFeed: HomeFeed,
    homeLoading: Boolean,
    watchlist: List<Anime>,
    watchHistory: List<WatchProgress>,
    schedule: List<AiringEntry>,
    scheduleLoading: Boolean,
    onRemoveFromWatchlist: (Anime) -> Unit,
    onClearWatchHistory: () -> Unit,
    accountRepository: AccountRepository,
    settingsRepository: SettingsRepository,
    settingsReady: Boolean,
    debugLogRepository: DebugLogRepository,
    searchHistoryRepository: SearchHistoryRepository,
    accountState: AccountState,
    onAccountStateChange: (AccountState) -> Unit,
    repository: AnimeRepository,
    onAnimeSelected: (Anime) -> Unit,
    onHistorySelected: (WatchProgress) -> Unit,
    onContinueWatching: (Anime) -> Unit,
    onScrollOffsetChange: (Dp) -> Unit = {},
    onRefreshHome: () -> Unit = {},
    onAboutFooterClick: (() -> Unit)? = null,
    isAdminProfile: Boolean = true,
    onReloadNeeded: () -> Unit = {}
) {
    when (section) {
        Section.Home -> {
            if (settings.homeWatchlist) {
                AccountListsScreen(
                    watchlist = watchlist,
                    watchHistory = watchHistory,
                    accountRepository = accountRepository,
                    accountState = accountState,
                    profileRating = profileRating,
                    myListLayout = myListLayout,
                    onMyListLayoutChange = onMyListLayoutChange,
                    onOpenAnime = onAnimeSelected,
                    onRemoveFromWatchlist = onRemoveFromWatchlist,
                    onHistorySelected = onHistorySelected,
                    onClearWatchHistory = onClearWatchHistory,
                    onScrollOffsetChange = onScrollOffsetChange
                )
            } else {
                HomeScreen(
                    homeFeed,
                    homeLoading,
                    settings.largeUi,
                    settings.trailerMode,
                    onAnimeSelected,
                    onContinueWatching,
                    watchHistory = watchHistory,
                    homeLayout = homeLayout,
                    onScrollOffsetChange = onScrollOffsetChange,
                    onRefreshRequested = onRefreshHome,
                    onAboutFooterClick = onAboutFooterClick
                )
            }
        }
        Section.Search -> SearchScreen(
            repository,
            tvLayout,
            settings.selectedSource,
            searchHistoryRepository,
            searchQuery,
            onSearchQueryChange,
            onBackToHome,
            onVoiceSearchResult,
            profileRating,
            profileActivation,
            onAnimeSelected,
            largeUi = settings.largeUi
        )
        Section.MyList -> OriginalStyleMyListScreen(
            watchlist = watchlist,
            watchHistory = watchHistory,
            accountRepository = accountRepository,
            accountState = accountState,
            profileRating = profileRating,
            myListLayout = myListLayout,
            onMyListLayoutChange = onMyListLayoutChange,
            onOpenAnime = { anime -> onAnimeSelected(anime.copy(sourceIndex = settings.selectedSource)) },
            onRemoveFromWatchlist = onRemoveFromWatchlist,
            onHistorySelected = onHistorySelected,
            onClearWatchHistory = onClearWatchHistory,
            onScrollOffsetChange = onScrollOffsetChange
        )
        Section.Schedule -> ScheduleScreen(
            schedule,
            scheduleLoading,
            settings.largeUi,
            onAnimeSelected,
            onScrollOffsetChange = onScrollOffsetChange
        )
        Section.Settings -> SettingsScreen(
            settings,
            onSettingsChange,
            accountRepository,
            accountState,
            onAccountStateChange,
            homeFeed,
            settingsRepository,
            settingsReady,
            debugLogRepository,
            repository,
            tvLayout = tvLayout,
            isAdminProfile = isAdminProfile,
            onReloadNeeded = onReloadNeeded
        )
    }
}
