# AnimeTV KMP Strict 1:1 UI/UX Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:dispatching-parallel-agents` (parallel non-colliding modules) and `superpowers:verification-before-completion` before claiming completion. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Achieve strict 1:1 visual, layout, theme, responsive scaling, animation, and interaction parity between `kmp` (`composeApp` / `androidApp`) and `AnimeTV` (`main.html`, `m.css`, `p.css`, `m.js`, `login/*`, `ui/player.html`, `MainActivity.java`, `AnimeView.java`).

**Architecture:** Compute CSS-equivalent viewport units (`vw`, `vh`, `u`, `--fs-7..100`, `--sys-stat-height`, `--sys-nav-height`) and 8-theme CSS color tokens (`primaryBg`, `primary1`, `primary2`, `primary3`, `accent1`, `dimm1`, `loginBg`) once at the root in `AnimeTvApp`, replacing the global `LocalDensity` `largeUi`/`fontScale` distortions and `Purple == Color.White` bug. Share 1:1 modal primitives (`ListOrderDialog`, `ConfirmDialog`, `TextPromptDialog`, `AboutPopupDialog`, `ImgPickerDialog`, `AppToast`) across all features, then migrate every screen in parallel across disjoint file sets.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.12.2, Material3, Coil3, Media3 1.8.1, Java 21 / Android SDK (Termux native `aapt2`).

## Global Constraints

- Design spec: `/data/data/com.termux/files/home/kmp/docs/superpowers/specs/2026-11-24-strict-1to1-ui-parity-design.md`
- Reference repo `/data/data/com.termux/files/home/AnimeTV` MUST NOT be modified.
- Target repo `/data/data/com.termux/files/home/kmp` MUST compile cleanly with:
  `ANDROID_HOME="$HOME/.local/android-sdk" ANDROID_SDK_ROOT="$HOME/.local/android-sdk" ./gradlew --console=plain --no-parallel :androidApp:compileDebugKotlin -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`
- Minimal code comments (only non-obvious logic).
- Keep all cross-worker public/internal function signatures strictly matching the `Interfaces` blocks below so parallel workers never break neighboring modules.

---

## Phase 1: Foundation (2 Parallel Workers — Disjoint Files)

### Task 1A: Icon Font Subset, Viewport Tokens, Theme Tokens & Shared 1:1 Modal Primitives
**Owner:** `Foundation-UI`
**Files:**
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/androidMain/res/font/paregi_icons.ttf`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/core/theme/ThemeTokens.kt`
- Create: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/core/theme/ViewportTokens.kt`
- Create: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/core/ui/AnimeTvModals.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/core/ui/SharedComponents.kt`

**Interfaces:**
- Produces in `com.paregi.animetv.core.theme`:
  - `data class AnimeTvThemeColors(val primaryBg: Color, val primary1: Color, val primary2: Color, val primary3: Color, val accent1: Color, val dimm1: Color, val loginBg: Color)`
  - `val LocalAnimeTvColors: ProvidableCompositionLocal<AnimeTvThemeColors>`
  - `fun animeTvThemeColorsFor(theme: String): AnimeTvThemeColors`
  - `val ANIME_TV_THEME_IDS: List<Pair<String, String>>` (`"theme_purple" to "Purple"`, `"theme_blue" to "Blue"`, `"theme_teal" to "Teal"`, `"theme_green" to "Green"`, `"theme_brown" to "Brown"`, `"theme_red" to "Red"`, `"theme_grey" to "Grey"`, `"theme_dark" to "Dark"`)
  - Keep existing `Background`, `Panel`, `Purple` (updated to `LocalAnimeTvColors.current.primary1`), `PurpleLight` (`LocalAnimeTvColors.current.accent1`), `TextPrimary`, `TextSecondary`, `BadgeThemeColors`, `LocalBadgeColors`, `badgeThemeColorsFor(theme)`, `resolveBadgeColors()`, `colorSchemeForTheme(theme, loginScreen)`, `loginBackdropForTheme(theme)`
  - Add new `IconGlyphs` constants: `RadioUnchecked = "\uE836"`, `RadioChecked = "\uE837"`, `DragIndicator = "\uE945"`, `Cancel = "\uE5C9"`, `SpaceBar = "\uE256"`, `LockOpen = "\uE898"`, `Block = "\uE14B"`, `LightMode = "\uE518"`, `GTranslate = "\uE927"`, `Sd = "\uE9DD"`, `FullHd = "\uF58B"`, `FourK = "\uE072"`, `Cloud = "\uE2BD"`, `ArrowRight = "\uE5DF"`, `VolumeDown = "\uE04D"`, `VolumeMute = "\uE04E"`, `ProgressActivity = "\uE9D0"`, `Cycle = "\uF854"`, `PartlyCloudyNight = "\uF174"`, `Update = "\uE923"`, `BlurOn = "\uE3A5"`, `Resize = "\uF707"`, `ShadowIcon = "\uE9DF"`, `Gradient = "\uE3E9"`, `TextFormat = "\uE165"`, `FormatBold = "\uE238"`, `Opacity = "\uE91C"`, `ExpandMore = "\uE5CF"`, `ChevronRight = "\uE5CC"`, `Close = "\uE5CD"`
  - `data class AnimeTvTokens(...)` with `screenWidth`, `screenHeight`, `vw`, `vh`, `u`, `portrait`, `isTouch`, `largeUi`, `sysStatHeight`, `sysNavHeight`, `tim1s..tim10s`, `easing`, `fs7..fs100: Dp`, plus `fsSp(dp: Dp): TextUnit` (dividing by `LocalDensity.current.fontScale`), and `LocalAnimeTvTokens`
  - `fun rememberAnimeTvTokens(maxWidth: Dp, maxHeight: Dp, sysStatHeight: Dp, sysNavHeight: Dp, fontSizeTier: Int, largeUi: Boolean, animationTier: Int, isTouch: Boolean): AnimeTvTokens`
  - `val LocalAppToast: ProvidableCompositionLocal<(String) -> Unit>`
- Produces in `com.paregi.animetv.core.ui`:
  - `enum class ListOrderMode { LIST, MENU, MULTI, THEME, ORDER }`
  - `data class ListOrderItem(val id: String, val title: String, val value: String? = null, val icon: String? = null, val selected: Boolean = false, val enabled: Boolean = true, val rightBadge: String? = null, val swatches: List<Color> = emptyList(), val disabledByOther: Boolean = false)`
  - `@Composable fun ListOrderDialog(title: String, items: List<ListOrderItem>, mode: ListOrderMode, onDismiss: () -> Unit, onSelect: (Int, ListOrderItem) -> Unit = { _, _ -> }, onReorder: ((List<ListOrderItem>) -> Unit)? = null, headerAvatar: (@Composable () -> Unit)? = null, headerTrailingIcon: String? = null, footerTip: String? = null, initialSelectedIndex: Int = 0)`
  - `@Composable fun ConfirmDialog(title: String?, message: String, titleIcon: String? = null, yesLabel: String = "YES", noLabel: String = "NO", onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun TextPromptDialog(title: String, initialValue: String = "", placeholder: String = "", maxLength: Int = 18, isPin: Boolean = false, okLabel: String = "OK", cancelLabel: String = "Cancel", onConfirm: (String) -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun AboutPopupDialog(visible: Boolean, loadingText: String? = null, qrImageUrl: String? = null, qrContent: (@Composable () -> Unit)? = null, caption: String? = null, onClick: (() -> Unit)? = null, onDismiss: () -> Unit)`
  - `data class ImgPickerItem(val id: String, val title: String, val imageUrl: String? = null, val drawableName: String? = null, val icon: String? = null, val selected: Boolean = false)`
  - `@Composable fun ImgPickerDialog(title: String, items: List<ImgPickerItem>, onSelect: (ImgPickerItem) -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun AppToastOverlay(message: String?)`
  - `@Composable fun AnimeTvSpinner(size: Dp, strokeWidth: Dp = size * 0.1f, color: Color = LocalAnimeTvColors.current.primary2)`

- [ ] **Step 1: Subset `icon.woff2` into `paregi_icons.ttf`** preserving all 86 existing codepoints plus all newly mapped AnimeTV codepoints (`fontTools.subset`).
- [ ] **Step 2: Update `ThemeTokens.kt`** with exact 8-theme CSS palettes, `LocalAnimeTvColors`, fixed `Purple`/`PurpleLight`, and complete `IconGlyphs`.
- [ ] **Step 3: Create `ViewportTokens.kt`** with exact CSS `--fs-*` tables for tiers `0..3` in landscape and portrait, timing variables, `LocalAnimeTvTokens`, and `LocalAppToast`.
- [ ] **Step 4: Create `AnimeTvModals.kt`** implementing `ListOrderDialog`, `ConfirmDialog`, `TextPromptDialog`, `AboutPopupDialog`, `ImgPickerDialog`, `AppToastOverlay`, and stepped `AnimeTvSpinner`.
- [ ] **Step 5: Update `SharedComponents.kt`** (`SettingsGroupTitle` in `#AAAAAA` mixed-case `--fs-12vw`, `#59A0A5A7` `FilterChip`).

### Task 1B: Android Insets, Domain Models, Repository Updates & Platform Expectations
**Owner:** `Foundation-Platform-Domain`
**Files:**
- Modify: `/data/data/com.termux/files/home/kmp/androidApp/src/main/kotlin/com/paregi/animetv/MainActivity.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/PlatformDevice.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/androidMain/kotlin/com/paregi/animetv/PlatformDevice.android.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/PlatformVoiceSearch.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/PlatformTrailer.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/SettingsModels.kt`
- Modify: `/data/data/com.termux/files/home/kmp/androidApp/src/main/kotlin/com/paregi/animetv/AndroidSettingsRepository.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/SourceCatalog.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/AnimeModels.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/CatalogModels.kt`
- Modify: `/data/data/com.termux/files/home/kmp/composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/AccountRepository.kt`
- Modify: `/data/data/com.termux/files/home/kmp/androidApp/src/main/kotlin/com/paregi/animetv/AndroidAccountRepository.kt`
- Modify: `/data/data/com.termux/files/home/kmp/androidApp/src/main/kotlin/com/paregi/animetv/AndroidAnimeRepository.kt`
- Modify: `/data/data/com.termux/files/home/kmp/androidApp/src/main/kotlin/com/paregi/animetv/source/anilist/AniListScheduleApi.kt`

**Interfaces:**
- Produces:
  - `expect fun platformIsTouchDevice(): Boolean` in `PlatformDevice.kt` / `PlatformDevice.android.kt`
  - `PlatformVoiceSearchButton(onResult: (String) -> Unit, inlineSearchBar: Boolean = false, iconColor: Color? = null, iconSize: TextUnit? = null)` in `PlatformVoiceSearch.kt` (also keep matching `PlatformVoiceSearch.android.kt` updated in Task 1B so Task 1 compiles!)
  - `PlatformTrailer(youtubeId: String, muted: Boolean, modifier: Modifier = Modifier, onPlaybackState: ((String) -> Unit)? = null)` in `PlatformTrailer.kt` (keep `PlatformTrailer.android.kt` matching `expect` signature in Task 1B, then Worker 3 enriches JS `postMessage` & `loop=1`!)
  - `MY_LIST_SECTION_IDS = listOf("maltab", "mal", "malplan", "anilisttab", "anilist", "anilistplan", "fav", "history")` in `SettingsModels.kt` & `AndroidSettingsRepository.kt` (normalizing legacy `"watchlist"` -> `"fav"` on read, supporting mutual exclusion `"maltab"` <-> `"mal"`, `"anilisttab"` <-> `"anilist"`)
  - `HOME_SECTION_IDS = listOf("recent", "trending", "popular", "alrecent", "altab", "altop", "alpopular", "alyear", "alupcoming")`, `DEFAULT_HOME_SECTIONS = listOf("recent", "trending", "popular")` (normalizing `"top"` <-> `"popular"`)
  - `SourceCatalog`: `"Re:ANIME"` displayName, `mirrors = listOf("gotaku.to")` / `listOf("reanime.to")`
  - `AnimeDetails`: optional `val seasons: List<Anime> = emptyList()`; `Anime`: optional `val nextAiringEpisode: Int? = null`; `Episode`: optional `val imageUrl: String? = null`
  - `AccountRepository.loginMalWithPassword(username: String, password: String): ConnectedAccount` implemented in `AndroidAccountRepository.kt`
  - `AniListScheduleApi`: exact 7-day window (`+ 604800`), `page < 10`
  - `AndroidAnimeRepository.loadHome`: Re:ANIME shelf titles `"Recently Released"`, `"Trending Today"`, `"Most Popular"`

- [ ] **Step 1: Update `MainActivity.kt` portrait window flags** (`WindowCompat.setDecorFitsSystemWindows(window, false)`, `SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_LAYOUT_STABLE`).
- [ ] **Step 2: Update `PlatformDevice.kt/.android.kt`, `PlatformVoiceSearch.kt/.android.kt`, `PlatformTrailer.kt/.android.kt`** signatures cleanly.
- [ ] **Step 3: Update `SettingsModels.kt`, `AndroidSettingsRepository.kt`, `SourceCatalog.kt`** for all 8 MyList IDs, AnimeTV Home IDs/defaults, and `Re:ANIME` / single default mirrors.
- [ ] **Step 4: Update `AnimeModels.kt`, `CatalogModels.kt`, `AccountRepository.kt`, `AndroidAccountRepository.kt`, `AndroidAnimeRepository.kt`, `AniListScheduleApi.kt`**.
- [ ] **Step 5: Verify Phase 1 compiles green** via `:androidApp:compileDebugKotlin`.

---

## Phase 2: Feature & Screen 1:1 Implementation (5 Parallel Workers — Disjoint Files)

### Cross-Worker Locked Entry Signatures (Do NOT Break Call Sites)
1. `HomeScreen(feed: HomeFeed, loading: Boolean, largeUi: Boolean, trailerMode: Int, onAnimeSelected: (Anime) -> Unit, onContinueWatching: (Anime) -> Unit, watchHistory: List<WatchProgress> = emptyList(), homeLayout: HomeLayout = HomeLayout.default(), onScrollOffsetChange: (Dp) -> Unit = {}, onRefreshRequested: () -> Unit = {}, onAboutFooterClick: (() -> Unit)? = null)`
2. `OriginalStyleMyListScreen(...)` & `AccountListsScreen(...)` keep their exact `SectionScreen.kt` parameter lists plus defaulted `onScrollOffsetChange: (Dp) -> Unit = {}` (both rendering the unified `#home_pagelist` stacked-shelves view!).
3. `ScheduleScreen(entries: List<AiringEntry>, loading: Boolean, largeUi: Boolean, onAnimeSelected: (Anime) -> Unit, onScrollOffsetChange: (Dp) -> Unit = {})`
4. `SearchScreen(repository: AnimeRepository, tvLayout: Boolean, selectedSource: Int, searchHistoryRepository: SearchHistoryRepository, query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit, onVoiceSearchResult: (String) -> Unit, profileRating: Int, profileActivation: Int, onAnimeSelected: (Anime) -> Unit, largeUi: Boolean = false)`
5. `DetailsScreen(...)` keeps exact `AnimeTvApp.kt` signature plus defaulted `loading: Boolean = false`, `openedFromCard: Boolean = true`.
6. `PlaybackScreen(...)` keeps exact `AnimeTvApp.kt` signature plus defaulted `seasons: List<Anime> = emptyList()`, `onOpenSettings: () -> Unit = {}`, `inWatchlist: Boolean = false`, `onToggleWatchlist: (() -> Unit)? = null`, `isTracked: Boolean = false`, `onTrackingClick: (() -> Unit)? = null`, `onSearchGenre: ((String) -> Unit)? = null`. `PlaybackErrorScreen(...)` & `PlaybackLoadingScreen(...)` keep existing parameters.
7. `SettingsScreen(...)` keeps exact signature plus defaulted `isAdminProfile: Boolean = true`, `onReloadNeeded: () -> Unit = {}`.
8. `HomeLayoutDialog(layout, onDismiss, onSave, sourceName: String = "")` and `MyListLayoutDialog(layout, onDismiss, onSave)`.

### Task 2A: Shell, Header, Bottom Nav, Sidebar, Wallpaper & Profile/Login (`Worker 1`)
**Files:** `AnimeTvApp.kt`, `app/SectionScreen.kt`, `app/AppConfirmationDialogs.kt`, `core/navigation/AppNavigation.kt`, `core/navigation/NavFloatingMenu.kt`, `features/profile/ProfileChooserScreen.kt`, `features/profile/ProfileManagementDialogs.kt`
- Implement `LocalAnimeTvTokens`, `LocalAnimeTvColors`, `LocalAppToast`, `AppToastOverlay`, `50%` wallpaper scrim over `#000000` + portrait bottom gradient, remove extra details backdrop blur, directional section transitions, header scroll-hide, live Settings overlay during playback, Exit Mode 3/4 Back fix, 2-step Clear History everywhere, clickable sidebar profile pill + `ListOrderDialog`-driven profile management, shrink-wrapped `#222` `PortraitNavCapsule`, exact `TvHeader` (clock, tooltips, inline mic, rotating logo menu, white external underline, `primary1` focus), `logo.svg` login watermark, 2s/4s chooser transitions, portrait `14vh` wrapping chooser grid, side-by-side PIN modal in both orientations with wrong-PIN shake + hardware keys.

### Task 2B: Home, Cards, MyList & Schedule (`Worker 2`)
**Files:** `core/ui/AnimeCard.kt`, `core/ui/AnimePresentation.kt`, `features/home/HomeScreen.kt`, `features/home/HomeLayoutDialog.kt`, `features/mylist/AccountListsScreen.kt`, `features/mylist/MyListLayoutDialog.kt`, `features/schedule/ScheduleScreen.kt`
- Implement exact `FocusCard` dimensions, unclipped `12.2vw` focus backplate + poster-only `accent1` shadow, `1.03f` `largeUi` scale, uncapped `--fs-8vw` badges at `0.7u`/`0.6u` inset, fixed bottom-start badge offset, `year`/`rating`/`isUpcoming`/`✨` badges, `"24 min"`/`"R+"`, `accent1` progress bar, Hero `5vw` external padding + portrait `--fs-*` sizing + `yt-play` fade + portrait `30s` pan + `#060` Watch Now pulse + 5-star gauge + cyclic dots above overlap + alpha masks + `altab` + `#home_about` + pull-to-refresh + `ListOrderDialog` layout customizers, unified 8-section stacked MyList with status tabs & auto-pagination, and 1:1 Schedule shelves & badges.

### Task 2C: Search, Details & Platform Voice/Trailer (`Worker 3`)
**Files:** `features/search/SearchScreen.kt`, `features/search/SearchRemoteKeyboard.kt`, `features/search/SearchFilterCatalog.kt`, `features/details/DetailsScreen.kt`, `features/details/AniListInfoDialog.kt`, `composeApp/src/androidMain/kotlin/com/paregi/animetv/PlatformTrailer.android.kt`, `composeApp/src/androidMain/kotlin/com/paregi/animetv/PlatformVoiceSearch.android.kt`
- Implement Search header, `50vw`/`70vw` borderless sanitized input + `_` cursor, `4vw` `backspace` fix, `#111`/`primary2` remote keyboard & keypad + persistent mute, TV history pills + delete confirm, `Type & Genres`-only filters (`TV SHORT`, no `Hentai`), auto-hide genres on result focus in `largeUi`, auto-pagination; Details `#malview` blur/surface, `#malview_loading`, `.view-bigfont` `72vw`, single portrait `More Info` + `32vh` action panel, centered landscape info, expanding `info` pill, uniform `#2a2a2a`/`primary1` buttons + `.disable` + `"Play Episode"` + active-only `#060`/`#800`, `PageUp`/`PageDown` ±10 stepping (no `EpisodeGridDialog`); More Info unified scroll + portrait 2-col facts + `trailer.thumbnail` + `alsd_infobox` + 4-col metrics + `Other Name` + compact bullet reviews + `alsd_tools` `postMessage` sync + `ListOrderDialog` status picker; inline voice overlay + YouTube JS events.

### Task 2D: Video Player, OSD, Subtitles & HTML5 (`Worker 4`)
**Files:** `composeApp/src/commonMain/kotlin/com/paregi/animetv/PlatformPlayer.kt`, `composeApp/src/androidMain/kotlin/com/paregi/animetv/PlatformPlayer.android.kt`, `composeApp/src/commonMain/kotlin/com/paregi/animetv/features/player/PlaybackScreens.kt`, `composeApp/src/androidMain/kotlin/com/paregi/animetv/AndroidHtml5PlayerDocument.kt`, `composeApp/src/commonMain/kotlin/com/paregi/animetv/domain/SubtitleStyleCatalog.kt`
- Implement `#pb` banner backdrop, exact `.z-loader`, 2-zone OSD reveal, left scrim, `#pb_action_streamtype`, un-overlapped `#pb_touch_close`, metadata pills + clickable genres/Watchlist/Tracking, `#pb_settings` chips + `ListOrderDialog` menus (including live `Settings` trigger & `CC` track menu), `primary3 -> primary1` seek track + z-ordered skip markers + 500ms debounce + accumulated swipe seek + always-active gestures, `1.8s` auto-skip intro & outro with 4s fade & ripple, inline horizontal `#pb_episodes`, `#pb_seasons`, `#pb_related`, `#pb_recs` shelves (retiring `PlaybackBrowseDialog`), unified `#vtt_subtitle` rendering + fullscreen `bottom: 3vw` TTF/stroke preview, HTML5 buffer/SRT/ASS support, and in-player `PLAYBACK ERROR` state.

### Task 2E: Settings & Modals (`Worker 5`)
**Files:** `features/settings/SettingsScreen.kt`, `features/settings/SettingsComponents.kt`, `features/settings/AccountIntegrationSettings.kt`, `features/settings/LanguagePickerDialogs.kt`, `features/settings/SourceMirrorBenchmarkDialog.kt`, `features/settings/WallpaperPickerDialog.kt`
- Implement single linear `#settings` slide-over (`34vw`/`100vw`, `translateX(50%)->0`), hide landscape back button & fix portrait `IconGlyphs.Back`, flat `#222`/`#444` rows with stacked `accent1` values & `--primary-3` switches (dim disabled `Use DoH`), remove KMP-only rows (`Source Domain`, `Mobile Navigation`, `Ambient Glow`), restore admin gating + Check for Nightly/Update + `needreload`, `ListOrderDialog` pickers for all 12 value settings + 4-swatch Interface Color + Performance multi-toggle, `MAL Login Type` (`QR` vs `Username + Password`) + white `#aboutpopup` modals, `Subtitle Language` (no search box) / `Audio Language` `ListOrderDialog`, `Benchmarking...` -> `ListOrderDialog` mirrors + `ConfirmDialog`, `ImgPickerDialog` 4-col `1:1` wallpaper picker, and `#logcat` dark modal (`#16161a`, `#1a1a22` header/footer, auto-scroll to bottom, `<c>close</c>` pill).

---

## Phase 3: Integration & Verification

- [ ] **Step 1:** Review all worker outputs for any cross-module signature mismatches or leftover unused imports/warnings.
- [ ] **Step 2:** Run full `:androidApp:compileDebugKotlin` and `:androidApp:assembleDebug` in `/data/data/com.termux/files/home/kmp` and resolve any issues until zero errors remain.
- [ ] **Step 3:** Inspect `PlaybackBrowseDialog.kt` / unused composables and verify clean build after final edit.
