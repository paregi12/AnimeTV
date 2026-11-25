package com.paregi.animetv

/** Returns the current local time using the host platform's locale and time zone. */
expect fun platformClockLabel(): String

data class PlatformScheduleDay(
    val offset: Int,
    val dayName: String,
    val shortName: String,
    val dateLabel: String,
    val isToday: Boolean
)

/** Locale-aware local time and status for an airing entry. */
data class PlatformAiringInfo(
    val weekdayIndex: Int,
    val timeLabel: String,
    val isToday: Boolean,
    val hasAired: Boolean,
    val dayOffset: Int = 0
)

expect fun platformScheduleDays(): List<PlatformScheduleDay>

expect fun platformAiringInfo(epochSeconds: Long): PlatformAiringInfo

expect fun platformCurrentWeekdayIndex(): Int
