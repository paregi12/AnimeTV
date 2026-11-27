package com.paregi.animetv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process as AndroidProcess
import android.util.Log
import com.paregi.animetv.domain.DebugLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class AndroidDebugLogRepository private constructor(context: Context) : DebugLogRepository {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("SERVER", Context.MODE_PRIVATE)
    private val processExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ParegiDebugLog").apply { isDaemon = true }
    }
    private var logcatProcess: java.lang.Process? = null

    fun onHostStarted() {
        processExecutor.execute {
            if (isEnabled()) startLoggingInternal()
        }
    }

    fun onHostFinished() {
        processExecutor.execute {
            stopLoggingInternal()
        }
    }

    override fun isEnabled(): Boolean = preferences.getBoolean(PREF_KEY, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_KEY, enabled).apply()
        processExecutor.execute {
            if (enabled) startLoggingInternal() else stopLoggingInternal()
        }
    }

    override fun logPath(): String = logFile().absolutePath

    override suspend fun recentLogs(lines: Int): String = withContext(Dispatchers.IO) {
        val limit = lines.takeIf { it > 0 } ?: DEFAULT_LOG_LINES
        val file = logFile()
        if (file.exists()) {
            return@withContext readSavedLogs(limit)
        }
        readLogcat(limit)
    }

    override suspend fun clearLogs() = withContext(Dispatchers.IO) {
        runCatching {
            processExecutor.submit(Runnable {
                stopLoggingInternal()
                listOf(logFile(), File(logFile().absolutePath + ".1"), File(logFile().absolutePath + ".2"))
                    .forEach { file -> runCatching { file.delete() } }
                runCatching { logFile().createNewFile() }
                if (isEnabled()) startLoggingInternal()
            }).get()
        }
        Unit
    }

    override fun copyToClipboard(logs: String): Boolean {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("Paregi Diagnostic Logs", logs))
            true
        }.getOrDefault(false)
    }

    private fun startLoggingInternal() {
        if (logcatProcess != null) return
        runCatching {
            logFile().parentFile?.mkdirs()
            val pid = AndroidProcess.myPid()
            logcatProcess = ProcessBuilder(
                "logcat", "-b", "main", "-b", "crash", "-v", "time",
                "--pid=$pid", "-f", logFile().absolutePath, "-r", "2048", "-n", "2"
            ).redirectErrorStream(true).start()
            Log.i(TAG, "Started app diagnostic capture: ${logFile().absolutePath}")
        }.onFailure { error ->
            logcatProcess = null
            Log.e(TAG, "Could not start app diagnostic capture", error)
        }
    }

    private fun stopLoggingInternal() {
        val process = logcatProcess ?: return
        logcatProcess = null
        runCatching {
            process.destroy()
            process.waitFor()
        }
        Log.i(TAG, "Stopped app diagnostic capture")
    }

    private fun readSavedLogs(maxLines: Int): String {
        val lines = ArrayDeque<String>(minOf(maxLines, 4096))
        val rotated = File(logFile().absolutePath + ".1")
        if (rotated.exists()) appendFilteredLines(rotated, lines, maxLines)
        appendFilteredLines(logFile(), lines, maxLines)
        return lines.joinToString(separator = "\n").let { content ->
            if (content.isEmpty()) "" else "$content\n"
        }
    }

    private fun appendFilteredLines(file: File, output: ArrayDeque<String>, maxLines: Int) {
        runCatching {
            BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!isNoise(line)) {
                        if (output.size >= maxLines) output.removeFirst()
                        output.addLast(line)
                    }
                }
            }
        }
    }

    private fun readLogcat(maxLines: Int): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-b", "main", "-b", "crash", "-v", "time",
            "--pid=${AndroidProcess.myPid()}", "-t", (maxLines * 3).toString()
        ).redirectErrorStream(true).start()
        val lines = ArrayDeque<String>(minOf(maxLines, 4096))
        try {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!isNoise(line)) {
                        if (lines.size >= maxLines) lines.removeFirst()
                        lines.addLast(line)
                    }
                }
            }
            process.waitFor()
        } finally {
            runCatching { process.destroy() }
        }
        lines.joinToString(separator = "\n").let { content ->
            if (content.isEmpty()) "" else "$content\n"
        }
    }.getOrElse { error -> "Error retrieving logs: ${error.message ?: "unknown error"}" }

    private fun logFile(): File {
        val directory = appContext.getExternalFilesDir(null) ?: appContext.cacheDir
        return File(directory, LOG_FILE_NAME)
    }

    private fun isNoise(line: String): Boolean {
        if (line.isBlank()) return true
        if (line.contains("ATVLOG") || line.contains(" E/") || line.contains(" F/") ||
            line.contains("FATAL") || line.contains("Exception")
        ) return false
        return NOISE_MARKERS.any(line::contains)
    }

    companion object {
        private const val TAG = "Paregi-Diagnostic-Logs"
        private const val PREF_KEY = "debug_logcat"
        private const val LOG_FILE_NAME = "paregi-debug.log"
        private const val DEFAULT_LOG_LINES = 1500
        private val NOISE_MARKERS = listOf(
            "Quality ", "ViewRootImplExtImpl", "BufferQueueProducer", "BufferPoolAccessor", "VRI[",
            "Oplus", "AtlasEventUploadUtils", "Codec2", "CCodec", "BLASTBufferQueue", "SurfaceUtils",
            "hw-BpHwBinder", "ResourcesManagerExtImpl", "ListServiceUtils", "SurfaceView", "OpenGLRenderer"
        )

        @Volatile
        private var instance: AndroidDebugLogRepository? = null

        fun get(context: Context): AndroidDebugLogRepository {
            val repository = instance ?: synchronized(this) {
                instance ?: AndroidDebugLogRepository(context).also { instance = it }
            }
            repository.onHostStarted()
            return repository
        }
    }
}
