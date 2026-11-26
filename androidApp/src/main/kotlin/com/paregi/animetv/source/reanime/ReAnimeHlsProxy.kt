package com.paregi.animetv.source.reanime

import android.net.Uri
import android.util.Log
import com.paregi.animetv.AndroidNetworkResponse
import com.paregi.animetv.AndroidNetworkStack
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** ReAnime/FlixCloud HLS manifest and obfuscated segment adaptation for the local player proxy. */
internal class ReAnimeHlsProxy(
    private val networkStack: AndroidNetworkStack,
    private val port: () -> Int,
    private val mask: () -> ByteArray
) {
    fun servePlaylist(out: OutputStream, url: String, wPayload: String, root: Boolean) {
        val master = root || isMasterPlaylist(url)
        val fetchUrl = if (master && !url.contains("enc-dec.app", ignoreCase = true)) {
            val encodedUrl = URLEncoder.encode(url, "UTF-8").replace("+", "%20")
            val encodedPayload = URLEncoder.encode(wPayload, "UTF-8").replace("+", "%20")
            "https://enc-dec.app/api/parse-flixcloud?url=" + encodedUrl + "&w_payload=" + encodedPayload
        } else url

        var text: String? = null
        var attempt = 0
        while (text == null) {
            var connection: AndroidNetworkResponse? = null
            try {
                connection = upstreamReAnime(fetchUrl)
                val status = connection.responseCode
                if (status !in 200..299) {
                    Log.w(TAG, "Playlist upstream HTTP $status: $fetchUrl")
                    return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
                }
                text = connection.inputStream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            } catch (e: SocketTimeoutException) {
                attempt++
                if (attempt >= MAX_PLAYLIST_ATTEMPTS) {
                    Log.w(TAG, "Manifest timeout after $attempt attempts", e)
                    return response(
                        out,
                        503,
                        "Service Unavailable",
                        "text/plain",
                        (e.message ?: "Upstream timeout").toByteArray(StandardCharsets.UTF_8)
                    )
                }
                Log.w(TAG, "Manifest timeout, retrying... (Attempt $attempt/$MAX_PLAYLIST_ATTEMPTS)")
            } finally {
                connection?.close()
            }
        }

        val parentUrl = if (url.contains("enc-dec.app", ignoreCase = true)) {
            Uri.parse(url).getQueryParameter("url") ?: url
        } else url
        val rewritten = rewriteReAnimeManifest(text, parentUrl, wPayload).toByteArray(StandardCharsets.UTF_8)
        response(out, 200, "OK", "application/vnd.apple.mpegurl", rewritten)
    }

    fun isManifestRequest(url: String): Boolean = isPlaylistUrl(url)

    private fun rewriteReAnimeManifest(text: String, parentUrl: String, wPayload: String): String {
        val parent = URL(parentUrl)
        val master = text.contains("#EXT-X-STREAM-INF")
        return text.lineSequence().joinToString("\n", postfix = "\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                !trimmed.startsWith("#") -> {
                    val resolved = ensureReAnimeToken(URL(parent, trimmed).toString(), parentUrl)
                    reAnimeProxyUrl(resolved, wPayload, master || isPlaylistUrl(resolved))
                }
                else -> {
                    val cleanedLine = if (trimmed.startsWith("#EXT-X-STREAM-INF")) fixBandwidth(line) else line
                    if (URI_REGEX.containsMatchIn(cleanedLine)) {
                        val isKeyLine = trimmed.startsWith("#EXT-X-KEY") ||
                            trimmed.startsWith("#EXT-X-SESSION-KEY")
                        val forcePlaylist = trimmed.startsWith("#EXT-X-MEDIA") ||
                            trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF")
                        URI_REGEX.replace(cleanedLine) { match ->
                            val rawUri = match.groupValues[1]
                            if (rawUri.isEmpty()) {
                                match.value
                            } else {
                                val resolved = ensureReAnimeToken(URL(parent, rawUri).toString(), parentUrl)
                                val playlist = !isKeyLine && (forcePlaylist || isPlaylistUrl(resolved))
                                "URI=\"" + reAnimeProxyUrl(resolved, wPayload, playlist, plain = isKeyLine) + "\""
                            }
                        }
                    } else {
                        cleanedLine
                    }
                }
            }
        }
    }

    private fun fixBandwidth(line: String): String {
        val peak = BANDWIDTH_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return line
        if (peak >= 100_000L) return line
        val avg = AVERAGE_BANDWIDTH_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull()
        val fixed = if (avg != null && avg > 100_000L) avg else peak * 1000L
        return BANDWIDTH_REGEX.replaceFirst(line, "BANDWIDTH=$fixed")
    }

    private fun reAnimeProxyUrl(
        url: String,
        wPayload: String,
        playlist: Boolean,
        plain: Boolean = false
    ): String {
        val query = StringBuilder("url=").append(URLEncoder.encode(url, "UTF-8"))
            .append("&re=1")
        if (playlist) query.append("&m3u8=1")
        if (plain) query.append("&plain=1")
        if (wPayload.isNotBlank()) query.append("&wp=").append(URLEncoder.encode(wPayload, "UTF-8"))
        val path = if (playlist) "/proxy/playlist.m3u8?" else "/proxy?"
        return "http://127.0.0.1:" + port() + path + query
    }

    private fun isMasterPlaylist(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return !lower.contains("enc-dec.app") &&
            lower.contains(".m3u8") &&
            (lower.contains("master.m3u8") ||
                (lower.contains("flixcloud.cc") && !lower.contains("audio.m3u8") && !lower.contains("video.m3u8")))
    }

    private fun isPlaylistUrl(url: String, depth: Int = 0): Boolean {
        val path = runCatching { URL(url).path.lowercase(Locale.ROOT) }.getOrDefault(url.lowercase(Locale.ROOT))
        if (path.endsWith(".m3u8") || path.contains("playlist") || isMasterPlaylist(url)) return true
        // Proxy-wrapped manifests (e.g. enc-dec.app/api/parse-flixcloud?url=...) carry the real playlist path in ?url=.
        if (depth < 2) {
            val nested = runCatching { Uri.parse(url).getQueryParameter("url") }.getOrNull()
            if (!nested.isNullOrBlank()) return isPlaylistUrl(nested, depth + 1)
        }
        return false
    }

    private fun ensureReAnimeToken(url: String, parentUrl: String): String = runCatching {
        val uri = Uri.parse(url)
        if (!uri.getQueryParameter("token").isNullOrEmpty()) return@runCatching url
        var current = parentUrl
        repeat(3) {
            val parent = Uri.parse(current)
            val token = parent.getQueryParameter("token")
            if (!token.isNullOrEmpty()) {
                return@runCatching uri.buildUpon().appendQueryParameter("token", token).build().toString()
            }
            current = parent.getQueryParameter("url") ?: return@runCatching url
        }
        url
    }.getOrDefault(url)

    fun serveSegment(out: OutputStream, url: String, rangeStart: Long, plain: Boolean = false) {
        var headersWritten = false
        var connection: AndroidNetworkResponse? = null
        try {
            val upstreamRange = if (plain) rangeStart else 0L
            connection = upstreamReAnime(url, upstreamRange)
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "Segment upstream HTTP $status: $url")
                return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            }
            val input = connection.inputStream
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val stream = BufferedInputStream(input)
            var headerBytes = 0
            if (!plain) {
                stream.mark(16)
                val probe = ByteArray(13)
                val read = stream.read(probe)
                stream.reset()
                if (read > 0) headerBytes = detectReAnimeHeader(probe.copyOf(read))
            }
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1)
            val outLength = if (contentLength >= 0) (contentLength - headerBytes).coerceAtLeast(0) else -1L
            val headers = StringBuilder("HTTP/1.1 ")
                .append(if (status == 206 && upstreamRange > 0L) "206 Partial Content" else "200 OK").append("\r\n")
                .append("Content-Type: ").append(
                    if (headerBytes > 0) "video/mp2t"
                    else connection.getHeaderField("Content-Type") ?: "application/octet-stream"
                ).append("\r\n")
            if (plain) headers.append("Accept-Ranges: bytes\r\n")
            if (outLength >= 0) headers.append("Content-Length: ").append(outLength).append("\r\n")
            if (plain) {
                connection.getHeaderField("Content-Range")?.let { headers.append("Content-Range: ").append(it).append("\r\n") }
            }
            headers.append("Access-Control-Allow-Origin: *\r\n\r\n")
            out.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
            headersWritten = true
            var skipped = 0
            while (skipped < headerBytes) {
                val count = stream.skip((headerBytes - skipped).toLong()).toInt()
                if (count <= 0) { if (stream.read() < 0) break else skipped++ } else skipped += count
            }
            val mask = mask()
            val buffer = ByteArray(8192)
            var maskIndex = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (!plain && headerBytes > 0 && mask.isNotEmpty()) {
                    repeat(count) { index ->
                        buffer[index] = (buffer[index].toInt() xor mask[maskIndex++ % mask.size].toInt()).toByte()
                    }
                }
                out.write(buffer, 0, count)
            }
            out.flush()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Segment upstream timeout", e)
            if (!headersWritten) {
                response(
                    out,
                    503,
                    "Service Unavailable",
                    "text/plain",
                    (e.message ?: "Upstream timeout").toByteArray(StandardCharsets.UTF_8)
                )
            }
        } finally {
            connection?.close()
        }
    }

    private fun detectReAnimeHeader(data: ByteArray): Int {
        if (data.size >= 12 && data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte() && data[8] == 0x57.toByte() &&
            data[9] == 0x45.toByte() && data[10] == 0x42.toByte() && data[11] == 0x50.toByte()
        ) return 12
        if (data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() && data[4] == 0x0D.toByte() &&
            data[5] == 0x0A.toByte() && data[6] == 0x1A.toByte() && data[7] == 0x0A.toByte()
        ) return 8
        return 0
    }

    private fun upstreamReAnime(url: String, rangeStart: Long = 0): AndroidNetworkResponse {
        val headers = buildMap {
            put("Accept", "*/*")
            put("User-Agent", USER_AGENT)
            if (url.contains("enc-dec.app", ignoreCase = true)) {
                put("Origin", "https://enc-dec.app")
                put("Referer", "https://enc-dec.app/")
            } else {
                put("Origin", "https://flixcloud.cc")
                put("Referer", "https://flixcloud.cc/")
                put("Sec-Fetch-Dest", "empty")
                put("Sec-Fetch-Mode", "cors")
                put("Sec-Fetch-Site", "same-site")
            }
            if (rangeStart > 0) put("Range", "bytes=" + rangeStart + "-")
        }
        return networkStack.execute(url, headers = headers, connectTimeoutMs = 15_000, readTimeoutMs = 30_000)
    }

    private fun response(out: OutputStream, status: Int, label: String, type: String, body: ByteArray) {
        val header = "HTTP/1.1 " + status + " " + label + "\r\nContent-Type: " + type +
            "\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: " + body.size + "\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private companion object {
        const val TAG = "ReAnimeHlsProxy"
        const val MAX_PLAYLIST_ATTEMPTS = 3
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
        val URI_REGEX = Regex("URI=\"([^\"]*)\"")
        val BANDWIDTH_REGEX = Regex("""(?<![A-Z-])BANDWIDTH=(\d+)""")
        val AVERAGE_BANDWIDTH_REGEX = Regex("""AVERAGE-BANDWIDTH=(\d+)""")
    }
}
