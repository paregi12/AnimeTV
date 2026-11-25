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
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Applies the original HTTP client, DNS, and cache preferences to Android network requests. */
class AndroidNetworkStack(context: Context) : Closeable {
    private val appContext = context.applicationContext
    internal val context: Context get() = appContext
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
            synchronized(clients) {
                clients.getOrPut(config) { createClients(config) }
                activeConfig = config
            }
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
                clients[activeConfig] = createClients(activeConfig)
            }
        }
    }

    fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 18_000
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
        clients.values.toSet().forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun createClients(config: NetworkConfig): NetworkClients {
        val cacheBytes = config.cacheSizeMb.toLong() * 1024L * 1024L
        val cacheDir = appContext.cacheDir
        val okHttpCache = if (config.httpClient == 0) Cache(
            cacheDir.resolve("http-okhttp-${config.useDnsOverHttps}-${config.cacheSizeMb}"),
            cacheBytes
        ) else null
        val bootstrapClient = OkHttpClient.Builder().apply {
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
        }.build()

        val cronet = if (config.httpClient == 2) {
            runCatching {
                CronetEngine.Builder(appContext)
                    .setStoragePath(cacheDir.resolve(
                        "http-cronet-${config.httpClient}-${config.useDnsOverHttps}-${config.cacheSizeMb}"
                    ).absolutePath)
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, cacheBytes)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .enableBrotli(true)
                    .build()
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
        val client = network.okHttp.newBuilder()
            .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
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

    private data class NetworkClients(
        val okHttp: OkHttpClient,
        val okHttpCache: Cache?,
        val cronet: CronetEngine?
    ) : Closeable {
        override fun close() {
            okHttpCache?.close()
            cronet?.shutdown()
        }
    }

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
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
