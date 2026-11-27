package com.paregi.animetv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.paregi.animetv.domain.AppUpdateCheckResult
import com.paregi.animetv.domain.AppUpdateDownloadResult
import com.paregi.animetv.domain.AppUpdateInfo
import com.paregi.animetv.domain.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Checks kmp server.json, downloads release APKs into filesDir, and triggers FileProvider installs. */
class AndroidAppUpdateRepository(
    context: Context,
    private val networkStack: AndroidNetworkStack,
    activity: Activity? = null
) : AppUpdateRepository {
    private val appContext = context.applicationContext
    private val activityRef = WeakReference(activity)
    private val preferences = appContext.getSharedPreferences("SERVER", Context.MODE_PRIVATE)
    private val downloading = AtomicBoolean(false)
    private val cachedPackageInfo: PackageInfo? by lazy { loadPackageInfo() }

    override fun currentVersionName(): String = readPackageInfo()?.versionName.orEmpty()

    override fun currentVersionCode(): Int =
        readPackageInfo()?.let { PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 0

    override fun setAutoUpdateDisabled(disabled: Boolean) {
        preferences.edit().putBoolean(PREF_UPDATE_DISABLE, disabled).apply()
    }

    override suspend fun checkForUpdate(cleanDownloadedApk: Boolean): AppUpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (cleanDownloadedApk && !downloading.get()) {
                cleanupUpdateFiles()
            }
            val url = "$SERVER_JSON_URL?${System.currentTimeMillis()}"
            runCatching {
                networkStack.execute(
                    url = url,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Cache-Control" to "no-cache"
                    )
                ).use { response ->
                    val status = response.responseCode
                    if (status !in 200..299) {
                        return@use AppUpdateCheckResult.Error("Update check failed (HTTP $status)")
                    }
                    val body = response.inputStream?.use {
                        it.readBoundedBytes(MAX_METADATA_BYTES).toString(StandardCharsets.UTF_8)
                    }.orEmpty()
                    if (body.isBlank()) {
                        return@use AppUpdateCheckResult.Error("Update metadata response was empty")
                    }
                    parseUpdateResponse(
                        body = body,
                        installedVersionCode = currentVersionCode(),
                        autoRemindDisabled = preferences.getBoolean(PREF_UPDATE_DISABLE, false)
                    )
                }
            }.getOrElse { error ->
                Log.w(TAG, "Update check failed", error)
                AppUpdateCheckResult.Error("Could not check for updates")
            }
        }

    override suspend fun downloadAndInstall(update: AppUpdateInfo): AppUpdateDownloadResult {
        if (!downloading.compareAndSet(false, true)) {
            return AppUpdateDownloadResult.AlreadyInProgress
        }
        val partFile = File(appContext.filesDir, UPDATE_PART_NAME)
        val apkFile = File(appContext.filesDir, UPDATE_APK_NAME)
        return try {
            val sizeLabel = withContext(Dispatchers.IO) {
                partFile.parentFile?.mkdirs()
                partFile.delete()
                apkFile.delete()
                val totalBytes = networkStack.execute(
                    url = update.apkUrl,
                    connectTimeoutMs = 20_000,
                    readTimeoutMs = 60_000
                ).use { response ->
                    val status = response.responseCode
                    check(status in 200..299) { "HTTP $status" }
                    val stream = checkNotNull(response.inputStream) { "Empty download stream" }
                    stream.use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                            var written = 0L
                            var checkedMagic = false
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                if (!checkedMagic) {
                                    check(read >= 2 && buffer[0] == ZIP_MAGIC_P && buffer[1] == ZIP_MAGIC_K) {
                                        "Downloaded file is not a valid APK"
                                    }
                                    checkedMagic = true
                                }
                                output.write(buffer, 0, read)
                                written += read
                            }
                            output.fd.sync()
                            check(written > 0L) { "Downloaded APK is empty" }
                            written
                        }
                    }
                }
                if (!partFile.renameTo(apkFile)) {
                    partFile.copyTo(apkFile, overwrite = true)
                    partFile.delete()
                }
                formatDownloadSize(totalBytes)
            }
            withContext(Dispatchers.Main) {
                launchApkInstaller(apkFile)
            }
            AppUpdateDownloadResult.Downloaded(sizeLabel)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to download or launch update APK", error)
            runCatching { partFile.delete() }
            runCatching { apkFile.delete() }
            AppUpdateDownloadResult.Error(error.message ?: "Download failed")
        } finally {
            downloading.set(false)
        }
    }

    private fun readPackageInfo(): PackageInfo? = cachedPackageInfo

    private fun loadPackageInfo(): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
    }.getOrNull()

    private fun cleanupUpdateFiles() {
        runCatching { File(appContext.filesDir, UPDATE_APK_NAME).delete() }
        runCatching { File(appContext.filesDir, UPDATE_PART_NAME).delete() }
    }

    private fun launchApkInstaller(apkFile: File) {
        val authority = "${appContext.packageName}.provider"
        val uri = FileProvider.getUriForFile(appContext, authority, apkFile)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        grantInstallerUriPermissions(viewIntent, uri)
        val host = activityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
        try {
            if (host != null) host.startActivity(viewIntent) else appContext.startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
            @Suppress("DEPRECATION")
            val legacyIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            grantInstallerUriPermissions(legacyIntent, uri)
            if (host != null) host.startActivity(legacyIntent) else appContext.startActivity(legacyIntent)
        }
    }

    private fun grantInstallerUriPermissions(intent: Intent, uri: Uri) {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolved.forEach { info ->
            runCatching {
                appContext.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    companion object {
        const val SERVER_JSON_URL =
            "https://raw.githubusercontent.com/paregi12/AnimeTV/kmp/server.json"
        private const val PREF_UPDATE_DISABLE = "update-disable"
        private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        private const val UPDATE_APK_NAME = "update.apk"
        private const val UPDATE_PART_NAME = "update.apk.part"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val ZIP_MAGIC_P = 0x50.toByte()
        private const val ZIP_MAGIC_K = 0x4B.toByte()
        private const val TAG = "AnimeTVUpdate"

        internal fun parseUpdateResponse(
            body: String,
            installedVersionCode: Int,
            autoRemindDisabled: Boolean
        ): AppUpdateCheckResult {
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return AppUpdateCheckResult.Error("Invalid update metadata")
            val remoteCode = json.optInt("appnum", 0)
            val versionName = json.optString("appver", "").trim()
            val apkUrl = json.optString("appurl", "").trim()
            val sizeLabel = json.optString("appsize", "").trim().ifBlank { "Unknown" }
            val changelog = json.optString("appnote", "").trim()
                .ifBlank { "- Bug fixes and improvements" }

            val validUrl = apkUrl.startsWith("https://", ignoreCase = true) ||
                apkUrl.startsWith("http://", ignoreCase = true)
            if (remoteCode > installedVersionCode && validUrl && versionName.isNotBlank()) {
                return AppUpdateCheckResult.Available(
                    update = AppUpdateInfo(
                        versionCode = remoteCode,
                        versionName = versionName,
                        sizeLabel = sizeLabel,
                        apkUrl = apkUrl,
                        changelog = changelog
                    ),
                    autoRemindDisabled = autoRemindDisabled
                )
            }
            return AppUpdateCheckResult.UpToDate
        }

        internal fun formatDownloadSize(bytes: Long): String {
            val megabytes = bytes / (1024.0 * 1024.0)
            return if (megabytes >= 1.0) {
                String.format(Locale.US, "%.1f MB", megabytes)
            } else {
                "${maxOf(1L, bytes / 1024L)} KB"
            }
        }
    }
}
