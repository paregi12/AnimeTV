package com.paregi.animetv.domain

/** Metadata describing an available Android APK update from server.json. */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val sizeLabel: String,
    val apkUrl: String,
    val changelog: String
)

/** Outcome of checking GitHub server.json against the installed app version. */
sealed interface AppUpdateCheckResult {
    data class Available(
        val update: AppUpdateInfo,
        val autoRemindDisabled: Boolean
    ) : AppUpdateCheckResult

    data object UpToDate : AppUpdateCheckResult
    data class Error(val message: String) : AppUpdateCheckResult
}

/** Outcome of downloading and launching installation for an update APK. */
sealed interface AppUpdateDownloadResult {
    data class Downloaded(val sizeLabel: String) : AppUpdateDownloadResult
    data object AlreadyInProgress : AppUpdateDownloadResult
    data class Error(val message: String) : AppUpdateDownloadResult
}

/** Checks, downloads, and installs self-hosted APK updates for the current platform. */
interface AppUpdateRepository {
    fun currentVersionName(): String = ""
    fun currentVersionCode(): Int = 0
    suspend fun checkForUpdate(cleanDownloadedApk: Boolean = false): AppUpdateCheckResult =
        AppUpdateCheckResult.UpToDate
    fun setAutoUpdateDisabled(disabled: Boolean) {}
    suspend fun downloadAndInstall(update: AppUpdateInfo): AppUpdateDownloadResult =
        AppUpdateDownloadResult.Error("In-app updates are unavailable on this platform")
}
