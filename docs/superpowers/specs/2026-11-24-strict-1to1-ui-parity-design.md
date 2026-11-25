# AnimeTV KMP Strict 1:1 UI/UX Parity Design

**Date:** 2026-11-24
**Approach:** Approach A — Root `ViewportTokens` + Shared `AnimeTV` Theme & Modal Primitives
**Reference Repo:** `/data/data/com.termux/files/home/AnimeTV` (`main.html`, `m.css`, `p.css`, `m.js`, `login/*`, `ui/player.html`, `MainActivity.java`, `AnimeView.java`)
**Target Repo:** `/data/data/com.termux/files/home/kmp` (`composeApp` & `androidApp`)

---

## 1. Scope & Non-Goals

- **Goal:** Bring every UI screen, overlay, modal, card, theme palette, animation, navigation flow, and interaction in `kmp` to strict 1:1 visual and UX parity with `AnimeTV` across both **Portrait (Phone)** and **Landscape / Android TV** modes.
- **Explicit Exclusions / Non-Goals:**
  - Keep `kmp`'s package/namespace (`com.paregi.animetv` / `com.paregi.androidtv.animetvjmto`) and keep only the two enabled catalog sources (`Gotaku` and `Re:ANIME`).
  - Keep `AnimeTV` repository untouched.
  - Keep Desktop deferred (Android Phone & Android TV / Google TV / Fire TV only).
  - Do not copy `AnimeTV` HTML/CSS/JS as runtime UI; implement purely in Compose Multiplatform + Android platform bridges.
  - Remove or retire KMP-only UI deviations that diverge from `AnimeTV` (such as `ModernFloatingNavPill`, unreachable categorized `isMobile` settings UI, inline `Source Domain` settings row, `EpisodeGridDialog`, `PlaybackBrowseDialog` bottom sheet, Search `Season`/`Sort`/`Year` extra chips, custom mirror domain input, and local device image pickers in UI dialogs).
  - Keep `AnimeTV` user-facing UI strings (`"Exit AnimeTV?"`, `"Donate Project"`, `"Join Discord Server"`, `"Re:ANIME"`, etc.).

---

## 2. Core Architecture: Viewport Units, Theme Tokens, System Insets & Icon Font

### 2.1 Root `ViewportTokens` (`core/theme/ViewportTokens.kt` + `AnimeTvApp.kt`)
Replace the global `Density(density * 1.12f, fontScale)` multiplier in `AnimeTvApp.kt` with a dedicated `LocalAnimeTvTokens` computed once at the root `BoxWithConstraints`:
- **Base viewport units:**
  - `screenWidth: Dp`, `screenHeight: Dp`
  - `vw = screenWidth / 100f`, `vh = screenHeight / 100f`
  - `portrait: Boolean = screenHeight > screenWidth`
  - `u = if (portrait) vh else vw` (mirrors `p.css` remapping `--fs-*vw` and card/modal dimensions from `vw` to `vh`)
  - `sysStatHeight: Dp` (`--sys-stat-height`), `sysNavHeight: Dp` (`--sys-nav-height`)
  - `isTouch: Boolean` (`!platformIsTelevision()` / Android `FEATURE_TOUCHSCREEN` distinction, separate from `portrait` so touchscreen tablets in landscape still get touch behaviors `.istouch` while non-touch TV gets remote behaviors).
- **Exact CSS `--fs-*` Typography Scale (`fontSize` `0..3`):**
  Convert viewport-based tokens directly to `TextUnit` with `fontScale` neutralized (`(dp.value / LocalDensity.current.fontScale).sp`) so `1.4vw` is strictly `1.4%` of screen width (landscape) or `1.7vh` in portrait, unaffected by OS font-scale multipliers:
  - **Landscape (`m.css:77-160`):**
    - `0` (Small): `7=.7vw, 8=.8vw, 10=1vw, 11=1.1vw, 12=1.2vw, 13=1.3vw, 14=1.4vw, 15=1.5vw, 17=1.7vw, 20=2vw, 22=2.2vw, 25=2.5vw, 30=3vw, 40=4vw, 60=6vw, 100=10vw`
    - `1` (Medium): `7=.9vw, 8=1vw, 10=1.2vw, 11=1.3vw, 12=1.4vw, 13=1.5vw, 14=1.6vw, 15=1.7vw, 17=1.8vw, 20=2.2vw, 22=2.4vw, 25=2.7vw, 30=3vw, 40=4vw, 60=6vw, 100=10vw`
    - `2` (Large — default): `7=1.1vw, 8=1.2vw, 10=1.4vw, 11=1.5vw, 12=1.6vw, 13=1.7vw, 14=1.8vw, 15=1.9vw, 17=2vw, 20=2.4vw, 22=2.6vw, 25=2.9vw, 30=3.4vw, 40=4.4vw, 60=6.4vw, 100=10vw`
    - `3` (Larger): `7=1.2vw, 8=1.3vw, 10=1.5vw, 11=1.6vw, 12=1.7vw, 13=1.8vw, 14=1.9vw, 15=2vw, 17=2.1vw, 20=2.5vw, 22=2.7vw, 25=3vw, 30=3.5vw, 40=4.5vw, 60=6.5vw, 100=10vw`
  - **Portrait (`p.css:1-79`):**
    - `0` (Small): `7=.9vh, 8=1vh, 10=1.2vh, 11=1.3vh, 12=1.4vh, 13=1.5vh, 14=1.6vh, 15=1.7vh, 17=1.8vh, 20=2.2vh, 22=2.4vh, 25=2.7vh, 30=3vh, 40=4vh, 60=6vh, 100=10vh`
    - `1` (Medium): `7=1.1vh, 8=1.2vh, 10=1.4vh, 11=1.5vh, 12=1.6vh, 13=1.7vh, 14=1.8vh, 15=1.9vh, 17=2vh, 20=2.4vh, 22=2.6vh, 25=2.9vh, 30=3.4vh, 40=4.4vh, 60=6.4vh, 100=10vh`
    - `2` (Large — default): `7=1.4vh, 8=1.5vh, 10=1.7vh, 11=1.8vh, 12=1.9vh, 13=2.0vh, 14=2.1vh, 15=2.2vh, 17=2.3vh, 20=2.7vh, 22=2.9vh, 25=3.2vh, 30=3.7vh, 40=4.7vh, 60=6.7vh, 100=10.2vh`
    - `3` (Larger): `7=1.6vh, 8=1.7vh, 10=1.9vh, 11=2.0vh, 12=2.1vh, 13=2.2vh, 14=2.3vh, 15=2.4vh, 17=2.5vh, 20=2.9vh, 22=3.1vh, 25=3.4vh, 30=3.9vh, 40=4.9vh, 60=6.9vh, 100=10.4vh`
  - **Login screen isolation:** `ProfileChooserScreen` always renders using scale tier `0` (`1vw` / `1vh` base), unaffected by user `fontSize` or `largeUi`.
  - **`largeUi` scoping:** Applies **only in landscape** (`!portrait`) to Home Hero (`55vw`) and horizontal poster shelves/search cards (`16vw`, `1.03f` focus scale), matching `.large-ui-list` (`m.css:4900-5009`).

### 2.2 Exact 8-Theme CSS Palette Model (`core/theme/ThemeTokens.kt`)
Separate AnimeTV's CSS theme variables from Material3 `onPrimary` so `Purple` never aliases `Color.White`:
- Provide `AnimeTvThemeColors(primaryBg, primary1, primary2, primary3, accent1, dimm1, loginBg)` via `LocalAnimeTvColors`:
  - `theme_purple`: `#030004`, `#440066`, `#660088`, `#8800AA`, `#CC55FF`, `#223344`, loginBg `#330055`
  - `theme_blue`: `#000003`, `#001144`, `#002266`, `#224488`, `#4477BB`, `#223344`, loginBg `#000839`
  - `theme_teal`: `#000404`, `#003333`, `#005555`, `#007777`, `#00AAAA`, `#223344`, loginBg `#002222`
  - `theme_green`: `#000300`, `#004400`, `#006600`, `#008800`, `#33BB33`, `#223344`, loginBg `#003300`
  - `theme_brown`: `#080400`, `#442200`, `#664400`, `#885522`, `#BB7700`, `#223344`, loginBg `#331100`
  - `theme_red`: `#060000`, `#440000`, `#660000`, `#882222`, `#BB4444`, `#223344`, loginBg `#330000`
  - `theme_grey`: `#080808`, `#112233`, `#223344`, `#335588`, `#9FD356`, `#223344`, loginBg `#333333`
  - `theme_dark` (default): `#040404`, `#004466`, `#006688`, `#2288AA`, `#EE8833`, `#222222`, loginBg `#22262F`
- Root `#animebg` background is pure `#000000` when no wallpaper is active (`bg0`); `TextPrimary = #FFFFFF`, `TextSecondary = #AAAAAA`.
- Legacy KMP theme IDs saved in prefs (if any) gracefully map into these 8 palettes via `SettingsScreen` / `ThemeTokens.kt` (`theme_violet` -> `theme_purple`, etc.) without breaking selection of `theme_dark` or `theme_teal`.

### 2.3 Portrait Edge-to-Edge & System Insets (`MainActivity.kt`)
- In portrait (`Configuration.ORIENTATION_PORTRAIT`), set `WindowCompat.setDecorFitsSystemWindows(window, false)` with `SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_LAYOUT_STABLE` and `statusBars()`/`navigationBars()` shown translucently (matching `AnimeView.java:188-196`).
- Measure `WindowInsetsCompat.Type.statusBars().top` and `navigationBars().bottom` in `MainActivity.kt` (via `WindowInsets` / Compose `WindowInsets`) and supply `statHeight` & `navHeight` into `LocalAnimeTvTokens` so headers, heroes, drawers, and scroll containers bleed behind bars and pad cleanly by `--sys-stat-height` and `--sys-nav-height`.

### 2.4 Icon Font Parity (`paregi_icons.ttf` + `IconGlyphs`)
- Regenerate `composeApp/src/androidMain/res/font/paregi_icons.ttf` as a subset of `AnimeTV/app/src/main/assets/view/icon.woff2` (preserving all existing 86 codepoints and adding every AnimeTV Material Symbol ligature missing today: `radio_button_unchecked`, `radio_button_checked`, `drag_indicator`, `cancel`, `space_bar`, `lock_open`, `block`, `light_mode`, `g_translate`, `sd`, `full_hd`, `4k`, `cloud`, `arrow_right`, `volume_down`, `volume_mute`, `progress_activity`, `cycle`, `partly_cloudy_night`, `update`, `blur_on`, `resize`, `shadow`, `gradient`, `text_format`).
- Add matching codepoint constants in `IconGlyphs` (`ThemeTokens.kt`).

### 2.5 Animation & Performance Flags (`m.css:95-99, 235-280`)
- Transition durations from `appSettings.animation` (`0..3`):
  - `0`: `100/200/300/400/500/700/800/1000` ms
  - `1`: `50/100/150/200/250/350/400/500` ms
  - `2`: `25/50/75/100/125/175/200/250` ms
  - `3`: `0` ms
- Easing: `CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)`.
- Respect all 5 `performanceEffects` flags everywhere (`[0] Blur`, `[1] Scale`, `[2] Shadow`, `[3] Mask`, `[4] Text Shadow`; `true` = disabled for performance).

---

## 3. Shared Modal & Feedback Primitives (`core/ui/AnimeTvModals.kt`)

Build reusable 1:1 Compose modal windows matching `m.css:3633-3701, 4432-4876` and `p.css:742-757, 1090-1260`:

1. **`ListOrderDialog` (`#listorder_window`):**
   - Scrim `rgba(0,0,0,.7)` fading in/out; centered card `width = 40vw` (`40vh` portrait, capped `90vw`), `maxHeight = 75vh`, background `#222222`, radius `.5vw`/`.5vh`, `scale(0.8 -> 1)` + opacity over `tim3s`.
   - Title bar `.listorder_title`: `#181818` background, bold `--fs-14vw`, supports optional profile header variant (`.listorder_title.profile`: circular `3vw` avatar + name + trailing `build` icon).
   - Row types:
     - **Radio (`showList`):** leading `radio_button_checked` (`accent1`) / `radio_button_unchecked` (`#666666`), primary label + optional `<span class="value">` (`accent1` below label) + optional trailing `alangavail` check/tag.
     - **Menu (`showMenu`):** leading `1.5vw` Material icon + label (plus optional `value` below or right-aligned for Subtitle Style).
     - **Multi-toggle (`showMulti`):** leading icon + label + right-aligned check icon (`check` in `accent1` / `clear` in `#666`).
     - **Theme picker:** 4 adjacent square color blocks (`fs-14vw`) per theme (`primary1`, `primary2`, `primary3`, `accent1`).
     - **Reorderable list (`show`):** checkbox + label + right `drag_indicator`; D-pad Left/Right moves selected item up/down; optional bottom tip bar `"Use left/right to move order"`.
   - Active/focused row turns `#444444` solid background (no border, no scale).
2. **`ConfirmDialog` (`#listorder_window.inputdialog`):**
   - Width `40vw` (`36vh` portrait, max `90vw`), `#222222` bg, `#181818` header bar (supports `<c>warning</c> WARNING!!!` / `<c>door_open</c> Exit AnimeTV?`), `#AAAAAA` message body (`1.4vw`), and two centered `45%` (`40%` portrait) `#111111` buttons: **`YES`** (left, initially focused with `primary1` fill) and **`NO`** (right).
3. **`TextPromptDialog` (`textPrompt`):**
   - Same `inputdialog` frame (`40vw`/`36vh`), `#181818` title bar, centered `#111111` text/PIN input field (`18` chars max for name, masked `4` chars for PIN), and `OK` / `Cancel` `#111111` (`primary1` focused) buttons.
4. **`AboutPopupDialog` (`#aboutpopup`):**
   - Scrim `rgba(0,0,0,.7)`, compact centered white `#FFFFFF` card (`minWidth = 15vw/vh`, `padding = 2vw/vh`, `borderRadius = 1vw/vh`), bold `--primary-1` text (`--fs-14vw`). Supports `z-loader z-small` spinner mode (`"Please Wait.."`, `"Benchmarking..."`, `"Please finish ... authorization"`) and `20vw×20vw` (`20vh×20vh` portrait) QR + caption mode (clickable card opens URL; dismiss on overlay tap or Back).
5. **`ImgPickerDialog` (`#listorder_window.imgpicker`):**
   - Width `43vw` landscape (`84vw` portrait), `#222222` title bar, `#000000` grid area (`maxHeight = 33vw` landscape / `64vw` portrait) with exact **4 columns** of `1:1` square tiles (`10vw × 10vw` landscape / `20vw × 20vw` portrait), `.5vw` transparent border (`#FFFFFF` when active/focused), `.2vw` `accent1` inner `.checked` outline, and bottom title `<b>` visible only on `.active` focus/hover.
6. **`AppToast` (`#animetv_toast`):**
   - Centered pill at `bottom = 4%` (`borderRadius = fs-40vw`, dark surface, white text, auto-dismiss) for all general AnimeTV toasts (`Add New User`, `PIN set`, `Playback failed`, `Please login to ... first...`, `Press back again to close playback...`).

---

## 4. Screen-by-Screen 1:1 Specifications

### 4.1 App Shell, Header, Portrait Capsule & Sidebar (`AnimeTvApp.kt`, `AppNavigation.kt`, `NavFloatingMenu.kt`)
- **Wallpaper Layer:**
  - Wallpaper drawn at `alpha = 1f` over `#000000` with `rgba(0,0,0,0.5)` overlay; portrait adds `linear-gradient(180deg, transparent 70%, color-mix(in srgb, primary1 30%, rgba(0,0,0,.5)) 100%)` over scroll container.
  - Remove `AnimeTvApp.kt:938-952` extra blurred anime backdrop when `selectedAnime != null`; `#malview` applies its own `backdrop-filter`-style blurred translucent surface (`rgba(32,32,32,.6)` when blur enabled, `#222` when disabled).
- **Landscape Header (`TvHeader`):**
  - `padding(start = 5vw, end = 5vw, top = 3vw, bottom = 0)`.
  - Items order: `[Clock (when showClock — right-aligned, #fff, fs-20vw, textShadow, 2s locale tick)]`, `[Settings icon]`, `[Schedule icon]`, `[AnimeTV logo]`, `[Voice mic]`, `[Search]`, `[Home]`, `[MyList]`.
  - `#home_logo`: `opacity = 0.7f` (`1f` focused), `fs-20vw`, outside-left `<c>menu</c>` offset `-(.2vw + fs-20vw)` with `-90deg -> 0deg` rotation (`tim2s`) without shifting `"AnimeTV"`.
  - `#home_voice`: Positioned between Logo and Search whenever speech recognition is available (`3vw × 3vw` white icon + shadow + expanding inline `:before` pill during active listening instead of centered modal).
  - Active/focus pill: `primary1` fill + `#fff` text on D-pad `.active`, `accent1` fill on `:hover`.
  - Selected underline: `#FFFFFF`, `60%` width, `.25vw` height at `bottom = -.5vw` (outside pill), animating `scaleX(1->0)` & `opacity(1->0)` on focus/hover. `Settings` never receives `.selected` underline.
  - Tooltips: Animated `:after` `"Airing Schedule"` and `"Settings"` below icons (`bottom: -3.5vw; width: 10vw; fs-11vw`) on focus/hover.
  - Scroll hide: Translate `-10vw` up when Home/MyList/Schedule scroll offset > `5vw`.
  - Re-clicking selected `Schedule` immediately refreshes Schedule; re-clicking `Home` or `MyList` opens `ListOrderDialog` menu without extra header title (`"Refresh Home"`, `"Customize Home"`, `"Reload AnimeTV"` with `cycle` icon / `"Refresh MyList"`, `"Customize MyList"`, `"Clear Watch History"`).
- **Portrait Bottom Nav (`PortraitNavCapsule`):**
  - Remove `ModernFloatingNavPill`, `CapsuleNavItem`, and `mobileNavStyle`/`mobileNavGlow` UI usage.
  - Shrink-wrapped `#222222` pill (`borderRadius = 10vh`, `padding = .9vh .7vh .6vh .3vh`, `marginBottom = sysNavHeight + 2vh`, no border, subtle shadow).
  - 6 icons in order (`Menu | Home | MyList | Search | Schedule | Settings`, with `2vh` extra gap before `Home`), `fs-25vw` (`rgba(255,255,255,0.8)` normal, `#fff` hover, `rgba(0,0,0,.4)` press), `#fff` `60% × .25vh` bottom underline (`bottom: -.5vh`), scroll hide `translateY(calc(110% + sysNavHeight + 2vh))`.
- **Sidebar (`SidebarDialog`):**
  - Slide+fade animation (`translateX(-5vw/-5vh -> 0)` + scrim fade over `tim3s`).
  - Landscape: `22vw` outer (`18vw` + `2vw` padding), scrim `#000(5%) -> .95(20%) -> .6(50%) -> 0(100%)`, top vertical fade mask.
  - Portrait: `50vw + 4vh` width, `2vh` side / `4vh + sysStatHeight` top inset, scrim `#000(5%) -> .8(35%) -> .6(70%) -> .3(100%)`; remove right-edge swipe-open trigger.
  - Profile pill: Transparent (`#fff` text) unfocused, solid `#FFFFFF` (`#000000` text) when focused/active; displays `displayName` + subtitle `"admin"` or `"user"` / `"user - <Rating>"`; **clicking opens Profile Options `ListOrderDialog`** (`Switch User`, `Display Name`, `Profile Picture`, `PIN`, plus `Manage Users` for admin or `Parental Control` / `Delete User` when editing non-admin) so non-admin users can edit their own profile. Remove inline `"Switch User"` / `"Manage Users"` text links below pill.
  - Source items: `"Gotaku"` and `"Re:ANIME"`; solid `#FFFFFF` pill (`5vw`/`8vh` radius) with `#000000` text when focused/selected; left `cloud` icon swapping to `settings` when active (no separate right gear button); `@domain` (`span i`) visible only when focused; touch requires 1 tap to focus and 2nd tap to switch source (or open domain benchmark if already active).
  - Fix Exit Mode 3/4 Back order in `AnimeTvApp.kt`: when `sidebarExitArmed` is true, Back executes exit/return-to-login instead of closing the sidebar.

### 4.2 Profile Chooser, PIN & Profile Management (`ProfileChooserScreen.kt`, `ProfileManagementDialogs.kt`)
- **Profile Chooser (`login.html`/`login.css`/`loginp.css`):**
  - Root `#000000`, gradient `rgba(0,0,0,0.5) -> loginBg`, 2s theme color animation (`tim10s*2`) & 4s wallpaper `Crossfade`.
  - Render `logo.svg` bottom-right watermark (`15vw × 7vw` landscape / `15vh × 7vh` portrait, `bottom/right: .5vw/vh`, `opacity: 0.3f`, `ContentScale.Crop`).
  - Heading `"Who's Watching?"` (`#999999`, `3.5vw`/`3.5vh`, `marginBottom = 2vw/2vh`), container `paddingBottom = 5.5vw/5.5vh`.
  - Portrait uses `FlowRow` wrapping centered items (`width = 15vh`, `margin = 3vh 1vh 2vh 1vh`, `14vh` avatar, `.8vh` border, `2.2vh` multi-line wrapping name). Landscape centers `15vw` items (`14vw` avatar, `.8vw` border, `2.2vw` name).
  - Remove `1.08f` focus scale and lock icon badge; focused border `#FFFFFF`, unfocused `0.6f` alpha + `#666666` label. Remove `"Cancel"` button (Back exits or closes dialog in-app).
- **PIN Entry Modal:**
  - Same side-by-side layout in BOTH landscape and portrait (`45vw` landscape / `45vh; maxWidth 90vw` portrait, `padding = 1u`, `borderRadius = .8u`, `rgba(30,30,30,.85)`, shadow, no border, `0.9 -> 1.0` scale + fade).
  - Left: `15u` `.user_item` (`14u` avatar + `.8u` white border + `2.2u` name, no `"Enter PIN"` subtitle).
  - Right: `28u` keypad (`marginStart = 1u`) with 4 PIN boxes (`5u × 4u`, `borderRadius = .5u`, `#000000` filled / `rgba(10,10,10,.7)` empty, no borders, `⬤` at `3u`) + 4×3 keypad (`8u × 3u` cells, `2.5u` font, `rgba(40,40,40,.5)` bg, `primary1` focus on digits, `#880000` focus on `clear`/`backspace`, `<c>clear</c>` icon-only clear button, no `"Cancel"`/`"Continue"` buttons).
  - Wrong PIN: turns boxes `#880000`, plays `0.4s` horizontal shake (`±.3u`), resets digits, moves focus to `clear`, no error text.
  - Support hardware keyboard `0–9` & `Backspace`; pressing Back clears entered digits first before dismissing modal.
- **Profile Management Flows:**
  - Replace form `ProfileManagerDialog` & `ProfileEditDialog` with `ListOrderDialog` menus (`Manage Users`: `"Add New User"` instant 1-click creation, `"Default User"` radio picker with explicit `"Always show login screen"` at index `0`, plus per-user submenu with `Display Name`, `Profile Picture`, `PIN`, `Parental Control`, `Delete User`).
  - `Display Name`: `TextPromptDialog` (`18` chars max).
  - `Profile Picture`: `ImgPickerDialog` (4-col `1:1` grid of 36 bundled avatars + connected `-anilist`/`-mal` avatars).
  - `PIN`: Masked `TextPromptDialog` flow (verify current PIN -> `"Set Pin"` -> `"Confirm Pin"`, or `ConfirmDialog` `"Remove PIN"`).
  - `Parental Control`: `ListOrderDialog` radio picker with full descriptions (`"G - All Ages"` .. `"No Limitation"`).
  - `Delete User`: `ConfirmDialog` (`"Delete User"`, `"Are you sure you want to delete user <b>name</b>?"`, `YES`/`NO`).

### 4.3 Home, Cards, MyList, Schedule & Search
- **Home (`HomeScreen.kt`, `HomeLayoutDialog.kt`):**
  - Hero heights: `60vh` portrait, `40vw` landscape, `55vw` landscape `largeUi`. First-shelf overlap: `7.5vh` portrait, `7.5vw` landscape.
  - Hero visual blending: `#000000` bg (`bg0`), fade-in on load, portrait `30s` `object-position` horizontal pan (`30% -> 90%`), no static `1.035f` zoom. When wallpaper active + Mask Effect on, apply `BlendMode.DstIn` left (`85deg`, `0->50%`) and bottom (`90%->100%`) alpha masks; otherwise `40vw` left and `20vw` bottom black gradients.
  - Hero trailer: enable in portrait when focused too (`!portrait` restriction removed), cross-fade image->iframe only on `yt-play`, auto-hide on `yt-pause`/`yt-end` via `PlatformTrailer.android.kt` JS bridge.
  - Hero text box: `5vw` external left offset + `55vw` width (`80vw` portrait), centered vertically inside `top: 10u; bottom: 12u`. Typography uses root `LocalAnimeTvTokens` (`--fs-30vw` title, `--fs-15vw` synopsis, 4 lines, text shadows).
  - Hero metadata & CTA: Watch Now pill with `#006600` pulsing border animation (`home_play_ani`), `--fs-7vw` continuous-clip 5-star gauge, `#FF3333` `R+` badge, `border-left: .1u solid rgba(255,255,255,.3)` vertical dividers, `#66BBDD` duration, wrapping `FlowRow` in portrait.
  - Hero pager: cyclic left/right wrapping, cross-fade wrap-around, `.7u` / `2.5u` animated pill dots at `right: 5vw; bottom: 10vw` (landscape) and `top: 50vh` (portrait, above `7.5vh` overlap).
  - Shelves: edge-bleeding `LazyRow` (`contentPadding = 5u` landscape / `2vh` portrait) with `.pb_menu` horizontal edge alpha fade mask, `#aaa -> #fff` section header transition, `HomeLayout` defaults matching `m.js` (source-specific `"Recently Updated"`/`"Recently Released"`, `"Trending Today"`, `"Popular This Week"`/`"Most Popular"` normalizing `"top"`/`"popular"` IDs, plus AniList shelves including functional `altab` tabbed shelf `"Top Anime"` / `"All Time Popular"` / `"Top <year>"` / `"Upcoming"`, and omitting `continue_watching` on Home; persist all `HOME_SECTION_IDS` in `AndroidSettingsRepository.kt`).
  - Add `#home_about` footer at bottom of scroll and pull-to-refresh `#home_pull_refresh` in portrait/touch.
  - Home Layout Customizer: `ListOrderDialog` reorderable list titled `"Customize Home - Source <name>"` with auto-save on close and `"Use left/right to move order"` footer.
- **Anime Cards & Badges (`AnimeCard.kt`, `AnimePresentation.kt`):**
  - Exact dimensions: portrait `16vh × (fs-20vw + 23.6vh)`, poster `15.2vh × 21vh`, `0.dp` cardGap; landscape default `12vw × (fs-20vw + 18.6vw)`, poster `11.2vw × 16vw`, `0.dp` extra `cardGap`; landscape `largeUi` `16vw × (fs-20vw + 23.6vw)`, poster `15.2vw × 21vh`.
  - Unclipped white focus backplate (`12.2vw × 17vw` or `12vw × 16.8vw` when scale ON) with `0 .1u 1u .1u accent1` shadow on poster only; `1.03f` scale in `largeUi`; `0.3s` animated scale.
  - Uncapped `--fs-8vw` badges inset `0.7u` top / `0.6u` left & right inside poster; fix bottom-start vertical offset (remove double `+ posterInsetY`).
  - Render missing `year`, `rating`, `isUpcoming` (`#0066CC` + `event_upcoming`), and `✨` (`info_ep_havenext`) badges; format `"24 min"` and `"R+"` (top-end on Schedule/MyList AniList); theme `accent1` rounded (`0.2u`) progress bar over `Black 50%`.
- **MyList (`AccountListsScreen.kt`, `MyListLayoutDialog.kt`):**
  - Unify `OriginalStyleMyListScreen` and `AccountListsScreen` into one `#home_pagelist` stacked-shelves view using `FocusCard`, transparent over wallpaper, `8vw` landscape / `calc(2vh + sysStatHeight)` portrait top pad + `--fs-40vw` `"MyList"` portrait title + bottom nav clearance.
  - Support all 8 layout sections (`maltab`, `mal`, `malplan`, `anilisttab`, `anilist`, `anilistplan`, `fav`, `history`) in `AndroidSettingsRepository.kt` & `MyListLayoutDialog.kt` (`ListOrderDialog` with `disabling` mutual exclusion between tabbed and flat shelves).
  - Tabbed shelves (`maltab`, `anilisttab`) render inline status tabs (`Watching`/`Planning`/`Completed`/`Dropped`/`On Hold` and `Current`/`Planning`/`Completed`/`Dropped`/`Paused`/`Repeating`), auto-paginate on horizontal scroll (`limit = 12`), compute real `watched / total` progress bars, and show `✨` next-airing badge.
  - Clear Watch History always triggers the 2-step `ConfirmDialog` (`WARNING!!!` -> `You have X anime...`).
- **Schedule (`ScheduleScreen.kt`, `AniListScheduleApi.kt`):**
  - `8vw` landscape / `calc(2vh + sysStatHeight)` portrait top pad + `--fs-40vw` `"Airing Schedule"` portrait title.
  - Plain `"Sunday"`..`"Saturday"` + `" (Today)"` headers (`#aaa -> #fff` on focus), full-shelf-height centered `"No schedule"` empty row, segmented `[R+] [FORMAT] [movie + ep] [bookmark + total]` top-end badges + bottom-start airing time pill, and exact 7-day (`page < 10`) fetch.
- **Search (`SearchScreen.kt`, `SearchRemoteKeyboard.kt`, `SearchFilterCatalog.kt`):**
  - Transparent landscape header over `#000 -> primaryBg`; portrait `color-mix(primary1 40%, rgba(0,0,0,.8))` with `sysStatHeight` top pad, `arrow_back` only (hide `#search_kbd` in portrait; `#search_kbd` focuses system IME in landscape).
  - Always show `"Search from AniList"` label (no border/fill when unfocused, `#fff` pill on TV focus); `50vw` (`53vw` if no voice) / `70vw` borderless `#search_kw` input with lowercase + `[^\w]+ -> space` normalization and `_` remote cursor; inline expanding voice overlay.
  - Fix `backspace` key width (`4vw`, `0.04f`); `#111111` resting keys with `primary2`/`#060`/`#600` focus highlights, icons (`cancel`, `space_bar`, `search`), `brand_awareness`/`volume_off` persisted `mute`; TV history pills (`4vw` radius, `history -> clear` red icon on Left D-pad + `"Delete History"` `ConfirmDialog`) & touch strip (`Reset` + history pills).
  - Remove `Season`/`Sort`/`Year` extra filters; `Type & Genres` only (`TV SHORT`, no `Hentai` in AniList mode; `.2u` radius `#59A0A5A7` chips with `<c>check</c>` prefix and `primary1` focus); hide `Type & Genres` on result focus when `largeUi`; auto-paginate results seamlessly without `"Load more"` button; center `"Searching..."`/`"No Result"` inside empty row height.

### 4.4 Details (`#malview` & `#anilist_detail`), Player OSD & Settings
- **Details (`DetailsScreen.kt`):**
  - Floating `#malview` surface (`rgba(32,32,32,.6)` with blur when blur enabled, `#222222` when disabled, no border); `64vw × 22vw` landscape (`72vw` & `19vw` actions when `fontSize > 0` `.view-bigfont`); `92vw × 60vh` portrait (`17vh × 22vh` poster, `32vh` action area `top: 0..bottom: 28vh`, `#malview_info` `top: 22vh..bottom: 0`, single floating `#malview_info_detail_button` at `top: 23vh; right: 1vh`).
  - Add `#malview_loading` spinner while `loadDetails` resolves.
  - Landscape `#malview_info` vertically centered; normal-weight `90%` title; `Duration:`/`Type:` floated right opposite `Status:` left; `Genre:` wrapping up to 3 lines; compact top-right `info` icon (`top: .8vw; right: 17.8vw`) expanding on hover/focus to `"More Info"`.
  - Poster `--primary-2` / `#AA0000` rating badge; uniform `#2A2A2A` action buttons with `primary1` focus; keep unavailable `Resume`/`Play Next` visible as `.disable` (`opacity: 0.6f`, `#666`, `#252525`); show `"Play Episode"` label when opened from Home/Search; `#060`/`#800` active states only when saved in Watchlist/History; direct episode click-to-play + `PageUp`/`PageDown` ±10 stepping (removing `EpisodeGridDialog`).
- **More Info (`AniListInfoDialog.kt`, `PlatformTrailer.android.kt`):**
  - Landscape `64vw × 90vh` single unified vertical scroll container (`rgba(8,8,8,.4)`, `1.5vw` padding); portrait `100vw × 80vh` (`bottom: 10vh`) with `16vh × 22vh` `.2vw` white-bordered poster, hiding `alsd_button_full` & `Year`, overlaying only `<h4>` title beside poster over banner/trailer, and 2-column (`50%`) left facts grid.
  - Banner fallback to `trailer.thumbnail`, `accent1` title, pipe-separated `.alsd_infobox` (stars, `#f33` `18+`, format/year, `#1a1` status, `#6bd` duration, episodes — no `"Synopsis"` heading), 4-column (`25%`) metrics grid (`Score`, `Popularity`, `Favorites`, `Trend`), separate `Other Name` + 50/50 `Synonyms`/`Studios`, compact bullet `Reviews` (`#080` `/100`, no upvotes), and `alsd_tools` bottom bar (`Play`/`Pause`/`Close Trailer` synced via YouTube `postMessage` + `loop=1`, plus right-floated `AniList` & `MAL` buttons opening `ListOrderDialog` status picker + `ConfirmDialog` removal or toast if logged out).
- **Player & OSD (`PlatformPlayer.android.kt`, `PlaybackScreens.kt`, `AndroidHtml5PlayerDocument.kt`):**
  - Replace broken `Purple` references with `AnimeTvThemeColors`; load banner/poster backdrop in `#pb` until first video frame; match `.z-loader` (`primary2` color, stepped keyframes, bold `"LOADING DATA"` with shadow, `8vh` portrait size).
  - Implement 2-zone OSD (`menusel == 2` shows `#pb_tracks`, `#pb_touch_actions` on touch, `#pb_action_streamtype`, and peeks `#pb_episodes` below; Up reveals `#pb_meta` & hides `#pb_touch_actions`); add full `#pb_meta` horizontal scrim, un-overlapped `#pb_touch_close`, top-right `#pb_action_streamtype` (`primary2` + `g_translate` + `.softsub_lang`), `40vw` sliding title without artist line, `50vw` synopsis with `QUALITY`/`RATING`/`SEASONS`/`EPISODES`/`TYPE` dark pills, clickable `Watchlist`/`Tracking`/`Type`/all-genres chips, and `#59A0A5A7` borderless `#pb_settings` chips (`Settings`, `Speed`, `Mirror Server`, dynamic-icon `Quality` `"Auto <res>"`, `Stream Type`, `Subtitles`, `Audio Language`).
  - Seek bar: show status strings in `#pb_track_pos` during load/error, `linear-gradient(180deg, primary3, primary1)` fill, `accent1 0.5f` buffer bar (wired in HTML5 mode too), z-order skip segments on top (`primaryBg 0.6f`), `500ms` debounced seek, accumulated `seek_target_pos` on horizontal swipe, blank edge double-tap circle, `light_mode` white brightness HUD, gestures enabled regardless of OSD visibility, and structured D-pad matrix (`Space`/`Play`, `PGUP`/`PGDN`, `NEXT`/`PREV`, `Enter` skip).
  - Skip Intro/Outro pill (`#pb_events`): `right: 5vw; bottom: 5vw`, `rgba(255,255,255,.4)`, `.05vw` white outline, `4s` fade to `0` (`0.3` touch), hidden when OSD open, `1.8s` countdown + `2s` purple `.autoskip` ripple for both **Intro and Outro**.
  - In-player browse: remove `PlaybackBrowseDialog` bottom sheet; render `#pb_episodes` (with `ep.title` + below-tile `.playing:after` + `PROG+ / PROG-` paging hint), `#pb_seasons` (optional `seasons: List<Anime>` slot, hidden when empty as in `m.js:14280-14313`), `#pb_related`, and `#pb_recs` inline as horizontal poster shelves inside `#pb_lists` (clicking opens `#malview` preview).
  - Player menus & Subtitles: use `ListOrderDialog` for Speed, Server, Quality (`Auto/High/Medium/Low`), Scaling, Stream Type, Audio Language (live track availability), and CC tracks; allow opening `Settings` mid-playback; render `#vtt_subtitle` unified overlay/preview pinned fullscreen at `bottom: 3vw` with real bundled TTFs, true stroke/shadow, and theme `primary1`/`accent1` colors (plus HTML5 SRT/ASS support).
  - Error state: keep `#pb` backdrop + OSD (`PLAYBACK ERROR`, warning icons, `#pb_settings`, `#pb_episodes`) accessible so user can switch Server/Stream/Episode without backing out.
- **Settings & Modals (`SettingsScreen.kt`, `SettingsComponents.kt`, `AccountIntegrationSettings.kt`, `SourceMirrorBenchmarkDialog.kt`, `WallpaperPickerDialog.kt`, `LanguagePickerDialogs.kt`):**
  - Remove dead `isMobile` category path; always render linear `#settings` (`34vw` landscape, `100vw` portrait with `sysStatHeight`/`sysNavHeight` padding, `translateX(50%)->0` + opacity animation).
  - Hide `arrow_back` in landscape; fix `IconGlyphs.Back` in portrait; `#AAAAAA` mixed-case group headers (`fs-12vw`); flat `#222222` (`#444444` focused) rows with stacked `accent1` value below label; `4u × 2u` `--primary-3` switches (dimming disabled `Use DoH`); remove KMP-only `Source Domain`, `Mobile Navigation`, `Ambient Glow` rows; restore admin-gated rows + `Check for Nightly Build`/`Check for Update` (with safe KMP status toast/message) and `needreload` on settings close.
  - All 12 value selectors open `ListOrderDialog` radio pickers (plus 4-block Interface Color picker); `Performance UI` uses `ListOrderDialog` multi-toggle with full names + icons.
  - Account integration: `MAL` first then `AniList` (`"MAL Login/Logout"`, `"AniList Login/Logout"`), `MAL Login Type` `ListOrderDialog` (`QRCode Web Authorization` vs `Username + Password` dialog wired to `AccountRepository.loginMalWithPassword` against `api.myanimelist.net/v2/auth/token`), white `#aboutpopup` QR/Wait modals (`Donate Project`, `Join Discord Server`, `QR login`), `ConfirmDialog` logout.
  - Language pickers (`Subtitle Language` without search bar; `Audio Language`), `Source Mirror Benchmark` (`AboutPopupDialog` `"Benchmarking..."` -> `ListOrderDialog` domains + `ConfirmDialog` `"Domain Warning!!"`), `Wallpaper Picker` (`ImgPickerDialog` 4-col `1:1` squares with `"No Wallpaper"` first tile, optional `"AniList Banner"`, focus-only titles), and `Diagnostic Logs` (`#16161a` card, `#1a1a22` header/footer bars, `#0d0e12` auto-scrolled `<pre>`, pill buttons + `<c>close</c>`).

---

## 5. Module Decomposition & Execution Phases (Collision-Free Ownership)

To keep implementation clean and parallelizable (whether via `/swarm` or parallel subagents), changes decompose into 6 non-overlapping phases/slices:
1. **Foundation (Shared Tokens, Insets, Icons, Modal Primitives):** `MainActivity.kt`, `paregi_icons.ttf`, `ThemeTokens.kt`, `ViewportTokens.kt` (new), `AnimeTvModals.kt` (new), `SharedComponents.kt`, `SettingsModels.kt`, `AndroidSettingsRepository.kt`, `SourceCatalog.kt`.
2. **Shell, Navigation, Wallpaper & Profile/Login:** `AnimeTvApp.kt`, `AppNavigation.kt`, `NavFloatingMenu.kt`, `ProfileChooserScreen.kt`, `ProfileManagementDialogs.kt`, `AppConfirmationDialogs.kt`.
3. **Home, Cards, MyList & Schedule:** `AnimeCard.kt`, `AnimePresentation.kt`, `HomeScreen.kt`, `HomeLayoutDialog.kt`, `AccountListsScreen.kt`, `MyListLayoutDialog.kt`, `ScheduleScreen.kt`, `AniListScheduleApi.kt`, `AndroidAnimeRepository.kt`.
4. **Search & Details:** `SearchScreen.kt`, `SearchRemoteKeyboard.kt`, `SearchFilterCatalog.kt`, `DetailsScreen.kt`, `AniListInfoDialog.kt`, `PlatformTrailer.android.kt`, `PlatformVoiceSearch.android.kt`.
5. **Player & OSD:** `PlatformPlayer.android.kt`, `PlaybackScreens.kt`, `AndroidHtml5PlayerDocument.kt`, `SubtitleStyleCatalog.kt` (retire `PlaybackBrowseDialog.kt` usage).
6. **Settings & Modals:** `SettingsScreen.kt`, `SettingsComponents.kt`, `AccountIntegrationSettings.kt`, `LanguagePickerDialogs.kt`, `SourceMirrorBenchmarkDialog.kt`, `WallpaperPickerDialog.kt`.
