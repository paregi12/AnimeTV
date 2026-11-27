package com.paregi.animetv

import android.content.Context
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import org.chromium.net.CronetEngine
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Applies the original HTTP client, DNS, and cache preferences to Android network requests. */
class AndroidNetworkStack(
    context: Context,
    private val enableDiskCache: Boolean = true
) : Closeable {
    private val appContext = context.applicationContext
    internal val context: Context get() = appContext
    internal val cacheGeneration = AtomicLong(0L)
    private val clients = ConcurrentHashMap<NetworkConfig, NetworkClients>()

    @Volatile
    private var activeConfig = NetworkConfig()

    suspend fun configure(httpClient: Int, useDnsOverHttps: Boolean, cacheSizeMb: Int) {
        val config = NetworkConfig(
            httpClient = httpClient.coerceIn(0, 2),
            useDnsOverHttps = useDnsOverHttps,
            cacheSizeMb = cacheSizeMb.coerceIn(5, 150)
        )
        if (config == activeConfig && clients.containsKey(config)) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val evicted = synchronized(clients) {
                clients.getOrPut(config) { createClients(config) }
                activeConfig = config
                clients.keys.filter { it != config }.mapNotNull { clients.remove(it) }
            }
            evicted.forEach { runCatching { it.close() } }
        }
    }

    suspend fun clearCache() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(clients) {
                clients.values.toSet().forEach { runCatching { it.close() } }
                clients.clear()
                val undeleted = appContext.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("http-okhttp-") || it.name.startsWith("http-cronet-") }
                    ?.filter { it.exists() && !it.deleteRecursively() && it.exists() }
                    .orEmpty()
                check(undeleted.isEmpty()) { "Could not remove the HTTP cache" }
                cacheGeneration.incrementAndGet()
                clients[activeConfig] = createClients(activeConfig)
            }
        }
    }

    fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
    ): AndroidNetworkResponse {
        val config = activeConfig
        val network = clients[config] ?: synchronized(clients) {
            clients.getOrPut(config) { createClients(config) }
        }
        return if (config.httpClient == 0) {
            executeWithOkHttp(network, url, method, headers, body, connectTimeoutMs, readTimeoutMs)
        } else {
            executeWithUrlConnection(network, url, method, headers, body, connectTimeoutMs, readTimeoutMs)
        }
    }

    override fun close() {
        val current = synchronized(clients) {
            clients.values.toSet().also { clients.clear() }
        }
        current.forEach { runCatching { it.close() } }
    }

    private fun createClients(config: NetworkConfig): NetworkClients {
        val cacheBytes = config.cacheSizeMb.toLong() * 1024L * 1024L
        val cacheDir = appContext.cacheDir
        val okHttpCache = if (enableDiskCache && config.httpClient == 0) Cache(
            cacheDir.resolve("http-okhttp-${config.useDnsOverHttps}-${config.cacheSizeMb}"),
            cacheBytes
        ) else null
        val bootstrapClient = OkHttpClient.Builder().apply {
            connectTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            readTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            writeTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            callTimeout(DOH_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (okHttpCache != null) cache(okHttpCache)
        }.build()
        val dns = if (config.httpClient == 0 && config.useDnsOverHttps) {
            runCatching {
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://1.1.1.1/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"))
                    .build()
            }.getOrNull()
        } else null
        val okHttp = bootstrapClient.newBuilder().apply {
            if (dns != null) dns(dns)
            connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            readTimeout(DEFAULT_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            writeTimeout(DEFAULT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            callTimeout(
                defaultCallTimeoutMs(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS),
                TimeUnit.MILLISECONDS
            )
        }.build()

        val cronet = if (config.httpClient == 2) {
            runCatching {
                CronetEngine.Builder(appContext).apply {
                    if (enableDiskCache) {
                        setStoragePath(
                            cacheDir.resolve(
                                "http-cronet-${config.httpClient}-${config.useDnsOverHttps}-${config.cacheSizeMb}"
                            ).absolutePath
                        )
                        enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, cacheBytes)
                    } else {
                        enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISABLED, 0L)
                    }
                    enableHttp2(true)
                    enableQuic(true)
                    enableBrotli(true)
                }.build()
            }.getOrNull()
        } else null
        return NetworkClients(okHttp, okHttpCache, cronet)
    }

    private fun executeWithOkHttp(
        network: NetworkClients,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): AndroidNetworkResponse {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }
                ?.value?.toMediaTypeOrNull()
            val requestBody = body?.toRequestBody(contentType)
                ?: if (method in BODY_METHODS) ByteArray(0).toRequestBody(contentType) else null
            method(method, requestBody)
        }.build()
        val client = network.clientFor(connectTimeoutMs, readTimeoutMs)
        val response = client.newCall(request).execute()
        return AndroidNetworkResponse(response)
    }

    private fun executeWithUrlConnection(
        network: NetworkClients,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): AndroidNetworkResponse {
        val connection = if (network.cronet != null) {
            runCatching { network.cronet.openConnection(URL(url)) as HttpURLConnection }
                .getOrElse { URL(url).openConnection() as HttpURLConnection }
        } else {
            URL(url).openConnection() as HttpURLConnection
        }
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        val status = connection.responseCode
        return AndroidNetworkResponse(connection, status)
    }

    private data class NetworkConfig(
        val httpClient: Int = 0,
        val useDnsOverHttps: Boolean = true,
        val cacheSizeMb: Int = 100
    )

    private class NetworkClients(
        val okHttp: OkHttpClient,
        val okHttpCache: Cache?,
        val cronet: CronetEngine?
    ) : Closeable {
        private val timeoutClients = ConcurrentHashMap<Long, OkHttpClient>()

        fun clientFor(connectTimeoutMs: Int, readTimeoutMs: Int): OkHttpClient {
            if (connectTimeoutMs == DEFAULT_CONNECT_TIMEOUT_MS && readTimeoutMs == DEFAULT_READ_TIMEOUT_MS) {
                return okHttp
            }
            val key = (connectTimeoutMs.toLong() shl 32) or (readTimeoutMs.toLong() and 0xFFFF_FFFFL)
            timeoutClients[key]?.let { return it }
            return synchronized(timeoutClients) {
                timeoutClients.getOrPut(key) {
                    okHttp.newBuilder()
                        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                        .callTimeout(defaultCallTimeoutMs(connectTimeoutMs, readTimeoutMs), TimeUnit.MILLISECONDS)
                        .build()
                }
            }
        }

        override fun close() {
            runCatching { okHttp.dispatcher.executorService.shutdown() }
            runCatching { okHttp.connectionPool.evictAll() }
            runCatching { okHttpCache?.close() }
            runCatching { cronet?.shutdown() }
        }
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 12_000
        const val DEFAULT_READ_TIMEOUT_MS = 18_000
        const val DEFAULT_WRITE_TIMEOUT_MS = 15_000L
        const val DOH_TIMEOUT_MS = 8_000L
        const val DOH_CALL_TIMEOUT_MS = 10_000L
        val BODY_METHODS = setOf("POST", "PUT", "PATCH")

        fun defaultCallTimeoutMs(connectTimeoutMs: Int, readTimeoutMs: Int): Long =
            if (readTimeoutMs > 20_000) 0L
            else maxOf(connectTimeoutMs.toLong() + readTimeoutMs.toLong() * 2L, 30_000L)
    }
}

internal fun InputStream.readBoundedBytes(maxBytes: Int = 16 * 1024 * 1024): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        check(total <= maxBytes) { "Response exceeded $maxBytes bytes" }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** Small common view of OkHttp and HttpURLConnection responses, including streaming bodies. */
class AndroidNetworkResponse internal constructor(
    private val connection: HttpURLConnection? = null,
    val responseCode: Int,
    private val okHttpResponse: Response? = null
) : Closeable {
    internal constructor(response: Response) : this(
        responseCode = response.code,
        okHttpResponse = response
    )

    val inputStream: InputStream?
        get() = if (responseCode in 200..299) {
            okHttpResponse?.body?.byteStream() ?: connection?.inputStream
        } else null

    val errorStream: InputStream?
        get() = if (responseCode !in 200..299) {
            okHttpResponse?.body?.byteStream() ?: connection?.errorStream
        } else null

    fun getHeaderField(name: String): String? = okHttpResponse?.header(name) ?: connection?.getHeaderField(name)

    fun getHeaderFieldLong(name: String, defaultValue: Long): Long =
        getHeaderField(name)?.toLongOrNull() ?: defaultValue

    override fun close() {
        okHttpResponse?.close()
        connection?.disconnect()
    }
}
