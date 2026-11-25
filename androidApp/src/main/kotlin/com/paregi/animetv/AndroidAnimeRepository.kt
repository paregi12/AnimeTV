package com.paregi.animetv

import android.net.Uri
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
import com.paregi.animetv.source.anilist.AndroidAniListApi
import com.paregi.animetv.source.AndroidCatalogApi
import com.paregi.animetv.source.AndroidPlaybackResolver
import com.paregi.animetv.source.CatalogSourceSelector
import com.paregi.animetv.source.gotaku.GotakuSource
import com.paregi.animetv.source.gotaku.GotakuSync
import com.paregi.animetv.source.lookup.SourceAnimeLookup
import com.paregi.animetv.source.shared.AndroidSourceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets

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
    private val aniListApi = AndroidAniListApi(
        requestJsonCall = sourceHttpClient::requestJson,
        requestTextCall = { url, body, contentType, referer ->
            sourceHttpClient.request(url, body, contentType, referer = referer)
        }
    )
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
                val stream = if (status in 200..299) response.inputStream else response.errorStream
                val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
                if (status in 200..299 && text.isNotBlank()) {
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
        coroutineScope {
            val trendingDeferred = async { catalogApi.fetchTitles(if (source.isReAnime) "popularity_desc" else "trending_day", 1, source) }
            val recentDeferred = async { catalogApi.fetchTitles(if (source.isReAnime) "latest_aired" else "latest", 1, source) }
            val popularDeferred = async { catalogApi.fetchTitles(if (source.isReAnime) "score_desc" else "trending_week", 1, source) }
            val alRecentDeferred = async { runCatching { aniListApi.fetchTvRecent(source) }.getOrDefault(emptyList()) }
            val alTopDeferred = async { runCatching { aniListApi.fetchTvRanking(trending = false, source = source) }.getOrDefault(emptyList()) }
            val alPopularDeferred = async { runCatching { aniListApi.fetchTvRanking(trending = true, source = source) }.getOrDefault(emptyList()) }
            val alUpcomingDeferred = async { runCatching { aniListApi.search(SearchFilters(query = "", season = "UPCOMING", aniList = true), 1, source).results }.getOrDefault(emptyList()) }
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val alYearDeferred = async { runCatching { aniListApi.search(SearchFilters(query = "", year = currentYear, aniList = true), 1, source).results }.getOrDefault(emptyList()) }

            val trending = trendingDeferred.await()
            val recent = recentDeferred.await()
            val popular = popularDeferred.await()
            val alRecent = alRecentDeferred.await()
            val alTop = alTopDeferred.await()
            val alPopular = alPopularDeferred.await()
            val alUpcoming = alUpcomingDeferred.await()
            val alYear = alYearDeferred.await()

            val featured = if (useAniListTrending) {
                runCatching { aniListApi.fetchTrending(highResolutionImages, source) }
                    .getOrDefault(emptyList())
                    .ifEmpty { trending.take(6) }
            } else trending.take(6)

            val sections = buildList {
                add(
                    AnimeSection(
                        "recent",
                        if (source.isReAnime) "Recently Released" else "Recently Updated",
                        recent
                    )
                )
                add(AnimeSection("trending", "Trending Today", trending))
                add(
                    AnimeSection(
                        "popular",
                        if (source.isReAnime) "Most Popular" else "Popular This Week",
                        popular
                    )
                )
                if (alRecent.isNotEmpty()) {
                    add(AnimeSection("alrecent", "Recently Released - AniList", alRecent))
                }
                if (alTop.isNotEmpty()) {
                    add(AnimeSection("altop", "Top Anime - AniList", alTop))
                }
                if (alPopular.isNotEmpty()) {
                    add(AnimeSection("alpopular", "All Time Popular - AniList", alPopular))
                }
                if (alYear.isNotEmpty()) {
                    add(AnimeSection("alyear", "Top $currentYear - AniList", alYear))
                }
                if (alUpcoming.isNotEmpty()) {
                    add(AnimeSection("alupcoming", "Upcoming - AniList", alUpcoming))
                }
            }
            HomeFeed(
                featured = featured,
                sections = sections
            )
        }
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
        val externalKey = animeId.substringBefore('|')
        val aniListId = externalKey.removePrefix("anilist:")
            .takeIf { animeId.startsWith("anilist:") && it.isNotBlank() }
        val malId = externalKey.removePrefix("mal:")
            .takeIf { animeId.startsWith("mal:") && it.isNotBlank() }
        val externalTitle = Uri.decode(animeId.substringAfter('|', "").substringBefore('|'))
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
        val gotakuDetail = if (!source.isReAnime) {
            catalogApi.json("${source.root}/api/v1/titles/${Uri.encode(sourceId)}", source)
                .let { it.optJSONObject("data") ?: it }
        } else null
        val anime = (if (gotakuDetail != null) {
            GotakuSource.anime(gotakuDetail.optJSONObject("title") ?: gotakuDetail, sourceId)
        } else {
            catalogApi.loadAnime(sourceId, source)
        }) ?: error("Anime details response had no title")
        val (gotakuSeasons, gotakuRelations, gotakuRecommendations) =
            gotakuDetail?.let { GotakuSource.relatedItems(it, sourceId) }
                ?: Triple(emptyList(), emptyList(), emptyList())
        val resolvedAniListId = anime.aniListId ?: titleMetadata?.aniListId ?: aniListId
        val resolvedMalId = anime.malId ?: titleMetadata?.malId ?: malId
        val trailerId = titleMetadata?.trailerId
            ?: resolvedAniListId?.let { runCatching { aniListApi.loadTrailer(it) }.getOrNull() }
        val detailsAnime = anime.copy(
            aniListId = resolvedAniListId,
            malId = resolvedMalId,
            trailerId = trailerId ?: anime.trailerId
        )
        val relatedTitles = resolvedAniListId
            ?.let { runCatching { aniListApi.loadRelatedTitles(it, source) }.getOrNull() }
        AnimeDetails(
            anime = detailsAnime,
            episodes = catalogApi.loadEpisodes(sourceId, source),
            related = relatedTitles?.related.orEmpty().ifEmpty { gotakuRelations },
            recommendations = relatedTitles?.recommendations.orEmpty().ifEmpty { gotakuRecommendations },
            seasons = gotakuSeasons
        )
    }

    override suspend fun loadAniListInfo(anime: Anime): AniListAnimeInfo? = withContext(Dispatchers.IO) {
        aniListApi.loadInfo(anime)
    }

    override suspend fun loadSchedule(day: Int?, includeNonJapan: Boolean): List<AiringEntry> =
        aniListApi.loadSchedule(day, includeNonJapan, sourceSnapshot())

    override suspend fun loadEpisodes(animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        catalogApi.loadEpisodes(animeId, sourceSnapshot())
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
