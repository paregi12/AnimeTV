package com.paregi.animetv.source.shared

import android.net.Uri
import com.paregi.animetv.AndroidNetworkStack
import com.paregi.animetv.source.CatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Applies provider request headers and adapts the Android network stack for source APIs. */
internal class AndroidSourceHttpClient(
    private val networkStack: AndroidNetworkStack,
    private val sourceSnapshot: () -> CatalogSource
) {
    suspend fun requestJson(
        url: String,
        body: String? = null,
        referer: String,
        origin: String? = null
    ): JSONObject {
        val headers = mutableMapOf(
            "Accept" to "application/json, text/plain, */*",
            "Referer" to referer
        )
        origin?.let { headers["Origin"] = it }
        return JSONObject(requestBytes(url, body, "application/json", headers).toString(StandardCharsets.UTF_8))
    }

    suspend fun request(
        url: String,
        body: String? = null,
        contentType: String? = null,
        accept: String = "*/*",
        source: CatalogSource = sourceSnapshot(),
        referer: String = if (source.isReAnime) source.root + "/home" else source.root + "/",
        origin: String? = null
    ): String = requestBytes(
        url,
        body,
        contentType,
        buildMap {
            put("Accept", accept)
            put("Referer", referer)
            origin?.let { put("Origin", it) }
        }
    ).toString(StandardCharsets.UTF_8)

    suspend fun requestBytes(
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap()
    ): ByteArray = withContext(Dispatchers.IO) {
        val requestHeaders = buildMap {
            putAll(headers)
            put("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36")
            val currentSource = sourceSnapshot()
            val host = Uri.parse(url).host?.lowercase(Locale.ROOT)
            val isCurrentSource = host != null && (host == currentSource.domain.lowercase(Locale.ROOT) || host == "gotaku.to" || host == "reanime.to")
            if (isCurrentSource) {
                val providerRoot = "https://${currentSource.domain}"
                put("X-Org-Prox", providerRoot)
                put("X-Ref-Prox", providerRoot + if (currentSource.isReAnime) "/home" else "/")
            }
            if (body != null) put("Content-Type", contentType ?: "application/json")
        }
        val response = networkStack.execute(
            url = url,
            method = if (body == null) "GET" else "POST",
            headers = requestHeaders,
            body = body?.toByteArray(StandardCharsets.UTF_8),
            connectTimeoutMs = 12_000,
            readTimeoutMs = 18_000
        )
        try {
            val status = response.responseCode
            val stream = if (status in 200..299) response.inputStream else response.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            check(status in 200..299) { "Request failed with HTTP " + status }
            bytes
        } finally {
            response.close()
        }
    }
}
