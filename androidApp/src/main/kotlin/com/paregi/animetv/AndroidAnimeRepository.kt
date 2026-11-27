package com.paregi.animetv

import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import com.paregi.animetv.domain.AiringEntry
import com.paregi.animetv.domain.AniListAnimeInfo
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AnimeDetails
import com.paregi.animetv.domain.AnimeRepository
import com.paregi.animetv.domain.DomainBenchmarkResult
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.AnimeSection
import com.paregi.animetv.domain.PlaybackChoice
import com.paregi.animetv.domain.PlaybackStreamType
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.domain.SourceCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.domain.TranslationRepository
import com.paregi.animetv.source.anilist.AniListCatalogApi
import com.paregi.animetv.source.anilist.AniListGraphQlClient
import com.paregi.animetv.source.anilist.AniListHomeShelves
import com.paregi.animetv.source.anilist.AndroidAniListApi
import com.paregi.animetv.source.AndroidCatalogApi
import com.paregi.animetv.source.AndroidPlaybackResolver
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.CatalogSourceSelector
import com.paregi.animetv.source.gotaku.GotakuSource
import com.paregi.animetv.source.gotaku.GotakuSync
import com.paregi.animetv.source.lookup.SourceAnimeLookup
import com.paregi.animetv.source.shared.AndroidSourceHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Calendar

/** Android source adapters for Gotaku and the ReAnime catalog API. */
class AndroidAnimeRepository(
    private val streamProxy: AndroidStreamProxy,
    private val networkStack: AndroidNetworkStack,
    private val translationRepository: TranslationRepository
) : AnimeRepository {
    private val sourceSelector = CatalogSourceSelector()
    private val sourceHttpClient = AndroidSourceHttpClient(networkStack, ::sourceSnapshot)
    private val catalogApi = AndroidCatalogApi(sourceHttpClient::requestBytes, GotakuSync(networkStack))
    private val sourceAnimeLookup = SourceAnimeLookup(catalogApi)
    private val aniListClient = AniListGraphQlClient(
        requestJsonCall = sourceHttpClient::requestJson,
        requestTextCall = { url, body, contentType, referer ->
            sourceHttpClient.request(url, body, contentType, referer = referer)
        }
    )
    private val aniListCatalogApi = AniListCatalogApi(aniListClient)
    private val aniListApi = AndroidAniListApi(
        requestJsonCall = sourceHttpClient::requestJson,
        requestTextCall = { url, body, contentType, referer ->
            sourceHttpClient.request(url, body, contentType, referer = referer)
        }
    )
    @Volatile
    private var cachedAniListHome: AniListHomeShelves? = null
    private val homeCache = LruCache<HomeCacheKey, TimedValue<HomeFeed>>(4)
    private val aniListHomeCache = LruCache<AniListHomeCacheKey, TimedValue<AniListHomeShelves>>(4)
    private val detailsCache = LruCache<ScopedCacheKey, TimedValue<AnimeDetails>>(32)
    private val episodesCache = LruCache<ScopedCacheKey, TimedValue<List<Episode>>>(32)
    private val scheduleCache = LruCache<ScheduleCacheKey, TimedValue<List<AiringEntry>>>(8)
    private val aniListInfoCache = LruCache<AniListInfoKey, TimedValue<AniListAnimeInfo>>(32)
    private val playbackResolver = AndroidPlaybackResolver(
        streamProxy = streamProxy,
        catalogApi = catalogApi,
        requestText = sourceHttpClient::request,
        requestJsonCall = sourceHttpClient::requestJson,
        translationRepository = translationRepository
    )

    private fun sourceSnapshot() = sourceSelector.snapshot()

    override suspend fun selectSource(index: Int): Int {
        return sourceSelector.select(index)
    }

    override suspend fun selectSource(index: Int, domain: String?): Int {
        return sourceSelector.select(index, domain)
    }

    override suspend fun benchmarkDomain(sourceIndex: Int, domain: String): DomainBenchmarkResult = withContext(Dispatchers.IO) {
        val cleanDomain = domain.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val sourceDef = SourceCatalog.find(sourceIndex) ?: SourceCatalog.defaultSource
        val path = sourceDef.benchmarkPath
        val checkKey = sourceDef.benchmarkKey
        val testUrl = "https://$cleanDomain$path"
        val t0 = System.currentTimeMillis()
        try {
            val headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36",
                "X-Fixdomain-Prox" to "1"
            )
            val response = networkStack.execute(
                url = testUrl,
                method = "GET",
                headers = headers,
                connectTimeoutMs = 4000,
                readTimeoutMs = 4000
            )
            val latency = System.currentTimeMillis() - t0
            try {
                val status = response.responseCode
                if (status !in 200..299) {
                    return@withContext DomainBenchmarkResult(
                        domain = cleanDomain,
                        latencyMs = latency,
                        success = false,
                        errorMessage = "HTTP $status"
                    )
                }
                val text = response.inputStream?.use {
                    it.readBoundedBytes(1024 * 1024).toString(StandardCharsets.UTF_8)
                }.orEmpty()
                if (text.isNotBlank()) {
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (json != null && (json.has(checkKey) || json.has("data") || json.has("results"))) {
                        DomainBenchmarkResult(
                            domain = cleanDomain,
                            latencyMs = latency,
                            success = true
                        )
                    } else {
                        DomainBenchmarkResult(
                            domain = cleanDomain,
                            latencyMs = latency,
                            success = false,
                            errorMessage = "Invalid JSON schema"
                        )
                    }
                } else {
                    DomainBenchmarkResult(
                        domain = cleanDomain,
                        latencyMs = latency,
                        success = false,
                        errorMessage = "HTTP $status"
                    )
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - t0
            DomainBenchmarkResult(
                domain = cleanDomain,
                latencyMs = latency,
                success = false,
                errorMessage = e.message ?: "Connection error"
            )
        }
    }

    override suspend fun benchmarkDomains(sourceIndex: Int, domains: List<String>): List<DomainBenchmarkResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            domains.map { domain ->
                async { benchmarkDomain(sourceIndex, domain) }
            }.awaitAll()
        }
    }

    override suspend fun loadHome(
        useAniListTrending: Boolean,
        highResolutionImages: Boolean
    ): HomeFeed = withContext(Dispatchers.IO) {
        val source = sourceSnapshot()
        val cacheGen = networkStack.cacheGeneration.get()
        val homeKey = HomeCacheKey(
            cacheGen = cacheGen,
            sourceIndex = source.index,
            domain = source.domain,
            useAniListTrending = useAniListTrending,
            highResolutionImages = highResolutionImages
        )
        homeCache.getFresh(homeKey, HOME_CACHE_TTL_MS)?.let { return@withContext it }

        val alYearTarget = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - 2_592_000_000L
        }.get(Calendar.YEAR)
        val alKey = AniListHomeCacheKey(
            cacheGen = cacheGen,
            includeFeatured = useAniListTrending,
            highResolutionImages = highResolutionImages,
            year = alYearTarget
        )
        coroutineScope {
            val trendingDeferred = async {
                safeFetch {
                    catalogApi.fetchTitles(if (source.isReAnime) "trending_today" else "trending_day", 1, source)
                }
            }
            val recentDeferred = async {
                safeFetch {
                    catalogApi.fetchTitles(if (source.isReAnime) "latest_aired" else "latest", 1, source)
                }
            }
            val popularDeferred = async {
                safeFetch {
                    catalogApi.fetchTitles(if (source.isReAnime) "popularity_desc" else "trending_week", 1, source)
                }
            }
            val aniListDeferred = async {
                aniListHomeCache.getFresh(alKey, ANILIST_HOME_CACHE_TTL_MS)?.let { return@async it }
                try {
                    val fresh = aniListCatalogApi.fetchHomeShelves(
                        highResolutionImages = highResolutionImages,
                        includeFeatured = useAniListTrending,
                        year = alYearTarget,
                        source = source
                    )
                    if (listOfNotNull(fresh.recent, fresh.top, fresh.popular, fresh.year, fresh.upcoming).isNotEmpty()) {
                        aniListHomeCache.putTimed(alKey, fresh)
                    }
                    fresh
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }

            val trending = trendingDeferred.await()
            val recent = recentDeferred.await()
            val popular = popularDeferred.await()
            val alShelves = mergeAniListHome(aniListDeferred.await(), source)

            val featured = if (useAniListTrending) {
                alShelves.featured.orEmpty().ifEmpty { trending.take(6) }.ifEmpty { recent.take(6) }
            } else {
                trending.take(6).ifEmpty { recent.take(6) }
            }

            val sections = listOf(
                AnimeSection(
                    "recent",
                    if (source.isReAnime) "Recently Released" else "Recently Updated",
                    recent
                ),
                AnimeSection("trending", "Trending Today", trending),
                AnimeSection(
                    "popular",
                    if (source.isReAnime) "Most Popular" else "Popular This Week",
                    popular
                ),
                AnimeSection("alrecent", "Recently Released - AniList", alShelves.recent.orEmpty()),
                AnimeSection("altop", "Top Anime - AniList", alShelves.top.orEmpty()),
                AnimeSection("alpopular", "All Time Popular - AniList", alShelves.popular.orEmpty()),
                AnimeSection("alyear", "Top $alYearTarget - AniList", alShelves.year.orEmpty()),
                AnimeSection("alupcoming", "Upcoming - AniList", alShelves.upcoming.orEmpty())
            )
            HomeFeed(
                featured = featured,
                sections = sections
            ).also { feed ->
                if (sections.any { it.items.isNotEmpty() }) {
                    homeCache.putTimed(homeKey, feed)
                }
            }
        }
    }

    private suspend fun safeFetch(block: suspend () -> List<Anime>): List<Anime> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    private fun mergeAniListHome(
        fresh: AniListHomeShelves?,
        source: CatalogSource
    ): AniListHomeShelves {
        val prev = cachedAniListHome
        fun List<Anime>.forSource() = map {
            if (it.sourceIndex == source.index) it else it.copy(sourceIndex = source.index)
        }
        val merged = AniListHomeShelves(
            featured = fresh?.featured ?: prev?.featured?.forSource(),
            recent = fresh?.recent ?: prev?.recent?.forSource(),
            top = fresh?.top ?: prev?.top?.forSource(),
            popular = fresh?.popular ?: prev?.popular?.forSource(),
            year = fresh?.year ?: prev?.year?.forSource(),
            upcoming = fresh?.upcoming ?: prev?.upcoming?.forSource()
        )
        if (fresh != null && listOfNotNull(fresh.recent, fresh.top, fresh.popular, fresh.year, fresh.upcoming).isNotEmpty()) {
            cachedAniListHome = merged
        }
        return merged
    }

    suspend fun loadAndroidTvHome(): HomeFeed = withContext(Dispatchers.IO) {
        val source = sourceSnapshot()
        coroutineScope {
            val recent = async { aniListApi.fetchTvRecent(source) }
            val trending = async { aniListApi.fetchTvRanking(trending = true, source = source) }
            val popular = async { aniListApi.fetchTvRanking(trending = false, source = source) }
            HomeFeed(
                sections = listOf(
                    AnimeSection("recent", "Recently Updated", recent.await()),
                    AnimeSection("trending", "Trending", trending.await()),
                    AnimeSection("popular", "Popular", popular.await())
                )
            )
        }
    }

    override suspend fun search(filters: SearchFilters, page: Int): SearchPage = withContext(Dispatchers.IO) {
        val source = sourceSnapshot()
        if (filters.aniList) aniListApi.search(filters, page, source)
        else catalogApi.search(filters, page, source)
    }

    override suspend fun loadDetails(animeId: String): AnimeDetails = withContext(Dispatchers.IO) {
        val source = sourceSnapshot()
        val cacheGen = networkStack.cacheGeneration.get()
        val detailKey = ScopedCacheKey(cacheGen, source.index, source.domain, animeId)
        detailsCache.getFresh(detailKey, DETAILS_CACHE_TTL_MS)?.let { return@withContext it }

        coroutineScope {
            val externalKey = animeId.substringBefore('|')
            val aniListId = externalKey.removePrefix("anilist:")
                .takeIf { animeId.startsWith("anilist:") && it.isNotBlank() }
            val malId = externalKey.removePrefix("mal:")
                .takeIf { animeId.startsWith("mal:") && it.isNotBlank() }
            val externalTitle = Uri.decode(animeId.substringAfter('|', "").substringBefore('|'))

            val prefetchedRelated = aniListId?.let { id ->
                async { runCatching { aniListApi.loadRelatedTitles(id, source) }.getOrNull() }
            }
            val titleMetadata = if (aniListId != null || malId != null) {
                runCatching { aniListApi.loadTitleMetadata(aniListId, malId) }.getOrNull()
            } else null
            val sourceId = if (aniListId != null || malId != null) {
                sourceAnimeLookup.resolveSourceId(
                    aniListId.orEmpty(),
                    externalTitle,
                    malId ?: titleMetadata?.malId,
                    titleMetadata?.aliases.orEmpty(),
                    titleMetadata?.year,
                    titleMetadata?.status,
                    source
                )
            } else animeId

            val gotakuDetail: JSONObject?
            val anime: Anime
            val episodesDeferred = if (!source.isReAnime) {
                async { loadEpisodesCached(sourceId, source) }
            } else null
            if (!source.isReAnime) {
                gotakuDetail = catalogApi.json("${source.root}/api/v1/titles/${Uri.encode(sourceId)}", source)
                    .let { it.optJSONObject("data") ?: it }
                anime = GotakuSource.anime(gotakuDetail.optJSONObject("title") ?: gotakuDetail, sourceId)
                    ?: error("Anime details response had no title")
            } else {
                gotakuDetail = null
                anime = catalogApi.loadAnime(sourceId, source)
                    ?: error("Anime details response had no title")
            }
            val activeEpisodesDeferred = episodesDeferred ?: async { loadEpisodesCached(sourceId, source) }

            val (gotakuSeasons, gotakuRelations, gotakuRecommendations) =
                gotakuDetail?.let { GotakuSource.relatedItems(it, sourceId) }
                    ?: Triple(emptyList(), emptyList(), emptyList())
            val resolvedAniListId = anime.aniListId ?: titleMetadata?.aniListId ?: aniListId
            val resolvedMalId = anime.malId ?: titleMetadata?.malId ?: malId
            val trailerDeferred = if (titleMetadata?.trailerId == null && anime.trailerId == null && resolvedAniListId != null) {
                async { runCatching { aniListApi.loadTrailer(resolvedAniListId) }.getOrNull() }
            } else null
            val relatedDeferred = prefetchedRelated ?: resolvedAniListId?.let { id ->
                async { runCatching { aniListApi.loadRelatedTitles(id, source) }.getOrNull() }
            }

            val episodes = activeEpisodesDeferred.await()
            val trailerId = titleMetadata?.trailerId ?: trailerDeferred?.await()
            val relatedTitles = relatedDeferred?.await()
            val detailsAnime = anime.copy(
                aniListId = resolvedAniListId,
                malId = resolvedMalId,
                trailerId = trailerId ?: anime.trailerId
            )
            AnimeDetails(
                anime = detailsAnime,
                episodes = episodes,
                related = relatedTitles?.related.orEmpty().ifEmpty { gotakuRelations },
                recommendations = relatedTitles?.recommendations.orEmpty().ifEmpty { gotakuRecommendations },
                seasons = gotakuSeasons
            ).also { details ->
                detailsCache.putTimed(detailKey, details)
            }
        }
    }

    override suspend fun loadAniListInfo(anime: Anime): AniListAnimeInfo? = withContext(Dispatchers.IO) {
        val key = AniListInfoKey(
            cacheGen = networkStack.cacheGeneration.get(),
            id = anime.id,
            aniListId = anime.aniListId,
            malId = anime.malId
        )
        aniListInfoCache.getFresh(key, INFO_CACHE_TTL_MS)?.let { return@withContext it }
        aniListApi.loadInfo(anime)?.also { aniListInfoCache.putTimed(key, it) }
    }

    override suspend fun loadSchedule(day: Int?, includeNonJapan: Boolean): List<AiringEntry> =
        withContext(Dispatchers.IO) {
            val source = sourceSnapshot()
            val key = ScheduleCacheKey(
                cacheGen = networkStack.cacheGeneration.get(),
                sourceIndex = source.index,
                day = day,
                includeNonJapan = includeNonJapan
            )
            scheduleCache.getFresh(key, SCHEDULE_CACHE_TTL_MS)?.let { return@withContext it }
            aniListApi.loadSchedule(day, includeNonJapan, source).also { entries ->
                if (entries.isNotEmpty()) scheduleCache.putTimed(key, entries)
            }
        }

    override suspend fun loadEpisodes(animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        loadEpisodesCached(animeId, sourceSnapshot())
    }

    private suspend fun loadEpisodesCached(animeId: String, source: CatalogSource): List<Episode> {
        val key = ScopedCacheKey(networkStack.cacheGeneration.get(), source.index, source.domain, animeId)
        episodesCache.getFresh(key, EPISODES_CACHE_TTL_MS)?.let { return it }
        return catalogApi.loadEpisodes(animeId, source).also { list ->
            if (list.isNotEmpty()) episodesCache.putTimed(key, list)
        }
    }

    private data class TimedValue<T>(val value: T, val cachedAtMs: Long)
    private data class HomeCacheKey(
        val cacheGen: Long,
        val sourceIndex: Int,
        val domain: String,
        val useAniListTrending: Boolean,
        val highResolutionImages: Boolean
    )
    private data class AniListHomeCacheKey(
        val cacheGen: Long,
        val includeFeatured: Boolean,
        val highResolutionImages: Boolean,
        val year: Int
    )
    private data class ScopedCacheKey(
        val cacheGen: Long,
        val sourceIndex: Int,
        val domain: String,
        val id: String
    )
    private data class ScheduleCacheKey(
        val cacheGen: Long,
        val sourceIndex: Int,
        val day: Int?,
        val includeNonJapan: Boolean
    )
    private data class AniListInfoKey(
        val cacheGen: Long,
        val id: String,
        val aniListId: String?,
        val malId: String?
    )

    private fun <K : Any, V : Any> LruCache<K, TimedValue<V>>.getFresh(key: K, ttlMs: Long): V? {
        val entry = get(key) ?: return null
        return if (SystemClock.elapsedRealtime() - entry.cachedAtMs < ttlMs) entry.value else null
    }

    private fun <K : Any, V : Any> LruCache<K, TimedValue<V>>.putTimed(key: K, value: V) {
        put(key, TimedValue(value, SystemClock.elapsedRealtime()))
    }

    private companion object {
        const val HOME_CACHE_TTL_MS = 60_000L
        const val ANILIST_HOME_CACHE_TTL_MS = 5 * 60_000L
        const val DETAILS_CACHE_TTL_MS = 3 * 60_000L
        const val EPISODES_CACHE_TTL_MS = 3 * 60_000L
        const val SCHEDULE_CACHE_TTL_MS = 5 * 60_000L
        const val INFO_CACHE_TTL_MS = 5 * 60_000L
    }

    override suspend fun resolvePlayback(
        episodeId: String,
        preferDub: Boolean,
        mirrorIndex: Int,
        subtitleLanguage: String,
        hasSub: Boolean,
        requestedStreamType: PlaybackStreamType?
    ): PlaybackChoice = playbackResolver.resolvePlayback(
        episodeId = episodeId,
        preferDub = preferDub,
        mirrorIndex = mirrorIndex,
        subtitleLanguage = subtitleLanguage,
        hasSub = hasSub,
        requestedStreamType = requestedStreamType,
        source = sourceSnapshot()
    )

    override suspend fun translatePlaybackSubtitle(
        choice: PlaybackChoice,
        targetLanguage: String
    ): SubtitleTrack? = playbackResolver.translateChoiceSubtitle(choice, targetLanguage)
}
