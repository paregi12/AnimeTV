package com.paregi.animetv

import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import com.paregi.animetv.source.reanime.ReAnimeHlsProxy
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

    fun createPlaylistUrl(source: String, key: String?, segmentBytes: Int): String {
        start()
        return proxyUrl(source, key, segmentBytes, playlist = true)
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
                val upstream = local.getQueryParameter("url") ?: return badRequest(client.getOutputStream())
                if (!upstream.startsWith("http://") && !upstream.startsWith("https://")) {
                    return badRequest(client.getOutputStream())
                }
                val key = local.getQueryParameter("key")
                val segmentBytes = local.getQueryParameter("sb")?.toIntOrNull() ?: 0
                when {
                    local.getQueryParameter("re") == "1" && local.getQueryParameter("m3u8") == "1" ->
                        reAnimeProxy.servePlaylist(client.getOutputStream(), upstream, local.getQueryParameter("wp").orEmpty(), root = parts[1].startsWith("/manifest"))
                    local.getQueryParameter("re") == "1" ->
                        reAnimeProxy.serveSegment(client.getOutputStream(), upstream, rangeStart)
                    local.getQueryParameter("m3u8") == "1" ->
                        servePlaylist(client.getOutputStream(), upstream, key, segmentBytes)
                    local.getQueryParameter("plain") == "1" ->
                        servePassthrough(client.getOutputStream(), upstream, rangeStart)
                    else -> serveSegment(client.getOutputStream(), upstream, key, segmentBytes, rangeStart)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Proxy request failed", error)
                runCatching { response(client.getOutputStream(), 502, "Bad Gateway", "text/plain", ByteArray(0)) }
            }
        }
    }

    private fun servePlaylist(out: OutputStream, url: String, key: String?, segmentBytes: Int) {
        val connection = upstream(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            val body = connection.inputStream?.use { it.readBytes() }
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val manifest = if (!key.isNullOrBlank() && body.size > 28) {
                runCatching {
                    val seed = AndroidGotakuCrypto.decodeUrlSafe(key) ?: error("Bad playlist key")
                    val uri = Uri.parse(url)
                    val token = uri.getQueryParameter("token") ?: uri.pathSegments.getOrNull(3).orEmpty()
                    val plKey = AndroidGotakuCrypto.hkdf(seed, token.toByteArray(), "pl|v1".toByteArray(), 32)
                        ?: error("Could not derive playlist key")
                    AndroidGotakuCrypto.gcm(Cipher.DECRYPT_MODE, plKey, body.copyOfRange(0, 12), body.copyOfRange(12, body.size))
                        ?.toString(StandardCharsets.UTF_8) ?: error("Could not decrypt playlist")
                }.getOrElse { body.toString(StandardCharsets.UTF_8) }
            } else body.toString(StandardCharsets.UTF_8)
            val rewritten = rewriteManifest(manifest, url, key, segmentBytes).toByteArray(StandardCharsets.UTF_8)
            response(out, 200, "OK", "application/vnd.apple.mpegurl", rewritten)
        } finally {
            connection.close()
        }
    }

    private fun rewriteManifest(text: String, parentUrl: String, key: String?, segmentBytes: Int): String {
        val parent = URL(parentUrl)
        val master = text.contains("#EXT-X-STREAM-INF")
        val uriPattern = Regex("URI=\"([^\"]+)\"")
        return text.lineSequence().joinToString("\n", postfix = "\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                !trimmed.startsWith("#") ->
                    proxyUrl(URL(parent, trimmed).toString(), key, segmentBytes, master)
                uriPattern.containsMatchIn(line) -> uriPattern.replace(line) { match ->
                    val resolved = URL(parent, match.groupValues[1]).toString()
                    val childIsPlaylist = line.contains("TYPE=SUBTITLES")
                    "URI=\"" + proxyUrl(resolved, key, segmentBytes, childIsPlaylist) + "\""
                }
                else -> line
            }
        }
    }

    private fun proxyUrl(url: String, key: String?, segmentBytes: Int, playlist: Boolean): String {
        val query = StringBuilder("url=").append(URLEncoder.encode(url, "UTF-8"))
        if (!key.isNullOrBlank()) query.append("&key=").append(URLEncoder.encode(key, "UTF-8"))
            .append("&sb=").append(segmentBytes)
        if (playlist) query.append("&m3u8=1")
        return "http://127.0.0.1:" + port + "/proxy?" + query
    }

    private fun serveSegment(out: OutputStream, url: String, key: String?, segmentBytes: Int, rangeStart: Long) {
        val connection = upstream(url, rangeStart)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
            val input = connection.inputStream
                ?: return response(out, 502, "Bad Gateway", "text/plain", ByteArray(0))
            val stream = BufferedInputStream(input)
            val keyBytes = key?.let { AndroidGotakuCrypto.decodeUrlSafe(it) }
            var cipher: Cipher? = null
            if (keyBytes != null && segmentBytes > 0 && rangeStart < segmentBytes) {
                if (rangeStart == 0L) {
                    stream.mark(512)
                    val probe = ByteArray(384)
                    val count = stream.read(probe).coerceAtLeast(0)
                    stream.reset()
                    val sample = probe.copyOf(count)
                    if (!looksLikeMedia(sample)) {
                        var fullPath = fullPathCounter
                        if (fullPath == null) {
                            fullPath = listOf(false, true).firstOrNull {
                                looksLikeMedia(decryptPreview(sample, url, keyBytes, it, segmentBytes))
                            } ?: false
                            fullPathCounter = fullPath
                        }
                        cipher = segmentCipher(keyBytes, url, fullPath, rangeStart)
                    }
                } else cipher = segmentCipher(keyBytes, url, fullPathCounter ?: false, rangeStart)
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
            val buffer = ByteArray(8192)
            var currentPos = rangeStart
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
        } finally {
            connection.close()
        }
    }

    private fun servePassthrough(out: OutputStream, url: String, rangeStart: Long) {
        val connection = upstream(url, rangeStart)
        try {
            val status = connection.responseCode
            if (status !in 200..299) return response(out, status, "Upstream Error", "text/plain", ByteArray(0))
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
            input.use { it.copyTo(out) }
            out.flush()
        } finally {
            connection.close()
        }
    }

    private fun upstream(url: String, rangeStart: Long = 0): AndroidNetworkResponse {
        val headers = buildMap {
            put("Accept", "*/*")
            put("Referer", "https://gotaku.to/")
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
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
    }
}
