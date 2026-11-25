package com.paregi.animetv

import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual fun platformClockLabel(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

actual fun platformScheduleDays(): List<PlatformScheduleDay> {
    val result = mutableListOf<PlatformScheduleDay>()
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val fullDayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val shortDayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    for (offset in 0..6) {
        val target = Calendar.getInstance().apply {
            timeInMillis = todayCal.timeInMillis
            add(Calendar.DAY_OF_YEAR, offset)
        }
        val date = target.time
        result += PlatformScheduleDay(
            offset = offset,
            dayName = fullDayFormat.format(date),
            shortName = shortDayFormat.format(date),
            dateLabel = dayFormat.format(date),
            isToday = offset == 0
        )
    }
    return result
}

actual fun platformAiringInfo(epochSeconds: Long): PlatformAiringInfo {
    val eventDate = Date(epochSeconds * 1000)
    val event = Calendar.getInstance().apply { time = eventDate }
    val now = Calendar.getInstance()
    val sameDay = event.get(Calendar.ERA) == now.get(Calendar.ERA) &&
        event.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        event.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val eventDayCal = Calendar.getInstance().apply {
        time = eventDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMillis = eventDayCal.timeInMillis - todayCal.timeInMillis
    val dayOffset = (diffMillis / (24 * 60 * 60 * 1000L)).toInt()

    return PlatformAiringInfo(
        weekdayIndex = event.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY,
        timeLabel = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(eventDate),
        isToday = sameDay,
        hasAired = epochSeconds < System.currentTimeMillis() / 1000,
        dayOffset = dayOffset
    )
}

actual fun platformCurrentWeekdayIndex(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
