package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SkipRange
import com.paregi.animetv.domain.SubtitleTrack

/** Android host implementation supplies the native Media3 playback surface. */
@Composable
expect fun PlatformPlayer(
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
    onPlaybackQualityDialogRequest: (() -> Unit)? = null,
    skipRanges: List<SkipRange>,
    autoSkipIntro: Boolean,
    skipFiller: Boolean,
    startPositionMs: Long,
    seekSeconds: Int,
    videoScale: Int,
    onVideoScaleDialogRequest: (() -> Unit)? = null,
    videoBuffer: Int,
    progressiveCache: Boolean,
    cacheSizeMb: Int,
    disableTouchGestures: Boolean,
    html5Player: Boolean,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onBack: () -> Unit,
    episodes: List<Episode> = emptyList(),
    selectedEpisodeId: String = "",
    onEpisodeSelected: ((Episode) -> Unit)? = null,
    seasons: List<Anime> = emptyList(),
    related: List<Anime> = emptyList(),
    recommendations: List<Anime> = emptyList(),
    onAnimeSelected: ((Anime) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    inWatchlist: Boolean = false,
    onToggleWatchlist: (() -> Unit)? = null,
    isTracked: Boolean = false,
    onTrackingClick: (() -> Unit)? = null,
    onSearchGenre: ((String) -> Unit)? = null,
    onSubtitlesDialogRequest: ((Int) -> Unit)? = null,
    selectedSubtitleIndexOverride: Int? = null,
    onAvailableAudioLanguagesChanged: ((Set<String>) -> Unit)? = null,
    onDetectedVideoHeightsChanged: ((Int, List<Int>) -> Unit)? = null,
    showBackHint: Boolean = false,
    currentAnimeId: String = "",
    metadataFormat: String? = null,
    metadataQuality: String? = null,
    metadataRating: String? = null,
    settingsOverlayOpen: Boolean = false
)

@Composable
expect fun platformSubtitleFontFamily(fontIndex: Int, weightIndex: Int): FontFamily

@Composable
expect fun PlatformSubtitlePreviewOverlay(
    recreateKey: Any?,
    content: @Composable () -> Unit
)

expect fun clearPlatformPlaybackCache()
