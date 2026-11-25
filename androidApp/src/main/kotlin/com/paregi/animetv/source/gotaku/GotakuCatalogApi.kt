package com.paregi.animetv.source.gotaku

import android.net.Uri
import com.paregi.animetv.AndroidGotakuCrypto
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SearchFilters
import com.paregi.animetv.domain.SearchPage
import com.paregi.animetv.source.CatalogSource
import com.paregi.animetv.source.SourceCatalogApi
import com.paregi.animetv.source.SourceRequestBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Gotaku catalog endpoints, filters, encrypted responses, and pagination. */
internal class GotakuCatalogApi(
    private val requestBytes: SourceRequestBytes,
    private val sync: GotakuSync? = null
) : SourceCatalogApi {
    override suspend fun search(filters: SearchFilters, page: Int, source: CatalogSource): SearchPage {
        val safePage = page.coerceAtLeast(1)
        val builder = Uri.parse(source.root + "/api/v1/titles").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("page", safePage.toString())
            .appendQueryParameter("sort", filters.sort)
        val refererBuilder = Uri.parse(source.root + "/browse").buildUpon()
            .appendQueryParameter("sort", filters.sort)
        if (filters.query.isNotBlank()) {
            val trimmed = filters.query.trim()
            builder.appendQueryParameter("q", trimmed)
            refererBuilder.appendQueryParameter("q", trimmed)
        }
        filters.year?.let {
            builder.appendQueryParameter("year", it.toString())
            refererBuilder.appendQueryParameter("year", it.toString())
        }
        filters.season?.let {
            val upper = it.uppercase(Locale.ROOT)
            builder.appendQueryParameter("season", upper)
            refererBuilder.appendQueryParameter("season", upper)
        }
        filters.types.forEach {
            builder.appendQueryParameter("type", it)
            refererBuilder.appendQueryParameter("type", it)
        }
        filters.genres.mapNotNull(GotakuSource::genreId).forEach {
            builder.appendQueryParameter("genre[]", it)
            refererBuilder.appendQueryParameter("genre", it)
        }
        filters.status.forEach {
            builder.appendQueryParameter("status", it)
            refererBuilder.appendQueryParameter("status", it)
        }
        if (safePage > 1) refererBuilder.appendQueryParameter("page", safePage.toString())

        val root = requestPlainOrDecrypt(builder.build().toString(), refererBuilder.build().toString())
        val items = root.optJSONArray("data") ?: JSONArray()
        val meta = root.optJSONObject("meta")
        val hasNextPage = when {
            meta != null && meta.has("has_more") && !meta.isNull("has_more") -> meta.optBoolean("has_more")
            else -> root.optJSONObject("pagination")?.optInt("last_page", page + 1)?.let { page < it }
                ?: (items.length() >= PAGE_SIZE)
        }
        return SearchPage(
            results = items.toAnimeList(),
            page = page,
            hasNextPage = hasNextPage
        )
    }

    override suspend fun fetchTitles(sort: String, page: Int, source: CatalogSource): List<Anime> {
        val url = Uri.parse(source.root + "/api/v1/titles").buildUpon()
            .appendQueryParameter("limit", PAGE_SIZE.toString())
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("sort", sort)
            .build().toString()
        val referer = Uri.parse(source.root + "/browse").buildUpon()
            .appendQueryParameter("sort", sort)
            .build().toString()
        return requestPlainOrDecrypt(url, referer).optJSONArray("data").toAnimeList()
    }

    override suspend fun loadAnime(sourceId: String, source: CatalogSource): Anime? {
        val encodedId = Uri.encode(sourceId)
        val url = source.root + "/api/v1/titles/" + encodedId
        val referer = source.root + "/title/" + encodedId
        val root = requestPlainOrDecrypt(url, referer)
        val data = root.optJSONObject("data") ?: root
        return GotakuSource.anime(data.optJSONObject("title") ?: data, sourceId)
    }

    override suspend fun loadEpisodes(animeId: String, source: CatalogSource): List<Episode> {
        val encodedId = Uri.encode(animeId)
        val url = source.root + "/api/v1/titles/" + encodedId + "/episodes"
        val referer = source.root + "/title/" + encodedId
        val root = decryptCall(url, source, referer)
        return GotakuSource.episodes(root.optJSONArray("data"), animeId)
    }

    override suspend fun json(url: String, source: CatalogSource): JSONObject {
        val referer = refererFor(url, source)
        return if (isEncryptedUrl(url)) {
            decryptCall(url, source, referer)
        } else {
            requestPlainOrDecrypt(url, referer)
        }
    }

    suspend fun decryptCall(
        url: String,
        source: CatalogSource,
        referer: String = source.root + "/"
    ): JSONObject = decryptCallInternal(url, source, referer, isRetry = false, overrideCreds = null)

    private suspend fun decryptCallInternal(
        url: String,
        source: CatalogSource,
        referer: String,
        isRetry: Boolean,
        overrideCreds: GotakuSync.Credentials?
    ): JSONObject {
        val creds = overrideCreds ?: sync?.current(source.root) ?: GotakuSync.Credentials(
            version = Uri.parse(url).getQueryParameter("k")?.takeIf(String::isNotBlank)
                ?: AndroidGotakuCrypto.DEFAULT_API_VERSION,
            seed = AndroidGotakuCrypto.DEFAULT_API_SEED,
            info = AndroidGotakuCrypto.DEFAULT_HKDF_INFO
        )
        val finalUrl = withApiVersion(url, creds.version)
        val bytes = try {
            requestBytes(finalUrl, null, null, apiHeaders(referer))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val is426 = error.message?.contains("HTTP 426") == true
            if (!isRetry && is426) {
                val refreshed = withContext(Dispatchers.IO) {
                    sync?.sync(source.root, force = true, replaceStored = true)
                }
                if (refreshed != null && refreshed != creds) {
                    return decryptCallInternal(url, source, referer, isRetry = true, overrideCreds = refreshed)
                }
            }
            throw error
        }
        val plain = AndroidGotakuCrypto.decryptApi(bytes, creds.version, creds.seed, creds.info)
            ?: bytes.toString(StandardCharsets.UTF_8).takeIf { it.trimStart().startsWith("{") }
        if (plain != null) return JSONObject(plain)
        if (!isRetry) {
            val refreshed = withContext(Dispatchers.IO) {
                sync?.sync(source.root, force = true, replaceStored = true)
            }
            if (refreshed != null && refreshed != creds) {
                return decryptCallInternal(url, source, referer, isRetry = true, overrideCreds = refreshed)
            }
        }
        error("Could not decrypt source response")
    }

    private suspend fun requestPlainOrDecrypt(url: String, referer: String): JSONObject {
        val bytes = requestBytes(url, null, null, apiHeaders(referer))
        val key = Uri.parse(url).getQueryParameter("k") ?: AndroidGotakuCrypto.DEFAULT_API_VERSION
        val plain = bytes.toString(StandardCharsets.UTF_8).takeIf { it.trimStart().startsWith("{") }
            ?: AndroidGotakuCrypto.decryptApi(bytes, key)
            ?: error("Could not decrypt source response")
        return JSONObject(plain)
    }

    private fun apiHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to referer,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin"
    )

    private fun withApiVersion(url: String, version: String): String {
        val parsed = Uri.parse(url)
        val builder = parsed.buildUpon().clearQuery()
        var replaced = false
        for (name in parsed.queryParameterNames) {
            if (name == "k") {
                if (!replaced) {
                    builder.appendQueryParameter("k", version)
                    replaced = true
                }
            } else {
                parsed.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        if (!replaced) builder.appendQueryParameter("k", version)
        return builder.build().toString()
    }

    private fun isEncryptedUrl(url: String): Boolean =
        url.contains("/episodes/") || Uri.parse(url).getQueryParameter("k") != null

    private fun refererFor(url: String, source: CatalogSource): String {
        val path = Uri.parse(url).path.orEmpty()
        if (path.startsWith("/api/v1/titles/")) {
            val slug = path.removePrefix("/api/v1/titles/").substringBefore('/')
            if (slug.isNotBlank()) return "${source.root}/title/${Uri.encode(slug)}"
        }
        if (path == "/api/v1/titles") return "${source.root}/browse"
        return "${source.root}/"
    }

    private fun JSONArray?.toAnimeList(): List<Anime> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { GotakuSource.anime(it) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 28
    }
}
