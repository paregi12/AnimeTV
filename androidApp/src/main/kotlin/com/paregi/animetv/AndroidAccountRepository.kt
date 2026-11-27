package com.paregi.animetv

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AccountRepository
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.AnimeTrackingState
import com.paregi.animetv.domain.ConnectedAccount
import com.paregi.animetv.domain.LoginPollResult
import com.paregi.animetv.domain.LoginSession
import com.paregi.animetv.domain.ProviderTrackingState
import com.paregi.animetv.domain.TrackedAnimeEntry
import com.paregi.animetv.domain.TrackedAnimePage
import com.paregi.animetv.domain.SourceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Account tokens and the existing QR authorization relay for AniList and MAL. */
class AndroidAccountRepository(
    context: Context,
    private val networkStack: AndroidNetworkStack,
    private val namespace: ProfileNamespace
) : AccountRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences("KMP_ACCOUNTS", Context.MODE_PRIVATE)
    private val http = AndroidAccountHttpClient(networkStack)
    private val progressAttempts = LruCache<String, Boolean>(256)

    override suspend fun readAccounts(): AccountState = withContext(Dispatchers.IO) {
        AccountState(
            aniListUsername = readAccount(AccountProvider.ANILIST)?.username,
            malUsername = readAccount(AccountProvider.MAL)?.username
        )
    }

    override suspend fun loadProfileAvatars(): Map<String, String> = withContext(Dispatchers.IO) {
        coroutineScope {
            val aniListToken = accessToken(AccountProvider.ANILIST)
            val malToken = accessToken(AccountProvider.MAL)
            val aniListAvatar = aniListToken?.let { token ->
                async {
                    runCatching {
                        val response = postJson(
                            "https://graphql.anilist.co",
                            JSONObject().put("query", "query { Viewer { avatar { large } } }").toString(),
                            mapOf("Authorization" to "Bearer $token", "Origin" to "https://anilist.co")
                        )
                        response.optJSONObject("data")?.optJSONObject("Viewer")
                            ?.optJSONObject("avatar")?.optString("large")
                            ?.takeIf { it.startsWith("http") }
                    }.getOrNull()
                }
            }
            val malAvatar = malToken?.let { token ->
                async {
                    runCatching {
                        JSONObject(
                            getText(
                                "https://api.myanimelist.net/v2/users/@me?fields=picture",
                                mapOf(
                                    "Authorization" to "Bearer $token",
                                    "X-MAL-CLIENT-ID" to MAL_CLIENT_ID,
                                    "Accept" to "application/json"
                                )
                            )
                        ).optString("picture").takeIf { it.startsWith("http") }
                    }.getOrNull()
                }
            }
            buildMap {
                aniListAvatar?.await()?.let { put("-anilist", it) }
                malAvatar?.await()?.let { put("-mal", it) }
            }
        }
    }

    override suspend fun startLogin(provider: AccountProvider): LoginSession = withContext(Dispatchers.IO) {
        val loginId = when (provider) {
            AccountProvider.ANILIST -> "AL" + System.currentTimeMillis() + "Y"
            AccountProvider.MAL -> "MAL" + System.currentTimeMillis() + "L"
        }
        val root = relayRoot(provider)
        repeat(30) {
            val check = relayJson("$root/check?lid=$loginId&t=${System.currentTimeMillis()}")
            if (check.optInt("st", -1) == 0) {
                return@withContext LoginSession(
                    provider = provider,
                    id = loginId,
                    loginUrl = "$root/login?lid=$loginId"
                )
            }
            delay(2_000)
        }
        error("The login relay did not become ready")
    }

    override suspend fun loginMalWithPassword(
        username: String,
        password: String
    ): ConnectedAccount = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        require(cleanUsername.isNotBlank() && password.isNotBlank()) {
            "Username and password are required"
        }
        val body = buildString {
            append("client_id=").append(formEncode(MAL_CLIENT_ID))
            append("&grant_type=password")
            append("&password=").append(formEncode(password))
            append("&username=").append(formEncode(cleanUsername))
        }
        val response = networkStack.execute(
            url = "https://api.myanimelist.net/v2/auth/token",
            method = "POST",
            headers = mapOf(
                "X-MAL-Client-ID" to MAL_CLIENT_ID,
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json",
                "User-Agent" to "AnimeTV KMP/6.6.7 (Android)"
            ),
            body = body.toByteArray(Charsets.UTF_8)
        )
        val responseText = try {
            val status = response.responseCode
            val stream = if (status in 200..299) response.inputStream else response.errorStream
            val text = stream?.use { it.readBoundedBytes(1024 * 1024).toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(text).let { json ->
                        json.optString("message").ifBlank { json.optString("error") }
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                error(message ?: "MyAnimeList login failed (HTTP $status)")
            }
            text
        } finally {
            response.close()
        }
        val json = JSONObject(responseText)
        val token = json.optString("access_token")
        if (token.isBlank()) {
            val message = json.optString("message").ifBlank { json.optString("error") }
            error(message.ifBlank { "MyAnimeList returned no access token" })
        }
        val expiresIn = json.optLong("expires_in").takeIf { it > 0 } ?: 2_592_000L
        val saved = JSONObject()
            .put("access_token", token)
            .put("expires_at", System.currentTimeMillis() / 1000 + expiresIn)
            .put("username", cleanUsername)
            .put("refresh_token", json.optString("refresh_token").takeIf { it.isNotBlank() })
        preferences.edit().putString(preferenceKey(AccountProvider.MAL), saved.toString()).apply()
        ConnectedAccount(AccountProvider.MAL, cleanUsername)
    }

    override suspend fun pollLogin(session: LoginSession): LoginPollResult = withContext(Dispatchers.IO) {
        val response = runCatching {
            relayJson("${relayRoot(session.provider)}/check?lid=${session.id}&t=${System.currentTimeMillis()}")
        }.getOrElse { return@withContext LoginPollResult.Waiting }
        when (response.optInt("st", -1)) {
            0 -> LoginPollResult.Waiting
            1 -> LoginPollResult.Authorizing
            2 -> completeLogin(session.provider, response)
            else -> LoginPollResult.Waiting
        }
    }

    override suspend fun cancelLogin(session: LoginSession) {
        withContext(Dispatchers.IO) {
            runCatching {
                relayJson(
                    "${relayRoot(session.provider)}/check?lid=${session.id}&del=${System.currentTimeMillis()}"
                )
            }
        }
    }

    override suspend fun disconnect(provider: AccountProvider) {
        withContext(Dispatchers.IO) {
            preferences.edit().remove(preferenceKey(provider)).apply()
        }
    }

    override suspend fun loadTracking(anime: Anime): AnimeTrackingState = withContext(Dispatchers.IO) {
        val aniListToken = accessToken(AccountProvider.ANILIST)
        val malToken = accessToken(AccountProvider.MAL)
        val aniListId = anime.aniListId?.toIntOrNull()
        val malId = anime.malId?.toLongOrNull()
        val media = resolveAniListMedia(anime, aniListToken)
        val resolvedAniListId = media?.optInt("id")?.takeIf { it > 0 } ?: aniListId
        val resolvedMalId = media?.optInt("idMal")?.takeIf { it > 0 }?.toLong() ?: malId
        val aniListEntry = media?.optJSONObject("mediaListEntry")
        val malStatus = if (malToken != null && resolvedMalId != null) {
            runCatching {
                JSONObject(
                    getText(
                        "https://api.myanimelist.net/v2/anime/$resolvedMalId?fields=my_list_status",
                        mapOf(
                            "Authorization" to "Bearer $malToken",
                            "X-MAL-CLIENT-ID" to MAL_CLIENT_ID,
                            "Accept" to "application/json"
                        )
                    )
                ).optJSONObject("my_list_status")
            }.getOrNull()
        } else null

        AnimeTrackingState(
            aniList = ProviderTrackingState(
                connected = aniListToken != null,
                mediaId = resolvedAniListId?.toString(),
                entryId = aniListEntry?.optInt("id")?.takeIf { it > 0 }?.toString(),
                progress = aniListEntry?.optInt("progress"),
                status = aniListEntry?.optString("status")?.takeIf { it.isNotBlank() }
            ),
            mal = ProviderTrackingState(
                connected = malToken != null,
                mediaId = resolvedMalId?.toString(),
                entryId = malStatus?.let { resolvedMalId?.toString() },
                progress = malStatus?.optInt("num_episodes_watched"),
                status = malStatus?.optString("status")?.takeIf { it.isNotBlank() }
            )
        )
    }

    override suspend fun setTracking(anime: Anime, provider: AccountProvider, tracked: Boolean) {
        val status = when (provider) {
            AccountProvider.ANILIST -> if (tracked) "CURRENT" else null
            AccountProvider.MAL -> if (tracked) "watching" else null
        }
        setTrackingStatus(anime, provider, status)
    }

    override suspend fun setTrackingStatus(anime: Anime, provider: AccountProvider, status: String?) {
        withContext(Dispatchers.IO) {
            val token = accessToken(provider) ?: error(
                "Connect ${if (provider == AccountProvider.ANILIST) "AniList" else "MyAnimeList"} in Settings first"
            )
            val aniListId = anime.aniListId?.toIntOrNull()
            val malId = anime.malId?.toLongOrNull()
            val aniListToken = if (provider == AccountProvider.ANILIST) token
            else accessToken(AccountProvider.ANILIST)
            val media = resolveAniListMedia(anime, aniListToken)
            when (provider) {
                AccountProvider.ANILIST -> {
                    val mediaId = media?.optInt("id")?.takeIf { it > 0 } ?: aniListId
                        ?: error("Could not match this title to AniList")
                    if (status != null) {
                        val validStatus = status.uppercase()
                        check(validStatus in setOf("CURRENT", "PLANNING", "COMPLETED", "DROPPED", "PAUSED", "REPEATING")) {
                            "Unsupported AniList status"
                        }
                        val result = aniListRequest(
                            JSONObject().put(
                                "query",
                                "mutation(\$mediaId:Int,\$status:MediaListStatus!){SaveMediaListEntry(mediaId:\$mediaId,status:\$status){id mediaId status}}"
                            ).put("variables", JSONObject().put("mediaId", mediaId).put("status", validStatus)).toString(),
                            token
                        )
                        check(result.optJSONObject("data")?.optJSONObject("SaveMediaListEntry") != null) {
                            "AniList did not save this title"
                        }
                    } else {
                        val entryId = media?.optJSONObject("mediaListEntry")?.optInt("id")
                            ?.takeIf { it > 0 } ?: error("This title is not on AniList")
                        val result = aniListRequest(
                            JSONObject().put(
                                "query",
                                "mutation(\$id:Int){DeleteMediaListEntry(id:\$id){deleted}}"
                            ).put("variables", JSONObject().put("id", entryId)).toString(),
                            token
                        )
                        check(result.optJSONObject("data")?.optJSONObject("DeleteMediaListEntry") != null) {
                            "AniList did not remove this title"
                        }
                    }
                }
                AccountProvider.MAL -> {
                    val resolvedMalId = media?.optInt("idMal")?.takeIf { it > 0 }?.toLong() ?: malId
                        ?: error("Could not match this title to MyAnimeList")
                    val url = "https://api.myanimelist.net/v2/anime/$resolvedMalId/my_list_status"
                    val headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "X-MAL-CLIENT-ID" to MAL_CLIENT_ID
                    )
                    if (status != null) {
                        val validStatus = status.lowercase()
                        check(validStatus in setOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")) {
                            "Unsupported MyAnimeList status"
                        }
                        putForm(url, "status=${URLEncoder.encode(validStatus, "UTF-8")}", headers)
                    }
                    else deleteRequest(url, headers)
                }
            }
        }
    }

    override suspend fun loadAccountList(
        provider: AccountProvider,
        status: String,
        page: Int
    ): TrackedAnimePage = withContext(Dispatchers.IO) {
        val token = accessToken(provider) ?: error("Connect the account in Settings first")
        when (provider) {
            AccountProvider.ANILIST -> {
                val username = readAccount(provider)?.username ?: error("AniList account is unavailable")
                val body = JSONObject().put(
                    "query",
                    "query(\$userName:String!,\$page:Int!,\$perPage:Int!,\$status:MediaListStatus!){Page(page:\$page,perPage:\$perPage){pageInfo{hasNextPage} mediaList(userName:\$userName,type:ANIME,status:\$status,sort:UPDATED_TIME_DESC){progress status media{id idMal title{english romaji native} coverImage{large medium} bannerImage description episodes nextAiringEpisode{episode} format seasonYear averageScore isAdult}}}}"
                ).put(
                    "variables",
                    JSONObject()
                        .put("userName", username)
                        .put("page", page.coerceAtLeast(1))
                        .put("perPage", 25)
                        .put("status", status)
                ).toString()
                val root = aniListRequest(body, token)
                val pageData = root.optJSONObject("data")?.optJSONObject("Page")
                    ?: error("AniList did not return a list page")
                val pageInfo = pageData.optJSONObject("pageInfo")
                val mediaList = pageData.optJSONArray("mediaList") ?: JSONArray()
                TrackedAnimePage(
                    entries = (0 until mediaList.length()).mapNotNull { index ->
                        val entry = mediaList.optJSONObject(index) ?: return@mapNotNull null
                        val media = entry.optJSONObject("media") ?: return@mapNotNull null
                        if (media.optBoolean("isAdult")) return@mapNotNull null
                        val mediaId = media.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
                        val titles = media.optJSONObject("title")
                        val title = titles?.optString("english").orEmpty()
                            .ifBlank { titles?.optString("romaji").orEmpty() }
                            .ifBlank { titles?.optString("native").orEmpty() }
                            .ifBlank { mediaId.toString() }
                        val cover = media.optJSONObject("coverImage")
                        TrackedAnimeEntry(
                            anime = Anime(
                                id = "anilist:$mediaId|${Uri.encode(title)}",
                                title = title,
                                originalTitle = titles?.optString("native")?.takeIf { it.isNotBlank() },
                                posterUrl = cover?.optString("large")?.takeIf { it.isNotBlank() }
                                    ?: cover?.optString("medium")?.takeIf { it.isNotBlank() },
                                bannerUrl = media.optString("bannerImage").takeIf { it.isNotBlank() },
                                synopsis = media.optString("description").takeIf { it.isNotBlank() },
                                year = media.optInt("seasonYear").takeIf { it > 0 },
                                score = media.optInt("averageScore").takeIf { it > 0 }?.div(10f),
                                episodeCount = media.optInt("episodes").takeIf { it > 0 },
                                nextAiringEpisode = media.optJSONObject("nextAiringEpisode")
                                    ?.optInt("episode")?.takeIf { it > 0 },
                                format = media.optString("format").takeIf { it.isNotBlank() },
                                malId = media.optInt("idMal").takeIf { it > 0 }?.toString(),
                                aniListId = mediaId.toString(),
                                sourceIndex = SourceCatalog.GOTAKU_INDEX
                            ),
                            status = entry.optString("status"),
                            progress = entry.optInt("progress")
                        )
                    },
                    hasNextPage = pageInfo?.optBoolean("hasNextPage") == true
                )
            }
            AccountProvider.MAL -> {
                val url = Uri.parse("https://api.myanimelist.net/v2/users/@me/animelist")
                    .buildUpon()
                    .appendQueryParameter(
                        "fields",
                        "list_status,start_season,num_episodes,media_type,mean,alternative_titles"
                    )
                    .appendQueryParameter("status", status)
                    .appendQueryParameter("sort", "list_updated_at")
                    .appendQueryParameter("limit", "100")
                    .appendQueryParameter("offset", ((page.coerceAtLeast(1) - 1) * 100).toString())
                    .build().toString()
                val root = JSONObject(
                    getText(
                        url,
                        mapOf(
                            "Authorization" to "Bearer $token",
                            "X-MAL-CLIENT-ID" to MAL_CLIENT_ID,
                            "Accept" to "application/json"
                        )
                    )
                )
                val items = root.optJSONArray("data") ?: JSONArray()
                TrackedAnimePage(
                    entries = (0 until items.length()).mapNotNull { index ->
                        val item = items.optJSONObject(index) ?: return@mapNotNull null
                        val node = item.optJSONObject("node") ?: return@mapNotNull null
                        val listStatus = item.optJSONObject("list_status") ?: return@mapNotNull null
                        val malId = node.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
                        val title = node.optJSONObject("alternative_titles")?.optString("en")
                            ?.takeIf { it.isNotBlank() } ?: node.optString("title").ifBlank { malId.toString() }
                        val picture = node.optJSONObject("main_picture")
                        TrackedAnimeEntry(
                            anime = Anime(
                                id = "mal:$malId|${Uri.encode(title)}",
                                title = title,
                                posterUrl = picture?.optString("large")?.takeIf { it.isNotBlank() }
                                    ?: picture?.optString("medium")?.takeIf { it.isNotBlank() },
                                year = node.optJSONObject("start_season")?.optInt("year")?.takeIf { it > 0 },
                                score = node.optDouble("mean").takeIf { it > 0 }?.toFloat(),
                                episodeCount = node.optInt("num_episodes").takeIf { it > 0 },
                                format = node.optString("media_type").takeIf { it.isNotBlank() },
                                malId = malId.toString(),
                                sourceIndex = SourceCatalog.GOTAKU_INDEX
                            ),
                            status = listStatus.optString("status"),
                            progress = listStatus.optInt("num_episodes_watched")
                        )
                    },
                    hasNextPage = root.optJSONObject("paging")?.optString("next")?.isNotBlank() == true
                )
            }
        }
    }

    override suspend fun updateTrackedProgress(anime: Anime, episodeNumber: Int) {
        if (episodeNumber < 1) return
        withContext(Dispatchers.IO) {
            val aniListToken = accessToken(AccountProvider.ANILIST)
            val malToken = accessToken(AccountProvider.MAL)
            val mediaId = anime.aniListId?.toIntOrNull()
            val malId = anime.malId?.toLongOrNull()
            val media = resolveAniListMedia(anime, aniListToken)
            val resolvedAniListId = media?.optInt("id")?.takeIf { it > 0 } ?: mediaId
            val resolvedMalId = media?.optInt("idMal")?.takeIf { it > 0 }?.toLong() ?: malId
            val initialAniListEntry = media?.optJSONObject("mediaListEntry")
            if (aniListToken != null && resolvedAniListId != null) {
                syncAniListProgress(
                    animeKey = anime.id,
                    mediaId = resolvedAniListId,
                    token = aniListToken,
                    episode = episodeNumber,
                    initialEntry = initialAniListEntry
                )
            }
            if (malToken != null && resolvedMalId != null) {
                syncMalProgress(anime.id, resolvedMalId, malToken, episodeNumber)
            }
        }
    }

    private fun syncAniListProgress(
        animeKey: String,
        mediaId: Int,
        token: String,
        episode: Int,
        initialEntry: JSONObject? = null
    ) {
        val attemptKey = "${namespace.prefix}|anilist|$animeKey|$episode"
        val recorded = synchronized(progressAttempts) {
            if (progressAttempts.get(attemptKey) != null) false
            else {
                progressAttempts.put(attemptKey, true)
                true
            }
        }
        if (!recorded) return
        try {
            val entry = initialEntry ?: run {
                val lookup = postJson(
                    "https://graphql.anilist.co",
                    JSONObject().put(
                        "query",
                        "query(\$id:Int){Media(id:\$id,type:ANIME){mediaListEntry{id progress}}}"
                    ).put("variables", JSONObject().put("id", mediaId)).toString(),
                    mapOf("Authorization" to "Bearer $token", "Origin" to "https://anilist.co")
                )
                lookup.optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("mediaListEntry")
            } ?: return
            val entryId = entry.optInt("id")
            if (entryId <= 0 || entry.optInt("progress") >= episode) return
            postJson(
                "https://graphql.anilist.co",
                JSONObject().put(
                    "query",
                    "mutation(\$id:Int,\$progress:Int){SaveMediaListEntry(id:\$id,progress:\$progress){id progress}}"
                ).put("variables", JSONObject().put("id", entryId).put("progress", episode)).toString(),
                mapOf("Authorization" to "Bearer $token", "Origin" to "https://anilist.co")
            )
        } catch (_: Exception) {
            progressAttempts.remove(attemptKey)
        }
    }

    private fun fetchAniListMedia(id: Int?, idMal: Long?, token: String?): JSONObject? {
        val validMalId = idMal?.takeIf { it in 1L..Int.MAX_VALUE }?.toInt()
        val decls = mutableListOf<String>()
        val args = mutableListOf("type:ANIME")
        val variables = JSONObject()
        if (id != null && id > 0) {
            decls += "${'$'}id:Int"
            args += "id:${'$'}id"
            variables.put("id", id)
        } else if (validMalId != null) {
            decls += "${'$'}idMal:Int"
            args += "idMal:${'$'}idMal"
            variables.put("idMal", validMalId)
        } else {
            return null
        }
        val body = JSONObject().put(
            "query",
            "query(${decls.joinToString(",")}){Media(${args.joinToString(",")}){id idMal mediaListEntry{id progress status}}}"
        ).put("variables", variables).toString()
        return aniListRequest(body, token)
            .optJSONObject("data")?.optJSONObject("Media")
    }

    private fun resolveAniListMedia(anime: Anime, token: String?): JSONObject? {
        val aniListId = anime.aniListId?.toIntOrNull()
        val byAniListId = aniListId?.let {
            runCatching { fetchAniListMedia(it, null, token) }.getOrNull()
        }
        if (byAniListId != null) return byAniListId
        val malId = anime.malId?.toLongOrNull()
        val byMalId = malId?.let {
            runCatching { fetchAniListMedia(null, it, token) }.getOrNull()
        }
        if (byMalId != null) return byMalId
        return runCatching { searchAniListMediaByTitle(anime.title, token) }.getOrNull()
    }

    /** Mirrors the original app's tracking lookup: exact title match on released media only. */
    private fun searchAniListMediaByTitle(title: String, token: String?): JSONObject? {
        if (title.isBlank()) return null
        val query = """
            query(${'$'}search:String!) {
              Page(page: 1, perPage: 10) {
                media(sort: SEARCH_MATCH, search: ${'$'}search, isAdult: false, type: ANIME) {
                  id idMal status title { english romaji } episodes
                  mediaListEntry { id progress status }
                }
              }
            }
        """.trimIndent()
        val root = aniListRequest(
            JSONObject().put("query", query)
                .put("variables", JSONObject().put("search", title)).toString(),
            token
        )
        val media = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            ?: return null
        if (media.length() == 0) return null
        val exactMatch = (0 until media.length()).mapNotNull(media::optJSONObject).firstOrNull { item ->
            val status = item.optString("status")
            val eligibleStatus = status == "FINISHED" || status == "RELEASING" || status == "HIATUS"
            val titles = item.optJSONObject("title")
            val titleMatches = listOf("english", "romaji").any { key ->
                val candidate = titles?.optString(key).orEmpty()
                candidate.isNotBlank() && aniListTitlesMatch(title, candidate)
            }
            eligibleStatus && titleMatches
        }
        return exactMatch
    }

    private fun aniListTitlesMatch(query: String, candidate: String): Boolean =
        legacyAsciiTitle(query) == legacyAsciiTitle(candidate) ||
            legacySlugTitle(query) == legacySlugTitle(candidate)

    private fun legacyAsciiTitle(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(when (char) {
                '\u201C', '\u201D', '\u201E', '\u2033' -> '"'
                '\u2013', '\u2014', '\u2015' -> '-'
                '\u2017' -> '_'
                '\u201A' -> ','
                '\u2018', '\u2019', '\u201B', '\u2032' -> '\''
                else -> char
            })
        }
    }

    private fun legacySlugTitle(value: String): String = value.lowercase(java.util.Locale.ROOT)
        .replace(SLUG_NON_WORD_REGEX, " ")
        .trim()
        .replace(SLUG_MULTI_SPACE_REGEX, " ")

    private fun aniListRequest(body: String, token: String?): JSONObject {
        val headers = buildMap {
            put("Origin", "https://anilist.co")
            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
        }
        val response = postJson("https://graphql.anilist.co", body, headers)
        val errors = response.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val message = errors.optJSONObject(0)?.optString("message")
                ?.takeIf { it.isNotBlank() } ?: "AniList request failed"
            error(message)
        }
        return response
    }

    private fun syncMalProgress(animeKey: String, malId: Long, token: String, episode: Int) {
        val attemptKey = "${namespace.prefix}|mal|$animeKey|$episode"
        val recorded = synchronized(progressAttempts) {
            if (progressAttempts.get(attemptKey) != null) false
            else {
                progressAttempts.put(attemptKey, true)
                true
            }
        }
        if (!recorded) return
        try {
            val status = JSONObject(
                getText(
                    "https://api.myanimelist.net/v2/anime/$malId?fields=my_list_status",
                    mapOf(
                        "Authorization" to "Bearer $token",
                        "X-MAL-CLIENT-ID" to MAL_CLIENT_ID,
                        "Accept" to "application/json"
                    )
                )
            ).optJSONObject("my_list_status") ?: return
            if (status.optInt("num_episodes_watched") >= episode) return
            putForm(
                "https://api.myanimelist.net/v2/anime/$malId/my_list_status",
                "num_watched_episodes=${formEncode(episode.toString())}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "X-MAL-CLIENT-ID" to MAL_CLIENT_ID
                )
            )
        } catch (_: Exception) {
            progressAttempts.remove(attemptKey)
        }
    }

    private suspend fun completeLogin(provider: AccountProvider, response: JSONObject): LoginPollResult {
        val token = response.optString("access_token").ifBlank { response.optString("tk") }
        if (token.isBlank()) return LoginPollResult.Failed("The relay returned no access token")
        val expiresIn = response.optLong("expires_in").takeIf { it > 0 }
            ?: response.optLong("ex").takeIf { it > 0 }
            ?: return LoginPollResult.Failed("The relay returned no token expiry")
        var username = response.optString("user").takeIf { it.isNotBlank() }.orEmpty()
        if (username.isBlank()) {
            username = when (provider) {
                AccountProvider.ANILIST -> runCatching { anilistUsername(token) }.getOrNull().orEmpty()
                AccountProvider.MAL -> runCatching { malUsername(token) }.getOrNull().orEmpty()
            }
        }
        if (username.isBlank()) return LoginPollResult.Failed("Could not read the account username")

        val account = ConnectedAccount(provider, username)
        val saved = JSONObject()
            .put("access_token", token)
            .put("expires_at", System.currentTimeMillis() / 1000 + expiresIn)
            .put("username", username)
            .put("user_id", response.optString("uid").takeIf { it.isNotBlank() })
        preferences.edit().putString(preferenceKey(provider), saved.toString()).apply()
        return LoginPollResult.Complete(account)
    }

    private fun readAccount(provider: AccountProvider): ConnectedAccount? {
        val raw = preferences.getString(preferenceKey(provider), null) ?: return null
        val saved = runCatching { JSONObject(raw) }.getOrNull() ?: run {
            preferences.edit().remove(preferenceKey(provider)).apply()
            return null
        }
        if (saved.optString("access_token").isBlank() ||
            saved.optLong("expires_at") <= System.currentTimeMillis() / 1000
        ) {
            preferences.edit().remove(preferenceKey(provider)).apply()
            return null
        }
        val username = saved.optString("username").takeIf { it.isNotBlank() } ?: return null
        return ConnectedAccount(provider, username)
    }

    private fun anilistUsername(token: String): String? {
        val body = JSONObject().put("query", "query { Viewer { id name } }").toString()
        val response = postJson(
            "https://graphql.anilist.co",
            body,
            mapOf("Authorization" to "Bearer $token", "Origin" to "https://anilist.co")
        )
        return response.optJSONObject("data")?.optJSONObject("Viewer")
            ?.optString("name")?.takeIf { it.isNotBlank() }
    }

    private fun malUsername(token: String): String? {
        val response = getText(
            "https://api.myanimelist.net/v2/users/@me",
            mapOf(
                "Authorization" to "Bearer $token",
                "X-MAL-CLIENT-ID" to MAL_CLIENT_ID,
                "Accept" to "application/json"
            )
        )
        return JSONObject(response).optString("name").takeIf { it.isNotBlank() }
    }

    private fun relayJson(url: String): JSONObject = http.relayJson(url)

    private fun postJson(url: String, body: String, headers: Map<String, String>): JSONObject =
        http.postJson(url, body, headers)

    private fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        http.getText(url, headers)

    private fun putForm(url: String, body: String, headers: Map<String, String>) =
        http.putForm(url, body, headers)

    private fun deleteRequest(url: String, headers: Map<String, String>) =
        http.deleteRequest(url, headers)

    private fun accessToken(provider: AccountProvider): String? {
        val raw = preferences.getString(preferenceKey(provider), null) ?: return null
        val saved = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (saved.optLong("expires_at") <= System.currentTimeMillis() / 1000) {
            preferences.edit().remove(preferenceKey(provider)).apply()
            return null
        }
        return saved.optString("access_token").takeIf { it.isNotBlank() }
    }

    private fun formEncode(value: String): String = http.formEncode(value)

    private fun relayRoot(provider: AccountProvider): String =
        LEGACY_RELAY + if (provider == AccountProvider.ANILIST) "/anilist" else "/mal"

    private fun preferenceKey(provider: AccountProvider): String = when (provider) {
        AccountProvider.ANILIST -> namespace.key(KEY_ANILIST)
        AccountProvider.MAL -> namespace.key(KEY_MAL)
    }

    private companion object {
        const val LEGACY_RELAY = "https://animetv.amarullz.com"
        const val MAL_CLIENT_ID = "0e9466e5ec09684cc69da53f20b07af6"
        const val KEY_ANILIST = "anilist_auth"
        const val KEY_MAL = "mal_auth"
        val SLUG_NON_WORD_REGEX = Regex("[^A-Za-z0-9_]+")
        val SLUG_MULTI_SPACE_REGEX = Regex(" +")
    }
}
