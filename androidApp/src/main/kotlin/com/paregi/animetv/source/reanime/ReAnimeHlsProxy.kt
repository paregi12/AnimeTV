package com.paregi.animetv.source.reanime

import android.net.Uri
import com.paregi.animetv.AndroidNetworkResponse
import com.paregi.animetv.AndroidNetworkStack
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** ReAnime/FlixCloud HLS manifest and obfuscated segment adaptation for the local player proxy. */
internal class ReAnimeHlsProxy(
    private val networkStack: AndroidNetworkStack,
    private val port: () -> Int,
    private val mask: () -> ByteArray
) {
    fun servePlaylist(out: OutputStream, url: String, wPayload: String, root: Boolean) {
        val master = root || (url.contains("master.m3u8", ignoreCase = true) ||
            (url.contains("flixcloud.cc", ignoreCase = true) &&
                !url.contains("audio.m3u8", ignoreCase = true) && !url.contains("video.m3u8", ignoreCase = true)))
        val fetchUrl = if (master && !url.contains("enc-dec.app", ignoreCase = true)) {
            val encodedUrl = URLEncoder.encode(url, "UTF-8").replace("+", "%20")
            val encodedPayload = URLEncoder.encode(wPayload, "UTF-8").replace("+", "%20")
            "https://enc-dec.app/api/parse-flixcloud?url=" + encodedUrl + "&w_payload=" + encodedPayload
        } else url
        val connection = upstreamReAnime(fetchUrl)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            val text = connection.inputStream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val rewritten = rewriteReAnimeManifest(text, url, wPayload).toByteArray(StandardCharsets.UTF_8)
            response(out, 200, "OK", "application/vnd.apple.mpegurl", rewritten)
        } finally {
            connection.close()
        }
    }

    private fun rewriteReAnimeManifest(text: String, parentUrl: String, wPayload: String): String {
        val parent = URL(parentUrl)
        val uriPattern = Regex("URI=\"([^\"]+)\"")
        return text.lineSequence().joinToString("\n", postfix = "\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith("#EXT-X-STREAM-INF") -> {
                    val peak = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                    val avg = Regex("AVERAGE-BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                    if (peak != null && peak < 100_000L) {
                        val fixed = if (avg != null && avg > 100_000L) avg else peak * 1000L
                        Regex("BANDWIDTH=\\d+").replaceFirst(line, "BANDWIDTH=" + fixed)
                    } else line
                }
                !trimmed.startsWith("#") -> {
                    val resolved = ensureReAnimeToken(URL(parent, trimmed).toString(), parentUrl)
                    reAnimeProxyUrl(resolved, wPayload, isPlaylistUrl(resolved))
                }
                uriPattern.containsMatchIn(line) -> uriPattern.replace(line) { match ->
                    val resolved = ensureReAnimeToken(URL(parent, match.groupValues[1]).toString(), parentUrl)
                    "URI=\"" + reAnimeProxyUrl(resolved, wPayload, isPlaylistUrl(resolved)) + "\""
                }
                else -> line
            }
        }
    }

    private fun reAnimeProxyUrl(url: String, wPayload: String, playlist: Boolean): String {
        val query = StringBuilder("url=").append(URLEncoder.encode(url, "UTF-8"))
            .append("&re=1")
        if (playlist) query.append("&m3u8=1")
        if (wPayload.isNotBlank()) query.append("&wp=").append(URLEncoder.encode(wPayload, "UTF-8"))
        return "http://127.0.0.1:" + port() + "/proxy?" + query
    }

    private fun isPlaylistUrl(url: String): Boolean {
        val path = runCatching { URL(url).path.lowercase() }.getOrDefault(url.lowercase())
        return path.endsWith(".m3u8") || path.contains("playlist")
    }

    private fun ensureReAnimeToken(url: String, parentUrl: String): String = runCatching {
        val uri = Uri.parse(url)
        if (uri.getQueryParameter("token") != null) return@runCatching url
        var current = parentUrl
        repeat(3) {
            val parent = Uri.parse(current)
            val token = parent.getQueryParameter("token")
            if (token != null) return@runCatching uri.buildUpon().appendQueryParameter("token", token).build().toString()
            current = parent.getQueryParameter("url") ?: return@runCatching url
        }
        url
    }.getOrDefault(url)

    fun serveSegment(out: OutputStream, url: String, rangeStart: Long) {
        val connection = upstreamReAnime(url, rangeStart)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            val stream = BufferedInputStream(connection.inputStream)
            var headerBytes = 0
            if (rangeStart == 0L) {
                stream.mark(16)
                val probe = ByteArray(13)
                val read = stream.read(probe)
                stream.reset()
                if (read > 0) headerBytes = detectReAnimeHeader(probe.copyOf(read))
            }
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1)
            val outLength = if (contentLength >= 0) (contentLength - headerBytes).coerceAtLeast(0) else -1L
            val headers = StringBuilder("HTTP/1.1 ")
                .append(if (status == 206) "206 Partial Content" else "200 OK").append("\r\n")
                .append("Content-Type: ").append(if (headerBytes > 0) "video/mp2t" else connection.getHeaderField("Content-Type") ?: "application/octet-stream").append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
            if (outLength >= 0) headers.append("Content-Length: ").append(outLength).append("\r\n")
            connection.getHeaderField("Content-Range")?.let { headers.append("Content-Range: ").append(it).append("\r\n") }
            headers.append("Access-Control-Allow-Origin: *\r\n\r\n")
            out.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
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
                if (headerBytes > 0) repeat(count) { index ->
                    buffer[index] = (buffer[index].toInt() xor mask[maskIndex++ % mask.size].toInt()).toByte()
                }
                out.write(buffer, 0, count)
            }
            out.flush()
        } finally {
            connection.close()
        }
    }

    private fun detectReAnimeHeader(data: ByteArray): Int {
        if (data.size >= 12 && data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte() && data[8] == 0x57.toByte() &&
            data[9] == 0x45.toByte() && data[10] == 0x42.toByte() && data[11] == 0x50.toByte()) return 12
        if (data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() && data[4] == 0x0D.toByte() &&
            data[5] == 0x0A.toByte() && data[6] == 0x1A.toByte() && data[7] == 0x0A.toByte()) return 8
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
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
    }
}
