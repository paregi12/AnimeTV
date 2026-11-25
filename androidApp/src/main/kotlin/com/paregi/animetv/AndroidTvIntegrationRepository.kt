package com.paregi.animetv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.paregi.androidtv.animetvjmto.R
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.TvIntegrationRepository
import com.paregi.animetv.domain.TvLaunchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Android TV launcher channels and Watch Next integration. */
class AndroidTvIntegrationRepository(context: Context) : TvIntegrationRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val channelMutex = CHANNEL_UPDATE_MUTEX
    private val watchNextMutex = Mutex()
    private val isTv by lazy {
        appContext.packageManager.hasSystemFeature("android.software.leanback")
    }
    private var launchHandler: ((TvLaunchRequest) -> Unit)? = null
    private var pendingLaunch: TvLaunchRequest? = null
    private var lastWatchNextKey: String? = null
    private var lastWatchNextWriteAt = 0L

    override suspend fun updateHomeChannels(feed: HomeFeed) {
        if (!isTv || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        withContext(Dispatchers.IO) {
            channelMutex.withLock {
                val recent = feed.sections.firstOrNull { it.id == "recent" }?.items.orEmpty()
                val trending = (feed.featured + feed.sections.firstOrNull { it.id == "trending" }?.items.orEmpty())
                    .distinctBy { it.id }
                val popular = feed.sections.firstOrNull { it.id == "popular" }?.items.orEmpty()
                listOf(
                    ChannelFeed("Recent", "paregi_recent", recent),
                    ChannelFeed("Trending", "paregi_trending", trending),
                    ChannelFeed("Popular", "paregi_popular", popular)
                ).forEach { channelFeed ->
                    runCatching { updateChannel(channelFeed) }
                }
            }
        }
    }

    override suspend fun updateContinueWatching(
        anime: Anime,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        force: Boolean
    ) {
        if (!isTv || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (positionMs < 2_000 || durationMs <= positionMs || durationMs - positionMs <= 5_000) return
        withContext(Dispatchers.IO) {
            watchNextMutex.withLock {
                val itemKey = anime.id + "|" + episode.id
                val now = System.currentTimeMillis()
                if (!force && itemKey == lastWatchNextKey && now - lastWatchNextWriteAt < WATCH_NEXT_UPDATE_INTERVAL_MS) {
                    return@withLock
                }
                runCatching {
                    clearOwnedWatchNextPrograms()
                    val intent = appIntent().apply {
                        putExtra(EXTRA_ANIME_JSON, anime.toTvJson().toString())
                        putExtra(EXTRA_EPISODE_ID, episode.id)
                        putExtra(EXTRA_POSITION_MS, positionMs)
                        putExtra(EXTRA_WATCH_NEXT_MARKER, true)
                    }
                    val intentUri = Uri.parse(intent.toUri(Intent.URI_ANDROID_APP_SCHEME))
                    val builder = WatchNextProgram.Builder()
                        .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                        .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setDurationMillis(durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                        .setLastPlaybackPositionMillis(positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                        .setLastEngagementTimeUtcMillis(now)
                        .setTitle(anime.title)
                        .setDescription("Episode ${episode.number}")
                        .setIntentUri(intentUri)
                    anime.posterUrl?.let { builder.setPosterArtUri(Uri.parse(it)) }
                    resolver.insert(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        builder.build().toContentValues()
                    )
                    lastWatchNextKey = itemKey
                    lastWatchNextWriteAt = now
                }
            }
        }
    }

    override suspend fun clearContinueWatching() {
        if (!isTv || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        withContext(Dispatchers.IO) {
            watchNextMutex.withLock {
                runCatching { clearOwnedWatchNextPrograms() }
                lastWatchNextKey = null
                lastWatchNextWriteAt = 0L
            }
        }
    }

    override fun setLaunchHandler(handler: ((TvLaunchRequest) -> Unit)?) {
        launchHandler = handler
        if (handler != null) {
            pendingLaunch?.let {
                pendingLaunch = null
                handler(it)
            }
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        val rawAnime = intent?.getStringExtra(EXTRA_ANIME_JSON) ?: return
        val anime = runCatching { JSONObject(rawAnime).toTvAnime() }.getOrNull() ?: return
        val request = TvLaunchRequest(
            anime = anime,
            episodeId = intent.getStringExtra(EXTRA_EPISODE_ID),
            positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        )
        val handler = launchHandler
        if (handler == null) pendingLaunch = request else handler(request)
    }

    private fun updateChannel(feed: ChannelFeed) {
        val existingChannelId = findChannelId(feed.providerId)
        if (feed.items.isEmpty()) {
            existingChannelId?.let(::clearChannelPrograms)
            return
        }
        val channelId = existingChannelId ?: createChannel(feed)
        clearChannelPrograms(channelId)
        feed.items.asSequence()
            .filter { !it.posterUrl.isNullOrBlank() }
            .take(MAX_PROGRAMS_PER_CHANNEL)
            .forEach { anime ->
                runCatching { insertPreviewProgram(channelId, anime) }
            }
    }

    private fun findChannelId(providerId: String): Long? {
        val projection = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID
        )
        return resolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            projection,
            "${TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID} = ?",
            arrayOf(providerId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(TvContractCompat.Channels._ID))
            else null
        }
    }

    private fun createChannel(feed: ChannelFeed): Long {
        val homeIntentUri = Uri.parse(appIntent().toUri(Intent.URI_ANDROID_APP_SCHEME))
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(feed.name)
            .setInternalProviderId(feed.providerId)
            .setAppLinkIntentUri(homeIntentUri)
            .build()
        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error("Android TV did not create the ${feed.name} channel")
        val channelId = ContentUris.parseId(uri)
        BitmapFactory.decodeResource(appContext.resources, R.drawable.app_banner_paregi)?.let { bitmap ->
            runCatching { ChannelLogoUtils.storeChannelLogo(appContext, channelId, bitmap) }
            bitmap.recycle()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { TvContract.requestChannelBrowsable(appContext, channelId) }
        }
        return channelId
    }

    private fun clearChannelPrograms(channelId: Long) {
        val projection = arrayOf(TvContractCompat.Programs._ID, TvContract.Programs.COLUMN_CHANNEL_ID)
        resolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            projection,
            "${TvContract.Programs.COLUMN_CHANNEL_ID} = ?",
            arrayOf(channelId.toString()),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(TvContractCompat.Programs._ID)
            while (cursor.moveToNext()) {
                val programUri = TvContractCompat.buildPreviewProgramUri(cursor.getLong(idIndex))
                resolver.delete(programUri, null, null)
            }
        }
    }

    private fun insertPreviewProgram(channelId: Long, anime: Anime) {
        val posterUrl = anime.posterUrl ?: return
        val intent = appIntent().apply { putExtra(EXTRA_ANIME_JSON, anime.toTvJson().toString()) }
        val program = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
            .setTitle(anime.title)
            .setDescription(anime.synopsis.orEmpty())
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            .setPosterArtUri(Uri.parse(posterUrl))
            .setIntentUri(Uri.parse(intent.toUri(Intent.URI_ANDROID_APP_SCHEME)))
            .build()
        resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
    }

    private fun clearOwnedWatchNextPrograms() {
        val projection = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI
        )
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms._ID)
            val intentIndex = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)
            while (cursor.moveToNext()) {
                if (intentIndex >= 0 && cursor.getString(intentIndex).orEmpty().contains(EXTRA_WATCH_NEXT_MARKER)) {
                    resolver.delete(TvContractCompat.buildWatchNextProgramUri(cursor.getLong(idIndex)), null, null)
                }
            }
        }
    }

    private fun appIntent() = Intent(appContext, MainActivity::class.java).apply {
        setPackage(appContext.packageName)
    }

    private fun Anime.toTvJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("posterUrl", posterUrl)
        .put("status", status)
        .put("duration", duration)
        .put("rating", rating)
        .put("malId", malId)
        .put("aniListId", aniListId)
        .put("sourceIndex", sourceIndex)

    private fun JSONObject.toTvAnime() = Anime(
        id = optString("id"),
        title = optString("title"),
        posterUrl = optString("posterUrl").takeIf { it.isNotBlank() },
        status = optString("status").takeIf { it.isNotBlank() },
        duration = optString("duration").takeIf { it.isNotBlank() },
        rating = optString("rating").takeIf { it.isNotBlank() },
        malId = optString("malId").takeIf { it.isNotBlank() },
        aniListId = optString("aniListId").takeIf { it.isNotBlank() },
        sourceIndex = optInt("sourceIndex", 9)
    )

    private data class ChannelFeed(val name: String, val providerId: String, val items: List<Anime>)

    companion object {
        const val EXTRA_ANIME_JSON = "paregi_anime_json"
        const val EXTRA_EPISODE_ID = "paregi_episode_id"
        const val EXTRA_POSITION_MS = "paregi_position_ms"
        private const val EXTRA_WATCH_NEXT_MARKER = "paregi_watch_next"
        private const val MAX_PROGRAMS_PER_CHANNEL = 50
        private const val WATCH_NEXT_UPDATE_INTERVAL_MS = 30_000L
        private val CHANNEL_UPDATE_MUTEX = Mutex()
    }
}
