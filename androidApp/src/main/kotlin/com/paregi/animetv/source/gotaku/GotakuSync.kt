package com.paregi.animetv.source.gotaku

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.paregi.animetv.AndroidGotakuCrypto
import com.paregi.animetv.AndroidNetworkStack
import com.paregi.animetv.readBoundedBytes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Captures live Gotaku API encryption credentials via a headless WebView session. */
internal class GotakuSync(private val networkStack: AndroidNetworkStack) {
    private val appContext = networkStack.context
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val isSyncing = AtomicBoolean(false)
    private val syncSessionCounter = AtomicInteger(0)

    @Volatile private var activeCompletionLatch: CountDownLatch? = null
    @Volatile private var currentPageUrl: String? = null
    @Volatile private var initialSyncBaseUrl: String? = null

    class Credentials(
        val version: String,
        val seed: ByteArray,
        val info: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Credentials
            return version == other.version && seed.contentEquals(other.seed) && info == other.info
        }

        override fun hashCode(): Int {
            var result = version.hashCode()
            result = 31 * result + seed.contentHashCode()
            result = 31 * result + info.hashCode()
            return result
        }
    }

    private class JsBridge(
        private val sessionId: Int,
        private val currentSessionIdProvider: () -> Int,
        private val trustedPage: () -> Boolean,
        private val onSeed: (Int, String) -> Unit,
        private val onInfo: (Int, String) -> Unit,
        private val onVersion: (Int, String) -> Unit,
    ) {
        @JavascriptInterface
        fun onSeedCaptured(seedJson: String) {
            if (sessionId == currentSessionIdProvider() && trustedPage()) {
                onSeed(sessionId, seedJson)
            }
        }

        @JavascriptInterface
        fun onInfoCaptured(infoStr: String) {
            if (sessionId == currentSessionIdProvider() && trustedPage()) {
                onInfo(sessionId, infoStr)
            }
        }

        @JavascriptInterface
        fun onVersionCaptured(versionStr: String) {
            if (sessionId == currentSessionIdProvider() && trustedPage()) {
                onVersion(sessionId, versionStr)
            }
        }
    }

    fun current(baseUrl: String): Credentials {
        ensureSync(baseUrl)
        return readStoredCredentials() ?: Credentials(
            AndroidGotakuCrypto.DEFAULT_API_VERSION,
            AndroidGotakuCrypto.DEFAULT_API_SEED,
            AndroidGotakuCrypto.DEFAULT_HKDF_INFO,
        )
    }

    private fun ensureSync(baseUrl: String) {
        val normalized = baseUrl.trimEnd('/')
        if (normalized.isEmpty() || initialSyncBaseUrl == normalized) return
        synchronized(this) {
            if (initialSyncBaseUrl == normalized) return
            initialSyncBaseUrl = normalized
        }
        if (readStoredCredentials() != null) return
        Thread({
            runCatching { sync(normalized, force = false, replaceStored = false) }
        }, "GotakuCredSync").apply {
            isDaemon = true
            start()
        }
    }

    fun sync(baseUrl: String, force: Boolean = false, replaceStored: Boolean = force): Credentials? {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        if (!force) {
            readStoredCredentials()?.let { return it }
        }
        val parsedBase = runCatching { normalizedBaseUrl.toHttpUrl() }.getOrNull()
        if (parsedBase == null || parsedBase.scheme != "https" || parsedBase.host.isEmpty()) {
            return readStoredCredentials()
        }
        val trustedHost = parsedBase.host

        val completionLatch = CountDownLatch(1)
        val waitLatch = synchronized(this) {
            if (isSyncing.get()) {
                activeCompletionLatch
            } else {
                isSyncing.set(true)
                activeCompletionLatch = completionLatch
                null
            }
        }

        if (waitLatch != null) {
            runCatching { waitLatch.await(TIMEOUT_SEC, TimeUnit.SECONDS) }
            return readStoredCredentials()
        }

        val sessionId = syncSessionCounter.incrementAndGet()
        val captureLatch = CountDownLatch(1)
        val webViewRef = AtomicReference<WebView?>(null)

        var capturedSeed: ByteArray? = null
        var capturedVersion: String? = null
        var capturedInfo = AndroidGotakuCrypto.DEFAULT_HKDF_INFO
        var infoCaptured = false
        val captureLock = Any()

        fun acceptVersion(ver: String) {
            val candidate = ver.trim().toIntOrNull() ?: return
            synchronized(captureLock) {
                val current = capturedVersion?.toIntOrNull()
                if (current == null || candidate > current) {
                    capturedVersion = candidate.toString()
                    checkAndFinish(captureLatch, capturedSeed, capturedVersion, infoCaptured)
                }
            }
        }

        fun acceptSeed(bytes: ByteArray) {
            synchronized(captureLock) {
                capturedSeed = bytes
                checkAndFinish(captureLatch, capturedSeed, capturedVersion, infoCaptured)
            }
        }

        fun acceptInfo(info: String) {
            synchronized(captureLock) {
                capturedInfo = info
                infoCaptured = true
                checkAndFinish(captureLatch, capturedSeed, capturedVersion, infoCaptured)
            }
        }

        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Log.w(TAG, "Skipping blocking WebView sync on main thread")
                return readStoredCredentials()
            }

            val bridge = JsBridge(
                sessionId = sessionId,
                currentSessionIdProvider = { syncSessionCounter.get() },
                trustedPage = { isTrustedPageUrl(currentPageUrl, trustedHost) },
                onSeed = { sId, seedJson ->
                    if (sId == sessionId) {
                        runCatching {
                            val bytes = seedJson.trim('[', ']').split(",")
                                .mapNotNull { it.trim().toIntOrNull()?.toByte() }
                                .toByteArray()
                            if (bytes.size == 32) acceptSeed(bytes)
                        }.onFailure { Log.w(TAG, "Failed to parse captured seed", it) }
                    }
                },
                onInfo = { sId, infoStr ->
                    if (sId == sessionId && infoStr.isNotBlank()) acceptInfo(infoStr)
                },
                onVersion = { sId, ver ->
                    if (sId == sessionId && ver.isNotBlank() && ver.all { it.isDigit() }) {
                        acceptVersion(ver)
                    }
                },
            )

            val originUrl = buildString {
                append(parsedBase.scheme).append("://").append(trustedHost)
                if (parsedBase.port != HttpUrl.defaultPort(parsedBase.scheme)) {
                    append(':').append(parsedBase.port)
                }
            }
            val hookScript = HOOK_SCRIPT_TEMPLATE.replace("%BASE_ORIGIN%", originUrl)

            handler.post {
                try {
                    val view = createWebView(bridge, hookScript, trustedHost, captureLock, { capturedSeed != null }) { kParam ->
                        acceptVersion(kParam)
                    }
                    webViewRef.set(view)
                    view.loadUrl("$normalizedBaseUrl/browse")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize WebView for sync session #$sessionId", e)
                    captureLatch.countDown()
                }
            }

            captureLatch.await(TIMEOUT_SEC, TimeUnit.SECONDS)

            val seed: ByteArray?
            val version: String?
            val info: String
            val infoOk: Boolean
            synchronized(captureLock) {
                seed = capturedSeed
                version = capturedVersion
                info = capturedInfo
                infoOk = infoCaptured
            }

            if (seed != null && version != null && infoOk && seed.size == 32 && version.all { it.isDigit() }) {
                val creds = Credentials(version, seed, info)
                if (verifyCredentials(normalizedBaseUrl, creds)) {
                    return promoteCapture(creds, replaceAny = replaceStored)
                }
                Log.w(TAG, "Captured credentials failed the API decrypt oracle — discarded")
                return readStoredCredentials()
            }

            return readStoredCredentials()
        } finally {
            handler.post {
                runCatching {
                    webViewRef.getAndSet(null)?.apply {
                        stopLoading()
                        removeJavascriptInterface("GotakuBridge")
                        destroy()
                    }
                }
            }
            synchronized(this) {
                completionLatch.countDown()
                activeCompletionLatch = null
                isSyncing.set(false)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        bridge: JsBridge,
        hookScript: String,
        trustedHost: String,
        captureLock: Any,
        hasSeed: () -> Boolean,
        onApiVersion: (String) -> Unit,
    ): WebView {
        val view = WebView(appContext)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = USER_AGENT
        }
        view.addJavascriptInterface(bridge, "GotakuBridge")

        val injectHook = { view.evaluateJavascript(hookScript, null) }

        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(v, url, favicon)
                currentPageUrl = url
                if (!isTrustedPageUrl(url, trustedHost)) {
                    v?.stopLoading()
                    return
                }
                injectHook()
            }

            override fun onLoadResource(v: WebView?, url: String?) {
                super.onLoadResource(v, url)
                val seedMissing = synchronized(captureLock) { !hasSeed() }
                if (seedMissing && isTrustedPageUrl(url, trustedHost)) {
                    injectHook()
                }
            }

            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                if (isTrustedPageUrl(url, trustedHost)) {
                    injectHook()
                }
            }

            override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean =
                !isTrustedPageUrl(request?.url?.toString(), trustedHost)

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean =
                !isTrustedPageUrl(url, trustedHost)

            override fun shouldInterceptRequest(
                v: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                if (request == null) return null
                val reqUrl = request.url.toString()
                val reqHeaders: Map<String, String>? = request.requestHeaders
                val dest = reqHeaders?.entries
                    ?.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }
                    ?.value?.lowercase(Locale.ROOT)
                if (!request.isForMainFrame) {
                    if ((dest == "iframe" || dest == "frame") && !isTrustedPageUrl(reqUrl, trustedHost)) {
                        Log.w(TAG, "Blocked untrusted frame document during sync: $reqUrl")
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            403,
                            "Blocked",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0)),
                        )
                    }
                    val path = reqUrl.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
                    if ((dest != null && dest in BLOCKED_FETCH_DESTS) || BLOCKED_ASSET_EXTENSIONS.any(path::endsWith)) {
                        val mime = if (dest == "style" || path.endsWith(".css")) "text/css" else "text/plain"
                        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }

                if (isTrustedPageUrl(currentPageUrl, trustedHost) && isTrustedPageUrl(reqUrl, trustedHost)) {
                    val seedMissing = synchronized(captureLock) { !hasSeed() }
                    if (seedMissing) {
                        handler.post(injectHook)
                    }
                    if (reqUrl.contains("/api/")) {
                        val kParam = request.url.getQueryParameter("k")
                        if (!kParam.isNullOrEmpty() && kParam.all { it.isDigit() }) {
                            onApiVersion(kParam)
                        }
                    }
                }
                return super.shouldInterceptRequest(v, request)
            }
        }
        return view
    }

    private fun isTrustedPageUrl(url: String?, trustedHost: String): Boolean {
        if (url.isNullOrEmpty() || trustedHost.isEmpty()) return false
        return try {
            val parsed = url.toHttpUrl()
            parsed.scheme == "https" && parsed.host == trustedHost
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyCredentials(baseUrl: String, creds: Credentials): Boolean {
        val url = "$baseUrl/api/v1/auth/me?k=${creds.version}"
        val headers = mapOf(
            "Accept" to "application/json",
            "Referer" to "$baseUrl/browse",
            "User-Agent" to USER_AGENT,
        )
        return runCatching {
            networkStack.execute(
                url = url,
                method = "GET",
                headers = headers,
                connectTimeoutMs = 10_000,
                readTimeoutMs = 10_000,
            ).use { response ->
                if (response.responseCode !in 200..299) return@use false
                val bytes = response.inputStream?.use { it.readBoundedBytes(4 * 1024 * 1024) } ?: return@use false
                AndroidGotakuCrypto.decryptApi(bytes, creds.version, creds.seed, creds.info) != null
            }
        }.getOrDefault(false)
    }

    private fun checkAndFinish(latch: CountDownLatch, seed: ByteArray?, version: String?, infoOk: Boolean) {
        if (seed != null && seed.size == 32 && version != null && version.all { it.isDigit() } && infoOk) {
            latch.countDown()
        }
    }

    private fun promoteCapture(creds: Credentials, replaceAny: Boolean): Credentials {
        val stored = readStoredCredentials()
        val credsVer = creds.version.toLongOrNull() ?: 0L
        val storedVer = stored?.version?.toLongOrNull() ?: -1L
        val better = replaceAny ||
            stored == null ||
            credsVer > storedVer ||
            (creds.version == stored.version && creds != stored)
        if (better) {
            saveStoredCredentials(creds)
            return creds
        }
        return stored
    }

    fun readStoredCredentials(): Credentials? {
        val prefs = preferences
        if (!prefs.contains(PREF_API_SEED_KEY) || !prefs.contains(PREF_API_VERSION_KEY)) {
            return null
        }
        val ver = prefs.getString(PREF_API_VERSION_KEY, null) ?: AndroidGotakuCrypto.DEFAULT_API_VERSION
        val seedStr = prefs.getString(PREF_API_SEED_KEY, null) ?: return null
        val seed = runCatching {
            seedStr.split(",").mapNotNull { it.trim().toIntOrNull()?.toByte() }
                .toByteArray()
                .takeIf { it.size == 32 }
        }.getOrNull() ?: return null
        val info = prefs.getString(PREF_HKDF_INFO_KEY, null) ?: AndroidGotakuCrypto.DEFAULT_HKDF_INFO
        return Credentials(ver, seed, info)
    }

    private fun saveStoredCredentials(creds: Credentials) {
        val seedStr = creds.seed.joinToString(",") { it.toInt().toString() }
        preferences.edit()
            .putString(PREF_API_SEED_KEY, seedStr)
            .putString(PREF_API_VERSION_KEY, creds.version)
            .putString(PREF_HKDF_INFO_KEY, creds.info)
            .apply()
        Log.d(TAG, "Saved Gotaku credentials: version=${creds.version}, info=${creds.info}")
    }

    private companion object {
        const val TAG = "GotakuSync"
        const val PREFS_NAME = "GOTAKU_SYNC"
        const val TIMEOUT_SEC = 15L
        const val PREF_API_SEED_KEY = "gotaku_api_seed"
        const val PREF_API_VERSION_KEY = "gotaku_api_version"
        const val PREF_HKDF_INFO_KEY = "gotaku_hkdf_info"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
        val BLOCKED_FETCH_DESTS = setOf("image", "style", "font", "media", "audio", "video", "track", "manifest")
        val BLOCKED_ASSET_EXTENSIONS = listOf(
            ".css", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico", ".bmp", ".avif",
            ".woff", ".woff2", ".ttf", ".otf", ".eot", ".mp4", ".webm", ".mp3", ".ogg", ".m4a"
        )

        val HOOK_SCRIPT_TEMPLATE = """
            (function() {
                try {
                    if (window !== window.top) return;
                    if (window.location.origin !== '%BASE_ORIGIN%') return;
                } catch(e) { return; }

                if (window.__gotakuCaptured) return;
                window.__gotakuCaptured = true;

                const origImportKey = window.crypto.subtle.importKey;
                window.crypto.subtle.importKey = async function(fmt, data, alg, ext, uses) {
                    if (fmt === 'raw' && data && uses && uses.includes('deriveKey')) {
                        try {
                            const bytes = ArrayBuffer.isView(data)
                                ? new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
                                : new Uint8Array(data);
                            const seedArr = Array.from(bytes);
                            if (window.GotakuBridge && window.GotakuBridge.onSeedCaptured) {
                                window.GotakuBridge.onSeedCaptured(JSON.stringify(seedArr));
                            }
                        } catch(e) {}
                    }
                    return origImportKey.call(this, fmt, data, alg, ext, uses);
                };

                const origDeriveKey = window.crypto.subtle.deriveKey;
                window.crypto.subtle.deriveKey = async function(alg, baseKey, derivedAlg, ext, uses) {
                    if (alg?.name === 'HKDF' && derivedAlg?.name === 'AES-GCM' && uses && uses.includes('decrypt')) {
                        try {
                            const infoStr = alg.info ? new TextDecoder().decode(alg.info) : 'api-seal|v1';
                            if (window.GotakuBridge && window.GotakuBridge.onInfoCaptured) {
                                window.GotakuBridge.onInfoCaptured(infoStr);
                            }
                        } catch(e) {}
                    }
                    return origDeriveKey.call(this, alg, baseKey, derivedAlg, ext, uses);
                };

                const origFetch = window.fetch;
                window.fetch = async function(...args) {
                    try {
                        const url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url);
                        if (url && String(url).includes('/api/')) {
                            const match = String(url).match(/[?&]k=(\d+)/);
                            if (match && match[1] && window.GotakuBridge && window.GotakuBridge.onVersionCaptured) {
                                window.GotakuBridge.onVersionCaptured(match[1]);
                            }
                        }
                    } catch(e) {}
                    return origFetch.apply(this, args);
                };

                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    try {
                        if (url && String(url).includes('/api/')) {
                            const match = String(url).match(/[?&]k=(\d+)/);
                            if (match && match[1] && window.GotakuBridge && window.GotakuBridge.onVersionCaptured) {
                                window.GotakuBridge.onVersionCaptured(match[1]);
                            }
                        }
                    } catch(e) {}
                    return origOpen.call(this, method, url, ...rest);
                };
            })();
        """.trimIndent()
    }
}
