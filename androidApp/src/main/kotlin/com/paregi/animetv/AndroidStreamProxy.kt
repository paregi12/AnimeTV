package com.paregi.animetv

import android.net.Uri
import android.util.Log
import com.paregi.animetv.source.reanime.ReAnimeHlsProxy
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Local Android HLS adapter for Gotaku's encrypted playlists and segment prefixes. */
class AndroidStreamProxy(private val networkStack: AndroidNetworkStack) : AutoCloseable {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var workers: ExecutorService? = null
    @Volatile private var fullPathCounter: Boolean? = null
    @Volatile private var port: Int = 0
    private val reAnimeMask = AtomicReference(byteArrayOf(
        157.toByte(), 42, 241.toByte(), 71, 179.toByte(), 142.toByte(), 92, 112,
        166.toByte(), 25, 228.toByte(), 59, 216.toByte(), 98, 15, 197.toByte()
    ))
    private val reAnimeProxy = ReAnimeHlsProxy(networkStack, { port }, { reAnimeMask.get() })

    @Synchronized
    fun start() {
        if (server?.isClosed == false) return
        val listener = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        server = listener
        port = listener.localPort
        val pool = Executors.newCachedThreadPool()
        workers = pool
        pool.execute {
            while (!listener.isClosed) {
                try {
                    val socket = listener.accept()
                    pool.execute { handle(socket) }
                } catch (error: Exception) {
                    if (!listener.isClosed) Log.w(TAG, "Proxy accept failed", error)
                }
            }
        }
    }

    fun createPlaylistUrl(
        source: String,
        key: String?,
        segmentBytes: Int,
        referer: String? = null
    ): String {
        start()
        return proxyUrl(source, key, segmentBytes, playlist = true, referer = referer)
    }

    fun createSubtitleUrl(source: String, referer: String? = null): String {
        start()
        val query = StringBuilder("url=").append(URLEncoder.encode(source, "UTF-8")).append("&sub=1")
        if (referer != null) query.append("&ref=").append(URLEncoder.encode(referer, "UTF-8"))
        return "http://127.0.0.1:" + port + "/proxy/subtitles.vtt?" + query
    }

    fun updateReAnimeMask(mask: ByteArray) {
        if (mask.isNotEmpty()) reAnimeMask.set(mask.copyOf())
    }

    fun createReAnimePlaylistUrl(source: String, wPayload: String): String {
        start()
        val query = StringBuilder("url=").append(URLEncoder.encode(source, "UTF-8"))
            .append("&re=1&m3u8=1")
        if (wPayload.isNotBlank()) query.append("&wp=").append(URLEncoder.encode(wPayload, "UTF-8"))
        return "http://127.0.0.1:" + port + "/manifest.m3u8?" + query
    }

    @Synchronized
    override fun close() {
        runCatching { server?.close() }
        server = null
        port = 0
        workers?.shutdownNow()
        workers = null
        fullPathCounter = null
    }

    private fun handle(socket: java.net.Socket) {
        socket.use { client ->
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                val parts = (reader.readLine() ?: return).split(' ')
                if (parts.size < 2) return
                var rangeStart = 0L
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("range:", ignoreCase = true)) {
                        rangeStart = line.substringAfter(':').trim().removePrefix("bytes=")
                            .substringBefore('-').toLongOrNull() ?: 0L
                    }
                }
                val local = Uri.parse("http://127.0.0.1" + parts[1])
                val upstreamUrl = local.getQueryParameter("url") ?: run {
                    Log.w(TAG, "Missing url query in proxy request: ${parts[1]}")
                    return badRequest(client.getOutputStream())
                }
                if (!upstreamUrl.startsWith("http://") && !upstreamUrl.startsWith("https://")) {
                    Log.w(TAG, "Unsupported upstream scheme in proxy request: ${parts[1]}")
                    return badRequest(client.getOutputStream())
                }
                val key = local.getQueryParameter("key")
                val segmentBytes = local.getQueryParameter("sb")?.toIntOrNull() ?: 0
                val referer = local.getQueryParameter("ref")
                when {
                    local.getQueryParameter("re") == "1" && (
                        local.getQueryParameter("m3u8") == "1" ||
                            parts[1].startsWith("/manifest") ||
                            reAnimeProxy.isManifestRequest(upstreamUrl)
                        ) ->
                        reAnimeProxy.servePlaylist(
                            client.getOutputStream(),
                            upstreamUrl,
                            local.getQueryParameter("wp").orEmpty(),
                            root = parts[1].startsWith("/manifest")
                        )
                    local.getQueryParameter("re") == "1" ->
                        reAnimeProxy.serveSegment(
                            client.getOutputStream(),
                            upstreamUrl,
                            rangeStart,
                            plain = local.getQueryParameter("plain") == "1"
                        )
                    local.getQueryParameter("sub") == "1" ->
                        serveSubtitle(client.getOutputStream(), upstreamUrl, referer)
                    local.getQueryParameter("m3u8") == "1" ->
                        servePlaylist(client.getOutputStream(), upstreamUrl, key, segmentBytes, referer)
                    local.getQueryParameter("plain") == "1" ->
                        servePassthrough(client.getOutputStream(), upstreamUrl, rangeStart, referer)
                    else ->
                        serveSegment(client.getOutputStream(), upstreamUrl, key, segmentBytes, rangeStart, referer)
                }
            } catch (_: SocketTimeoutException) {
                Log.w(TAG, "Proxy upstream timeout")
                runCatching {
                    response(
                        client.getOutputStream(),
                        503,
                        "Service Unavailable",
                        "text/plain",
                        "Upstream timeout".toByteArray(StandardCharsets.UTF_8)
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "Proxy request failed", error)
                runCatching { response(client.getOutputStream(), 502, "Bad Gateway", "text/plain", ByteArray(0)) }
            }
        }
    }

    private fun servePlaylist(
        out: OutputStream,
        url: String,
        key: String?,
        segmentBytes: Int,
        referer: String?
    ) {
        var body: ByteArray? = null
        var attempt = 0
        while (body == null) {
            var connection: AndroidNetworkResponse? = null
            try {
                connection = upstream(url, referer = referer)
                val status = connection.responseCode
                if (status !in 200..299) {
                    Log.w(TAG, "Playlist upstream HTTP $status: $url")
                    return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
                }
                body = connection.inputStream?.use { it.readBytes() }
                    ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            } catch (error: SocketTimeoutException) {
                attempt++
                if (attempt >= 3) throw error
                Log.w(TAG, "Manifest timeout, retrying ($attempt/3)")
            } finally {
                connection?.close()
            }
        }

        val manifest = if (!key.isNullOrBlank()) {
            try {
                if (body.size < 28) error("Encrypted playlist too short (${body.size})")
                val seed = AndroidGotakuCrypto.decodeUrlSafe(key) ?: error("Bad playlist key")
                val uri = Uri.parse(url)
                val token = uri.getQueryParameter("token") ?: uri.pathSegments.getOrNull(3).orEmpty()
                val plKey = AndroidGotakuCrypto.hkdf(
                    seed,
                    token.toByteArray(StandardCharsets.UTF_8),
                    "pl|v1".toByteArray(StandardCharsets.UTF_8),
                    32
                ) ?: error("Could not derive playlist key")
                AndroidGotakuCrypto.gcm(
                    Cipher.DECRYPT_MODE,
                    plKey,
                    body.copyOfRange(0, 12),
                    body.copyOfRange(12, body.size)
                )?.toString(StandardCharsets.UTF_8) ?: error("Could not decrypt playlist")
            } catch (error: Exception) {
                Log.w(TAG, "Playlist GCM decrypt failed: len=${body.size} - ${error.message}")
                val asText = String(body, StandardCharsets.UTF_8)
                if (asText.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")) {
                    asText
                } else {
                    return response(
                        out,
                        500,
                        "Internal Server Error",
                        "text/plain",
                        "Playlist decrypt failed: ${error.message}".toByteArray(StandardCharsets.UTF_8)
                    )
                }
            }
        } else {
            String(body, StandardCharsets.UTF_8)
        }
        val rewritten = rewriteManifest(manifest, url, key, segmentBytes, referer).toByteArray(StandardCharsets.UTF_8)
        response(out, 200, "OK", "application/vnd.apple.mpegurl", rewritten)
    }

    private fun rewriteManifest(
        text: String,
        parentUrl: String,
        key: String?,
        segmentBytes: Int,
        referer: String?
    ): String {
        val parent = URL(parentUrl)
        val master = text.contains("#EXT-X-STREAM-INF")
        return text.lineSequence().joinToString("\n", postfix = "\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                !trimmed.startsWith("#") ->
                    proxyUrl(URL(parent, trimmed).toString(), key, segmentBytes, master, referer = referer)
                trimmed.contains("URI=\"") -> rewriteTagLine(trimmed, parent, key, segmentBytes, referer)
                else -> line
            }
        }
    }

    private fun rewriteTagLine(
        line: String,
        parent: URL,
        key: String?,
        segmentBytes: Int,
        referer: String?
    ): String {
        return URI_REGEX.replace(line) { match ->
            val rawUri = match.groupValues[1]
            if (rawUri.isEmpty()) {
                match.value
            } else {
                val resolved = URL(parent, rawUri).toString()
                val replacement = when {
                    line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-SESSION-KEY") ->
                        proxyUrl(resolved, null, 0, playlist = false, plain = true, referer = referer)
                    line.startsWith("#EXT-X-MEDIA") ->
                        if (line.contains("TYPE=SUBTITLES")) {
                            proxyUrl(resolved, key = null, segmentBytes = 0, playlist = true, referer = referer)
                        } else {
                            proxyUrl(resolved, key, segmentBytes, playlist = true, referer = referer)
                        }
                    line.startsWith("#EXT-X-I-FRAME-STREAM-INF") ->
                        proxyUrl(resolved, key, segmentBytes, playlist = true, referer = referer)
                    else -> proxyUrl(resolved, key, segmentBytes, playlist = false, referer = referer)
                }
                "URI=\"$replacement\""
            }
        }
    }

    private fun proxyUrl(
        url: String,
        key: String?,
        segmentBytes: Int,
        playlist: Boolean = false,
        plain: Boolean = false,
        referer: String? = null
    ): String {
        val query = StringBuilder("url=").append(URLEncoder.encode(url, "UTF-8"))
        if (!plain && !key.isNullOrBlank()) {
            query.append("&key=").append(URLEncoder.encode(key, "UTF-8"))
                .append("&sb=").append(segmentBytes)
        }
        if (playlist) query.append("&m3u8=1")
        if (plain) query.append("&plain=1")
        if (referer != null) query.append("&ref=").append(URLEncoder.encode(referer, "UTF-8"))
        val path = when {
            playlist -> "/proxy/playlist.m3u8?"
            plain -> "/proxy/passthrough?"
            else -> "/proxy/segment.ts?"
        }
        return "http://127.0.0.1:" + port + path + query
    }

    private fun serveSegment(
        out: OutputStream,
        url: String,
        key: String?,
        segmentBytes: Int,
        rangeStart: Long,
        referer: String?
    ) {
        val connection = upstream(url, rangeStart, referer)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "Segment upstream HTTP $status: $url")
                return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            }
            val input = connection.inputStream
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val stream = BufferedInputStream(input)
            val actualStart = if (status == 206) rangeStart else 0L
            val keyBytes = key?.takeIf { it.isNotBlank() }?.let { AndroidGotakuCrypto.decodeUrlSafe(it) }
            var cipher: Cipher? = null
            if (keyBytes != null && segmentBytes > 0 && actualStart < segmentBytes) {
                if (actualStart == 0L) {
                    stream.mark(512)
                    val probe = ByteArray(384)
                    var count = 0
                    while (count < probe.size) {
                        val read = stream.read(probe, count, probe.size - count)
                        if (read <= 0) break
                        count += read
                    }
                    stream.reset()
                    val sample = probe.copyOf(count)
                    if (!looksLikeMedia(sample)) {
                        var fullPath = fullPathCounter
                        if (fullPath == null && sample.size >= 189) {
                            fullPath = listOf(false, true).firstOrNull {
                                looksLikeMedia(decryptPreview(sample, url, keyBytes, it, segmentBytes))
                            }
                            if (fullPath != null) {
                                fullPathCounter = fullPath
                                Log.i(TAG, "Counter interpretation: ${if (fullPath) "full URL" else "path only"}")
                            }
                        }
                        cipher = segmentCipher(keyBytes, url, fullPath ?: false, actualStart)
                    }
                } else {
                    cipher = segmentCipher(keyBytes, url, fullPathCounter ?: false, actualStart)
                }
            }
            val length = connection.getHeaderFieldLong("Content-Length", -1)
            val partial = status == 206
            val headers = StringBuilder("HTTP/1.1 ")
                .append(if (partial) "206 Partial Content" else "200 OK").append("\r\n")
                .append("Content-Type: ").append(connection.getHeaderField("Content-Type") ?: "video/mp2t").append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
            if (length >= 0) headers.append("Content-Length: ").append(length).append("\r\n")
            connection.getHeaderField("Content-Range")?.let { headers.append("Content-Range: ").append(it).append("\r\n") }
            headers.append("Access-Control-Allow-Origin: *\r\n\r\n")
            out.write(headers.toString().toByteArray(StandardCharsets.UTF_8))

            // Do not let mid-body exceptions emit an HTTP error status into the media stream.
            runCatching {
                val buffer = ByteArray(8192)
                var currentPos = actualStart
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (cipher != null && currentPos < segmentBytes) {
                        val n = minOf(count.toLong(), segmentBytes - currentPos).toInt()
                        val mask = cipher.update(ByteArray(n)) ?: ByteArray(n)
                        repeat(n) { i -> buffer[i] = (buffer[i].toInt() xor mask[i].toInt()).toByte() }
                    }
                    currentPos += count
                    out.write(buffer, 0, count)
                }
                out.flush()
            }.onFailure { Log.w(TAG, "Segment stream interrupted", it) }
        } finally {
            connection.close()
        }
    }

    private fun servePassthrough(out: OutputStream, url: String, rangeStart: Long, referer: String?) {
        val connection = upstream(url, rangeStart, referer)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "Passthrough upstream HTTP $status: $url")
                return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            }
            val input = connection.inputStream
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val length = connection.getHeaderFieldLong("Content-Length", -1)
            val headers = StringBuilder("HTTP/1.1 ")
                .append(if (status == 206) "206 Partial Content" else "200 OK").append("\r\n")
                .append("Content-Type: ").append(connection.getHeaderField("Content-Type") ?: "application/octet-stream").append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
            if (length >= 0) headers.append("Content-Length: ").append(length).append("\r\n")
            connection.getHeaderField("Content-Range")?.let { headers.append("Content-Range: ").append(it).append("\r\n") }
            headers.append("Access-Control-Allow-Origin: *\r\n\r\n")
            out.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
            runCatching {
                input.use { it.copyTo(out) }
                out.flush()
            }.onFailure { Log.w(TAG, "Passthrough stream interrupted", it) }
        } finally {
            connection.close()
        }
    }

    private fun serveSubtitle(out: OutputStream, url: String, referer: String?) {
        val connection = upstream(url, referer = referer)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            val input = connection.inputStream
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val path = url.substringBefore('?')
            val contentType = when {
                path.endsWith(".ass", ignoreCase = true) -> "text/x-ass"
                path.endsWith(".srt", ignoreCase = true) -> "application/x-subrip"
                path.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
                else -> connection.getHeaderField("Content-Type") ?: "text/plain"
            }
            val bytes = try {
                input.use { stream ->
                    val bufferOut = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = stream.read(buf)
                        if (n == -1) break
                        total += n
                        if (total > MAX_SUBTITLE_BYTES) error("Subtitle too large")
                        bufferOut.write(buf, 0, n)
                    }
                    bufferOut.toByteArray()
                }
            } catch (_: Exception) {
                return response(
                    out,
                    500,
                    "Internal Server Error",
                    "text/plain",
                    "Subtitle too large / read error".toByteArray(StandardCharsets.UTF_8)
                )
            }
            val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: ${bytes.size}\r\n\r\n"
            out.write(headers.toByteArray(StandardCharsets.UTF_8))
            out.write(bytes)
            out.flush()
        } finally {
            connection.close()
        }
    }

    private fun upstream(url: String, rangeStart: Long = 0, referer: String? = null): AndroidNetworkResponse {
        val headers = buildMap {
            put("Accept", "*/*")
            if (!referer.isNullOrBlank()) {
                val origin = runCatching {
                    val parsed = URL(referer)
                    "${parsed.protocol}://${parsed.authority}"
                }.getOrElse { referer.trimEnd('/') }
                put("Referer", referer)
                put("Origin", origin)
                put("Sec-Fetch-Dest", "empty")
                put("Sec-Fetch-Mode", "cors")
                put("Sec-Fetch-Site", "cross-site")
            } else {
                put("Referer", "https://gotaku.to/")
            }
            put("User-Agent", USER_AGENT)
            if (rangeStart > 0) put("Range", "bytes=" + rangeStart + "-")
        }
        return networkStack.execute(url, headers = headers, connectTimeoutMs = 15_000, readTimeoutMs = 30_000)
    }

    private fun segmentCipher(key: ByteArray, url: String, fullPath: Boolean, rangeStart: Long): Cipher? =
        runCatching {
            val rawPath = url.substringBefore('?').substringAfter("://").substringAfter('/', "")
            val pieces = rawPath.split('/').filter(String::isNotEmpty)
            val selected = pieces.drop(if (fullPath) 0 else 2).joinToString("/")
            val urlId = selected.substringBeforeLast('.', selected)
            val hash = MessageDigest.getInstance("SHA-256").digest(urlId.toByteArray(StandardCharsets.UTF_8))
            val iv = ByteArray(16)
            hash.copyInto(iv, endIndex = 8)
            Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                var remaining = rangeStart
                val zeros = ByteArray(4096)
                while (remaining > 0) {
                    val size = minOf(remaining, zeros.size.toLong()).toInt()
                    update(zeros, 0, size)
                    remaining -= size
                }
            }
        }.getOrNull()

    private fun decryptPreview(data: ByteArray, url: String, key: ByteArray, fullPath: Boolean, segmentBytes: Int): ByteArray {
        val cipher = segmentCipher(key, url, fullPath, 0) ?: return data
        val size = minOf(data.size, segmentBytes)
        val mask = cipher.update(ByteArray(size)) ?: return data
        return data.copyOf().also { out ->
            repeat(size) { i -> out[i] = (out[i].toInt() xor mask[i].toInt()).toByte() }
        }
    }

    private fun looksLikeMedia(data: ByteArray): Boolean {
        if (data.size >= 188 && (data[0].toInt() and 0xFF) == 0x47) {
            for (i in 188 until data.size step 188) if ((data[i].toInt() and 0xFF) != 0x47) return false
            return true
        }
        return data.size >= 8 && String(data, 4, 4, StandardCharsets.ISO_8859_1) in setOf("ftyp", "styp", "moof")
    }

    private fun badRequest(out: OutputStream) = response(out, 400, "Bad Request", "text/plain", ByteArray(0))

    private fun response(out: OutputStream, status: Int, label: String, type: String, body: ByteArray) {
        val header = "HTTP/1.1 " + status + " " + label + "\r\nContent-Type: " + type +
            "\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: " + body.size + "\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private companion object {
        const val TAG = "AndroidStreamProxy"
        const val MAX_SUBTITLE_BYTES = 512 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
        val URI_REGEX = Regex("URI=\"([^\"]+)\"")
    }
}
