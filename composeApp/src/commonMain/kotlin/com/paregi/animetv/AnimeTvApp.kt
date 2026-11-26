package com.paregi.animetv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paregi.animetv.app.ApplicationExitConfirmationDialog
import com.paregi.animetv.app.PlaybackCloseConfirmationDialog
import com.paregi.animetv.app.SectionScreen
import com.paregi.animetv.app.TrackingPromptDialog
import com.paregi.animetv.app.TrackingPromptState
import com.paregi.animetv.app.animeStateSaver
import com.paregi.animetv.app.episodePositionSaver
import com.paregi.animetv.app.episodeStateSaver
import com.paregi.animetv.core.navigation.AppHeader
import com.paregi.animetv.core.navigation.NavFloatingMenu
import com.paregi.animetv.core.navigation.Section
import com.paregi.animetv.core.navigation.SidebarDialog
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAppToast
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.animeTvThemeColorsFor
import com.paregi.animetv.core.theme.appTypography
import com.paregi.animetv.core.theme.badgeThemeColorsFor
import com.paregi.animetv.core.theme.colorSchemeForTheme
import com.paregi.animetv.core.theme.rememberAnimeTvTokens
import com.paregi.animetv.core.ui.AppToastOverlay
import com.paregi.animetv.core.ui.CardStylePreferences
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.LocalCardStyle
import com.paregi.animetv.core.ui.displayAnimeTitle
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AccountRepository
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.AiringEntry
import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AnimeDetails
import com.paregi.animetv.domain.AnimeRepository
import com.paregi.animetv.domain.AnimeTrackingState
import com.paregi.animetv.domain.AppSettings
import com.paregi.animetv.domain.DebugLogRepository
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.HomeLayout
import com.paregi.animetv.domain.LibraryRepository
import com.paregi.animetv.domain.MyListLayout
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.ProfileAppearance
import com.paregi.animetv.domain.ProfileDirectory
import com.paregi.animetv.domain.ProfileRepository
import com.paregi.animetv.domain.SearchHistoryRepository
import com.paregi.animetv.domain.SettingsRepository
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.domain.isAllowedForParentalRating
import com.paregi.animetv.domain.normalizeAgeRating
import com.paregi.animetv.domain.TranslationRepository
import com.paregi.animetv.domain.TvIntegrationRepository
import com.paregi.animetv.domain.TvLaunchRequest
import com.paregi.animetv.domain.UserProfile
import com.paregi.animetv.domain.WatchProgress
import com.paregi.animetv.domain.allowedForRating
import com.paregi.animetv.domain.filteredForRating
import com.paregi.animetv.features.details.DetailsScreen
import com.paregi.animetv.features.home.HomeLayoutDialog
import com.paregi.animetv.features.mylist.MyListLayoutDialog
import com.paregi.animetv.features.player.PlaybackErrorScreen
import com.paregi.animetv.features.player.PlaybackLoadingScreen
import com.paregi.animetv.features.player.PlaybackScreen
import com.paregi.animetv.features.profile.ProfileChooserScreen
import com.paregi.animetv.features.profile.ProfileLoadingScreen
import com.paregi.animetv.features.profile.ProfileOptionsFlowDialog
import com.paregi.animetv.features.profile.curatedWallpapers
import com.paregi.animetv.features.search.SearchScreen
import com.paregi.animetv.features.settings.SettingsScreen
import com.paregi.animetv.features.settings.SourceMirrorBenchmarkDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimeTvApp(
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    animeRepository: AnimeRepository,
    libraryRepository: LibraryRepository,
    searchHistoryRepository: SearchHistoryRepository,
    accountRepository: AccountRepository,
    tvIntegrationRepository: TvIntegrationRepository,
    translationRepository: TranslationRepository,
    debugLogRepository: DebugLogRepository,
    onExitApplication: () -> Unit
) {
    var profileDirectory by remember { mutableStateOf(ProfileDirectory(emptyList())) }
    var activeProfile by remember { mutableStateOf<UserProfile?>(null) }
    var savedActiveProfilePrefix by rememberSaveable { mutableStateOf<String?>(null) }
    var profileLoading by remember { mutableStateOf(true) }
    var loginScreenStyle by remember { mutableStateOf(1) }
    var profileAppearances by remember { mutableStateOf<Map<String, ProfileAppearance>>(emptyMap()) }
    var loginPreviewProfilePrefix by remember { mutableStateOf<String?>(null) }
    var profileActivation by remember { mutableStateOf(0) }
    var profileChooserOpen by remember { mutableStateOf(false) }
    var profileOptionsOpen by remember { mutableStateOf(false) }
    var profileOptionsStartManage by remember { mutableStateOf(false) }

    var section by rememberSaveable { mutableStateOf(Section.Home) }
    var mainSection by rememberSaveable { mutableStateOf(Section.Home) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsReloadPending by remember { mutableStateOf(false) }
    val scrollOffsets = remember { mutableStateMapOf<Section, Dp>() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingSearchFilter by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingSearchFilterNonce by remember { mutableIntStateOf(0) }
    var selectedAnime by rememberSaveable(stateSaver = animeStateSaver) { mutableStateOf<Anime?>(null) }
    var retainedDetailAnime by remember { mutableStateOf<Anime?>(null) }
    var retainedAnimeDetails by remember { mutableStateOf<AnimeDetails?>(null) }
    var retainedSynopsis by remember { mutableStateOf<String?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var selectedEpisode by rememberSaveable(stateSaver = episodeStateSaver) { mutableStateOf<Episode?>(null) }
    var resumeEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var animeDetails by remember { mutableStateOf<AnimeDetails?>(null) }
    var aniListInfoAnimeId by remember { mutableStateOf<String?>(null) }
    var aniListInfo by remember { mutableStateOf<AniListAnimeInfo?>(null) }
    var aniListInfoLoading by remember { mutableStateOf(false) }
    var aniListInfoLoaded by remember { mutableStateOf(false) }
    var aniListInfoError by remember { mutableStateOf<String?>(null) }
    var translatedSynopsis by remember { mutableStateOf<String?>(null) }
    val synopsisTranslationCache = remember { mutableStateMapOf<Pair<String, String>, String>() }
    val translatedSubtitleCache = remember { mutableStateMapOf<Pair<String, String>, SubtitleTrack>() }

    var playerPreviewAnime by remember { mutableStateOf<Anime?>(null) }
    var playerPreviewDetails by remember { mutableStateOf<AnimeDetails?>(null) }
    var playerPreviewLoading by remember { mutableStateOf(false) }
    var playerPreviewSynopsis by remember { mutableStateOf<String?>(null) }
    var playerPreviewTracking by remember { mutableStateOf(AnimeTrackingState()) }
    var playerPreviewTrackingLoading by remember { mutableStateOf(false) }
    var playerPreviewTrackingBusy by remember { mutableStateOf(false) }
    var playerPreviewTrackingMessage by remember { mutableStateOf<String?>(null) }
    var playerPreviewAniListInfoAnimeId by remember { mutableStateOf<String?>(null) }
    var playerPreviewAniListInfo by remember { mutableStateOf<AniListAnimeInfo?>(null) }
    var playerPreviewAniListInfoLoading by remember { mutableStateOf(false) }
    var playerPreviewAniListInfoLoaded by remember { mutableStateOf(false) }
    var playerPreviewAniListInfoError by remember { mutableStateOf<String?>(null) }

    var appSettings by remember { mutableStateOf(AppSettings()) }
    var myListLayout by remember { mutableStateOf(MyListLayout.default()) }
    var homeLayout by remember { mutableStateOf(HomeLayout.default()) }
    var navActionMenuSection by remember { mutableStateOf<Section?>(null) }
    var homeLayoutDialogOpen by remember { mutableStateOf(false) }
    var myListLayoutDialogOpen by remember { mutableStateOf(false) }
    var clearHistoryStage by remember { mutableIntStateOf(0) }
    var settingsReady by remember { mutableStateOf(false) }
    var pendingTvWatchNextSource by remember { mutableStateOf<Int?>(null) }
    var homeFeed by remember { mutableStateOf(HomeFeed()) }
    var watchlist by remember { mutableStateOf(emptyList<Anime>()) }
    var watchHistory by remember { mutableStateOf(emptyList<WatchProgress>()) }
    var accountState by remember { mutableStateOf(AccountState()) }
    var animeTracking by remember { mutableStateOf(AnimeTrackingState()) }
    var trackingLoading by remember { mutableStateOf(false) }
    var trackingBusy by remember { mutableStateOf(false) }
    var trackingMessage by remember { mutableStateOf<String?>(null) }
    var trackingPrompt by remember { mutableStateOf<TrackingPromptState?>(null) }
    var playerTrackingDialogOpen by remember { mutableStateOf(false) }
    var removeTrackingConfirm by remember { mutableStateOf<AccountProvider?>(null) }
    var skippedTrackingPromptIds by remember { mutableStateOf(emptySet<String>()) }
    var schedule by remember { mutableStateOf(emptyList<AiringEntry>()) }
    var scheduleCacheKey by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var homeLoading by remember { mutableStateOf(true) }
    var scheduleLoading by remember { mutableStateOf(false) }
    var playbackChoice by remember { mutableStateOf<PlaybackChoice?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var playbackStreamOverride by remember { mutableStateOf<PlaybackStreamType?>(null) }
    var playbackMirrorOverride by remember { mutableStateOf<Int?>(null) }
    var playbackPositionMs by rememberSaveable(selectedEpisode?.id) { mutableStateOf<Long?>(null) }
    var playbackDurationMs by rememberSaveable(selectedEpisode?.id) { mutableStateOf<Long?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var tvResumePosition by rememberSaveable(stateSaver = episodePositionSaver) {
        mutableStateOf<Pair<String, Long>?>(null)
    }
    var closePlaybackConfirmation by remember { mutableStateOf(false) }
    var exitConfirmation by remember { mutableStateOf(false) }
    var showPlaybackBackHint by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var sidebarExitArmed by remember { mutableStateOf(false) }
    var benchmarkSourceIndex by remember { mutableStateOf<Int?>(null) }
    var playbackStartOverride by rememberSaveable(stateSaver = episodePositionSaver) {
        mutableStateOf<Pair<String, Long>?>(null)
    }
    var preloadedPlaybackChoice by remember { mutableStateOf<Pair<String, PlaybackChoice>?>(null) }
    var preloadedAppliedEpisodeId by remember { mutableStateOf<String?>(null) }

    var appToastMessage by remember { mutableStateOf<String?>(null) }
    var appToastVersion by remember { mutableIntStateOf(0) }
    val showToast: (String) -> Unit = remember {
        { message ->
            if (message.isNotBlank()) {
                appToastMessage = message
                appToastVersion += 1
            }
        }
    }
    LaunchedEffect(appToastVersion) {
        if (appToastVersion > 0 && !appToastMessage.isNullOrBlank()) {
            delay(2_000)
            appToastMessage = null
        }
    }

    val coroutineScope = rememberCoroutineScope()

    fun selectMainSection(next: Section) {
        if (next == Section.Home || next == Section.MyList || next == Section.Schedule) {
            if (mainSection != next) {
                scrollOffsets[next] = 0.dp
            }
            mainSection = next
        }
        section = next
    }

    fun refreshHome(clearCache: Boolean = false) {
        homeLoading = true
        coroutineScope.launch {
            if (clearCache) settingsRepository.clearCache()
            val currentDomain = appSettings.sourceDomains[appSettings.selectedSource]
            animeRepository.selectSource(appSettings.selectedSource, currentDomain)
            val rating = activeProfile?.parentalRating ?: 6
            homeFeed = runCatching {
                animeRepository.loadHome(
                    useAniListTrending = appSettings.aniListSlideshow,
                    highResolutionImages = appSettings.highResolutionBanners
                )
            }.getOrElse { HomeFeed() }.filteredForRating(rating)
            homeLoading = false
        }
    }

    fun refreshSchedule(force: Boolean = true) {
        if (!force) return
        coroutineScope.launch {
            scheduleLoading = true
            val rating = activeProfile?.parentalRating ?: 6
            schedule = runCatching {
                animeRepository.loadSchedule(includeNonJapan = appSettings.includeChineseAnime)
            }.getOrDefault(emptyList()).filter { it.anime.allowedForRating(rating) }
            scheduleCacheKey = appSettings.selectedSource to appSettings.includeChineseAnime
            scheduleLoading = false
        }
    }

    fun closeSettings() {
        settingsOpen = false
        if (settingsReloadPending) {
            settingsReloadPending = false
            refreshHome()
            if (section == Section.Schedule || scheduleCacheKey != null) {
                scheduleCacheKey = null
                if (section == Section.Schedule) refreshSchedule()
            }
        }
    }

    fun startPlayback(episode: Episode) {
        if (preloadedPlaybackChoice?.first == episode.id) {
            preloadedAppliedEpisodeId = episode.id
            playbackChoice = preloadedPlaybackChoice?.second
            preloadedPlaybackChoice = null
            playbackError = null
            selectedEpisode = episode
            return
        }
        preloadedAppliedEpisodeId = null
        playbackChoice = null
        playbackError = null
        selectedEpisode = episode
    }

    fun activateProfile(profile: UserProfile, preserveNavigation: Boolean = false) {
        profileActivation += 1
        profileRepository.activate(profile)
        activeProfile = profile
        savedActiveProfilePrefix = profile.prefix
        profileChooserOpen = false
        profileOptionsOpen = false
        if (!preserveNavigation) {
            section = Section.Home
            mainSection = Section.Home
            scrollOffsets.clear()
            selectedAnime = null
            selectedEpisode = null
            searchQuery = ""
        }
        retainedDetailAnime = null
        retainedAnimeDetails = null
        retainedSynopsis = null
        detailsLoading = false
        animeDetails = null
        aniListInfoAnimeId = null
        aniListInfo = null
        aniListInfoLoading = false
        aniListInfoLoaded = false
        aniListInfoError = null
        translatedSynopsis = null
        appSettings = AppSettings()
        myListLayout = MyListLayout.default()
        homeLayout = HomeLayout.default()
        homeFeed = HomeFeed()
        homeLoading = true
        schedule = emptyList()
        scheduleCacheKey = null
        playbackChoice = null
        playbackStreamOverride = null
        playbackMirrorOverride = null
        playbackError = null
        preloadedPlaybackChoice = null
        preloadedAppliedEpisodeId = null
        tvResumePosition = null
        playbackStartOverride = null
        closePlaybackConfirmation = false
        showPlaybackBackHint = false
        sidebarOpen = false
        sidebarExitArmed = false
        settingsReady = false
        pendingTvWatchNextSource = null
        settingsOpen = false
        settingsReloadPending = false
        pendingSearchFilter = null
        translatedSubtitleCache.clear()
        synopsisTranslationCache.clear()
        playerPreviewAnime = null
        playerPreviewDetails = null
        playerPreviewLoading = false
        playerPreviewSynopsis = null
        playerPreviewTracking = AnimeTrackingState()
        playerPreviewTrackingLoading = false
        playerPreviewTrackingBusy = false
        playerPreviewTrackingMessage = null
        playerPreviewAniListInfoAnimeId = null
        playerPreviewAniListInfo = null
        playerPreviewAniListInfoLoading = false
        playerPreviewAniListInfoLoaded = false
        playerPreviewAniListInfoError = null
        watchlist = emptyList()
        watchHistory = emptyList()
        accountState = AccountState()
        animeTracking = AnimeTrackingState()
        trackingLoading = false
        trackingBusy = false
        trackingMessage = null
        trackingPrompt = null
        playerTrackingDialogOpen = false
        removeTrackingConfirm = null
        skippedTrackingPromptIds = emptySet()
    }

    fun saveProfileDirectory(directory: ProfileDirectory) {
        profileDirectory = directory
        coroutineScope.launch { profileRepository.saveProfiles(directory) }
    }

    fun saveMyListLayout(layout: MyListLayout) {
        myListLayout = layout
        coroutineScope.launch { settingsRepository.saveMyListLayout(layout) }
    }

    fun saveHomeLayout(layout: HomeLayout) {
        homeLayout = layout
        coroutineScope.launch { settingsRepository.saveHomeLayout(layout) }
    }

    fun closePlayerPreview() {
        playerPreviewAnime = null
        playerPreviewDetails = null
        playerPreviewLoading = false
        playerPreviewSynopsis = null
        playerPreviewTracking = AnimeTrackingState()
        playerPreviewTrackingLoading = false
        playerPreviewTrackingBusy = false
        playerPreviewTrackingMessage = null
        playerPreviewAniListInfoAnimeId = null
        playerPreviewAniListInfo = null
        playerPreviewAniListInfoLoading = false
        playerPreviewAniListInfoLoaded = false
        playerPreviewAniListInfoError = null
    }

    fun isAnimeRestricted(anime: Anime): Boolean =
        !anime.isAllowedForParentalRating(activeProfile?.parentalRating ?: 6)

    suspend fun translateSynopsisCached(synopsis: String?, language: String): String? {
        if (synopsis.isNullOrBlank() || language.isBlank() ||
            language in setOf("hard", "nosub", "dub", "en")
        ) return null
        val key = synopsis to language
        synopsisTranslationCache[key]?.let { return it }
        val translated = try {
            translationRepository.translateText(synopsis, language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!translated.isNullOrBlank()) {
            synopsisTranslationCache[key] = translated
        }
        return translated
    }

    fun requestPreviewAniListInfo(anime: Anime, queryAnime: Anime = anime, retry: Boolean = false) {
        if (!retry && playerPreviewAniListInfoAnimeId == anime.id &&
            (playerPreviewAniListInfoLoading || playerPreviewAniListInfoLoaded)
        ) return
        playerPreviewAniListInfoAnimeId = anime.id
        playerPreviewAniListInfo = null
        playerPreviewAniListInfoError = null
        playerPreviewAniListInfoLoading = true
        playerPreviewAniListInfoLoaded = false
        coroutineScope.launch {
            val result = runCatching { animeRepository.loadAniListInfo(queryAnime) }
            if (playerPreviewAnime?.id == anime.id) {
                playerPreviewAniListInfo = result.getOrNull()
                playerPreviewAniListInfoError = result.exceptionOrNull()?.message
                playerPreviewAniListInfoLoading = false
                playerPreviewAniListInfoLoaded = true
            }
        }
    }

    fun requestAniListInfo(anime: Anime, queryAnime: Anime = anime, retry: Boolean = false) {
        if (!retry && aniListInfoAnimeId == anime.id && (aniListInfoLoading || aniListInfoLoaded)) return
        aniListInfoAnimeId = anime.id
        aniListInfo = null
        aniListInfoError = null
        aniListInfoLoading = true
        aniListInfoLoaded = false
        coroutineScope.launch {
            val result = runCatching { animeRepository.loadAniListInfo(queryAnime) }
            if (selectedAnime?.id == anime.id) {
                aniListInfo = result.getOrNull()
                aniListInfoError = result.exceptionOrNull()?.message
                aniListInfoLoading = false
                aniListInfoLoaded = true
            }
        }
    }

    LaunchedEffect(profileRepository) {
        val directory = runCatching { profileRepository.loadProfiles() }
            .getOrElse {
                ProfileDirectory(listOf(UserProfile("", "Default User")), "")
            }
        profileDirectory = directory
        profileLoading = false
        val restoredProfile = directory.profiles.firstOrNull {
            it.prefix == savedActiveProfilePrefix
        }
        val singleProfile = directory.profiles.singleOrNull()
        val defaultProfile = directory.defaultPrefix
            ?.takeIf { it.isNotBlank() }
            ?.let { prefix -> directory.profiles.firstOrNull { it.prefix == prefix } }
        when {
            restoredProfile != null && restoredProfile.pin.isBlank() -> {
                activateProfile(restoredProfile, preserveNavigation = true)
            }
            restoredProfile != null -> {
                loginPreviewProfilePrefix = restoredProfile.prefix
                profileChooserOpen = true
            }
            savedActiveProfilePrefix != null -> {
                savedActiveProfilePrefix = null
                if (singleProfile != null && singleProfile.pin.isBlank()) activateProfile(singleProfile)
                else if (defaultProfile != null && defaultProfile.pin.isBlank()) activateProfile(defaultProfile)
                else profileChooserOpen = true
            }
            singleProfile != null && singleProfile.pin.isBlank() -> activateProfile(singleProfile)
            defaultProfile != null && defaultProfile.pin.isBlank() -> activateProfile(defaultProfile)
            else -> profileChooserOpen = true
        }
    }

    LaunchedEffect(settingsRepository, profileDirectory.profiles, activeProfile?.prefix, profileChooserOpen) {
        if (activeProfile != null && !profileChooserOpen) return@LaunchedEffect
        loginScreenStyle = runCatching { settingsRepository.readLoginScreenStyle() }.getOrDefault(1)
        val appearances = mutableMapOf<String, ProfileAppearance>()
        profileDirectory.profiles.forEach { profile ->
            appearances[profile.prefix] = runCatching {
                settingsRepository.readProfileAppearance(profile.prefix)
            }.getOrDefault(ProfileAppearance())
        }
        profileAppearances = appearances
        loginPreviewProfilePrefix = loginPreviewProfilePrefix
            ?.takeIf { prefix -> profileDirectory.profiles.any { it.prefix == prefix } }
            ?: profileDirectory.defaultPrefix
            ?.takeIf { prefix -> profileDirectory.profiles.any { it.prefix == prefix } }
            ?: profileDirectory.profiles.firstOrNull()?.prefix
    }

    LaunchedEffect(settingsRepository, activeProfile?.prefix, profileActivation) {
        if (activeProfile == null) {
            settingsReady = false
            return@LaunchedEffect
        }
        settingsReady = false
        val stored = runCatching { settingsRepository.readSettings() }.getOrDefault(AppSettings())
        val watchNextSource = pendingTvWatchNextSource?.also { pendingTvWatchNextSource = null }
        val desiredSource = watchNextSource ?: stored.selectedSource
        val availableSource = animeRepository.selectSource(desiredSource, stored.sourceDomains[desiredSource])
        val resolvedSettings = stored.copy(selectedSource = availableSource)
        if (watchNextSource != null && resolvedSettings.selectedSource != stored.selectedSource) {
            runCatching { settingsRepository.updateSettings(resolvedSettings) }
        }
        appSettings = resolvedSettings
        myListLayout = runCatching { settingsRepository.readMyListLayout() }
            .getOrDefault(MyListLayout.default())
        homeLayout = runCatching { settingsRepository.readHomeLayout() }
            .getOrDefault(HomeLayout.default())
        settingsReady = true
    }

    LaunchedEffect(
        animeRepository,
        settingsReady,
        appSettings.selectedSource,
        appSettings.sourceDomains[appSettings.selectedSource],
        appSettings.aniListSlideshow,
        appSettings.highResolutionBanners,
        activeProfile?.parentalRating,
        profileActivation
    ) {
        if (!settingsReady) return@LaunchedEffect
        val currentDomain = appSettings.sourceDomains[appSettings.selectedSource]
        animeRepository.selectSource(appSettings.selectedSource, currentDomain)
        homeLoading = true
        val rating = activeProfile?.parentalRating ?: 6
        homeFeed = runCatching {
            animeRepository.loadHome(
                useAniListTrending = appSettings.aniListSlideshow,
                highResolutionImages = appSettings.highResolutionBanners
            )
        }.getOrElse { HomeFeed() }.filteredForRating(rating)
        homeLoading = false
    }

    LaunchedEffect(libraryRepository, activeProfile?.prefix, profileActivation) {
        if (activeProfile == null) {
            watchlist = emptyList()
            watchHistory = emptyList()
            return@LaunchedEffect
        }
        watchlist = runCatching { libraryRepository.loadWatchlist() }.getOrDefault(emptyList())
        watchHistory = runCatching { libraryRepository.loadHistory() }.getOrDefault(emptyList())
    }

    LaunchedEffect(showPlaybackBackHint) {
        if (showPlaybackBackHint) {
            delay(1_000)
            showPlaybackBackHint = false
        }
    }

    LaunchedEffect(accountRepository, settingsReady, activeProfile?.prefix, profileActivation) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        accountState = runCatching { accountRepository.readAccounts() }.getOrDefault(AccountState())
    }

    LaunchedEffect(
        selectedAnime?.id,
        selectedAnime?.aniListId,
        selectedAnime?.malId,
        accountState.aniListUsername,
        accountState.malUsername,
        accountRepository,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val anime = selectedAnime
        trackingMessage = null
        if (anime == null) {
            animeTracking = AnimeTrackingState()
            trackingLoading = false
        } else {
            trackingLoading = true
            animeTracking = runCatching { accountRepository.loadTracking(anime) }
                .getOrDefault(AnimeTrackingState())
            trackingLoading = false
        }
    }

    LaunchedEffect(section, animeRepository, settingsReady, appSettings.selectedSource, appSettings.includeChineseAnime, profileActivation) {
        if (!settingsReady) return@LaunchedEffect
        val requestedScheduleKey = appSettings.selectedSource to appSettings.includeChineseAnime
        if (section == Section.Schedule && scheduleCacheKey != requestedScheduleKey) {
            scheduleLoading = true
            val rating = activeProfile?.parentalRating ?: 6
            schedule = runCatching {
                animeRepository.loadSchedule(includeNonJapan = appSettings.includeChineseAnime)
            }.getOrDefault(emptyList()).filter { it.anime.allowedForRating(rating) }
            scheduleCacheKey = requestedScheduleKey
            scheduleLoading = false
        }
    }

    LaunchedEffect(
        selectedAnime?.id,
        animeRepository,
        appSettings.selectedSource,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val anime = selectedAnime
        if (anime == null) {
            animeDetails = null
            detailsLoading = false
        } else {
            animeDetails = null
            detailsLoading = true
            animeRepository.selectSource(appSettings.selectedSource)
            val detailResult = runCatching { animeRepository.loadDetails(anime.id) }
            val loadedDetails = detailResult.getOrNull()
            if (selectedAnime?.id != anime.id) return@LaunchedEffect
            detailsLoading = false
            if (loadedDetails == null &&
                detailResult.exceptionOrNull()?.message == "This title is not available from the selected source"
            ) {
                searchQuery = anime.title
                selectedAnime = null
                retainedDetailAnime = null
                retainedAnimeDetails = null
                retainedSynopsis = null
                resumeEpisodeId = null
                section = Section.Search
                return@LaunchedEffect
            }
            animeDetails = loadedDetails
            if (loadedDetails != null &&
                !loadedDetails.anime.allowedForRating(activeProfile?.parentalRating ?: 6)
            ) {
                selectedAnime = null
                retainedDetailAnime = null
                retainedAnimeDetails = null
                retainedSynopsis = null
                resumeEpisodeId = null
                animeDetails = null
                showToast("PARENTAL: Opening anime is not allowed...")
                return@LaunchedEffect
            }
            retainedAnimeDetails = loadedDetails?.copy(
                related = loadedDetails.related.filter { it.allowedForRating(activeProfile?.parentalRating ?: 6) },
                recommendations = loadedDetails.recommendations.filter {
                    it.allowedForRating(activeProfile?.parentalRating ?: 6)
                }
            )
            retainedSynopsis = loadedDetails?.anime?.synopsis ?: anime.synopsis
            val resumeId = resumeEpisodeId
            if (resumeId != null) {
                loadedDetails?.episodes?.firstOrNull { it.id == resumeId }?.let(::startPlayback)
                resumeEpisodeId = null
            }
        }
    }

    LaunchedEffect(
        selectedAnime?.id,
        animeDetails?.anime?.synopsis,
        appSettings.subtitleLanguage,
        translationRepository
    ) {
        val animeId = selectedAnime?.id
        val synopsis = animeDetails?.anime?.synopsis ?: selectedAnime?.synopsis
        val language = appSettings.subtitleLanguage
        val translated = translateSynopsisCached(synopsis, language)
        translatedSynopsis = translated
        if (animeId != null && selectedAnime?.id == animeId) {
            retainedSynopsis = translated ?: synopsis ?: retainedDetailAnime?.synopsis
        }
    }

    LaunchedEffect(
        playerPreviewAnime?.id,
        animeRepository,
        appSettings.selectedSource,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val preview = playerPreviewAnime
        if (preview == null) {
            playerPreviewDetails = null
            playerPreviewLoading = false
        } else {
            playerPreviewDetails = null
            playerPreviewLoading = true
            animeRepository.selectSource(appSettings.selectedSource)
            val result = runCatching { animeRepository.loadDetails(preview.id) }
            val loaded = result.getOrNull()
            if (playerPreviewAnime?.id != preview.id) return@LaunchedEffect
            playerPreviewLoading = false
            if (loaded != null && !loaded.anime.allowedForRating(activeProfile?.parentalRating ?: 6)) {
                closePlayerPreview()
                showToast("PARENTAL: Opening anime is not allowed...")
                return@LaunchedEffect
            }
            if (loaded == null &&
                result.exceptionOrNull()?.message == "This title is not available from the selected source"
            ) {
                showToast("This title is not available from the selected source")
            }
            playerPreviewDetails = loaded
            playerPreviewSynopsis = loaded?.anime?.synopsis ?: preview.synopsis
        }
    }

    LaunchedEffect(
        playerPreviewAnime?.id,
        playerPreviewDetails?.anime?.synopsis,
        appSettings.subtitleLanguage,
        translationRepository
    ) {
        val preview = playerPreviewAnime ?: return@LaunchedEffect
        val synopsis = playerPreviewDetails?.anime?.synopsis ?: preview.synopsis
        val translated = translateSynopsisCached(synopsis, appSettings.subtitleLanguage)
        if (playerPreviewAnime?.id == preview.id) {
            playerPreviewSynopsis = translated ?: synopsis
        }
    }

    LaunchedEffect(
        playerPreviewAnime?.id,
        playerPreviewAnime?.aniListId,
        playerPreviewAnime?.malId,
        playerPreviewDetails?.anime?.aniListId,
        playerPreviewDetails?.anime?.malId,
        accountState.aniListUsername,
        accountState.malUsername,
        accountRepository,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val target = playerPreviewDetails?.anime ?: playerPreviewAnime
        playerPreviewTrackingMessage = null
        if (target == null) {
            playerPreviewTracking = AnimeTrackingState()
            playerPreviewTrackingLoading = false
        } else {
            playerPreviewTrackingLoading = true
            val loaded = runCatching { accountRepository.loadTracking(target) }
                .getOrDefault(AnimeTrackingState())
            if ((playerPreviewDetails?.anime ?: playerPreviewAnime)?.id == target.id) {
                playerPreviewTracking = loaded
                playerPreviewTrackingLoading = false
            }
        }
    }

    val hardSubOnlyPreference = !appSettings.preferDub &&
        playbackStreamOverride == null &&
        selectedAnime?.sourceIndex != SourceCatalog.REANIME_INDEX &&
        appSettings.subtitleLanguage == "hard"

    LaunchedEffect(
        appSettings.preferDub,
        playbackStreamOverride,
        playbackMirrorOverride,
        appSettings.mirrorServer,
        appSettings.selectedSource,
        hardSubOnlyPreference
    ) {
        preloadedPlaybackChoice = null
        preloadedAppliedEpisodeId = null
    }

    LaunchedEffect(
        selectedEpisode?.id,
        appSettings.preferDub,
        playbackStreamOverride,
        playbackMirrorOverride,
        appSettings.mirrorServer,
        appSettings.selectedSource,
        hardSubOnlyPreference,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val episode = selectedEpisode ?: return@LaunchedEffect
        if (preloadedAppliedEpisodeId == episode.id && playbackChoice != null) {
            preloadedAppliedEpisodeId = null
            return@LaunchedEffect
        }
        preloadedAppliedEpisodeId = null
        val requestedSource = appSettings.selectedSource
        val requestedMirror = playbackMirrorOverride ?: appSettings.mirrorServer
        val requestedSubtitleLanguage = appSettings.subtitleLanguage
        val requestedPreferDub = appSettings.preferDub
        val requestedStreamOverride = playbackStreamOverride
        val requestedHardSub = hardSubOnlyPreference
        val requestedStreamType = playbackStreamOverride ?: when {
            appSettings.preferDub && episode.hasDub -> PlaybackStreamType.DUB
            requestedHardSub && episode.hasSub -> PlaybackStreamType.SUB
            else -> null
        }
        val preferDub = episode.hasDub &&
            (requestedStreamType == PlaybackStreamType.DUB ||
                (!episode.hasSub && requestedStreamType == null))
        fun requestIsCurrent(): Boolean {
            val currentHardSub = !appSettings.preferDub &&
                playbackStreamOverride == null &&
                selectedAnime?.sourceIndex != SourceCatalog.REANIME_INDEX &&
                appSettings.subtitleLanguage == "hard"
            val currentMirror = playbackMirrorOverride ?: appSettings.mirrorServer
            return selectedEpisode?.id == episode.id &&
                appSettings.selectedSource == requestedSource &&
                currentMirror == requestedMirror &&
                appSettings.preferDub == requestedPreferDub &&
                playbackStreamOverride == requestedStreamOverride &&
                currentHardSub == requestedHardSub
        }
        playbackChoice = null
        playbackError = null
        try {
            animeRepository.selectSource(requestedSource)
            val resolvedChoice = animeRepository.resolvePlayback(
                episodeId = episode.id,
                preferDub = preferDub,
                mirrorIndex = requestedMirror,
                subtitleLanguage = requestedSubtitleLanguage,
                hasSub = episode.hasSub,
                requestedStreamType = requestedStreamType
            )
            if (!requestIsCurrent()) return@LaunchedEffect
            playbackChoice = resolvedChoice
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!requestIsCurrent()) return@LaunchedEffect
            val errMsg = error.message ?: "Could not resolve this episode"
            playbackError = errMsg
            showToast(errMsg)
        }
    }

    LaunchedEffect(
        playbackChoice?.url,
        appSettings.subtitleLanguage,
        settingsReady,
        activeProfile?.prefix
    ) {
        if (!settingsReady || activeProfile == null) return@LaunchedEffect
        val choice = playbackChoice ?: return@LaunchedEffect
        val targetLanguage = appSettings.subtitleLanguage
        if (targetLanguage.isBlank() || targetLanguage in setOf("hard", "nosub", "dub", "en")) {
            return@LaunchedEffect
        }
        fun matchesTarget(track: SubtitleTrack): Boolean {
            val trackLang = track.language.lowercase()
            val target = targetLanguage.lowercase()
            return trackLang == target || trackLang.substringBefore('-') == target.substringBefore('-')
        }
        if (choice.subtitles.any(::matchesTarget)) return@LaunchedEffect
        val sourceUrlKey = (choice.subtitles.firstOrNull {
            it.language.equals("en", ignoreCase = true) || it.language.startsWith("en-", ignoreCase = true)
        } ?: choice.subtitles.firstOrNull { it.defaultSelected } ?: choice.subtitles.firstOrNull())?.url
            ?: return@LaunchedEffect
        val cacheKey = sourceUrlKey to targetLanguage
        val cached = translatedSubtitleCache[cacheKey]
        val translated = if (cached != null) {
            cached
        } else {
            try {
                animeRepository.translatePlaybackSubtitle(choice, targetLanguage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }?.also { translatedSubtitleCache[cacheKey] = it }
        } ?: return@LaunchedEffect
        playbackChoice = playbackChoice?.let { current ->
            if (current.url == choice.url && current.subtitles.none { it.id == translated.id }) {
                current.copy(subtitles = current.subtitles + translated)
            } else {
                current
            }
        }
    }

    val onSettingsChange: (AppSettings) -> Unit = { newSettings ->
        loginScreenStyle = newSettings.loginScreenStyle.coerceIn(0, 2)
        coroutineScope.launch {
            val domain = newSettings.sourceDomains[newSettings.selectedSource]
            val availableSource = animeRepository.selectSource(newSettings.selectedSource, domain)
            val adjusted = newSettings.copy(selectedSource = availableSource)
            settingsRepository.updateSettings(adjusted)
            appSettings = adjusted
        }
    }

    fun openVoiceSearchResult(value: String) {
        val term = value.trim()
        if (term.isBlank()) return
        searchQuery = term
        section = Section.Search
        coroutineScope.launch { runCatching { searchHistoryRepository.record(term) } }
    }

    fun openAnime(anime: Anime, resumeId: String?, allowSourceSwitch: Boolean = false) {
        if (isAnimeRestricted(anime)) {
            showToast("PARENTAL: Opening anime is not allowed...")
        } else {
            closePlayerPreview()
            selectedEpisode = null
            playbackChoice = null
            playbackStreamOverride = null
            playbackMirrorOverride = null
            preloadedPlaybackChoice = null
            preloadedAppliedEpisodeId = null
            playbackError = null
            tvResumePosition = null
            playbackStartOverride = null
            aniListInfoAnimeId = null
            aniListInfo = null
            aniListInfoLoading = false
            aniListInfoLoaded = false
            aniListInfoError = null
            animeDetails = null
            detailsLoading = true
            translatedSynopsis = null
            val targetSource = if (allowSourceSwitch) {
                SourceCatalog.find(anime.sourceIndex)?.index
            } else null
            if (targetSource != null) {
                if (!settingsReady) {
                    pendingTvWatchNextSource = targetSource
                } else if (targetSource != appSettings.selectedSource) {
                    val updated = appSettings.copy(selectedSource = targetSource)
                    appSettings = updated
                    onSettingsChange(updated)
                }
            }
            val effectiveAnime = when {
                targetSource != null -> anime.copy(sourceIndex = targetSource)
                settingsReady -> anime.copy(sourceIndex = appSettings.selectedSource)
                else -> anime
            }
            retainedDetailAnime = effectiveAnime
            retainedAnimeDetails = null
            retainedSynopsis = effectiveAnime.synopsis
            resumeEpisodeId = resumeId
            selectedAnime = effectiveAnime
        }
    }

    fun openPlayerPreview(anime: Anime) {
        if (isAnimeRestricted(anime)) {
            showToast("PARENTAL: Opening anime is not allowed...")
            return
        }
        closePlayerPreview()
        val effectiveAnime = if (settingsReady) anime.copy(sourceIndex = appSettings.selectedSource) else anime
        playerPreviewSynopsis = effectiveAnime.synopsis
        playerPreviewLoading = true
        playerPreviewAnime = effectiveAnime
    }

    fun saveActiveContinueWatching() {
        val anime = selectedAnime
        val episode = selectedEpisode
        val position = playbackPositionMs
        val duration = playbackDurationMs
        if (anime != null && episode != null && position != null && duration != null) {
            coroutineScope.launch {
                tvIntegrationRepository.updateContinueWatching(
                    anime = anime,
                    episode = episode,
                    positionMs = position,
                    durationMs = duration,
                    force = true
                )
            }
        }
    }

    fun leavePlayback() {
        saveActiveContinueWatching()
        closePlayerPreview()
        selectedEpisode = null
        playbackChoice = null
        playbackStreamOverride = null
        playbackMirrorOverride = null
        preloadedPlaybackChoice = null
        preloadedAppliedEpisodeId = null
        playbackError = null
        tvResumePosition = null
        playbackStartOverride = null
        closePlaybackConfirmation = false
        showPlaybackBackHint = false
        playerTrackingDialogOpen = false
        removeTrackingConfirm = null
        coroutineScope.launch { runCatching { watchHistory = libraryRepository.loadHistory() } }
    }

    fun requestPlaybackClose(fromSystemBack: Boolean) {
        when {
            appSettings.closeConfirmation == 1 -> closePlaybackConfirmation = true
            appSettings.closeConfirmation == 2 -> leavePlayback()
            fromSystemBack && showPlaybackBackHint -> leavePlayback()
            fromSystemBack -> {
                showPlaybackBackHint = true
                showToast("Press back again to close playback...")
            }
            else -> leavePlayback()
        }
    }

    fun returnToProfileChooserOrExit() {
        val onlyProfile = profileDirectory.profiles.singleOrNull()
        if (onlyProfile != null && onlyProfile.pin.isBlank()) {
            onExitApplication()
            return
        }
        loginPreviewProfilePrefix = activeProfile?.prefix
        savedActiveProfilePrefix = null
        activeProfile = null
        profileChooserOpen = false
        section = Section.Home
        mainSection = Section.Home
        selectedAnime = null
        selectedEpisode = null
        playbackChoice = null
        playbackError = null
        sidebarOpen = false
        sidebarExitArmed = false
        settingsReady = false
    }

    fun handleApplicationBack() {
        when {
            clearHistoryStage > 0 -> clearHistoryStage = 0
            homeLayoutDialogOpen -> homeLayoutDialogOpen = false
            myListLayoutDialogOpen -> myListLayoutDialogOpen = false
            navActionMenuSection != null -> navActionMenuSection = null
            exitConfirmation -> exitConfirmation = false
            settingsOpen -> closeSettings()
            playerPreviewAnime != null -> closePlayerPreview()
            selectedEpisode != null && playbackChoice == null && playbackError == null -> leavePlayback()
            selectedEpisode != null -> requestPlaybackClose(fromSystemBack = true)
            selectedAnime != null -> selectedAnime = null
            profileChooserOpen -> profileChooserOpen = false
            sidebarOpen && sidebarExitArmed -> {
                sidebarExitArmed = false
                sidebarOpen = false
                if (appSettings.exitMode == 4) returnToProfileChooserOrExit()
                else onExitApplication()
            }
            sidebarOpen -> sidebarOpen = false
            section == Section.Search -> section = mainSection
            section != Section.Home -> selectMainSection(Section.Home)
            else -> when (appSettings.exitMode) {
                1 -> exitConfirmation = true
                2 -> returnToProfileChooserOrExit()
                3, 4 -> {
                    sidebarExitArmed = true
                    sidebarOpen = true
                }
                else -> onExitApplication()
            }
        }
    }

    val currentTvLaunchHandler = rememberUpdatedState<(TvLaunchRequest) -> Unit> { request ->
        playbackStartOverride = null
        openAnime(
            request.anime,
            request.episodeId,
            allowSourceSwitch = !request.episodeId.isNullOrBlank()
        )
        tvResumePosition = request.episodeId?.let { it to request.positionMs }
    }
    DisposableEffect(tvIntegrationRepository, activeProfile?.prefix) {
        if (activeProfile != null) {
            tvIntegrationRepository.setLaunchHandler { request -> currentTvLaunchHandler.value(request) }
        }
        onDispose { tvIntegrationRepository.setLaunchHandler(null) }
    }

    fun trackAndPlay(anime: Anime, episode: Episode, providers: List<AccountProvider>) {
        coroutineScope.launch {
            trackingBusy = true
            trackingMessage = null
            var failure: String? = null
            providers.forEach { provider ->
                runCatching { accountRepository.setTracking(anime, provider, true) }
                    .onFailure { failure = it.message ?: "Could not update tracking" }
            }
            animeTracking = runCatching { accountRepository.loadTracking(anime) }
                .getOrDefault(animeTracking)
            trackingBusy = false
            trackingMessage = failure
            if (failure != null) showToast(failure)
            startPlayback(episode)
        }
    }

    fun beginEpisode(anime: Anime, episode: Episode, startPositionMs: Long? = null) {
        val episodes = animeDetails?.episodes.orEmpty()
        val index = episodes.indexOfFirst { it.id == episode.id }
        val playableEpisode = if (appSettings.skipFiller && episode.filler) {
            episodes.drop(index + 1).firstOrNull { !it.filler }
        } else episode
        playbackStartOverride = playableEpisode?.let { playable ->
            startPositionMs?.let { playable.id to if (playable.id == episode.id) it else 0L }
        }
        if (playableEpisode == null) return
        if (appSettings.malTrackingDialog == 0) {
            startPlayback(playableEpisode)
            return
        }
        coroutineScope.launch {
            val state = runCatching { accountRepository.loadTracking(anime) }
                .getOrDefault(AnimeTrackingState())
            animeTracking = state
            val providers = listOf(
                AccountProvider.ANILIST to state.aniList,
                AccountProvider.MAL to state.mal
            )
            val untracked = providers.filter { (_, entry) -> entry.canTrack && !entry.isTracked }
                .map { it.first }
            when (appSettings.malTrackingDialog) {
                1 -> if (untracked.isNotEmpty() && anime.id !in skippedTrackingPromptIds) {
                    trackingPrompt = TrackingPromptState(anime, playableEpisode, untracked)
                } else startPlayback(playableEpisode)
                2, 3, 4 -> {
                    val desired = when (appSettings.malTrackingDialog) {
                        2 -> listOf(AccountProvider.ANILIST)
                        3 -> listOf(AccountProvider.MAL)
                        else -> listOf(AccountProvider.ANILIST, AccountProvider.MAL)
                    }
                    val toAdd = desired.filter { it in untracked }
                    if (toAdd.isEmpty()) startPlayback(playableEpisode)
                    else trackAndPlay(anime, playableEpisode, toAdd)
                }
                else -> startPlayback(playableEpisode)
            }
        }
    }

    PlatformBackHandler(
        enabled = selectedEpisode != null || selectedAnime != null || playerPreviewAnime != null ||
            profileChooserOpen || sidebarOpen || sidebarExitArmed || settingsOpen ||
            section != Section.Home || navActionMenuSection != null || homeLayoutDialogOpen ||
            myListLayoutDialogOpen || clearHistoryStage > 0 ||
            (activeProfile != null && appSettings.exitMode != 0),
        onBack = ::handleApplicationBack
    )
    PlatformPlaybackOrientation(landscape = selectedEpisode != null && activeProfile != null)

    val chooserVisible = activeProfile == null || profileChooserOpen
    val loginAppearance = profileAppearances[loginPreviewProfilePrefix] ?: ProfileAppearance()
    val visibleTheme = if (chooserVisible) {
        if (loginScreenStyle == 0) "theme_purple" else loginAppearance.theme
    } else appSettings.theme
    val themeColors = remember(visibleTheme) { animeTvThemeColorsFor(visibleTheme) }

    val appFont = platformFontFamily(icon = false)
    val iconFont = platformFontFamily(icon = true)

    MaterialTheme(
        colorScheme = colorSchemeForTheme(visibleTheme, loginScreen = chooserVisible),
        typography = remember(appFont) { appTypography(appFont) }
    ) {
        CompositionLocalProvider(
            LocalAnimeTvColors provides themeColors,
            LocalAppToast provides showToast,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = appFont),
            LocalIconFont provides iconFont,
            LocalPerformanceEffects provides appSettings.performanceEffects,
            LocalJapaneseTitles provides appSettings.japaneseTitles,
            LocalImageCdn provides appSettings.useImageCdn,
            LocalNavigationSound provides rememberPlatformClickSound(appSettings.navigationSound),
            LocalCardStyle provides CardStylePreferences(
                cardSize = 0,
                cardRadius = 0,
                badgeStyle = 0,
                badgeColors = badgeThemeColorsFor(visibleTheme)
            )
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val portraitLayout = maxHeight > maxWidth
                    val density = LocalDensity.current
                    val statusInsets = WindowInsets.statusBars
                    val navInsets = WindowInsets.navigationBars
                    val sysStatHeight = if (portraitLayout) {
                        with(density) { statusInsets.getTop(this).toDp() }
                    } else 0.dp
                    val sysNavHeight = if (portraitLayout) {
                        with(density) { navInsets.getBottom(this).toDp() }
                    } else 0.dp
                    val isTouchDevice = platformIsTouchDevice()
                    val tokens = rememberAnimeTvTokens(
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        sysStatHeight = sysStatHeight,
                        sysNavHeight = sysNavHeight,
                        fontSizeTier = if (chooserVisible) 0 else appSettings.fontSize,
                        largeUi = if (chooserVisible) false else appSettings.largeUi,
                        animationTier = appSettings.animation,
                        isTouch = isTouchDevice
                    )

                    CompositionLocalProvider(LocalAnimeTvTokens provides tokens) {
                        Box(Modifier.fillMaxSize()) {
                            if (activeProfile == null) {
                                if (profileLoading) {
                                    ProfileLoadingScreen()
                                } else {
                                    ProfileChooserScreen(
                                        profiles = profileDirectory.profiles,
                                        defaultPrefix = profileDirectory.defaultPrefix,
                                        preferredPrefix = loginPreviewProfilePrefix,
                                        allowCancel = false,
                                        loginScreenStyle = loginScreenStyle,
                                        appearance = loginAppearance,
                                        backdropColor = themeColors.loginBg,
                                        onSelected = ::activateProfile,
                                        onCancel = {},
                                        onPreviewProfile = { loginPreviewProfilePrefix = it.prefix },
                                        onExitApplication = onExitApplication
                                    )
                                }
                            } else {
                                val localWallpaper = curatedWallpapers.firstOrNull {
                                    it.source == appSettings.wallpaperUrl
                                }
                                val wallpaperUrl = appSettings.wallpaperUrl.takeIf {
                                    localWallpaper == null && it.isNotBlank()
                                }
                                if (localWallpaper != null) {
                                    Image(
                                        painter = platformImagePainter(localWallpaper.drawable!!),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (wallpaperUrl != null) {
                                    AsyncImage(
                                        model = wallpaperUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (localWallpaper != null || wallpaperUrl != null) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                                    )
                                }
                                if (portraitLayout && selectedEpisode == null) {
                                    val p1 = themeColors.primary1
                                    val bottomTint = Color(
                                        red = p1.red * (0.30f / 0.65f),
                                        green = p1.green * (0.30f / 0.65f),
                                        blue = p1.blue * (0.30f / 0.65f),
                                        alpha = 0.65f
                                    )
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                0.70f to Color.Transparent,
                                                1.00f to bottomTint
                                            )
                                        )
                                    )
                                }

                                val tvLayout = platformIsTelevision()
                                val currentScrollOffset = scrollOffsets[mainSection] ?: 0.dp
                                val headerScrolled = currentScrollOffset > tokens.u * 5f
                                val activeEpisode = selectedEpisode
                                val activeAnime = selectedAnime
                                val profileRating = activeProfile?.parentalRating ?: 6
                                val selectedSource = appSettings.selectedSource
                                val sourceWatchlist = watchlist.filter { it.sourceIndex == selectedSource }
                                val sourceHistory = watchHistory.filter {
                                    (it.anime?.sourceIndex ?: SourceCatalog.GOTAKU_INDEX) == selectedSource
                                }
                                val homeWithHistory = homeFeed.copy(
                                    continueWatching = (sourceHistory.sortedByDescending { it.updatedAtEpochMs }
                                        .mapNotNull { it.anime } + homeFeed.continueWatching.filter { it.sourceIndex == selectedSource })
                                        .distinctBy { it.id to it.sourceIndex }
                                ).filteredForRating(profileRating)
                                val visibleWatchlist = sourceWatchlist.filter { it.allowedForRating(profileRating) }
                                val visibleHistory = sourceHistory.sortedByDescending { it.updatedAtEpochMs }
                                    .filter { it.anime?.allowedForRating(profileRating) == true }
                                    .distinctBy { it.animeId }
                                val visibleSchedule = schedule.filter { it.anime.allowedForRating(profileRating) }
                                val visibleAnimeDetails = animeDetails?.copy(
                                    related = animeDetails?.related.orEmpty().filter {
                                        it.allowedForRating(profileRating)
                                    },
                                    recommendations = animeDetails?.recommendations.orEmpty().filter {
                                        it.allowedForRating(profileRating)
                                    }
                                )
                                val retainedVisibleAnimeDetails = retainedAnimeDetails?.copy(
                                    related = retainedAnimeDetails?.related.orEmpty().filter {
                                        it.allowedForRating(profileRating)
                                    },
                                    recommendations = retainedAnimeDetails?.recommendations.orEmpty().filter {
                                        it.allowedForRating(profileRating)
                                    }
                                )

                                Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        if (activeEpisode != null) {
                                            val choice = playbackChoice
                                            val episodes = visibleAnimeDetails?.episodes ?: animeDetails?.episodes.orEmpty()
                                            val currentIndex = episodes.indexOfFirst { it.id == activeEpisode.id }
                                            val previousEpisode = if (currentIndex > 0) {
                                                episodes.take(currentIndex).lastOrNull {
                                                    !appSettings.skipFiller || !it.filler
                                                }
                                            } else null
                                            val nextEpisode = if (currentIndex >= 0) {
                                                episodes.drop(currentIndex + 1).firstOrNull {
                                                    !appSettings.skipFiller || !it.filler
                                                }
                                            } else null
                                            val playerDetailsAnime = visibleAnimeDetails?.anime ?: activeAnime
                                            val playerBackdropUrl = playerDetailsAnime?.bannerUrl?.takeIf { it.isNotBlank() }
                                                ?: activeAnime?.bannerUrl?.takeIf { it.isNotBlank() }
                                                ?: playerDetailsAnime?.posterUrl?.takeIf { it.isNotBlank() }
                                                ?: activeAnime?.posterUrl
                                            val formattedEpisodeLabel = formatPlaybackEpisodeLabel(activeEpisode)
                                            val playerDescription = translatedSynopsis
                                                ?: playerDetailsAnime?.synopsis.orEmpty()
                                            val playerGenres = playerDetailsAnime?.genres?.ifEmpty {
                                                activeAnime?.genres.orEmpty()
                                            } ?: activeAnime?.genres.orEmpty()
                                            val playerFormat = playerDetailsAnime?.format ?: activeAnime?.format
                                            val playerQuality = playerDetailsAnime?.status ?: activeAnime?.status
                                            val playerRating = normalizeAgeRating(playerDetailsAnime?.rating)
                                                ?: normalizeAgeRating(activeAnime?.rating)
                                                ?: if (playerDetailsAnime?.adult == true || activeAnime?.adult == true) {
                                                    "R+"
                                                } else null
                                            val inWatchlistNow = activeAnime != null &&
                                                sourceWatchlist.any { it.id == activeAnime.id }
                                            val isTrackedNow =
                                                animeTracking.aniList.isTracked || animeTracking.mal.isTracked
                                            val playerSubLabel = if (activeAnime?.sourceIndex == SourceCatalog.REANIME_INDEX) {
                                                "Sub"
                                            } else "Hardsub"
                                            val onPlayerToggleWatchlist: () -> Unit = {
                                                activeAnime?.let { anime ->
                                                    val targetAnime = anime.copy(sourceIndex = selectedSource)
                                                    val currentlySaved = sourceWatchlist.any { it.id == targetAnime.id }
                                                    coroutineScope.launch {
                                                        if (currentlySaved) {
                                                            libraryRepository.removeFromWatchlist(targetAnime.id, selectedSource)
                                                        } else {
                                                            libraryRepository.addToWatchlist(targetAnime)
                                                        }
                                                        watchlist = libraryRepository.loadWatchlist()
                                                    }
                                                }
                                            }
                                            val onPlayerTrackingClick: () -> Unit = {
                                                val anime = activeAnime
                                                if (anime != null) {
                                                    val connected =
                                                        accountState.aniListUsername != null ||
                                                            accountState.malUsername != null
                                                    val canAnyTrack =
                                                        animeTracking.aniList.canTrack ||
                                                            animeTracking.mal.canTrack
                                                    when {
                                                        !connected -> showToast(
                                                            "Please login to AniList or MAL first..."
                                                        )
                                                        !canAnyTrack -> showToast("Anime Untrackable...")
                                                        else -> playerTrackingDialogOpen = true
                                                    }
                                                }
                                            }
                                            val onPlayerSearchGenre: (String) -> Unit = { genre ->
                                                closePlayerPreview()
                                                leavePlayback()
                                                selectedAnime = null
                                                searchQuery = ""
                                                pendingSearchFilterNonce += 1
                                                pendingSearchFilter = pendingSearchFilterNonce to genre
                                                section = Section.Search
                                            }
                                            val onPlayerOpenSettings: () -> Unit = {
                                                settingsReloadPending = false
                                                settingsOpen = true
                                            }

                                            if (choice != null) {
                                                val subtitleVariantLanguage = appSettings.subtitleLanguage
                                                    .takeIf { it.isNotBlank() && it != "hard" } ?: "en"
                                                val selectedSubtitleVariant =
                                                    appSettings.subtitleVariants[subtitleVariantLanguage]
                                                        ?: appSettings.subtitleVariant

                                                PlaybackScreen(
                                                    title = activeAnime?.let {
                                                        displayAnimeTitle(it, appSettings.japaneseTitles)
                                                    } ?: "AnimeTV",
                                                    description = playerDescription,
                                                    genres = playerGenres,
                                                    episodes = episodes,
                                                    selectedEpisodeId = activeEpisode.id,
                                                    related = visibleAnimeDetails?.related.orEmpty(),
                                                    recommendations = visibleAnimeDetails?.recommendations.orEmpty(),
                                                    onEpisodeSelected = { episode ->
                                                        playbackStartOverride = null
                                                        activeAnime?.let { beginEpisode(it, episode) }
                                                    },
                                                    onAnimeSelected = ::openPlayerPreview,
                                                    episodeLabel = formattedEpisodeLabel,
                                                    posterUrl = playerBackdropUrl,
                                                    canPrevious = previousEpisode != null,
                                                    canNext = nextEpisode != null,
                                                    choice = choice,
                                                    hasSubStream = activeEpisode.hasSub,
                                                    hasDubStream = activeEpisode.hasDub,
                                                    subStreamLabel = playerSubLabel,
                                                    onStreamTypeChange = { type ->
                                                        playbackStreamOverride = type
                                                        playbackMirrorOverride = 0
                                                    },
                                                    streamServerNames = choice.streamServerNames,
                                                    selectedStreamServerIndex = choice.selectedStreamServerIndex,
                                                    onStreamServerChange = { index ->
                                                        playbackMirrorOverride = null
                                                        onSettingsChange(appSettings.copy(mirrorServer = index))
                                                    },
                                                    playbackSpeed = playbackSpeed,
                                                    onPlaybackSpeedChange = { playbackSpeed = it },
                                                    subtitleLanguage = appSettings.subtitleLanguage,
                                                    subtitleVariant = selectedSubtitleVariant,
                                                    onSubtitleVariantSelected = { variant ->
                                                        onSettingsChange(
                                                            appSettings.copy(
                                                                subtitleVariant = variant,
                                                                subtitleVariants = appSettings.subtitleVariants +
                                                                    (subtitleVariantLanguage to variant)
                                                            )
                                                        )
                                                    },
                                                    audioLanguage = appSettings.audioLanguage,
                                                    onAudioLanguageChange = { language ->
                                                        onSettingsChange(appSettings.copy(audioLanguage = language))
                                                    },
                                                    subtitleStyle = appSettings.subtitleStyle,
                                                    onSubtitleStyleChange = { style ->
                                                        onSettingsChange(appSettings.copy(subtitleStyle = style))
                                                    },
                                                    playbackQuality = appSettings.playbackQuality,
                                                    onPlaybackQualityChange = { quality ->
                                                        onSettingsChange(appSettings.copy(playbackQuality = quality))
                                                    },
                                                    autoSkipIntro = appSettings.autoSkipIntro,
                                                    skipFiller = appSettings.skipFiller,
                                                    startPositionMs = playbackPositionMs
                                                        ?: playbackStartOverride?.takeIf { it.first == activeEpisode.id }?.second
                                                        ?: tvResumePosition?.takeIf { it.first == activeEpisode.id }?.second
                                                        ?: sourceHistory.sortedByDescending { it.updatedAtEpochMs }
                                                            .firstOrNull {
                                                                it.episodeId == activeEpisode.id &&
                                                                    (activeAnime == null || it.animeId == activeAnime.id)
                                                            }?.positionMs
                                                        ?: 0L,
                                                    seekSeconds = appSettings.seekSeconds,
                                                    videoScale = appSettings.videoScale,
                                                    onVideoScaleChange = { scale ->
                                                        onSettingsChange(appSettings.copy(videoScale = scale))
                                                    },
                                                    videoBuffer = appSettings.videoBuffer,
                                                    progressiveCache = appSettings.progressiveCache,
                                                    cacheSizeMb = appSettings.cacheSizeMb,
                                                    disableTouchGestures = appSettings.disableTouchGestures,
                                                    html5Player = appSettings.html5Player,
                                                    onProgress = { position, duration ->
                                                        playbackPositionMs = position
                                                        playbackDurationMs = duration
                                                        activeAnime?.let { anime ->
                                                            val savedAnime = anime.copy(sourceIndex = selectedSource)
                                                            val episodeNumber = activeEpisode.number.toIntOrNull()
                                                                ?: activeEpisode.number.toDoubleOrNull()?.toInt()
                                                            val progressPercent = if (duration > 0) {
                                                                position.toDouble() * 100.0 / duration
                                                            } else 0.0
                                                            val trackingThreshold = appSettings.listProgressMode * 24 + 1
                                                            if (episodeNumber != null && progressPercent >= trackingThreshold) {
                                                                coroutineScope.launch {
                                                                    accountRepository.updateTrackedProgress(
                                                                        savedAnime,
                                                                        episodeNumber
                                                                    )
                                                                }
                                                            }
                                                            coroutineScope.launch {
                                                                tvIntegrationRepository.updateContinueWatching(
                                                                    savedAnime,
                                                                    activeEpisode,
                                                                    position,
                                                                    duration
                                                                )
                                                            }
                                                            if (appSettings.preloadNextEpisode && nextEpisode != null &&
                                                                preloadedPlaybackChoice?.first != nextEpisode.id
                                                            ) {
                                                                if (progressPercent >= 80.0) {
                                                                    coroutineScope.launch {
                                                                        runCatching {
                                                                            val prefDub =
                                                                                appSettings.preferDub && nextEpisode.hasDub
                                                                            val preloaded = animeRepository.resolvePlayback(
                                                                                episodeId = nextEpisode.id,
                                                                                preferDub = prefDub,
                                                                                mirrorIndex = playbackMirrorOverride ?: appSettings.mirrorServer,
                                                                                subtitleLanguage = appSettings.subtitleLanguage,
                                                                                hasSub = nextEpisode.hasSub,
                                                                                requestedStreamType = playbackStreamOverride
                                                                            )
                                                                            preloadedPlaybackChoice =
                                                                                nextEpisode.id to preloaded
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            coroutineScope.launch {
                                                                libraryRepository.saveProgress(
                                                                    WatchProgress(
                                                                        animeId = savedAnime.id,
                                                                        episodeId = activeEpisode.id,
                                                                        positionMs = position,
                                                                        durationMs = duration,
                                                                        updatedAtEpochMs = 0,
                                                                        anime = savedAnime
                                                                    )
                                                                )
                                                                val topContinue = homeFeed.continueWatching.firstOrNull()
                                                                if (topContinue?.id != savedAnime.id || topContinue.sourceIndex != savedAnime.sourceIndex) {
                                                                    watchHistory = libraryRepository.loadHistory()
                                                                    homeFeed = homeFeed.copy(
                                                                        continueWatching = (listOf(savedAnime) +
                                                                            homeFeed.continueWatching).distinctBy { it.id to it.sourceIndex }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onEnded = {
                                                        coroutineScope.launch {
                                                            tvIntegrationRepository.clearContinueWatching()
                                                        }
                                                        if (appSettings.autoNext) {
                                                            nextEpisode?.let {
                                                                playbackStartOverride = null
                                                                startPlayback(it)
                                                            }
                                                        }
                                                    },
                                                    onPrevious = {
                                                        playbackStartOverride = null
                                                        previousEpisode?.let(::startPlayback)
                                                    },
                                                    onNext = {
                                                        playbackStartOverride = null
                                                        nextEpisode?.let(::startPlayback)
                                                    },
                                                    onBack = {
                                                        requestPlaybackClose(fromSystemBack = false)
                                                    },
                                                    showBackHint = false,
                                                    seasons = visibleAnimeDetails?.seasons.orEmpty(),
                                                    onOpenSettings = onPlayerOpenSettings,
                                                    inWatchlist = inWatchlistNow,
                                                    onToggleWatchlist = onPlayerToggleWatchlist,
                                                    isTracked = isTrackedNow,
                                                    onTrackingClick = onPlayerTrackingClick,
                                                    onSearchGenre = onPlayerSearchGenre,
                                                    currentAnimeId = activeAnime?.id.orEmpty(),
                                                    animeFormat = playerFormat,
                                                    animeQuality = playerQuality,
                                                    animeRating = playerRating,
                                                    settingsOverlayOpen = settingsOpen || playerPreviewAnime != null
                                                )
                                            } else if (playbackError == null) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(Color.Black),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val backdropModel = imageCdnUrl(playerBackdropUrl, LocalImageCdn.current)
                                                    if (!backdropModel.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = backdropModel,
                                                            contentDescription = activeAnime?.let {
                                                                displayAnimeTitle(it, appSettings.japaneseTitles)
                                                            },
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                            } else {
                                                val errorStreamServers = if (activeAnime?.sourceIndex == SourceCatalog.GOTAKU_INDEX) {
                                                    buildList {
                                                        if (activeEpisode.hasSub) {
                                                            add("Soft Sub")
                                                            add("Hard Sub")
                                                        }
                                                        if (activeEpisode.hasDub) add("Dub")
                                                    }.ifEmpty { listOf("Main", "Mirror") }
                                                } else {
                                                    listOf("Main", "Mirror")
                                                }
                                                val errorStreamType = playbackStreamOverride
                                                    ?: if (appSettings.preferDub && activeEpisode.hasDub) PlaybackStreamType.DUB
                                                    else PlaybackStreamType.SUB
                                                PlaybackErrorScreen(
                                                    title = activeAnime?.let {
                                                        displayAnimeTitle(it, appSettings.japaneseTitles)
                                                    } ?: "AnimeTV",
                                                    episodeLabel = formattedEpisodeLabel,
                                                    message = playbackError ?: "This episode stream could not be opened",
                                                    onBack = { requestPlaybackClose(fromSystemBack = false) },
                                                    description = playerDescription,
                                                    genres = playerGenres,
                                                    artworkUrl = playerBackdropUrl,
                                                    canPrevious = previousEpisode != null,
                                                    canNext = nextEpisode != null,
                                                    hasSubStream = activeEpisode.hasSub,
                                                    hasDubStream = activeEpisode.hasDub,
                                                    subStreamLabel = playerSubLabel,
                                                    currentStreamType = errorStreamType,
                                                    streamServerNames = errorStreamServers,
                                                    selectedStreamServerIndex = (playbackMirrorOverride ?: appSettings.mirrorServer)
                                                        .coerceIn(0, errorStreamServers.lastIndex),
                                                    onStreamServerChange = { index ->
                                                        playbackMirrorOverride = null
                                                        onSettingsChange(appSettings.copy(mirrorServer = index))
                                                    },
                                                    onStreamTypeChange = { type ->
                                                        playbackStreamOverride = type
                                                        playbackMirrorOverride = 0
                                                    },
                                                    playbackSpeed = playbackSpeed,
                                                    onPlaybackSpeedChange = { playbackSpeed = it },
                                                    playbackQuality = appSettings.playbackQuality,
                                                    onPlaybackQualityChange = { quality ->
                                                        onSettingsChange(appSettings.copy(playbackQuality = quality))
                                                    },
                                                    audioLanguage = appSettings.audioLanguage,
                                                    onAudioLanguageChange = { language ->
                                                        onSettingsChange(appSettings.copy(audioLanguage = language))
                                                    },
                                                    episodes = episodes,
                                                    selectedEpisodeId = activeEpisode.id,
                                                    currentAnimeId = activeAnime?.id.orEmpty(),
                                                    metadataFormat = playerFormat,
                                                    metadataQuality = playerQuality,
                                                    metadataRating = playerRating,
                                                    seasons = visibleAnimeDetails?.seasons.orEmpty(),
                                                    related = visibleAnimeDetails?.related.orEmpty(),
                                                    recommendations = visibleAnimeDetails?.recommendations.orEmpty(),
                                                    inWatchlist = inWatchlistNow,
                                                    isTracked = isTrackedNow,
                                                    settingsOverlayOpen = settingsOpen || playerPreviewAnime != null,
                                                    onPrevious = {
                                                        playbackStartOverride = null
                                                        previousEpisode?.let(::startPlayback)
                                                    },
                                                    onNext = {
                                                        playbackStartOverride = null
                                                        nextEpisode?.let(::startPlayback)
                                                    },
                                                    onEpisodeSelected = { episode ->
                                                        playbackStartOverride = null
                                                        activeAnime?.let { beginEpisode(it, episode) }
                                                    },
                                                    onAnimeSelected = ::openPlayerPreview,
                                                    onOpenSettings = onPlayerOpenSettings,
                                                    onToggleWatchlist = onPlayerToggleWatchlist,
                                                    onTrackingClick = onPlayerTrackingClick,
                                                    onSearchGenre = onPlayerSearchGenre
                                                )
                                            }

                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = choice == null && playbackError == null,
                                                enter = if (tokens.tim5s == 0) EnterTransition.None else fadeIn(
                                                    tween(durationMillis = tokens.tim5s, easing = tokens.easing)
                                                ),
                                                exit = if (tokens.tim5s == 0) ExitTransition.None else fadeOut(
                                                    tween(durationMillis = tokens.tim5s, easing = tokens.easing)
                                                ),
                                                label = "playback-loading-transition"
                                            ) {
                                                PlaybackLoadingScreen()
                                            }
                                        } else {
                                            AnimatedContent(
                                                targetState = mainSection,
                                                transitionSpec = {
                                                    if (tokens.tim3s == 0) {
                                                        EnterTransition.None togetherWith ExitTransition.None
                                                    } else if (tokens.portrait) {
                                                        val enter = fadeIn(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) + scaleIn(
                                                            initialScale = 0.7f,
                                                            animationSpec = tween(
                                                                durationMillis = tokens.tim3s,
                                                                easing = tokens.easing
                                                            )
                                                        ) + slideInVertically(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) { fullHeight -> (fullHeight * 0.02f).toInt() }
                                                        val exit = fadeOut(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) + scaleOut(
                                                            targetScale = 0.7f,
                                                            animationSpec = tween(
                                                                durationMillis = tokens.tim3s,
                                                                easing = tokens.easing
                                                            )
                                                        ) + slideOutVertically(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) { fullHeight -> (fullHeight * 0.02f).toInt() }
                                                        enter togetherWith exit
                                                    } else {
                                                        val enter = fadeIn(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) + slideInHorizontally(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) { fullWidth ->
                                                            if (targetState == Section.MyList) {
                                                                (fullWidth * 0.05f).toInt()
                                                            } else {
                                                                (-fullWidth * 0.10f).toInt()
                                                            }
                                                        }
                                                        val exit = fadeOut(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) + slideOutHorizontally(
                                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                        ) { fullWidth ->
                                                            if (initialState == Section.MyList) {
                                                                (fullWidth * 0.05f).toInt()
                                                            } else {
                                                                (-fullWidth * 0.10f).toInt()
                                                            }
                                                        }
                                                        enter togetherWith exit
                                                    }
                                                },
                                                label = "section-transition"
                                            ) { targetSection ->
                                                SectionScreen(
                                                    section = targetSection,
                                                    tvLayout = tvLayout,
                                                    settings = appSettings,
                                                    profileRating = profileRating,
                                                    profileActivation = profileActivation,
                                                    myListLayout = myListLayout,
                                                    onMyListLayoutChange = ::saveMyListLayout,
                                                    homeLayout = homeLayout,
                                                    searchQuery = searchQuery,
                                                    onSearchQueryChange = { searchQuery = it },
                                                    onBackToHome = { section = mainSection },
                                                    onVoiceSearchResult = { openVoiceSearchResult(it) },
                                                    onSettingsChange = onSettingsChange,
                                                    homeFeed = homeWithHistory,
                                                    homeLoading = homeLoading,
                                                    watchlist = visibleWatchlist,
                                                    watchHistory = visibleHistory,
                                                    schedule = visibleSchedule,
                                                    scheduleLoading = scheduleLoading,
                                                    onRemoveFromWatchlist = { anime ->
                                                        coroutineScope.launch {
                                                            libraryRepository.removeFromWatchlist(anime.id, anime.sourceIndex)
                                                            watchlist = libraryRepository.loadWatchlist()
                                                        }
                                                    },
                                                    onClearWatchHistory = { clearHistoryStage = 1 },
                                                    accountRepository = accountRepository,
                                                    settingsRepository = settingsRepository,
                                                    settingsReady = settingsReady,
                                                    debugLogRepository = debugLogRepository,
                                                    searchHistoryRepository = searchHistoryRepository,
                                                    accountState = accountState,
                                                    onAccountStateChange = { accountState = it },
                                                    repository = animeRepository,
                                                    onAnimeSelected = { openAnime(it, null) },
                                                    onHistorySelected = { progress ->
                                                        progress.anime?.let { openAnime(it, progress.episodeId) }
                                                    },
                                                    onContinueWatching = { anime ->
                                                        openAnime(
                                                            anime,
                                                            sourceHistory.sortedByDescending { it.updatedAtEpochMs }
                                                                .firstOrNull { it.animeId == anime.id }?.episodeId
                                                        )
                                                    },
                                                    onScrollOffsetChange = { offset ->
                                                        scrollOffsets[targetSection] = offset
                                                    },
                                                    onRefreshHome = { refreshHome() },
                                                    onAboutFooterClick = {
                                                        settingsReloadPending = false
                                                        settingsOpen = true
                                                    },
                                                    isAdminProfile = activeProfile?.prefix.isNullOrEmpty(),
                                                    onReloadNeeded = { settingsReloadPending = true }
                                                )
                                            }

                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = section == Section.Search,
                                                enter = if (tokens.tim3s == 0) EnterTransition.None else fadeIn(
                                                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                ),
                                                exit = if (tokens.tim3s == 0) ExitTransition.None else fadeOut(
                                                    tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                                ),
                                                label = "search-overlay"
                                            ) {
                                                SearchScreen(
                                                    repository = animeRepository,
                                                    tvLayout = tvLayout,
                                                    selectedSource = appSettings.selectedSource,
                                                    searchHistoryRepository = searchHistoryRepository,
                                                    query = searchQuery,
                                                    onQueryChange = { searchQuery = it },
                                                    onBack = { section = mainSection },
                                                    onVoiceSearchResult = { openVoiceSearchResult(it) },
                                                    profileRating = profileRating,
                                                    profileActivation = profileActivation,
                                                    onAnimeSelected = { openAnime(it, null) },
                                                    largeUi = appSettings.largeUi,
                                                    pendingFilterRequest = pendingSearchFilter,
                                                    onPendingFilterConsumed = { pendingSearchFilter = null }
                                                )
                                            }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = activeAnime != null && activeEpisode == null,
                                            enter = if (tokens.tim3s == 0) EnterTransition.None else
                                                fadeIn(tween(durationMillis = tokens.tim3s, easing = tokens.easing)) +
                                                    scaleIn(
                                                        initialScale = 0.5f,
                                                        animationSpec = tween(
                                                            durationMillis = tokens.tim3s,
                                                            easing = tokens.easing
                                                        )
                                                    ),
                                            exit = if (tokens.tim3s == 0) ExitTransition.None else
                                                fadeOut(tween(durationMillis = tokens.tim3s, easing = tokens.easing)) +
                                                    scaleOut(
                                                        targetScale = 0.5f,
                                                        animationSpec = tween(
                                                            durationMillis = tokens.tim3s,
                                                            easing = tokens.easing
                                                        )
                                                    ),
                                            label = "details-transition"
                                        ) {
                                            val detailAnime = activeAnime ?: retainedDetailAnime
                                            if (detailAnime != null) {
                                                DetailsScreen(
                                                    anime = detailAnime,
                                                    tvLayout = tvLayout,
                                                    details = if (activeAnime?.id == detailAnime.id) {
                                                        visibleAnimeDetails
                                                    } else {
                                                        retainedVisibleAnimeDetails
                                                    },
                                                    synopsis = (if (activeAnime?.id == detailAnime.id) {
                                                        translatedSynopsis ?: animeDetails?.anime?.synopsis
                                                    } else {
                                                        retainedSynopsis
                                                    }) ?: detailAnime.synopsis,
                                                    aniListInfo = aniListInfo.takeIf {
                                                        aniListInfoAnimeId == detailAnime.id
                                                    },
                                                    aniListInfoLoading = aniListInfoAnimeId == detailAnime.id &&
                                                        aniListInfoLoading,
                                                    aniListInfoLoaded = aniListInfoAnimeId == detailAnime.id &&
                                                        aniListInfoLoaded,
                                                    aniListInfoError = aniListInfoError.takeIf {
                                                        aniListInfoAnimeId == detailAnime.id
                                                    },
                                                    onRequestAniListInfo = { retry ->
                                                        requestAniListInfo(
                                                            detailAnime,
                                                            animeDetails?.anime ?: detailAnime,
                                                            retry
                                                        )
                                                    },
                                                    watchProgress = sourceHistory.filter {
                                                        it.animeId == detailAnime.id
                                                    }.maxByOrNull { it.updatedAtEpochMs },
                                                    inHistory = sourceHistory.any { it.animeId == detailAnime.id },
                                                    skipFiller = appSettings.skipFiller,
                                                    inWatchlist = sourceWatchlist.any { it.id == detailAnime.id },
                                                    tracking = animeTracking,
                                                    trackingLoading = trackingLoading,
                                                    trackingBusy = trackingBusy,
                                                    trackingMessage = trackingMessage,
                                                    onTrackingStatusChange = { provider, status ->
                                                        coroutineScope.launch {
                                                            trackingBusy = true
                                                            trackingMessage = null
                                                            runCatching {
                                                                accountRepository.setTrackingStatus(
                                                                    detailAnime,
                                                                    provider,
                                                                    status
                                                                )
                                                                animeTracking =
                                                                    accountRepository.loadTracking(detailAnime)
                                                            }.onFailure { error ->
                                                                val msg = error.message ?: "Could not update tracking"
                                                                trackingMessage = msg
                                                                showToast(msg)
                                                            }
                                                            trackingBusy = false
                                                        }
                                                    },
                                                    onConnectAccounts = {
                                                        selectedAnime = null
                                                        settingsReloadPending = false
                                                        settingsOpen = true
                                                    },
                                                    onWatchlistChange = { shouldSave ->
                                                        val targetAnime = detailAnime.copy(sourceIndex = selectedSource)
                                                        coroutineScope.launch {
                                                            if (shouldSave) {
                                                                libraryRepository.addToWatchlist(targetAnime)
                                                            } else {
                                                                libraryRepository.removeFromWatchlist(targetAnime.id, selectedSource)
                                                            }
                                                            watchlist = libraryRepository.loadWatchlist()
                                                        }
                                                    },
                                                    onHistoryChange = { shouldKeep ->
                                                        if (!shouldKeep) coroutineScope.launch {
                                                            libraryRepository.removeAnimeHistory(detailAnime.id, selectedSource)
                                                            watchHistory = libraryRepository.loadHistory()
                                                            homeFeed = homeFeed.copy(
                                                                continueWatching = homeFeed.continueWatching.filterNot {
                                                                    it.id == detailAnime.id && it.sourceIndex == selectedSource
                                                                }
                                                            )
                                                        }
                                                    },
                                                    onEpisodeSelected = { episode, startPositionMs ->
                                                        beginEpisode(detailAnime, episode, startPositionMs)
                                                    },
                                                    onDismiss = { selectedAnime = null },
                                                    loading = activeAnime?.id == detailAnime.id && detailsLoading,
                                                    openedFromCard = true
                                                )
                                            }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = playerPreviewAnime != null && activeEpisode != null,
                                            enter = if (tokens.tim3s == 0) EnterTransition.None else
                                                fadeIn(tween(durationMillis = tokens.tim3s, easing = tokens.easing)) +
                                                    scaleIn(
                                                        initialScale = 0.5f,
                                                        animationSpec = tween(
                                                            durationMillis = tokens.tim3s,
                                                            easing = tokens.easing
                                                        )
                                                    ),
                                            exit = if (tokens.tim3s == 0) ExitTransition.None else
                                                fadeOut(tween(durationMillis = tokens.tim3s, easing = tokens.easing)) +
                                                    scaleOut(
                                                        targetScale = 0.5f,
                                                        animationSpec = tween(
                                                            durationMillis = tokens.tim3s,
                                                            easing = tokens.easing
                                                        )
                                                    ),
                                            label = "player-preview-details"
                                        ) {
                                            val previewAnime = playerPreviewAnime
                                            if (previewAnime != null) {
                                                val resolvedDetails = playerPreviewDetails?.copy(
                                                    related = playerPreviewDetails?.related.orEmpty().filter {
                                                        it.allowedForRating(profileRating)
                                                    },
                                                    recommendations = playerPreviewDetails?.recommendations.orEmpty().filter {
                                                        it.allowedForRating(profileRating)
                                                    }
                                                )
                                                val detailTarget = resolvedDetails?.anime ?: previewAnime
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                ) {
                                                    DetailsScreen(
                                                        anime = previewAnime,
                                                        tvLayout = tvLayout,
                                                        details = resolvedDetails,
                                                        synopsis = playerPreviewSynopsis ?: previewAnime.synopsis,
                                                        aniListInfo = playerPreviewAniListInfo.takeIf {
                                                            playerPreviewAniListInfoAnimeId == previewAnime.id
                                                        },
                                                        aniListInfoLoading = playerPreviewAniListInfoAnimeId == previewAnime.id &&
                                                            playerPreviewAniListInfoLoading,
                                                        aniListInfoLoaded = playerPreviewAniListInfoAnimeId == previewAnime.id &&
                                                            playerPreviewAniListInfoLoaded,
                                                        aniListInfoError = playerPreviewAniListInfoError.takeIf {
                                                            playerPreviewAniListInfoAnimeId == previewAnime.id
                                                        },
                                                        onRequestAniListInfo = { retry ->
                                                            requestPreviewAniListInfo(
                                                                previewAnime,
                                                                resolvedDetails?.anime ?: previewAnime,
                                                                retry
                                                            )
                                                        },
                                                        watchProgress = sourceHistory.filter {
                                                            it.animeId == detailTarget.id || it.animeId == previewAnime.id
                                                        }.maxByOrNull { it.updatedAtEpochMs },
                                                        inHistory = sourceHistory.any {
                                                            it.animeId == detailTarget.id || it.animeId == previewAnime.id
                                                        },
                                                        skipFiller = appSettings.skipFiller,
                                                        inWatchlist = sourceWatchlist.any {
                                                            it.id == detailTarget.id || it.id == previewAnime.id
                                                        },
                                                        tracking = playerPreviewTracking,
                                                        trackingLoading = playerPreviewTrackingLoading,
                                                        trackingBusy = playerPreviewTrackingBusy,
                                                        trackingMessage = playerPreviewTrackingMessage,
                                                        onTrackingStatusChange = { provider, status ->
                                                            coroutineScope.launch {
                                                                playerPreviewTrackingBusy = true
                                                                playerPreviewTrackingMessage = null
                                                                runCatching {
                                                                    accountRepository.setTrackingStatus(
                                                                        detailTarget,
                                                                        provider,
                                                                        status
                                                                    )
                                                                    playerPreviewTracking =
                                                                        accountRepository.loadTracking(detailTarget)
                                                                    if (selectedAnime?.id == detailTarget.id) {
                                                                        animeTracking = playerPreviewTracking
                                                                    }
                                                                }.onFailure { error ->
                                                                    val msg = error.message ?: "Could not update tracking"
                                                                    playerPreviewTrackingMessage = msg
                                                                    showToast(msg)
                                                                }
                                                                playerPreviewTrackingBusy = false
                                                            }
                                                        },
                                                        onConnectAccounts = {
                                                            closePlayerPreview()
                                                            settingsReloadPending = false
                                                            settingsOpen = true
                                                        },
                                                        onWatchlistChange = { shouldSave ->
                                                            val targetAnime = detailTarget.copy(sourceIndex = selectedSource)
                                                            coroutineScope.launch {
                                                                if (shouldSave) {
                                                                    libraryRepository.addToWatchlist(targetAnime)
                                                                } else {
                                                                    libraryRepository.removeFromWatchlist(detailTarget.id, selectedSource)
                                                                    if (previewAnime.id != detailTarget.id) {
                                                                        libraryRepository.removeFromWatchlist(previewAnime.id, selectedSource)
                                                                    }
                                                                }
                                                                watchlist = libraryRepository.loadWatchlist()
                                                            }
                                                        },
                                                        onHistoryChange = { shouldKeep ->
                                                            if (!shouldKeep) coroutineScope.launch {
                                                                libraryRepository.removeAnimeHistory(detailTarget.id, selectedSource)
                                                                if (previewAnime.id != detailTarget.id) {
                                                                    libraryRepository.removeAnimeHistory(previewAnime.id, selectedSource)
                                                                }
                                                                watchHistory = libraryRepository.loadHistory()
                                                                homeFeed = homeFeed.copy(
                                                                    continueWatching = homeFeed.continueWatching.filterNot {
                                                                        (it.id == detailTarget.id || it.id == previewAnime.id) &&
                                                                            it.sourceIndex == selectedSource
                                                                    }
                                                                )
                                                            }
                                                        },
                                                        onEpisodeSelected = { episode, startPositionMs ->
                                                            saveActiveContinueWatching()
                                                            closePlayerPreview()
                                                            if (selectedAnime?.id == detailTarget.id) {
                                                                beginEpisode(detailTarget, episode, startPositionMs)
                                                            } else {
                                                                openAnime(detailTarget, episode.id)
                                                                playbackStartOverride = startPositionMs?.let { episode.id to it }
                                                            }
                                                        },
                                                        onDismiss = ::closePlayerPreview,
                                                        loading = playerPreviewLoading,
                                                        openedFromCard = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (selectedEpisode == null && section != Section.Search) {
                                    Box(
                                        Modifier.align(
                                            if (portraitLayout) Alignment.BottomCenter else Alignment.TopCenter
                                        ).fillMaxWidth(),
                                        contentAlignment = if (portraitLayout) {
                                            Alignment.BottomCenter
                                        } else {
                                            Alignment.TopCenter
                                        }
                                    ) {
                                        AppHeader(
                                            selected = if (settingsOpen) Section.Settings else section,
                                            tvLayout = tvLayout,
                                            portraitLayout = portraitLayout,
                                            headerScrolled = headerScrolled,
                                            showClock = appSettings.showClock,
                                            directPageSwitch = appSettings.directPageSwitch,
                                            directSidebar = appSettings.directSidebar,
                                            sidebarOpen = sidebarOpen,
                                            showVoiceSearch = selectedAnime == null,
                                            onOpenSidebar = {
                                                sidebarExitArmed = false
                                                sidebarOpen = true
                                            },
                                            onVoiceSearchResult = { openVoiceSearchResult(it) },
                                            onSelect = {
                                                if (it == Section.Settings) {
                                                    settingsReloadPending = false
                                                    settingsOpen = true
                                                } else {
                                                    closeSettings()
                                                    selectMainSection(it)
                                                }
                                            },
                                            onSectionActionRequest = { navActionMenuSection = it },
                                            onRefreshSchedule = { refreshSchedule(force = true) }
                                        )
                                    }
                                }

                                SidebarDialog(
                                    visible = sidebarOpen,
                                    settings = appSettings,
                                    profile = activeProfile!!,
                                    onDismiss = {
                                        sidebarExitArmed = false
                                        sidebarOpen = false
                                    },
                                    onDisarmExit = { sidebarExitArmed = false },
                                    onOpenProfileMenu = {
                                        sidebarExitArmed = false
                                        sidebarOpen = false
                                        profileOptionsStartManage = false
                                        profileOptionsOpen = true
                                    },
                                    onSourceSelected = { sourceIndex ->
                                        sidebarExitArmed = false
                                        sidebarOpen = false
                                        onSettingsChange(appSettings.copy(selectedSource = sourceIndex))
                                    },
                                    onConfigureSource = { sourceIndex ->
                                        sidebarExitArmed = false
                                        sidebarOpen = false
                                        benchmarkSourceIndex = sourceIndex
                                    }
                                )

                                AnimatedVisibility(
                                    visible = settingsOpen,
                                    enter = if (tokens.tim3s == 0) EnterTransition.None else fadeIn(
                                        tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                    ),
                                    exit = if (tokens.tim3s == 0) ExitTransition.None else fadeOut(
                                        tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                    ),
                                    label = "settings-scrim"
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = ::closeSettings
                                        )
                                    )
                                }

                                AnimatedVisibility(
                                    visible = settingsOpen,
                                    enter = if (tokens.tim3s == 0) EnterTransition.None else
                                        slideInHorizontally(
                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                        ) { it / 2 } + fadeIn(
                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                        ),
                                    exit = if (tokens.tim3s == 0) ExitTransition.None else
                                        slideOutHorizontally(
                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                        ) { it / 2 } + fadeOut(
                                            tween(durationMillis = tokens.tim3s, easing = tokens.easing)
                                        ),
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    label = "settings-panel"
                                ) {
                                    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
                                    val settingsPanelWidth = if (portraitLayout) tokens.screenWidth else tokens.vw * 34f
                                    Surface(
                                        modifier = Modifier
                                            .width(settingsPanelWidth)
                                            .fillMaxHeight()
                                            .then(
                                                if (!shadowsDisabled) {
                                                    Modifier.shadow(tokens.vw * 0.56f, clip = false)
                                                } else Modifier
                                            ),
                                        color = Color(0xFF222222)
                                    ) {
                                        SettingsScreen(
                                            settings = appSettings,
                                            onChange = onSettingsChange,
                                            accountRepository = accountRepository,
                                            accountState = accountState,
                                            onAccountStateChange = { accountState = it },
                                            homeFeed = homeFeed,
                                            settingsRepository = settingsRepository,
                                            settingsReady = settingsReady,
                                            debugLogRepository = debugLogRepository,
                                            animeRepository = animeRepository,
                                            tvLayout = tvLayout,
                                            forceLinearLayout = true,
                                            onClose = ::closeSettings,
                                            isAdminProfile = activeProfile?.prefix.isNullOrEmpty(),
                                            onReloadNeeded = { settingsReloadPending = true }
                                        )
                                    }
                                }

                                benchmarkSourceIndex?.let { srcIndex ->
                                    SourceMirrorBenchmarkDialog(
                                        sourceIndex = srcIndex,
                                        settings = appSettings,
                                        animeRepository = animeRepository,
                                        onDismiss = { benchmarkSourceIndex = null },
                                        onSelectDomain = { domain ->
                                            benchmarkSourceIndex = null
                                            val updatedDomains = appSettings.sourceDomains + (srcIndex to domain)
                                            onSettingsChange(appSettings.copy(sourceDomains = updatedDomains))
                                        }
                                    )
                                }

                                trackingPrompt?.let { prompt ->
                                    TrackingPromptDialog(
                                        prompt = prompt,
                                        onDismiss = { trackingPrompt = null },
                                        onTrackProvider = { provider ->
                                            trackingPrompt = null
                                            skippedTrackingPromptIds = skippedTrackingPromptIds - prompt.anime.id
                                            trackAndPlay(prompt.anime, prompt.episode, listOf(provider))
                                        },
                                        onTrackBoth = {
                                            trackingPrompt = null
                                            skippedTrackingPromptIds = skippedTrackingPromptIds - prompt.anime.id
                                            trackAndPlay(prompt.anime, prompt.episode, prompt.providers)
                                        },
                                        onPlayWithoutTracking = {
                                            trackingPrompt = null
                                            skippedTrackingPromptIds = skippedTrackingPromptIds + prompt.anime.id
                                            startPlayback(prompt.episode)
                                        }
                                    )
                                }

                                if (playerTrackingDialogOpen && activeAnime != null) {
                                    val canAniList = animeTracking.aniList.canTrack
                                    val canMal = animeTracking.mal.canTrack
                                    val hasAniList = animeTracking.aniList.isTracked
                                    val hasMal = animeTracking.mal.isTracked
                                    val items = buildList {
                                        if (canAniList && canMal && !hasAniList && !hasMal) {
                                            add(
                                                ListOrderItem(
                                                    id = "trackall",
                                                    title = "Track on AniList and MAL",
                                                    icon = IconGlyphs.Done
                                                )
                                            )
                                        }
                                        if (canAniList && !hasAniList) {
                                            add(
                                                ListOrderItem(
                                                    id = "anilist",
                                                    title = "Track on AniList",
                                                    icon = IconGlyphs.PlaylistAddCheckCircle
                                                )
                                            )
                                        }
                                        if (canMal && !hasMal) {
                                            add(
                                                ListOrderItem(
                                                    id = "mal",
                                                    title = "Track on MAL",
                                                    icon = IconGlyphs.PlaylistAddCheckCircle
                                                )
                                            )
                                        }
                                        if (canAniList && hasAniList) {
                                            add(
                                                ListOrderItem(
                                                    id = "delanilist",
                                                    title = "Remove from AniList",
                                                    icon = IconGlyphs.Cancel
                                                )
                                            )
                                        }
                                        if (canMal && hasMal) {
                                            add(
                                                ListOrderItem(
                                                    id = "delmal",
                                                    title = "Remove from MAL",
                                                    icon = IconGlyphs.Cancel
                                                )
                                            )
                                        }
                                    }
                                    ListOrderDialog(
                                        title = "Tracking Anime",
                                        items = items,
                                        mode = ListOrderMode.MENU,
                                        onDismiss = { playerTrackingDialogOpen = false },
                                        onSelect = { _, item ->
                                            playerTrackingDialogOpen = false
                                            when (item.id) {
                                                "trackall", "anilist", "mal" -> {
                                                    val targets = when (item.id) {
                                                        "trackall" -> listOf(AccountProvider.ANILIST, AccountProvider.MAL)
                                                        "anilist" -> listOf(AccountProvider.ANILIST)
                                                        else -> listOf(AccountProvider.MAL)
                                                    }
                                                    coroutineScope.launch {
                                                        var failed = false
                                                        targets.forEach { p ->
                                                            runCatching {
                                                                accountRepository.setTracking(activeAnime, p, true)
                                                            }.onFailure {
                                                                failed = true
                                                                showToast("Track on ${accountProviderName(p)} failed...")
                                                            }
                                                        }
                                                        animeTracking = runCatching {
                                                            accountRepository.loadTracking(activeAnime)
                                                        }.getOrDefault(animeTracking)
                                                        if (!failed) {
                                                            val okMsg = when (item.id) {
                                                                "trackall" -> "Saved to AniList and MAL"
                                                                "anilist" -> "Saved to AniList"
                                                                else -> "Saved to MAL"
                                                            }
                                                            showToast(okMsg)
                                                        }
                                                    }
                                                }
                                                "delanilist" -> removeTrackingConfirm = AccountProvider.ANILIST
                                                "delmal" -> removeTrackingConfirm = AccountProvider.MAL
                                            }
                                        }
                                    )
                                }

                                removeTrackingConfirm?.let { provider ->
                                    val label = if (provider == AccountProvider.ANILIST) "AniList" else "MAL"
                                    ConfirmDialog(
                                        title = "Remove $label",
                                        message = "Remove this anime from your $label?",
                                        yesLabel = "YES",
                                        noLabel = "NO",
                                        onConfirm = {
                                            removeTrackingConfirm = null
                                            val anime = activeAnime ?: return@ConfirmDialog
                                            coroutineScope.launch {
                                                runCatching {
                                                    accountRepository.setTracking(anime, provider, false)
                                                    animeTracking = accountRepository.loadTracking(anime)
                                                    showToast("Removed from $label")
                                                }.onFailure {
                                                    showToast("Remove from $label failed...")
                                                }
                                            }
                                        },
                                        onDismiss = { removeTrackingConfirm = null }
                                    )
                                }

                                PlaybackCloseConfirmationDialog(
                                    visible = closePlaybackConfirmation,
                                    onKeepWatching = { closePlaybackConfirmation = false },
                                    onClosePlayback = ::leavePlayback
                                )

                                ApplicationExitConfirmationDialog(
                                    visible = exitConfirmation,
                                    onCancel = { exitConfirmation = false },
                                    onExit = {
                                        exitConfirmation = false
                                        onExitApplication()
                                    }
                                )

                                if (profileChooserOpen) {
                                    ProfileChooserScreen(
                                        profiles = profileDirectory.profiles,
                                        defaultPrefix = profileDirectory.defaultPrefix,
                                        preferredPrefix = loginPreviewProfilePrefix,
                                        allowCancel = true,
                                        loginScreenStyle = loginScreenStyle,
                                        appearance = loginAppearance,
                                        backdropColor = themeColors.loginBg,
                                        onSelected = ::activateProfile,
                                        onCancel = { profileChooserOpen = false },
                                        onPreviewProfile = { loginPreviewProfilePrefix = it.prefix }
                                    )
                                }

                                if (profileOptionsOpen && activeProfile != null) {
                                    ProfileOptionsFlowDialog(
                                        directory = profileDirectory,
                                        activeProfile = activeProfile!!,
                                        accountRepository = accountRepository,
                                        startInManageUsers = profileOptionsStartManage,
                                        onDismiss = { profileOptionsOpen = false },
                                        onSwitchUser = {
                                            profileOptionsOpen = false
                                            profileChooserOpen = true
                                        },
                                        onCreateUser = { name ->
                                            if (profileDirectory.profiles.size < 5) {
                                                coroutineScope.launch {
                                                    val profile = profileRepository.createProfile(name)
                                                    saveProfileDirectory(
                                                        profileDirectory.copy(
                                                            profiles = profileDirectory.profiles + profile
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onSetDefaultUser = { prefix ->
                                            saveProfileDirectory(profileDirectory.copy(defaultPrefix = prefix))
                                        },
                                        onSaveUser = { updated ->
                                            val profiles = profileDirectory.profiles.map {
                                                if (it.prefix == updated.prefix) updated else it
                                            }
                                            saveProfileDirectory(profileDirectory.copy(profiles = profiles))
                                            if (activeProfile?.prefix == updated.prefix) {
                                                activeProfile = updated
                                            }
                                        },
                                        onDeleteUser = { prefix ->
                                            coroutineScope.launch {
                                                profileRepository.deleteProfileData(prefix)
                                                val remaining = profileDirectory.profiles.filterNot {
                                                    it.prefix == prefix
                                                }
                                                saveProfileDirectory(
                                                    profileDirectory.copy(
                                                        profiles = remaining,
                                                        defaultPrefix = profileDirectory.defaultPrefix
                                                            ?.takeIf { it != prefix }
                                                            ?: ""
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }

                                navActionMenuSection?.let { sec ->
                                    NavFloatingMenu(
                                        section = sec,
                                        onDismiss = { navActionMenuSection = null },
                                        onRefreshHome = { refreshHome() },
                                        onCustomizeHome = { homeLayoutDialogOpen = true },
                                        onReloadAnimeTv = { refreshHome(clearCache = true) },
                                        onRefreshMyList = {
                                            coroutineScope.launch {
                                                if (activeProfile != null) {
                                                    watchlist = runCatching {
                                                        libraryRepository.loadWatchlist()
                                                    }.getOrDefault(emptyList())
                                                    watchHistory = runCatching {
                                                        libraryRepository.loadHistory()
                                                    }.getOrDefault(emptyList())
                                                }
                                            }
                                        },
                                        onCustomizeMyList = { myListLayoutDialogOpen = true },
                                        onClearWatchHistory = { clearHistoryStage = 1 },
                                        onRefreshSchedule = { refreshSchedule(force = true) }
                                    )
                                }

                                if (homeLayoutDialogOpen) {
                                    HomeLayoutDialog(
                                        layout = homeLayout,
                                        onDismiss = { homeLayoutDialogOpen = false },
                                        onSave = {
                                            homeLayoutDialogOpen = false
                                            saveHomeLayout(it)
                                        },
                                        sourceName = SourceCatalog.find(appSettings.selectedSource)?.displayName
                                            ?: "Gotaku"
                                    )
                                }

                                if (myListLayoutDialogOpen) {
                                    MyListLayoutDialog(
                                        layout = myListLayout,
                                        onDismiss = { myListLayoutDialogOpen = false },
                                        onSave = {
                                            myListLayoutDialogOpen = false
                                            saveMyListLayout(it)
                                        }
                                    )
                                }

                                if (clearHistoryStage == 1) {
                                    ConfirmDialog(
                                        title = "Clear Watch History",
                                        titleIcon = IconGlyphs.Warning,
                                        message = "<h6>WARNING!!!</h6>This action will clear your watch history, and <b>can't be restored</b>.<br><b>You want to continue?</b>",
                                        yesLabel = "YES",
                                        noLabel = "NO",
                                        onConfirm = { clearHistoryStage = 2 },
                                        onDismiss = { clearHistoryStage = 0 }
                                    )
                                } else if (clearHistoryStage == 2) {
                                    ConfirmDialog(
                                        title = "Confirm Watch History",
                                        titleIcon = IconGlyphs.Warning,
                                        message = "<h6>WARNING!!!</h6>You Have <b>${visibleHistory.size}</b> anime in your watch history.<br><b>Clear it now?</b>",
                                        yesLabel = "YES",
                                        noLabel = "NO",
                                        onConfirm = {
                                            clearHistoryStage = 0
                                            coroutineScope.launch {
                                                libraryRepository.clearHistory(selectedSource)
                                                watchHistory = libraryRepository.loadHistory()
                                                homeFeed = homeFeed.copy(
                                                    continueWatching = homeFeed.continueWatching.filterNot {
                                                        it.sourceIndex == selectedSource
                                                    }
                                                )
                                                tvIntegrationRepository.clearContinueWatching()
                                            }
                                        },
                                        onDismiss = { clearHistoryStage = 0 }
                                    )
                                }
                            }

                            AppToastOverlay(appToastMessage)
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackEpisodeLabel(episode: Episode): String {
    val defaultTitle = "Episode ${episode.number}"
    val titleSuffix = episode.title
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals(defaultTitle, ignoreCase = true) }
        ?.let { ". ${it.uppercase()}" }
        .orEmpty()
    return "EPISODE ${episode.number}$titleSuffix"
}

internal fun accountProviderName(provider: AccountProvider): String = when (provider) {
    AccountProvider.ANILIST -> "AniList"
    AccountProvider.MAL -> "MyAnimeList"
}
