package com.paregi.animetv.domain

interface DebugLogRepository {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun logPath(): String
    suspend fun recentLogs(lines: Int = 1500): String
    suspend fun clearLogs()
    fun copyToClipboard(logs: String): Boolean
}
