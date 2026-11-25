package com.paregi.animetv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import com.paregi.animetv.domain.SubtitleStyleCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.features.player.PlaybackOsdContent
import com.paregi.animetv.features.player.PlayerOsdHudState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private object PlaybackCache {
    private var cache: SimpleCache? = null
    private var maxBytes: Long = -1
    private var appContext: Context? = null

    @Synchronized
    fun get(context: Context, sizeMb: Int): Cache {
        val desiredMaxBytes = sizeMb.coerceIn(5, 150).toLong() * 1024 * 1024
        cache?.takeIf { maxBytes == desiredMaxBytes }?.let { return it }

        cache?.release()
        val appContext = context.applicationContext
        this.appContext = appContext
        val directory = File(appContext.cacheDir, "playback-cache")
        directory.mkdirs()
        return SimpleCache(
            directory,
            LeastRecentlyUsedCacheEvictor(desiredMaxBytes),
            StandaloneDatabaseProvider(appContext)
        ).also {
            cache = it
            maxBytes = desiredMaxBytes
        }
    }

    @Synchronized
    fun clear() {
        runCatching { cache?.release() }
        cache = null
        maxBytes = -1
        appContext?.cacheDir?.resolve("playback-cache")?.deleteRecursively()
    }
}

actual fun clearPlatformPlaybackCache() {
    PlaybackCache.clear()
}

@Composable
actual fun platformSubtitleFontFamily(fontIndex: Int, weightIndex: Int): FontFamily {
    val context = LocalContext.current
    return remember(context, fontIndex) {
        val assets = context.assets
        when (fontIndex) {
            0 -> FontFamily.Serif
            1 -> runCatching {
                FontFamily(
                    Font("subtitle-fonts/fira_sans_300.ttf", assets, FontWeight.Light),
                    Font("subtitle-fonts/fira_sans.ttf", assets, FontWeight.Normal),
                    Font("subtitle-fonts/fira_sans_700.ttf", assets, FontWeight.Bold),
                    Font("subtitle-fonts/fira_sans_900.ttf", assets, FontWeight.Black)
                )
            }.getOrDefault(FontFamily.SansSerif)
            9 -> runCatching {
                FontFamily(
                    Font("subtitle-fonts/comic_neue_300.ttf", assets, FontWeight.Light),
                    Font("subtitle-fonts/comic_neue.ttf", assets, FontWeight.Normal),
                    Font("subtitle-fonts/comic_neue_700.ttf", assets, FontWeight.Bold),
                    Font("subtitle-fonts/comic_neue_700.ttf", assets, FontWeight.Black)
                )
            }.getOrDefault(FontFamily.Cursive)
            else -> {
                val path = SubtitleTypefaceCache.assetPath(fontIndex)
                if (path != null) {
                    runCatching { FontFamily(Font(path, assets, FontWeight.Normal)) }
                        .getOrDefault(FontFamily.SansSerif)
                } else FontFamily.SansSerif
            }
        }
    }
}

@Composable
actual fun PlatformSubtitlePreviewOverlay(
    recreateKey: Any?,
    content: @Composable () -> Unit
) {
    key(recreateKey) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = LocalView.current
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                window.setDimAmount(0f)
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
actual fun PlatformPlayer(
    url: String,
    metadataTitle: String,
    metadataArtist: String,
    metadataDescription: String,
    metadataGenres: List<String>,
    artworkUrl: String?,
    hasNext: Boolean,
    hasPrevious: Boolean,
    episodePickerLabel: String?,
    onEpisodePickerRequest: (() -> Unit)?,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit,
    headers: Map<String, String>,
    subtitles: List<SubtitleTrack>,
    playbackSpeed: Float,
    onPlaybackSpeedDialogRequest: () -> Unit,
    currentStreamType: PlaybackStreamType,
    hasSubStream: Boolean,
    hasDubStream: Boolean,
    subStreamLabel: String,
    onStreamTypeDialogRequest: () -> Unit,
    streamServerLabel: String?,
    onStreamServerDialogRequest: () -> Unit,
    subtitleLanguage: String,
    subtitleVariant: String,
    onSubtitleVariantSelected: (String) -> Unit,
    audioLanguage: String,
    audioLanguageLabel: String,
    onAudioLanguageDialogRequest: () -> Unit,
    subtitleStyle: Int,
    onSubtitleStyleDialogRequest: () -> Unit,
    playbackQuality: Int,
    onPlaybackQualityChange: (Int) -> Unit,
    onPlaybackQualityDialogRequest: (() -> Unit)?,
    skipRanges: List<SkipRange>,
    autoSkipIntro: Boolean,
    skipFiller: Boolean,
    startPositionMs: Long,
    seekSeconds: Int,
    videoScale: Int,
    onVideoScaleDialogRequest: (() -> Unit)?,
    videoBuffer: Int,
    progressiveCache: Boolean,
    cacheSizeMb: Int,
    disableTouchGestures: Boolean,
    html5Player: Boolean,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onBack: () -> Unit,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    onEpisodeSelected: ((Episode) -> Unit)?,
    seasons: List<Anime>,
    related: List<Anime>,
    recommendations: List<Anime>,
    onAnimeSelected: ((Anime) -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    inWatchlist: Boolean,
    onToggleWatchlist: (() -> Unit)?,
    isTracked: Boolean,
    onTrackingClick: (() -> Unit)?,
    onSearchGenre: ((String) -> Unit)?,
    onSubtitlesDialogRequest: ((Int) -> Unit)?,
    selectedSubtitleIndexOverride: Int?,
    onAvailableAudioLanguagesChanged: ((Set<String>) -> Unit)?,
    onDetectedVideoHeightsChanged: ((Int, List<Int>) -> Unit)?,
    showBackHint: Boolean
) {
    val context = LocalContext.current
    val colors = LocalAnimeTvColors.current
    val coroutineScope = rememberCoroutineScope()

    var positionMs by remember(url) { mutableStateOf(startPositionMs) }
    var durationMs by remember(url) { mutableStateOf(0L) }
    var bufferedPositionMs by remember(url) { mutableStateOf(0L) }
    var playing by remember(url) { mutableStateOf(true) }
    var isBuffering by remember(url) { mutableStateOf(true) }
    var ended by remember(url) { mutableStateOf(false) }
    var firstFrameRendered by remember(url) { mutableStateOf(false) }
    var playbackErrorMessage by remember(url) { mutableStateOf<String?>(null) }
    var statusText by remember(url) { mutableStateOf<String?>("LOADING SERVER") }
    var detectedVideoHeight by remember(url) { mutableIntStateOf(0) }
    var availableVideoHeights by remember(url) { mutableStateOf<List<Int>>(emptyList()) }
    var currentTracks by remember(url) { mutableStateOf(Tracks.EMPTY) }

    var osdState by remember(url) { mutableStateOf<PlayerOsdHudState?>(null) }
    var seekIndicatorSide by remember(url) { mutableStateOf<Int?>(null) }
    var seekDebounceJob by remember(url) { mutableStateOf<Job?>(null) }

    var html5PositionMs by remember(url, html5Player) { mutableStateOf(startPositionMs) }
    var html5DurationMs by remember(url, html5Player) { mutableStateOf(0L) }
    var html5BufferedMs by remember(url, html5Player) { mutableStateOf(0L) }
    var html5Playing by remember(url, html5Player) { mutableStateOf(true) }
    var html5Ended by remember(url, html5Player) { mutableStateOf(false) }
    var html5Ready by remember(url, html5Player) { mutableStateOf(false) }

    val initialStartPositionMs = remember(url, headers, subtitles, html5Player) { startPositionMs }
    val primary1Hex = remember(colors.primary1) { colors.primary1.toHexString() }
    val accent1Hex = remember(colors.accent1) { colors.accent1.toHexString() }

    val html5Bridge = remember(url, html5Player) {
        Html5PlaybackBridge(
            allowedSubtitleUrls = subtitles.map { it.url }.toSet(),
            headers = headers,
            onState = { position, duration, buffered, isPlayingState, isEndedState ->
                html5PositionMs = position
                html5DurationMs = duration
                html5BufferedMs = buffered
                html5Playing = isPlayingState
                html5Ended = isEndedState
                if (duration > 0L) {
                    statusText = null
                    isBuffering = false
                    if (isPlayingState || position > 0L) firstFrameRendered = true
                }
            },
            onError = { errorMsg ->
                playbackErrorMessage = errorMsg.ifBlank { "Playback error" }
                statusText = "PLAYBACK ERROR"
                osdState = PlayerOsdHudState(
                    icon = IconGlyphs.Warning,
                    text = playbackErrorMessage ?: "Playback error",
                    progressPercent = 0f
                )
            },
            onVideoInfo = { height, audioLangs, heights ->
                if (height > 0) detectedVideoHeight = height
                if (heights.isNotEmpty()) availableVideoHeights = heights
                onDetectedVideoHeightsChanged?.invoke(height, heights)
                if (audioLangs.isNotEmpty()) onAvailableAudioLanguagesChanged?.invoke(audioLangs)
            }
        )
    }

    val html5View = remember(url, headers, subtitles, html5Player) {
        if (!html5Player) null else WebView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.apply {
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
            addJavascriptInterface(html5Bridge, "AnimeTV")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return uri.scheme != "http" || uri.host != "127.0.0.1"
                }

                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    if (loadedUrl.startsWith("file:///android_asset/")) {
                        html5Ready = true
                        statusText = "PREPARE VIDEO"
                    }
                }
            }
            setBackgroundColor(AndroidColor.BLACK)
            loadDataWithBaseURL(
                "file:///android_asset/",
                html5PlayerDocument(
                    url = url,
                    headers = headers,
                    subtitles = subtitles,
                    startPositionMs = initialStartPositionMs,
                    primary1Hex = primary1Hex,
                    accent1Hex = accent1Hex
                ),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    DisposableEffect(html5View) {
        onDispose {
            html5View?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("AnimeTV")
                destroy()
            }
        }
    }

    val player = remember(url, headers, subtitles, videoBuffer, progressiveCache, cacheSizeMb, html5Player) {
        val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpFactory)
        val dataSourceFactory: DataSource.Factory = if (progressiveCache) {
            CacheDataSource.Factory()
                .setCache(PlaybackCache.get(context, cacheSizeMb))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory { dataSpec ->
                    dataSpec.key ?: dataSpec.uri.toString().replaceFirst(
                        Regex("^http://127\\.0\\.0\\.1:\\d+"),
                        "http://127.0.0.1"
                    )
                }
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else upstreamFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        val bufferDurations = when (videoBuffer.coerceIn(0, 4)) {
            0 -> intArrayOf(30_000, 30_000, 1_500, 3_000)
            1 -> intArrayOf(60_000, 60_000, 2_000, 4_000)
            2 -> intArrayOf(120_000, 120_000, 2_500, 5_000)
            3 -> intArrayOf(300_000, 300_000, 2_500, 5_000)
            else -> intArrayOf(600_000, 600_000, 2_500, 5_000)
        }
        val backBufferDuration = when (videoBuffer.coerceIn(0, 4)) {
            0 -> 10_000
            1 -> 20_000
            2 -> 30_000
            3 -> 60_000
            else -> 120_000
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(backBufferDuration, true)
            .setBufferDurationsMs(bufferDurations[0], bufferDurations[1], bufferDurations[2], bufferDurations[3])
            .build()
        val subtitleConfigs = subtitles.map { track ->
            val ext = track.url.substringBefore('?').substringAfterLast('.').lowercase()
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setMimeType(
                    when (ext) {
                        "srt" -> MimeTypes.APPLICATION_SUBRIP
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.TEXT_VTT
                    }
                )
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (track.defaultSelected) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItem = MediaItem.Builder().setUri(url).setSubtitleConfigurations(subtitleConfigs).build()
        var playerRetryCount = 0
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(mediaItem)
                if (startPositionMs > 0 && !html5Player) seekTo(startPositionMs)
                playWhenReady = !html5Player
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_BUFFERING && duration <= 0L) {
                            statusText = "PREPARE VIDEO"
                        } else if (playbackState == Player.STATE_READY) {
                            statusText = null
                            isBuffering = false
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        firstFrameRendered = true
                        statusText = null
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.height > 0) {
                            detectedVideoHeight = videoSize.height
                            onDetectedVideoHeightsChanged?.invoke(videoSize.height, availableVideoHeights)
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        currentTracks = tracks
                        val audioLangs = mutableSetOf<String>()
                        val videoHeights = mutableListOf<Int>()
                        for (group in tracks.groups) {
                            when (group.type) {
                                C.TRACK_TYPE_AUDIO -> {
                                    for (i in 0 until group.length) {
                                        val fmt = group.getTrackFormat(i)
                                        fmt.language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(audioLangs::add)
                                        fmt.label?.trim()?.lowercase()?.take(3)?.takeIf { it.isNotEmpty() }?.let(audioLangs::add)
                                    }
                                }
                                C.TRACK_TYPE_VIDEO -> {
                                    for (i in 0 until group.length) {
                                        val h = group.getTrackFormat(i).height
                                        if (h > 0) videoHeights += h
                                    }
                                }
                            }
                        }
                        val sortedHeights = videoHeights.distinct().sortedDescending()
                        availableVideoHeights = sortedHeights
                        onDetectedVideoHeightsChanged?.invoke(detectedVideoHeight, sortedHeights)
                        if (audioLangs.isNotEmpty()) {
                            onAvailableAudioLanguagesChanged?.invoke(audioLangs)
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (playerRetryCount < 2) {
                            playerRetryCount++
                            val resumePos = currentPosition.coerceAtLeast(0)
                            seekTo(resumePos)
                            prepare()
                            play()
                        } else {
                            playbackErrorMessage = "Playback error: ${error.errorCodeName}"
                            statusText = "PLAYBACK ERROR"
                            osdState = PlayerOsdHudState(
                                icon = IconGlyphs.Warning,
                                text = playbackErrorMessage ?: "Playback error",
                                progressPercent = 0f
                            )
                        }
                    }
                })
                if (!html5Player) prepare()
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    fun currentPosition(): Long = if (html5Player) html5PositionMs else player.currentPosition.coerceAtLeast(0)
    fun currentDuration(): Long = if (html5Player) html5DurationMs else player.duration.coerceAtLeast(0)

    fun seekToImmediate(targetMs: Long) {
        seekDebounceJob?.cancel()
        seekDebounceJob = null
        val duration = currentDuration()
        val target = targetMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        positionMs = target
        if (html5Player) {
            html5View?.evaluateJavascript("window.animeTvCommand('seek', ${target / 1000.0});", null)
        } else {
            player.seekTo(target)
        }
    }

    fun seekToDebounced(targetMs: Long) {
        val duration = currentDuration()
        val target = targetMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        positionMs = target
        seekDebounceJob?.cancel()
        seekDebounceJob = coroutineScope.launch {
            delay(500L)
            if (html5Player) {
                html5View?.evaluateJavascript("window.animeTvCommand('seek', ${target / 1000.0});", null)
            } else {
                player.seekTo(target)
            }
        }
    }

    fun setPlaying(shouldPlay: Boolean) {
        if (html5Player) {
            html5View?.evaluateJavascript("window.animeTvCommand('${if (shouldPlay) "play" else "pause"}', 0);", null)
        } else {
            player.playWhenReady = shouldPlay
        }
    }

    val mediaSession = remember(context) { MediaSession(context, context.packageName) }
    val latestSetPlaying = rememberUpdatedState(newValue = { shouldPlay: Boolean -> setPlaying(shouldPlay) })
    val latestSeekTo = rememberUpdatedState(newValue = { targetMs: Long -> seekToImmediate(targetMs) })
    val latestOnNext = rememberUpdatedState(onNextEpisode)
    val latestOnPrevious = rememberUpdatedState(onPreviousEpisode)
    DisposableEffect(mediaSession) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = latestSetPlaying.value(true)
            override fun onPause() = latestSetPlaying.value(false)
            override fun onStop() = latestSetPlaying.value(false)
            override fun onSeekTo(pos: Long) = latestSeekTo.value(pos)
            override fun onSkipToNext() = latestOnNext.value.invoke()
            override fun onSkipToPrevious() = latestOnPrevious.value.invoke()
        })
        mediaSession.isActive = true
        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    LaunchedEffect(mediaSession, metadataTitle, metadataArtist, artworkUrl, durationMs) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, metadataTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, metadataArtist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "$metadataTitle · $metadataArtist")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        artworkUrl?.let {
            metadata.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, it)
            metadata.putString(MediaMetadata.METADATA_KEY_ART_URI, it)
        }
        mediaSession.setMetadata(metadata.build())
    }

    LaunchedEffect(player, html5View, html5Ready, html5Player, playbackSpeed) {
        if (html5Player) {
            if (html5Ready) {
                html5View?.evaluateJavascript("window.animeTvCommand('speed', $playbackSpeed);", null)
            }
        } else {
            player.setPlaybackSpeed(playbackSpeed)
        }
    }

    LaunchedEffect(html5View, html5Ready, html5Player, primary1Hex, accent1Hex) {
        if (html5Player && html5Ready) {
            html5View?.evaluateJavascript(
                "window.animeTvCommand('themeColors', ${JSONObject.quote("$primary1Hex,$accent1Hex")});",
                null
            )
        }
    }

    fun updateMediaSessionState(state: Int, position: Long, speed: Float) {
        val actions = PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SEEK_TO or
            (if (hasNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or
            (if (hasPrevious) PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L)
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, speed, SystemClock.elapsedRealtime())
                .build()
        )
    }

    val effectiveSubtitleIndex = remember(
        subtitles,
        subtitleLanguage,
        subtitleVariant,
        selectedSubtitleIndexOverride
    ) {
        if (selectedSubtitleIndexOverride != null) {
            selectedSubtitleIndexOverride.takeIf { it in subtitles.indices } ?: -1
        } else {
            resolveAutoSubtitleIndex(subtitles, subtitleLanguage, subtitleVariant)
        }
    }

    LaunchedEffect(
        player, html5View, html5Ready, subtitles, effectiveSubtitleIndex, audioLanguage,
        playbackQuality, videoScale, subtitleStyle, currentTracks, availableVideoHeights
    ) {
        if (html5Player) {
            if (html5Ready) {
                html5View?.evaluateJavascript("window.animeTvCommand('subtitle', $effectiveSubtitleIndex);", null)
                html5View?.evaluateJavascript("window.animeTvCommand('audio', ${JSONObject.quote(audioLanguage)});", null)
                html5View?.evaluateJavascript("window.animeTvCommand('quality', $playbackQuality);", null)
                html5View?.evaluateJavascript("window.animeTvCommand('scale', $videoScale);", null)
                html5View?.evaluateJavascript("window.animeTvCommand('subtitleStyle', $subtitleStyle);", null)
            }
            return@LaunchedEffect
        }
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, effectiveSubtitleIndex < 0)

        if (effectiveSubtitleIndex in subtitles.indices) {
            val targetTrack = subtitles[effectiveSubtitleIndex]
            builder.setPreferredTextLanguage(targetTrack.language.takeIf { it.isNotBlank() })
            findMatchingTextGroup(currentTracks, targetTrack, effectiveSubtitleIndex)?.let { (group, trackIdx) ->
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
            }
        }

        builder.setPreferredAudioLanguage(audioLanguage.takeIf { it.isNotBlank() })

        val maxHeight = when (playbackQuality.coerceIn(0, 3)) {
            1 -> availableVideoHeights.getOrNull(0) ?: 1080
            2 -> availableVideoHeights.getOrNull(1) ?: 720
            3 -> availableVideoHeights.getOrNull(2) ?: 480
            else -> Int.MAX_VALUE
        }
        builder.setMaxVideoSize(Int.MAX_VALUE, maxHeight)
        player.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(player, skipRanges, autoSkipIntro, skipFiller) {
        var lastReport = 0L
        var endedReported = false
        while (true) {
            delay(500)
            val isEnded = if (html5Player) html5Ended else player.playbackState == Player.STATE_ENDED
            if (isEnded) {
                ended = true
                updateMediaSessionState(PlaybackState.STATE_STOPPED, currentPosition(), 0f)
                if (!endedReported) {
                    onProgress(currentPosition(), currentDuration())
                    onEnded()
                    endedReported = true
                }
                continue
            }
            endedReported = false
            ended = false
            val position = currentPosition()
            val positionSeconds = position / 1000.0
            if (skipFiller) {
                val fillerSkip = skipRanges.firstOrNull { range ->
                    range.kind == SkipRange.Kind.FILLER &&
                        positionSeconds >= range.startSeconds &&
                        positionSeconds < range.endSeconds
                }
                if (fillerSkip != null) {
                    seekToImmediate((fillerSkip.endSeconds * 1000).toLong())
                    osdState = PlayerOsdHudState(
                        icon = IconGlyphs.FastForward,
                        text = "Filler skipped",
                        progressPercent = 100f
                    )
                }
            }
            if (seekDebounceJob == null) {
                positionMs = currentPosition()
            }
            durationMs = currentDuration()
            bufferedPositionMs = if (html5Player) html5BufferedMs else player.bufferedPosition.coerceAtLeast(0)
            playing = if (html5Player) html5Playing else player.isPlaying
            updateMediaSessionState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                positionMs,
                if (playing) playbackSpeed else 0f
            )
            if (position - lastReport >= 10_000 || position < lastReport) {
                onProgress(position, durationMs)
                lastReport = position
            }
        }
    }

    LaunchedEffect(osdState) {
        if (osdState != null) {
            delay(1_500L)
            osdState = null
        }
    }

    LaunchedEffect(seekIndicatorSide) {
        if (seekIndicatorSide != null) {
            delay(400L)
            seekIndicatorSide = null
        }
    }

    val selectedSubtitleLabel = subtitles.getOrNull(effectiveSubtitleIndex)?.let { track ->
        track.label.ifBlank { track.language.uppercase().ifBlank { "CC" } }
    }

    PlaybackOsdContent(
        metadataTitle = metadataTitle,
        episodeLabel = metadataArtist,
        metadataDescription = metadataDescription,
        metadataGenres = metadataGenres,
        artworkUrl = artworkUrl,
        firstFrameRendered = firstFrameRendered,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        isPlaying = playing,
        isLoading = isBuffering,
        isEnded = ended,
        statusTextOverride = statusText,
        errorMessage = playbackErrorMessage,
        skipRanges = skipRanges,
        autoSkipIntro = autoSkipIntro,
        seekSeconds = seekSeconds,
        playbackSpeed = playbackSpeed,
        currentStreamType = currentStreamType,
        hasSubStream = hasSubStream,
        hasDubStream = hasDubStream,
        subStreamLabel = subStreamLabel,
        streamServerLabel = streamServerLabel,
        subtitleLanguage = subtitleLanguage,
        selectedSubtitleLabel = selectedSubtitleLabel,
        subtitlesCount = subtitles.size,
        audioLanguageLabel = audioLanguageLabel,
        playbackQuality = playbackQuality,
        detectedVideoHeight = detectedVideoHeight,
        disableTouchGestures = disableTouchGestures,
        episodes = episodes,
        selectedEpisodeId = selectedEpisodeId,
        seasons = seasons,
        related = related,
        recommendations = recommendations,
        inWatchlist = inWatchlist,
        isTracked = isTracked,
        osdHudState = osdState,
        seekIndicatorSide = seekIndicatorSide,
        onShowOsdHud = { osdState = it },
        onShowSeekIndicator = { seekIndicatorSide = it },
        onDebouncedSeek = ::seekToDebounced,
        onImmediateSeek = ::seekToImmediate,
        onTogglePlay = { _ ->
            if (ended) {
                seekToImmediate(0L)
                setPlaying(true)
            } else {
                setPlaying(!playing)
            }
        },
        onNextEpisode = onNextEpisode,
        onPreviousEpisode = onPreviousEpisode,
        onEpisodeSelected = onEpisodeSelected,
        onAnimeSelected = onAnimeSelected,
        onOpenSettings = onOpenSettings,
        onPlaybackSpeedDialogRequest = onPlaybackSpeedDialogRequest,
        onStreamServerDialogRequest = onStreamServerDialogRequest,
        onPlaybackQualityDialogRequest = onPlaybackQualityDialogRequest,
        onStreamTypeDialogRequest = onStreamTypeDialogRequest,
        onSubtitlesDialogRequest = { onSubtitlesDialogRequest?.invoke(effectiveSubtitleIndex) },
        onAudioLanguageDialogRequest = onAudioLanguageDialogRequest,
        onToggleWatchlist = onToggleWatchlist,
        onTrackingClick = onTrackingClick,
        onSearchGenre = onSearchGenre,
        onAdjustBrightness = { direction ->
            val brightness = adjustWindowBrightness(context, direction * 0.04f)
            osdState = PlayerOsdHudState(
                icon = IconGlyphs.LightMode,
                text = "Brightness",
                progressPercent = (brightness * 100f).coerceIn(0f, 100f)
            )
        },
        onAdjustVolume = { direction ->
            val volume = adjustMediaVolume(context, direction)
            val volIcon = when {
                volume == 0 -> IconGlyphs.VolumeOff
                volume < 40 -> IconGlyphs.VolumeMute
                volume < 70 -> IconGlyphs.VolumeDown
                else -> IconGlyphs.VolumeUp
            }
            osdState = PlayerOsdHudState(
                icon = volIcon,
                text = "Volume",
                progressPercent = volume.toFloat().coerceIn(0f, 100f)
            )
        },
        onBack = onBack,
        videoContent = {
            if (html5Player) {
                html5View?.let { view ->
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                }
            } else {
                val primary1Argb = colors.primary1.toArgb()
                val accent1Argb = colors.accent1.toArgb()
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            applySubtitleStyle(this, subtitleStyle, primary1Argb, accent1Argb)
                            resizeMode = when (videoScale) {
                                1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setKeepContentOnPlayerReset(true)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            post { applySubtitleStyle(this, subtitleStyle, primary1Argb, accent1Argb) }
                        }
                    },
                    update = {
                        it.player = player
                        applySubtitleStyle(it, subtitleStyle, primary1Argb, accent1Argb)
                        it.resizeMode = when (videoScale) {
                            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

private fun Color.toHexString(): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02x%02x%02x".format(r, g, b)
}

private fun resolveAutoSubtitleIndex(
    subtitles: List<SubtitleTrack>,
    subtitleLanguage: String,
    subtitleVariant: String
): Int {
    if (subtitles.isEmpty() || subtitleLanguage.equals("nosub", ignoreCase = true)) return -1
    fun matchesVariant(track: SubtitleTrack): Boolean = when (subtitleVariant.lowercase()) {
        "full" -> track.label.contains("full", ignoreCase = true) ||
            !track.label.contains("sign", ignoreCase = true)
        "signs only" -> track.label.contains("sign", ignoreCase = true)
        else -> subtitleVariant.isNotBlank() && track.label.equals(subtitleVariant, ignoreCase = true)
    }
    val automaticEnglish = subtitleLanguage.isBlank() || subtitleLanguage.equals("hard", ignoreCase = true)
    fun matchesLanguage(track: SubtitleTrack): Boolean {
        if (automaticEnglish) {
            return track.language.equals("en", ignoreCase = true) ||
                track.language.startsWith("en-", ignoreCase = true)
        }
        val trackCode = track.language.lowercase()
        val requestedCode = subtitleLanguage.lowercase()
        return trackCode == requestedCode || trackCode.substringBefore('-') == requestedCode.substringBefore('-')
    }
    val variantIndex = subtitles.indexOfFirst { matchesLanguage(it) && matchesVariant(it) }
    val preferredIndex = subtitles.indexOfFirst { !automaticEnglish && subtitleLanguage.isNotBlank() && matchesLanguage(it) }
    val englishIndex = subtitles.indexOfFirst(::matchesLanguage)
    val defaultIndex = subtitles.indexOfFirst { it.defaultSelected }
    return when {
        variantIndex >= 0 -> variantIndex
        preferredIndex >= 0 -> preferredIndex
        automaticEnglish && englishIndex >= 0 -> englishIndex
        defaultIndex >= 0 -> defaultIndex
        subtitleLanguage.isBlank() -> 0
        else -> -1
    }
}

private fun findMatchingTextGroup(
    tracks: Tracks,
    target: SubtitleTrack,
    targetIndex: Int
): Pair<Tracks.Group, Int>? {
    val textPairs = mutableListOf<Pair<Tracks.Group, Int>>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            val fmt = group.getTrackFormat(i)
            val labelMatches = !fmt.label.isNullOrBlank() && fmt.label.equals(target.label, ignoreCase = true)
            val langMatches = !fmt.language.isNullOrBlank() && fmt.language.equals(target.language, ignoreCase = true)
            if (labelMatches && (langMatches || target.language.isBlank())) {
                return group to i
            }
            textPairs += group to i
        }
    }
    return textPairs.getOrNull(targetIndex)
}

private fun adjustMediaVolume(context: Context, direction: Int): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val currentPercent = current * 100f / maximum
    val updatedPercent = (currentPercent + direction * 10f).coerceIn(0f, 100f)
    val updated = (updatedPercent * maximum / 100f).toInt().coerceIn(0, maximum)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, updated, 0)
    return updatedPercent.toInt()
}

private fun adjustWindowBrightness(context: Context, delta: Float): Float {
    val activity = context.findActivity() ?: return 0.5f
    val window = activity.window
    val attributes = window.attributes
    val current = if (attributes.screenBrightness >= 0f) {
        attributes.screenBrightness
    } else {
        runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }
    val updated = (current + delta).coerceIn(0.02f, 1f)
    attributes.screenBrightness = updated
    window.attributes = attributes
    return updated
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun applySubtitleStyle(
    playerView: PlayerView,
    style: Int,
    primary1Argb: Int,
    accent1Argb: Int
) {
    val subtitles = playerView.subtitleView ?: return
    val fontIndex = SubtitleStyleCatalog.selectedIndex(style, 0)
    val sizeIndex = SubtitleStyleCatalog.selectedIndex(style, 1)
    val weightIndex = SubtitleStyleCatalog.selectedIndex(style, 2)
    val backgroundIndex = SubtitleStyleCatalog.selectedIndex(style, 3)
    val colorIndex = SubtitleStyleCatalog.selectedIndex(style, 4)
    val opacityIndex = SubtitleStyleCatalog.selectedIndex(style, 5)
    val outlineIndex = SubtitleStyleCatalog.selectedIndex(style, 6)
    val typeface = SubtitleTypefaceCache.get(playerView.context, fontIndex, weightIndex)
    val palette = intArrayOf(
        AndroidColor.WHITE,
        AndroidColor.BLACK,
        AndroidColor.CYAN,
        AndroidColor.YELLOW,
        AndroidColor.RED,
        AndroidColor.GREEN,
        AndroidColor.rgb(102, 102, 255),
        primary1Argb,
        accent1Argb
    )
    val textOpacity = SubtitleStyleCatalog.resolveTextOpacity(opacityIndex)
    val baseTextColor = palette[colorIndex.coerceIn(palette.indices)]
    val foreground = AndroidColor.argb(
        (255 * textOpacity).toInt(),
        AndroidColor.red(baseTextColor),
        AndroidColor.green(baseTextColor),
        AndroidColor.blue(baseTextColor)
    )
    val backgroundOpacity = SubtitleStyleCatalog.resolveBackgroundOpacity(backgroundIndex)
    val background = AndroidColor.argb((255 * backgroundOpacity).toInt(), 0, 0, 0)
    val edgeType = if (outlineIndex == 0) CaptionStyleCompat.EDGE_TYPE_NONE
    else CaptionStyleCompat.EDGE_TYPE_OUTLINE
    val edgeColor = if (colorIndex in listOf(1, 6, 7)) AndroidColor.WHITE else AndroidColor.BLACK
    subtitles.setApplyEmbeddedStyles(false)
    subtitles.setApplyEmbeddedFontSizes(false)
    subtitles.setStyle(
        CaptionStyleCompat(
            foreground,
            background,
            AndroidColor.TRANSPARENT,
            edgeType,
            edgeColor,
            typeface
        )
    )
    val viewportRatio = if (playerView.width > 0 && playerView.height > 0) {
        playerView.width.toFloat() / playerView.height
    } else 16f / 9f
    val widthBasedTextSizes = floatArrayOf(0.03f, 0.025f, 0.02f, 0.03233f)
    subtitles.setFractionalTextSize(widthBasedTextSizes[sizeIndex] * viewportRatio)
}

private object SubtitleTypefaceCache {
    private val files = listOf(
        null,
        "subtitle-fonts/fira_sans.ttf",
        "subtitle-fonts/itim.ttf",
        "subtitle-fonts/salsa.ttf",
        "subtitle-fonts/outfit.ttf",
        "subtitle-fonts/merriweather.ttf",
        "subtitle-fonts/cantora_one.ttf",
        "subtitle-fonts/open_sans.ttf",
        "subtitle-fonts/ruluko.ttf",
        "subtitle-fonts/comic_neue.ttf"
    )
    private val firaSansWeights = listOf(
        "subtitle-fonts/fira_sans.ttf",
        "subtitle-fonts/fira_sans_700.ttf",
        "subtitle-fonts/fira_sans_900.ttf",
        "subtitle-fonts/fira_sans_300.ttf"
    )
    private val comicNeueWeights = listOf(
        "subtitle-fonts/comic_neue.ttf",
        "subtitle-fonts/comic_neue_700.ttf",
        "subtitle-fonts/comic_neue_700.ttf",
        "subtitle-fonts/comic_neue_300.ttf"
    )
    private val cache = mutableMapOf<Int, Typeface>()

    fun assetPath(fontIndex: Int): String? = files.getOrNull(fontIndex)

    @Synchronized
    fun get(context: Context, fontIndex: Int, weightIndex: Int): Typeface {
        val familyIndex = fontIndex.coerceIn(files.indices)
        val normalizedWeight = weightIndex.coerceIn(0, 3)
        val cacheKey = familyIndex * 10 + normalizedWeight
        cache[cacheKey]?.let { return it }
        val asset = when (familyIndex) {
            1 -> firaSansWeights[normalizedWeight]
            9 -> comicNeueWeights[normalizedWeight]
            else -> files[familyIndex]
        }
        val base = asset?.let { assetPath ->
            runCatching { Typeface.createFromAsset(context.assets, assetPath) }.getOrNull()
        } ?: Typeface.SERIF
        val weight = intArrayOf(400, 700, 900, 300)[normalizedWeight]
        val typeface = when {
            familyIndex == 1 -> base
            familyIndex == 9 && normalizedWeight != 2 -> base
            Build.VERSION.SDK_INT >= 28 -> Typeface.create(base, weight, false)
            else -> Typeface.create(base, if (normalizedWeight == 1 || normalizedWeight == 2) Typeface.BOLD else Typeface.NORMAL)
        }
        cache[cacheKey] = typeface
        return typeface
    }
}
