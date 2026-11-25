# AnimeTV KMP migration

This is the isolated Kotlin Multiplatform rewrite. The existing AnimeTV project is kept as the reference and is not modified.

## Initial targets

- Android phones
- Android TV / Google TV / Fire TV

The shared Compose UI is in `composeApp/src/commonMain`; Android entry points and platform APIs are in `androidApp` and the Android source set. Desktop is intentionally not configured in this phase.

## Building in Termux

For routine Kotlin syntax and type checks, compile the Android app without packaging an APK:

```sh
ANDROID_HOME="$HOME/.local/android-sdk" ANDROID_SDK_ROOT="$HOME/.local/android-sdk" ./gradlew --console=plain --no-parallel :androidApp:compileDebugKotlin -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Build a debug APK only when you need a new installable package. The APK was assembled on Android/ARM64 using Termux's Java 21, the SDK under `$HOME/.local/android-sdk`, and Termux's native `aapt2`:

```sh
ANDROID_HOME="$HOME/.local/android-sdk" ANDROID_SDK_ROOT="$HOME/.local/android-sdk" ./gradlew --console=plain --no-parallel :androidApp:assembleDebug -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

The APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Migration status

The current migration slice includes a shared Compose phone/TV shell, the two enabled providers Gotaku and ReAnime, anime details and episodes, AniList trending slides with configurable YouTube trailer playback, recommendations and airing schedule, AniList/MAL QR account connection through the existing OAuth relay, paged AniList/MAL account lists in MyList, title-specific list status and add/remove actions, configurable tracking prompts and auto-add modes, progress updates for already tracked AniList/MAL entries, direct resume from Continue Watching, watchlist and playback history storage, settings persistence, Media3 playback with Compose controls, all 136 original subtitle language choices with synopsis and soft-subtitle translation, all 11 original audio language choices, the original seven-part subtitle styling controls, configurable buffer levels, quality caps, and localhost HLS adapters for both providers. External AniList/MAL titles resolve through provider IDs first, then through normalized AniList title aliases and source title aliases, with the first source search result retained for released titles when no exact alias matches. Tracking lookup mirrors the original: use an AniList ID when available; otherwise search by title and choose an exact English/romaji match among FINISHED, RELEASING, or HIATUS entries using the original ASCII and slug normalization. It does not select a non-matching first result. Android TV also publishes Recent, Trending, and Popular launcher channels, refreshes them hourly, and writes the current episode position to Watch Next for resume. MyList as Home, large interface scaling, the local clock, Japanese Titles, Include Chinese Anime filtering on the airing schedule, Transition Animation on tab changes, Direct Page Switching in the header, Navigation Sounds for click actions and D-pad focus movement, Performance UI options for card scale, shadow, masking, text shadow, and the dimmed anime backdrop blur on Details, the bundled 41-image curated wallpaper gallery plus Home feed anime banners, and the Double Back / Close Confirmation / Close Directly playback modes are wired into Compose. Existing visual assets and the Firac typeface are included in the KMP project. Android targets API 23 and higher with the current Compose Foundation version. Settings use the old preference keys in the new app's own data space.

The player also offers the original 0.25×–2.5× speed choices, the 11-option Audio Language chooser, and source-specific Hardsub/Sub and Dub switching. Manual stream choices carry across episodes, and switching streams resumes from the current position.

Title details include the original Play from Beginning, Resume, Play Next, and previous/next episode selection actions, plus an on-demand AniList info panel with metadata, reviews, and trailer playback.

Catalog, account, and localhost HLS proxy requests now use the selected HTTP client: OkHttp with optional DNS-over-HTTPS, generic `HttpURLConnection`, or Cronet with generic fallback if Cronet cannot initialize. Cache Size configures the OkHttp/Cronet disk caches and Media3 playback-cache LRU limit; Progressive Cache toggles Media3 playback caching. The Cache Size chooser also includes the original confirmed Clear Cache action for network and playback data.

Networks settings also include Android app diagnostic logging, a viewer for the latest 1,500 lines and log file path, clipboard copy, and clear controls.

On Android touch screens, horizontal player swipes seek and vertical swipes adjust brightness or volume. The Disable Touch Gestures option turns off these handlers; D-pad input remains active.

The UI settings include the original app Exit Mode choices for direct exit, confirmation, returning to the profile chooser, and opening the sidebar before exit. Returning to login reopens a lone profile without a PIN and prompts for its PIN when required.

The profile chooser also supports the original static, user theme, and user theme plus wallpaper styles.

Compose uses the original eight color palettes for app screens and the profile chooser.

Search now stores recent queries and offers matching suggestions, removal of individual queries, and a confirmation before clearing all history. The original optional AniList search mode is also available; AniList results still resolve to Gotaku or ReAnime for playable details and episodes.

MyList includes local Watch History with resume from the latest saved episode position, the original two-step clear confirmation, and profile-specific shelf visibility and ordering for Watchlist, History, AniList, and MAL.

Local profiles support up to five users, PIN-protected selection, default-user selection (including automatic startup into a saved non-default profile without a PIN), last-used profile focus when returning to the chooser, bundled, connected AniList/MAL, and picked-image avatar choices, display-name and parental-rating editing, and profile-specific preferences, accounts, watchlists, search history, and playback history. Deleting a profile clears its local data. Parental ratings filter adult titles from Home, Search, MyList, schedule, and recommendations and block opening them. Android can also use a picked image as the app wallpaper.

Feature and visual parity is still in progress. Remaining migration areas include unmatched external titles whose aliases are absent from the providers' search results, launcher-specific TV behavior, and fine visual matching across every screen.

QR authorization intentionally depends on the original relay host because the existing service owns that login handoff. The app's package and visible branding remain Paregi; changing the relay hostname would require an equivalent service to be deployed first.

The project uses `paregi` for its package and namespace. The Android application ID is `com.paregi.androidtv.animetvjmto`.

The nine named subtitle font families from the original style menu are bundled under `androidApp/src/main/assets/subtitle-fonts`, with their Open Font License notices alongside them. The subtitle choices therefore render without fetching fonts at runtime.

The current app's HTML/CSS/JavaScript and Java sources in the original project are the parity reference. They are not copied into this project as runtime code.

