# KMP project structure

The shared module contains UI and app behavior that can be shared across targets. Android-specific networking, playback integration, and platform services stay in the Android app module.

## `composeApp/src/commonMain`

- `app/` — screen routing, app-level confirmation dialogs, and app-state save/restore helpers.
- `core/navigation/` — app header, section navigation, and sidebar.
- `core/theme/` — theme palettes, shared colors, composition locals, and icon glyphs.
- `core/ui/` — reusable Compose components and anime presentation helpers.
- `domain/` — anime/catalog models, account/profile/settings models, focused repository contracts, source catalog, and visibility rules. Each repository contract is in a file named for its responsibility.
- `features/` — Home, Search, MyList, Schedule, Details, Settings, Profile, and Player screens, grouped by feature. The Details AniList panel, Search remote keyboard, MyList layout editor, player browse dialog, Profile management dialogs, and account integration rows/dialogs have separate files alongside their screen flows.
- `features/search/SearchFilterCatalog.kt` — provider-specific filter choices, separate from Search rendering.
- `features/settings/SettingsComponents.kt` — reusable setting rows, separate from the settings screen flow.
- `features/settings/LanguagePickerDialogs.kt` and `WallpaperPickerDialog.kt` — dedicated language selectors and wallpaper gallery, separate from settings state and navigation.
- `features/profile/WallpaperCatalog.kt` — wallpaper metadata used by profile appearance and settings.
- `PlatformDevice.kt` — platform device classification used to select the TV-specific layout independently of window width.

## `composeApp/src/androidMain`

- `PlatformDevice.android.kt` — detects Android TV using television UI mode and the Leanback system feature.
- `PlatformPlayer.android.kt` — native Media3 player and shared control integration.
- `AndroidHtml5PlayerDocument.kt` — optional Video.js HTML surface and JavaScript playback bridge.

## `androidApp/src/main`

- `AndroidAnimeRepository.kt` — coordinates the selected catalog, source title lookup, AniList metadata, and playback entry points.
- `AndroidAccountRepository.kt` — coordinates account, tracking, and list behavior; `AndroidAccountHttpClient.kt` owns account-service HTTP framing and response handling.
- `source/AndroidCatalogSource.kt` — source selection and immutable source snapshots.
- `source/AndroidCatalogApi.kt` — routes catalog calls to a provider-specific API.
- `source/gotaku/` and `source/reanime/` — each provider's catalog endpoints, filters, pagination, response mapping, playback resolution, and stream handling. ReAnime's HLS manifest/segment adapter is in `source/reanime/ReAnimeHlsProxy.kt`.
- `source/AndroidPlaybackResolver.kt` — selects the provider playback resolver and applies shared subtitle translation.
- `source/anilist/` — AniList catalog, title metadata, combined related-title/recommendation lookup, schedule, GraphQL transport, and API facade.
- `source/lookup/` — maps AniList/MAL titles to the selected playable provider.
- `source/shared/` — Android source HTTP transport, request boundaries, response conversions, and title normalization shared by the providers.
- Other `Android*Repository.kt` files — Android-backed account, settings, profile, library, translation, search-history, TV-integration, and diagnostics services.

The original `AnimeTV` project remains separate from this tree.
