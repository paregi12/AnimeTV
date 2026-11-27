package com.paregi.animetv

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private class CachedClockState(val locale: Locale, val timeZone: TimeZone) {
    val clockFormat = SimpleDateFormat("h:mm a", locale).apply { this.timeZone = this@CachedClockState.timeZone }
    val dayFormat = SimpleDateFormat("MMM d", locale).apply { this.timeZone = this@CachedClockState.timeZone }
    val fullDayFormat = SimpleDateFormat("EEEE", locale).apply { this.timeZone = this@CachedClockState.timeZone }
    val shortDayFormat = SimpleDateFormat("EEE", locale).apply { this.timeZone = this@CachedClockState.timeZone }
    val shortTimeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
        this.timeZone = this@CachedClockState.timeZone
    }
    val calA: Calendar = Calendar.getInstance(timeZone, locale)
    val calB: Calendar = Calendar.getInstance(timeZone, locale)
    val date: Date = Date()
}

private val threadLocalClockState = ThreadLocal<CachedClockState>()

private fun clockState(): CachedClockState {
    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val current = threadLocalClockState.get()
    if (current != null && current.locale == locale && current.timeZone == timeZone) {
        return current
    }
    return CachedClockState(locale, timeZone).also(threadLocalClockState::set)
}

actual fun platformClockLabel(): String {
    val state = clockState()
    state.date.time = System.currentTimeMillis()
    return state.clockFormat.format(state.date)
}

actual fun platformScheduleDays(): List<PlatformScheduleDay> {
    val state = clockState()
    val cal = state.calA
    cal.timeInMillis = System.currentTimeMillis()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val result = ArrayList<PlatformScheduleDay>(7)
    for (offset in 0..6) {
        if (offset > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
        state.date.time = cal.timeInMillis
        result += PlatformScheduleDay(
            offset = offset,
            dayName = state.fullDayFormat.format(state.date),
            shortName = state.shortDayFormat.format(state.date),
            dateLabel = state.dayFormat.format(state.date),
            isToday = offset == 0
        )
    }
    return result
}

actual fun platformAiringInfo(epochSeconds: Long): PlatformAiringInfo {
    val state = clockState()
    val nowMillis = System.currentTimeMillis()
    val eventMillis = epochSeconds * 1000L

    val todayCal = state.calA
    todayCal.timeInMillis = nowMillis
    val nowEra = todayCal.get(Calendar.ERA)
    val nowYear = todayCal.get(Calendar.YEAR)
    val nowDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
    todayCal.set(Calendar.HOUR_OF_DAY, 0)
    todayCal.set(Calendar.MINUTE, 0)
    todayCal.set(Calendar.SECOND, 0)
    todayCal.set(Calendar.MILLISECOND, 0)

    val eventCal = state.calB
    eventCal.timeInMillis = eventMillis
    val weekdayIndex = eventCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val sameDay = eventCal.get(Calendar.ERA) == nowEra &&
        eventCal.get(Calendar.YEAR) == nowYear &&
        eventCal.get(Calendar.DAY_OF_YEAR) == nowDayOfYear
    eventCal.set(Calendar.HOUR_OF_DAY, 0)
    eventCal.set(Calendar.MINUTE, 0)
    eventCal.set(Calendar.SECOND, 0)
    eventCal.set(Calendar.MILLISECOND, 0)

    val dayOffset = ((eventCal.timeInMillis - todayCal.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
    state.date.time = eventMillis

    return PlatformAiringInfo(
        weekdayIndex = weekdayIndex,
        timeLabel = state.shortTimeFormat.format(state.date),
        isToday = sameDay,
        hasAired = epochSeconds < nowMillis / 1000L,
        dayOffset = dayOffset
    )
}

actual fun platformCurrentWeekdayIndex(): Int {
    val state = clockState()
    state.calA.timeInMillis = System.currentTimeMillis()
    return state.calA.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
}
