package com.paregi.animetv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.core.theme.ANIME_TV_THEME_IDS
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalAppToast
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.animeTvThemeColorsFor
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AboutPopupDialog
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.SettingsGroupTitle
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AccountRepository
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.AnimeRepository
import com.paregi.animetv.domain.AppSettings
import com.paregi.animetv.domain.AudioLanguageCatalog
import com.paregi.animetv.domain.DebugLogRepository
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.LoginPollResult
import com.paregi.animetv.domain.SettingsRepository
import com.paregi.animetv.domain.SubtitleLanguageCatalog
import com.paregi.animetv.domain.SubtitleStyleCatalog
import com.paregi.animetv.features.player.SubtitleStylePickerDialog
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private const val ICON_SUBTITLES = "\uE048"

private val VIDEO_SCALING_OPTIONS = listOf("Normal", "Cover", "Stretch")
private val VIDEO_QUALITY_OPTIONS = listOf("Auto", "High", "Medium", "Low")
private val VIDEO_BUFFER_OPTIONS = listOf(
    "30 Seconds (Low RAM)",
    "1 Minute",
    "2 Minutes (Recommended)",
    "5 Minutes",
    "10 Minutes (High RAM)"
)
private val CLOSE_CONFIRM_OPTIONS = listOf("Double Back", "Close Confirmation", "Close Directly")
private val FONT_SIZE_OPTIONS = listOf("Small", "Medium", "Large", "Larger")
private val LOGIN_STYLE_OPTIONS = listOf("Static Style", "Follow User Theme", "Follow User Theme and Wallpaper")
private val ANIMATION_OPTIONS = listOf("Normal", "Fast", "Faster", "No Transition")
private val EXIT_MODE_OPTIONS = listOf(
    "Exit directly",
    "Confirm to exit",
    "Back to login screen",
    "Open sidebar before exit",
    "Sidebar to login screen"
)
private val TRAILER_OPTIONS = listOf("No trailer", "Trailer without sound", "Trailer with sound")
private val HTTP_CLIENT_OPTIONS = listOf(
    "OkHttp with DoH Support",
    "Generic HttpClient",
    "Cronet - Chromium Networking"
)
private val PROGRESS_MODE_OPTIONS = listOf(
    "Start playing",
    "Watched 25%",
    "Watched 50%",
    "Watched 75%",
    "Finish watching"
)
private val AUTO_TRACKING_OPTIONS = listOf(
    "Do nothing",
    "Show tracking dialog",
    "Auto add to AniList",
    "Auto add to MAL",
    "Auto add to AniList and MAL"
)
private val PERFORMANCE_FULL_NAMES = listOf("Blur Effect", "Scale Effect", "Drop Shadow", "Mask Effect", "Text Shadow")
private val PERFORMANCE_SHORT_NAMES = listOf("Blur", "Scale", "Shadow", "Mask", "Text")
private val PERFORMANCE_ICONS = listOf(
    IconGlyphs.BlurOn,
    IconGlyphs.Resize,
    IconGlyphs.ShadowIcon,
    IconGlyphs.Gradient,
    IconGlyphs.TextFormat
)
private val PERFORMANCE_DEFAULTS = listOf(true, false, true, true, false)
private val CACHE_SIZES_MB = listOf(5, 10, 20, 30, 50, 80, 100, 120, 150)

private enum class ValuePickerType {
    VIDEO_SCALING,
    VIDEO_QUALITY,
    VIDEO_BUFFER,
    CLOSE_CONFIRMATION,
    FONT_SIZE,
    LOGIN_STYLE,
    TRANSITION_ANIMATION,
    EXIT_MODE,
    PLAY_TRAILER,
    HTTP_CLIENT,
    WATCH_PROGRESS,
    AUTO_TRACKING,
    INTERFACE_COLOR,
    SEEK_VALUE,
    CACHE_SIZE
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    accountRepository: AccountRepository,
    accountState: AccountState,
    onAccountStateChange: (AccountState) -> Unit,
    homeFeed: HomeFeed,
    settingsRepository: SettingsRepository,
    settingsReady: Boolean,
    debugLogRepository: DebugLogRepository,
    animeRepository: AnimeRepository? = null,
    tvLayout: Boolean = false,
    forceLinearLayout: Boolean = false,
    onClose: (() -> Unit)? = null,
    isAdminProfile: Boolean = true,
    onReloadNeeded: () -> Unit = {},
    currentVersionName: String = "",
    onCheckForUpdate: () -> Unit = {}
) {
    val tokens = LocalAnimeTvTokens.current
    val showToast = LocalAppToast.current
    val accountScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val firstItemRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!tokens.isTouch) {
            yield()
            runCatching { firstItemRequester.requestFocus() }
        }
    }

    var needReload by remember { mutableStateOf(false) }
    val currentOnReloadNeeded by rememberUpdatedState(onReloadNeeded)
    fun flushReloadIfNeeded() {
        if (needReload) {
            needReload = false
            currentOnReloadNeeded()
        }
    }
    DisposableEffect(Unit) {
        onDispose { flushReloadIfNeeded() }
    }

    var activeValuePicker by remember { mutableStateOf<ValuePickerType?>(null) }
    var performanceDialog by remember { mutableStateOf(false) }
    var subtitleLanguageDialog by remember { mutableStateOf(false) }
    var subtitleStyleDialog by remember { mutableStateOf(false) }
    var audioLanguageDialog by remember { mutableStateOf(false) }
    var wallpaperDialog by remember { mutableStateOf(false) }
    var clearCacheConfirmation by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var debugLogging by remember(debugLogRepository) { mutableStateOf(debugLogRepository.isEnabled()) }
    var diagnosticLogsOpen by remember { mutableStateOf(false) }
    var diagnosticLogsLoading by remember { mutableStateOf(false) }
    var diagnosticLogs by remember { mutableStateOf("") }
    var aboutUri by remember { mutableStateOf<String?>(null) }

    var malLoginTypeDialog by remember { mutableStateOf(false) }
    var malPasswordDialog by remember { mutableStateOf(false) }
    var malPasswordSubmitting by remember { mutableStateOf(false) }
    var malPasswordError by remember { mutableStateOf<String?>(null) }
    var loginDialog by remember { mutableStateOf<LoginDialogState?>(null) }
    var logoutConfirmation by remember { mutableStateOf<AccountProvider?>(null) }
    var loginGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(accountRepository, settingsReady) {
        if (!settingsReady) return@LaunchedEffect
        onAccountStateChange(runCatching { accountRepository.readAccounts() }.getOrDefault(AccountState()))
    }

    LaunchedEffect(loginDialog?.session?.id) {
        val session = loginDialog?.session ?: return@LaunchedEffect
        while (true) {
            delay(2_000)
            val result = runCatching { accountRepository.pollLogin(session) }
                .getOrElse { LoginPollResult.Waiting }
            when (result) {
                LoginPollResult.Waiting -> {
                    loginDialog = loginDialog?.copy(authorizing = false)
                }
                LoginPollResult.Authorizing -> {
                    loginDialog = loginDialog?.copy(authorizing = true)
                }
                is LoginPollResult.Complete -> {
                    onAccountStateChange(accountRepository.readAccounts())
                    loginDialog = null
                    showToast(
                        if (session.provider == AccountProvider.ANILIST) "AniList login successful"
                        else "MyAnimeList login successful"
                    )
                    break
                }
                is LoginPollResult.Failed -> {
                    loginDialog = null
                    showToast(result.message)
                    break
                }
            }
        }
    }

    fun startQrLogin(provider: AccountProvider) {
        val requestId = loginGeneration + 1
        loginGeneration = requestId
        loginDialog = LoginDialogState(provider = provider, session = null)
        accountScope.launch {
            val result = runCatching { accountRepository.startLogin(provider) }
            if (loginGeneration == requestId) {
                result.fold(
                    onSuccess = { session ->
                        loginDialog = LoginDialogState(provider = provider, session = session)
                    },
                    onFailure = { error ->
                        loginDialog = null
                        showToast(error.message ?: "Could not start login")
                    }
                )
            } else {
                result.getOrNull()?.let { runCatching { accountRepository.cancelLogin(it) } }
            }
        }
    }

    fun dismissLogin() {
        loginGeneration += 1
        val session = loginDialog?.session
        loginDialog = null
        if (session != null) {
            accountScope.launch { runCatching { accountRepository.cancelLogin(session) } }
        }
    }

    fun disconnect(provider: AccountProvider) {
        accountScope.launch {
            runCatching { accountRepository.disconnect(provider) }
            onAccountStateChange(runCatching { accountRepository.readAccounts() }.getOrDefault(AccountState()))
            logoutConfirmation = null
            showToast(
                if (provider == AccountProvider.MAL) "MyAnimeList logout successful..."
                else "AniList logout successful..."
            )
        }
    }

    fun loadDiagnosticLogs() {
        diagnosticLogsOpen = true
        diagnosticLogsLoading = true
        accountScope.launch {
            val result = runCatching { debugLogRepository.recentLogs(1500) }
            diagnosticLogs = result.getOrElse { "Error retrieving logs: ${it.message ?: "unknown error"}" }
                .ifBlank { "(empty logs)" }
            diagnosticLogsLoading = false
        }
    }

    val performanceFlags = remember(settings.performanceEffects) {
        List(PERFORMANCE_FULL_NAMES.size) { index ->
            settings.performanceEffects.getOrElse(index) { PERFORMANCE_DEFAULTS[index] }
        }
    }
    val performanceSummary = remember(performanceFlags) {
        val activeVisualEffects = performanceFlags.indices.filter { !performanceFlags[it] }
        when (activeVisualEffects.size) {
            0 -> "Full performance"
            PERFORMANCE_SHORT_NAMES.size -> "Full effects"
            else -> activeVisualEffects.joinToString(", ") { PERFORMANCE_SHORT_NAMES[it] }
        }
    }

    val selectedSubtitleLanguage = remember(settings.subtitleLanguage) {
        SubtitleLanguageCatalog.all.firstOrNull {
            it.code == settings.subtitleLanguage
        } ?: SubtitleLanguageCatalog.all.first()
    }
    val subtitleLangCode = selectedSubtitleLanguage.code
    val subtitleValueIcon = if (subtitleLangCode.isNotBlank() && subtitleLangCode != "hard" && subtitleLangCode != "nosub") {
        IconGlyphs.ClosedCaption
    } else ICON_SUBTITLES
    val subtitleValueBadge = subtitleLangCode.takeIf {
        it.isNotBlank() && it != "hard" && it != "nosub"
    }?.uppercase()

    val selectedAudioLanguage = remember(settings.audioLanguage) {
        AudioLanguageCatalog.all.firstOrNull {
            it.code == settings.audioLanguage
        } ?: AudioLanguageCatalog.all.first()
    }

    val currentSettings by rememberUpdatedState(settings)
    val currentOnChange by rememberUpdatedState(onChange)

    val playbackToggles = remember(settings.autoNext, settings.autoSkipIntro, settings.skipFiller) {
        listOf(
            SettingToggle("Auto Next", settings.autoNext) { currentOnChange(currentSettings.copy(autoNext = it)) },
            SettingToggle("Auto Skip Intro", settings.autoSkipIntro) { currentOnChange(currentSettings.copy(autoSkipIntro = it)) },
            SettingToggle("Skip Filler", settings.skipFiller) { currentOnChange(currentSettings.copy(skipFiller = it)) }
        )
    }
    val preferDubToggle = remember(settings.preferDub) {
        SettingToggle("Prefer DUB Stream", settings.preferDub) {
            currentOnChange(currentSettings.copy(preferDub = it))
        }
    }
    val disableTouchToggle = remember(settings.disableTouchGestures) {
        SettingToggle("Disable Touch Gesture", settings.disableTouchGestures) {
            currentOnChange(currentSettings.copy(disableTouchGestures = it))
        }
    }
    val preloadNextToggle = remember(settings.preloadNextEpisode) {
        SettingToggle("Preload Next Episode", settings.preloadNextEpisode) {
            currentOnChange(currentSettings.copy(preloadNextEpisode = it))
        }
    }

    val uiToggles = remember(
        settings.homeWatchlist,
        settings.showClock,
        settings.directPageSwitch,
        settings.directSidebar
    ) {
        listOf(
            SettingToggle("MyList Main Page", settings.homeWatchlist) { currentOnChange(currentSettings.copy(homeWatchlist = it)) },
            SettingToggle("Show Clock", settings.showClock) { currentOnChange(currentSettings.copy(showClock = it)) },
            SettingToggle("Switch page on select", settings.directPageSwitch) { currentOnChange(currentSettings.copy(directPageSwitch = it)) },
            SettingToggle("Show sidebar directly", settings.directSidebar) { currentOnChange(currentSettings.copy(directSidebar = it)) }
        )
    }

    val listState = rememberLazyListState()
    val playClickSound = LocalNavigationSound.current
    val groupRequesters = remember { List(8) { if (it == 0) firstItemRequester else FocusRequester() } }
    var focusedGroupIndex by remember { mutableIntStateOf(0) }
    val groupHeaderIndices = remember(tokens.isTouch, isAdminProfile) {
        val g0Header = 0
        val g1Header = 6
        val g2Header = g1Header + 10 + (if (tokens.isTouch) 1 else 0) + 1
        val g3Header = g2Header + 5 + (if (isAdminProfile) 1 else 0) + 1
        val g4Header = g3Header + 8 + (if (isAdminProfile) 1 else 0) + 1
        val g5Header = g4Header + 6 + 1
        val g6Header = g5Header + 7 + (if (isAdminProfile) 2 else 0) + 1
        val g7Header = g6Header + 2
        listOf(g0Header, g1Header, g2Header, g3Header, g4Header, g5Header, g6Header, g7Header)
    }

    fun jumpToSettingsGroup(delta: Int) {
        val nextGroup = (focusedGroupIndex + delta + groupHeaderIndices.size) % groupHeaderIndices.size
        focusedGroupIndex = nextGroup
        playClickSound()
        accountScope.launch {
            listState.scrollToItem(groupHeaderIndices[nextGroup])
            yield()
            runCatching { groupRequesters[nextGroup].requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusProperties { onExit = { cancelFocusChange() } }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.PageUp -> {
                        jumpToSettingsGroup(-1)
                        true
                    }
                    Key.DirectionRight, Key.PageDown -> {
                        jumpToSettingsGroup(1)
                        true
                    }
                    else -> false
                }
            }
            .background(Color(0xFF222222))
    ) {
        SettingsPanelHeader(
            onBack = if (tokens.portrait && onClose != null) {
                {
                    flushReloadIfNeeded()
                    onClose()
                }
            } else null
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = tokens.sysNavHeight)
        ) {
            item(key = "header_subtitles", contentType = "header") { SettingsGroupTitle("Subtitle & Languages") }
            item(key = "row_sub_lang", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue(
                        label = "Subtitle Language",
                        value = selectedSubtitleLanguage.label,
                        valueIcon = subtitleValueIcon,
                        valueBadge = subtitleValueBadge
                    ) { subtitleLanguageDialog = true },
                    modifier = Modifier
                        .focusRequester(groupRequesters[0])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 0 }
                )
            }
            item(key = "row_sub_style", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Subtitle Style", SubtitleStyleCatalog.summary(settings.subtitleStyle)) {
                        subtitleStyleDialog = true
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 0 }
                )
            }
            item(key = "row_prefer_dub", contentType = "toggle_row") {
                SettingToggleRow(
                    item = preferDubToggle,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 0 }
                )
            }
            item(key = "row_audio_lang", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Audio Language", selectedAudioLanguage.label) {
                        audioLanguageDialog = true
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 0 }
                )
            }
            item(key = "spacer_subtitles", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_playback", contentType = "header") { SettingsGroupTitle("Playback") }
            itemsIndexed(
                playbackToggles,
                key = { _, toggle -> "row_${toggle.label}" },
                contentType = { _, _ -> "toggle_row" }
            ) { idx, toggle ->
                SettingToggleRow(
                    item = toggle,
                    modifier = (if (idx == 0) Modifier.focusRequester(groupRequesters[1]) else Modifier)
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "row_video_scaling", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Video Scaling", VIDEO_SCALING_OPTIONS[settings.videoScale.coerceIn(0, 2)]) {
                        activeValuePicker = ValuePickerType.VIDEO_SCALING
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "row_video_quality", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Video Quality", VIDEO_QUALITY_OPTIONS[settings.playbackQuality.coerceIn(0, 3)]) {
                        activeValuePicker = ValuePickerType.VIDEO_QUALITY
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "row_seek_value", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Seek Value", "${settings.seekSeconds} seconds") {
                        activeValuePicker = ValuePickerType.SEEK_VALUE
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "row_video_buffer", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Video Buffer", VIDEO_BUFFER_OPTIONS[settings.videoBuffer.coerceIn(0, 4)]) {
                        activeValuePicker = ValuePickerType.VIDEO_BUFFER
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "row_close_confirm", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Close Confirmation", CLOSE_CONFIRM_OPTIONS[settings.closeConfirmation.coerceIn(0, 2)]) {
                        activeValuePicker = ValuePickerType.CLOSE_CONFIRMATION
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            if (tokens.isTouch) {
                item(key = "row_disable_touch", contentType = "toggle_row") {
                    SettingToggleRow(
                        item = disableTouchToggle,
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                    )
                }
            }
            item(key = "row_preload_next", contentType = "toggle_row") {
                SettingToggleRow(
                    item = preloadNextToggle,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 1 }
                )
            }
            item(key = "spacer_playback", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_styling", contentType = "header") { SettingsGroupTitle("Styling") }
            item(key = "row_font_size", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Font Size", FONT_SIZE_OPTIONS[settings.fontSize.coerceIn(0, 3)]) {
                        activeValuePicker = ValuePickerType.FONT_SIZE
                    },
                    modifier = Modifier
                        .focusRequester(groupRequesters[2])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 2 }
                )
            }
            item(key = "row_interface_color", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Interface Color", formatThemeName(settings.theme)) {
                        activeValuePicker = ValuePickerType.INTERFACE_COLOR
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 2 }
                )
            }
            item(key = "row_wallpaper", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Wallpaper", settings.wallpaperTitle.ifBlank { "No Wallpaper" }) {
                        wallpaperDialog = true
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 2 }
                )
            }
            item(key = "row_large_ui", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Large UI", settings.largeUi) { onChange(settings.copy(largeUi = it)) },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 2 }
                )
            }
            if (isAdminProfile) {
                item(key = "row_login_style", contentType = "value_row") {
                    SettingValueRow(
                        item = SettingValue("Login Style", LOGIN_STYLE_OPTIONS[settings.loginScreenStyle.coerceIn(0, 2)]) {
                            activeValuePicker = ValuePickerType.LOGIN_STYLE
                        },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 2 }
                    )
                }
            }
            item(key = "spacer_styling", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_ui_perf", contentType = "header") { SettingsGroupTitle("UI & Performance") }
            item(key = "row_transition_anim", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Transition Animation", ANIMATION_OPTIONS[settings.animation.coerceIn(0, 3)]) {
                        activeValuePicker = ValuePickerType.TRANSITION_ANIMATION
                    },
                    modifier = Modifier
                        .focusRequester(groupRequesters[3])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 3 }
                )
            }
            item(key = "row_performance_ui", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Performance UI", performanceSummary) {
                        performanceDialog = true
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 3 }
                )
            }
            item(key = "row_nav_sound", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Navigation Sound", settings.navigationSound) {
                        onChange(settings.copy(navigationSound = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 3 }
                )
            }
            items(
                uiToggles,
                key = { "row_${it.label}" },
                contentType = { "toggle_row" }
            ) {
                SettingToggleRow(
                    item = it,
                    modifier = Modifier.onFocusChanged { st -> if (st.hasFocus) focusedGroupIndex = 3 }
                )
            }
            if (isAdminProfile) {
                item(key = "row_exit_mode", contentType = "value_row") {
                    SettingValueRow(
                        item = SettingValue("Exit Mode", EXIT_MODE_OPTIONS[settings.exitMode.coerceIn(0, 4)]) {
                            activeValuePicker = ValuePickerType.EXIT_MODE
                        },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 3 }
                    )
                }
            }
            item(key = "spacer_ui_perf", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_others", contentType = "header") { SettingsGroupTitle("Others") }
            item(key = "row_anilist_slideshow", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("AniList Slideshow", settings.aniListSlideshow) {
                        needReload = true
                        onChange(settings.copy(aniListSlideshow = it))
                    },
                    modifier = Modifier
                        .focusRequester(groupRequesters[4])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 4 }
                )
            }
            item(key = "row_hires_banner", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("HiRes AniList Banner", settings.highResolutionBanners) {
                        needReload = true
                        onChange(settings.copy(highResolutionBanners = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 4 }
                )
            }
            item(key = "row_play_trailer", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Play Trailer", TRAILER_OPTIONS[settings.trailerMode.coerceIn(0, 2)]) {
                        activeValuePicker = ValuePickerType.PLAY_TRAILER
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 4 }
                )
            }
            item(key = "row_chinese_anime", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Chinese Anime", settings.includeChineseAnime) {
                        needReload = true
                        onChange(settings.copy(includeChineseAnime = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 4 }
                )
            }
            item(key = "row_japanese_titles", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Japanese Titles", settings.japaneseTitles) {
                        onChange(settings.copy(japaneseTitles = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 4 }
                )
            }
            item(key = "spacer_others", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_networks", contentType = "header") { SettingsGroupTitle("Networks") }
            item(key = "row_http_client", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("HTTP Client", HTTP_CLIENT_OPTIONS[settings.httpClient.coerceIn(0, 2)]) {
                        activeValuePicker = ValuePickerType.HTTP_CLIENT
                    },
                    modifier = Modifier
                        .focusRequester(groupRequesters[5])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            item(key = "row_use_doh", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle(
                        label = "Use DoH",
                        checked = settings.useDnsOverHttps,
                        enabled = settings.httpClient == 0
                    ) {
                        onChange(settings.copy(useDnsOverHttps = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            if (isAdminProfile) {
                item(key = "row_cache_size", contentType = "value_row") {
                    SettingValueRow(
                        item = SettingValue("Cache Size", "${settings.cacheSizeMb} MB") {
                            activeValuePicker = ValuePickerType.CACHE_SIZE
                        },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                    )
                }
            }
            item(key = "row_progressive_cache", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Progressive Cache", settings.progressiveCache) {
                        onChange(settings.copy(progressiveCache = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            if (isAdminProfile) {
                item(key = "row_image_cdn", contentType = "toggle_row") {
                    SettingToggleRow(
                        item = SettingToggle("Use Image CDN", settings.useImageCdn) {
                            onChange(settings.copy(useImageCdn = it))
                        },
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                    )
                }
            }
            item(key = "row_hls_proxy", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("HLS Stream Proxy", settings.hlsProxy) {
                        onChange(settings.copy(hlsProxy = it))
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            item(key = "row_debug_logging", contentType = "toggle_row") {
                SettingToggleRow(
                    item = SettingToggle("Debug Logging", debugLogging) { enabled ->
                        debugLogRepository.setEnabled(enabled)
                        debugLogging = debugLogRepository.isEnabled()
                    },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            item(key = "row_view_logs", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("View Logs", "") { loadDiagnosticLogs() },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 5 }
                )
            }
            item(key = "spacer_networks", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "group_integration", contentType = "integration_group") {
                Box(
                    modifier = Modifier
                        .focusRequester(groupRequesters[6])
                        .focusGroup()
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 6 }
                ) {
                    AccountIntegrationRows(
                        accountState = accountState,
                        progressSetting = SettingValue(
                            "Update watch progress",
                            PROGRESS_MODE_OPTIONS[settings.listProgressMode.coerceIn(0, 4)]
                        ) { activeValuePicker = ValuePickerType.WATCH_PROGRESS },
                        trackingSetting = SettingValue(
                            "Auto Tracking",
                            AUTO_TRACKING_OPTIONS[settings.malTrackingDialog.coerceIn(0, 4)]
                        ) { activeValuePicker = ValuePickerType.AUTO_TRACKING },
                        onConnect = { provider ->
                            if (provider == AccountProvider.MAL) {
                                malLoginTypeDialog = true
                            } else {
                                startQrLogin(AccountProvider.ANILIST)
                            }
                        },
                        onLogoutRequest = { logoutConfirmation = it }
                    )
                }
            }
            item(key = "spacer_integration", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }

            item(key = "header_about", contentType = "header") { SettingsGroupTitle("About") }
            item(key = "row_donation", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Donation", "") { aboutUri = "https://ko-fi.com/paregi" },
                    modifier = Modifier
                        .focusRequester(groupRequesters[7])
                        .onFocusChanged { if (it.hasFocus) focusedGroupIndex = 7 }
                )
            }
            item(key = "row_discord", contentType = "value_row") {
                SettingValueRow(
                    item = SettingValue("Discord Server", "") { aboutUri = "https://discord.gg/M76sBtFpH" },
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 7 }
                )
            }
            if (isAdminProfile) {
                item(key = "row_check_update", contentType = "value_row") {
                    SettingValueRow(
                        item = SettingValue(
                            label = "Check for Update",
                            value = currentVersionName.takeIf { it.isNotBlank() }?.let { "v$it" }.orEmpty(),
                            onClick = onCheckForUpdate
                        ),
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedGroupIndex = 7 }
                    )
                }
            }
            item(key = "spacer_about", contentType = "spacer") { Spacer(Modifier.height(tokens.u * 0.5f)) }
        }
    }

    activeValuePicker?.let { picker ->
        when (picker) {
            ValuePickerType.INTERFACE_COLOR -> {
                val selectedThemeId = normalizeThemeId(settings.theme)
                val themeItems = remember(selectedThemeId) {
                    ANIME_TV_THEME_IDS.map { (id, name) ->
                        val themeColors = animeTvThemeColorsFor(id)
                        ListOrderItem(
                            id = id,
                            title = name,
                            selected = id == selectedThemeId,
                            swatches = listOf(
                                themeColors.primary1,
                                themeColors.primary2,
                                themeColors.primary3,
                                themeColors.accent1
                            )
                        )
                    }
                }
                ListOrderDialog(
                    title = "Interface Color",
                    items = themeItems,
                    mode = ListOrderMode.THEME,
                    initialSelectedIndex = ANIME_TV_THEME_IDS.indexOfFirst { it.first == selectedThemeId }.coerceAtLeast(0),
                    onDismiss = { activeValuePicker = null },
                    onSelect = { _, item ->
                        onChange(settings.copy(theme = item.id))
                        activeValuePicker = null
                    }
                )
            }
            ValuePickerType.CACHE_SIZE -> {
                val currentIdx = CACHE_SIZES_MB.indexOf(settings.cacheSizeMb)
                val items = remember(settings.cacheSizeMb) {
                    buildList {
                        add(
                            ListOrderItem(
                                id = "clear",
                                title = "Clear Cache",
                                icon = IconGlyphs.DeleteForever
                            )
                        )
                        CACHE_SIZES_MB.forEach { size ->
                            add(
                                ListOrderItem(
                                    id = size.toString(),
                                    title = "$size MB",
                                    selected = settings.cacheSizeMb == size
                                )
                            )
                        }
                    }
                }
                ListOrderDialog(
                    title = "Cache Size",
                    items = items,
                    mode = ListOrderMode.LIST,
                    initialSelectedIndex = if (currentIdx >= 0) currentIdx + 1 else 7,
                    onDismiss = { activeValuePicker = null },
                    onSelect = { index, item ->
                        activeValuePicker = null
                        if (index == 0) {
                            clearCacheConfirmation = true
                        } else {
                            item.id.toIntOrNull()?.let { mb ->
                                onChange(settings.copy(cacheSizeMb = mb))
                            }
                        }
                    }
                )
            }
            else -> {
                val (title, options, selectedIndex) = when (picker) {
                    ValuePickerType.VIDEO_SCALING ->
                        Triple("Video Scaling", VIDEO_SCALING_OPTIONS, settings.videoScale.coerceIn(0, 2))
                    ValuePickerType.VIDEO_QUALITY ->
                        Triple("Video Quality", VIDEO_QUALITY_OPTIONS, settings.playbackQuality.coerceIn(0, 3))
                    ValuePickerType.VIDEO_BUFFER ->
                        Triple("Video Buffer", VIDEO_BUFFER_OPTIONS, settings.videoBuffer.coerceIn(0, 4))
                    ValuePickerType.CLOSE_CONFIRMATION ->
                        Triple("Close Confirmation", CLOSE_CONFIRM_OPTIONS, settings.closeConfirmation.coerceIn(0, 2))
                    ValuePickerType.FONT_SIZE ->
                        Triple("Font Size", FONT_SIZE_OPTIONS, settings.fontSize.coerceIn(0, 3))
                    ValuePickerType.LOGIN_STYLE ->
                        Triple("Login Screen Style", LOGIN_STYLE_OPTIONS, settings.loginScreenStyle.coerceIn(0, 2))
                    ValuePickerType.TRANSITION_ANIMATION ->
                        Triple("Transition Animation", ANIMATION_OPTIONS, settings.animation.coerceIn(0, 3))
                    ValuePickerType.EXIT_MODE ->
                        Triple("Exit Mode", EXIT_MODE_OPTIONS, settings.exitMode.coerceIn(0, 4))
                    ValuePickerType.PLAY_TRAILER ->
                        Triple("Play Trailer", TRAILER_OPTIONS, settings.trailerMode.coerceIn(0, 2))
                    ValuePickerType.HTTP_CLIENT ->
                        Triple("HTTP Client", HTTP_CLIENT_OPTIONS, settings.httpClient.coerceIn(0, 2))
                    ValuePickerType.WATCH_PROGRESS ->
                        Triple("Update watch progress", PROGRESS_MODE_OPTIONS, settings.listProgressMode.coerceIn(0, 4))
                    ValuePickerType.AUTO_TRACKING ->
                        Triple("Auto Tracking", AUTO_TRACKING_OPTIONS, settings.malTrackingDialog.coerceIn(0, 4))
                    ValuePickerType.SEEK_VALUE ->
                        Triple("Seek Value", (2..20).map { "$it seconds" }, (settings.seekSeconds - 2).coerceIn(0, 18))
                    ValuePickerType.INTERFACE_COLOR, ValuePickerType.CACHE_SIZE -> error("Handled above")
                }
                val items = remember(options, selectedIndex) {
                    options.mapIndexed { idx, label ->
                        ListOrderItem(
                            id = idx.toString(),
                            title = label,
                            selected = idx == selectedIndex
                        )
                    }
                }
                ListOrderDialog(
                    title = title,
                    items = items,
                    mode = ListOrderMode.LIST,
                    initialSelectedIndex = selectedIndex,
                    onDismiss = { activeValuePicker = null },
                    onSelect = { idx, _ ->
                        val updated = when (picker) {
                            ValuePickerType.VIDEO_SCALING -> settings.copy(videoScale = idx)
                            ValuePickerType.VIDEO_QUALITY -> settings.copy(playbackQuality = idx)
                            ValuePickerType.VIDEO_BUFFER -> settings.copy(videoBuffer = idx)
                            ValuePickerType.CLOSE_CONFIRMATION -> settings.copy(closeConfirmation = idx)
                            ValuePickerType.FONT_SIZE -> settings.copy(fontSize = idx)
                            ValuePickerType.LOGIN_STYLE -> settings.copy(loginScreenStyle = idx)
                            ValuePickerType.TRANSITION_ANIMATION -> settings.copy(animation = idx)
                            ValuePickerType.EXIT_MODE -> settings.copy(exitMode = idx)
                            ValuePickerType.PLAY_TRAILER -> settings.copy(trailerMode = idx)
                            ValuePickerType.HTTP_CLIENT -> settings.copy(httpClient = idx)
                            ValuePickerType.WATCH_PROGRESS -> settings.copy(listProgressMode = idx)
                            ValuePickerType.AUTO_TRACKING -> settings.copy(malTrackingDialog = idx)
                            ValuePickerType.SEEK_VALUE -> settings.copy(seekSeconds = 2 + idx)
                            ValuePickerType.INTERFACE_COLOR, ValuePickerType.CACHE_SIZE -> settings
                        }
                        onChange(updated)
                        activeValuePicker = null
                    }
                )
            }
        }
    }

    if (performanceDialog) {
        // Inverted parity with AnimeTV's rev=true: checked row = visual effect active (performanceFlags[i] == false).
        val items = remember(performanceFlags) {
            PERFORMANCE_FULL_NAMES.mapIndexed { index, name ->
                ListOrderItem(
                    id = "perf_$index",
                    title = name,
                    icon = PERFORMANCE_ICONS[index],
                    selected = !performanceFlags[index]
                )
            }
        }
        ListOrderDialog(
            title = "Performance UI",
            items = items,
            mode = ListOrderMode.MULTI,
            onDismiss = { performanceDialog = false },
            onSelect = { index, item ->
                val updatedFlags = performanceFlags.toMutableList().apply {
                    this[index] = !item.selected
                }
                onChange(settings.copy(performanceEffects = updatedFlags))
            }
        )
    }

    if (subtitleLanguageDialog) {
        SubtitleLanguageDialog(
            selectedLanguageCode = settings.subtitleLanguage,
            onSelect = { code ->
                onChange(settings.copy(subtitleLanguage = code))
                subtitleLanguageDialog = false
            },
            onDismiss = { subtitleLanguageDialog = false }
        )
    }

    if (audioLanguageDialog) {
        AudioLanguageDialog(
            selectedLanguageCode = settings.audioLanguage,
            onSelect = { code ->
                onChange(settings.copy(audioLanguage = code))
                audioLanguageDialog = false
            },
            onDismiss = { audioLanguageDialog = false }
        )
    }

    if (subtitleStyleDialog) {
        SubtitleStylePickerDialog(
            style = settings.subtitleStyle,
            onChange = { style -> onChange(settings.copy(subtitleStyle = style)) },
            onDismiss = { subtitleStyleDialog = false }
        )
    }

    if (wallpaperDialog) {
        WallpaperPickerDialog(
            homeFeed = homeFeed,
            selectedWallpaperUrl = settings.wallpaperUrl,
            onSelect = { url, title ->
                onChange(settings.copy(wallpaperUrl = url, wallpaperTitle = title))
                wallpaperDialog = false
            },
            onDismiss = { wallpaperDialog = false }
        )
    }

    if (clearCacheConfirmation) {
        ConfirmDialog(
            title = "Clear Cache",
            message = "Clear AnimeTV Cache?",
            yesLabel = "YES",
            noLabel = "NO",
            onConfirm = {
                if (!clearingCache) {
                    clearingCache = true
                    accountScope.launch {
                        val result = runCatching { settingsRepository.clearCache() }
                        clearingCache = false
                        clearCacheConfirmation = false
                        result.fold(
                            onSuccess = { showToast("Cache cleared") },
                            onFailure = { error -> showToast(error.message ?: "Could not clear cached data") }
                        )
                    }
                }
            },
            onDismiss = { if (!clearingCache) clearCacheConfirmation = false }
        )
    }

    aboutUri?.let { uri ->
        val caption = if (uri.contains("discord.gg")) {
            "Join Discord Server\n$uri"
        } else {
            "Donate Project\n$uri"
        }
        AboutPopupDialog(
            visible = true,
            qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=${encodeUrlParam(uri)}",
            caption = caption,
            onClick = {
                runCatching { uriHandler.openUri(uri) }
                aboutUri = null
            },
            onDismiss = { aboutUri = null }
        )
    }

    if (diagnosticLogsOpen) {
        DiagnosticLogsDialog(
            logPath = debugLogRepository.logPath(),
            loading = diagnosticLogsLoading,
            logs = diagnosticLogs,
            onCopy = {
                accountScope.launch {
                    val result = runCatching { debugLogRepository.recentLogs(2000) }
                    val copied = result.getOrNull()?.let(debugLogRepository::copyToClipboard) == true
                    showToast(if (copied) "Logs copied to clipboard" else "Failed to copy logs")
                }
            },
            onClear = {
                diagnosticLogsLoading = true
                accountScope.launch {
                    val result = runCatching { debugLogRepository.clearLogs() }
                    diagnosticLogs = if (result.isSuccess) "(logs cleared)"
                    else "Could not clear logs: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    showToast(if (result.isSuccess) "Logs cleared" else "Could not clear logs")
                    diagnosticLogsLoading = false
                }
            },
            onDismiss = { diagnosticLogsOpen = false }
        )
    }

    AccountIntegrationDialogs(
        malLoginTypeDialog = malLoginTypeDialog,
        malPasswordDialog = malPasswordDialog,
        malPasswordSubmitting = malPasswordSubmitting,
        malPasswordError = malPasswordError,
        loginDialog = loginDialog,
        logoutConfirmation = logoutConfirmation,
        onDismissMalLoginType = { malLoginTypeDialog = false },
        onSelectMalLoginType = { usePassword ->
            malLoginTypeDialog = false
            if (usePassword) {
                malPasswordError = null
                malPasswordDialog = true
            } else {
                startQrLogin(AccountProvider.MAL)
            }
        },
        onSubmitMalPassword = { username, password ->
            if (!malPasswordSubmitting) {
                malPasswordSubmitting = true
                malPasswordError = null
                accountScope.launch {
                    val result = runCatching {
                        accountRepository.loginMalWithPassword(username, password)
                    }
                    malPasswordSubmitting = false
                    result.fold(
                        onSuccess = {
                            onAccountStateChange(
                                runCatching { accountRepository.readAccounts() }.getOrDefault(AccountState())
                            )
                            malPasswordDialog = false
                            showToast("MyAnimeList login successful")
                        },
                        onFailure = { error ->
                            malPasswordError = error.message ?: "MyAnimeList login failed"
                        }
                    )
                }
            }
        },
        onDismissMalPassword = {
            if (!malPasswordSubmitting) {
                malPasswordDialog = false
                malPasswordError = null
            }
        },
        onDismissLogin = ::dismissLogin,
        onDismissLogout = { logoutConfirmation = null },
        onDisconnect = ::disconnect
    )
}

@Composable
private fun SettingsPanelHeader(onBack: (() -> Unit)?) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var backFocused by remember { mutableStateOf(false) }

    val backHighlight = when {
        pressed || backFocused -> colors.primary1
        hovered -> Color(0xFF333333)
        else -> Color.Transparent
    }

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF2A2A2A))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (tokens.portrait) tokens.sysStatHeight else 0.dp,
                    start = if (onBack != null) tokens.u * 1f else tokens.u * 2f,
                    end = tokens.u * 2f
                )
                .height(tokens.u * 6f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .padding(end = tokens.u * 1f)
                        .clip(CircleShape)
                        .background(backHighlight)
                        .hoverable(interactionSource = interactionSource)
                        .onFocusChanged { backFocused = it.isFocused }
                        .navigationClickable(onClick = onBack)
                        .padding(tokens.u * 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = IconGlyphs.Back,
                        fontFamily = LocalIconFont.current,
                        color = Color.White,
                        fontSize = fsSp(tokens.fs17)
                    )
                }
            }
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = fsSp(tokens.fs17),
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(
            thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp),
            color = Color(0xFF111111)
        )
    }
}

@Composable
private fun DiagnosticLogsDialog(
    logPath: String,
    loading: Boolean,
    logs: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val playClickSound = LocalNavigationSound.current
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val closeFocusRequester = remember { FocusRequester() }
    val logLines = remember(logs, loading) {
        if (loading) emptyList() else logs.lines()
    }

    LaunchedEffect(Unit) {
        yield()
        runCatching { closeFocusRequester.requestFocus() }
    }

    LaunchedEffect(loading, logLines.size) {
        if (!loading && logLines.isNotEmpty()) {
            yield()
            listState.scrollToItem(logLines.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center
        ) {
            val windowWidth = (tokens.vw * 82f).coerceAtMost(960.dp)
            val cornerRadius = tokens.u * 0.8f
            val borderThickness = (tokens.u * 0.1f).coerceAtLeast(1.dp)

            Column(
                modifier = Modifier
                    .width(windowWidth)
                    .heightIn(max = tokens.vh * 85f)
                    .then(
                        if (!shadowsDisabled) {
                            Modifier.shadow(tokens.u * 2f, RoundedCornerShape(cornerRadius), clip = false)
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF16161A))
                    .border(borderThickness, Color.White.copy(alpha = 0.15f), RoundedCornerShape(cornerRadius))
                    .pointerInput(Unit) { detectTapGestures {} }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                playClickSound()
                                onDismiss()
                                true
                            }
                            Key.DirectionUp -> {
                                scope.launch { listState.scrollBy(-180f) }
                                true
                            }
                            Key.DirectionDown -> {
                                scope.launch { listState.scrollBy(180f) }
                                true
                            }
                            Key.PageUp -> {
                                scope.launch { listState.scrollBy(-600f) }
                                true
                            }
                            Key.PageDown -> {
                                scope.launch { listState.scrollBy(600f) }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A22))
                        .padding(horizontal = tokens.u * 1.5f, vertical = tokens.u * 1f)
                ) {
                    Text(
                        text = "Diagnostic Logs",
                        color = Color.White,
                        fontSize = fsSp(tokens.fs14),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "File: $logPath",
                        color = Color(0xFF888888),
                        fontSize = fsSp(tokens.u * 0.9f),
                        modifier = Modifier.padding(top = tokens.u * 0.3f)
                    )
                }
                HorizontalDivider(thickness = borderThickness, color = Color.White.copy(alpha = 0.10f))

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (tokens.vh * 30f).coerceAtMost(250.dp), max = tokens.vh * 52f)
                            .background(Color(0xFF0D0E12))
                            .padding(horizontal = tokens.u * 1.5f, vertical = tokens.u * 1f)
                    ) {
                        Text(
                            text = "Loading logs...",
                            color = Color(0xFFD1D5DB),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fsSp(tokens.fs10),
                            lineHeight = fsSp(tokens.fs10) * 1.4f
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (tokens.vh * 30f).coerceAtMost(250.dp), max = tokens.vh * 52f)
                            .background(Color(0xFF0D0E12)),
                        contentPadding = PaddingValues(horizontal = tokens.u * 1.5f, vertical = tokens.u * 1f)
                    ) {
                        itemsIndexed(
                            logLines,
                            key = { idx, _ -> idx },
                            contentType = { _, _ -> "log_line" }
                        ) { _, line ->
                            Text(
                                text = line,
                                color = Color(0xFFD1D5DB),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fsSp(tokens.fs10),
                                lineHeight = fsSp(tokens.fs10) * 1.4f
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = borderThickness, color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A22))
                        .padding(horizontal = tokens.u * 1.5f, vertical = tokens.u * 0.8f),
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 1f, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogcatButton(
                        label = "Copy to Clipboard",
                        onClick = onCopy
                    )
                    LogcatButton(
                        label = "Clear Logs",
                        onClick = onClear
                    )
                    LogcatButton(
                        label = "Close",
                        icon = IconGlyphs.Close,
                        focusRequester = closeFocusRequester,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun LogcatButton(
    label: String,
    icon: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = focused || hovered
    val borderThickness = (tokens.u * 0.1f).coerceAtLeast(1.dp)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.u * 0.4f))
            .background(if (active) colors.primary1 else Color(0xFF262730))
            .border(
                borderThickness,
                if (active) colors.accent1 else Color.Transparent,
                RoundedCornerShape(tokens.u * 0.4f)
            )
            .hoverable(interactionSource = interactionSource)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(horizontal = tokens.u * 1.4f, vertical = tokens.u * 0.6f),
        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!icon.isNullOrBlank()) {
            Text(
                text = icon,
                fontFamily = LocalIconFont.current,
                color = Color.White,
                fontSize = fsSp(tokens.fs11)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = fsSp(tokens.fs11)
        )
    }
}

private fun encodeUrlParam(value: String): String = buildString {
    val hex = "0123456789ABCDEF"
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        val safe = code in 0x41..0x5A || code in 0x61..0x7A || code in 0x30..0x39 ||
            code == 0x2D || code == 0x5F || code == 0x2E || code == 0x7E
        if (safe) append(code.toChar()) else {
            append('%')
            append(hex[code shr 4])
            append(hex[code and 0x0F])
        }
    }
}
