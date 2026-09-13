package com.foxtrotalpha.reelsblocker.ui

import com.foxtrotalpha.reelsblocker.data.CalendarEvent
import com.foxtrotalpha.reelsblocker.data.SleepSession
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

object DashboardFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val stepFormatter = NumberFormat.getIntegerInstance()

    fun formatTodayDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }

    fun formatSteps(count: Long?): String {
        return if (count == null) "—" else stepFormatter.format(count)
    }

    fun formatDurationMs(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun formatActiveCalories(kcal: Double?): String? {
        return kcal?.let { "${it.toInt()} active kcal" }
    }

    fun formatWeekdayLabel(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    }

    fun formatSleepSummary(sessions: List<SleepSession>): String? {
        if (sessions.isEmpty()) return null
        val primary = sessions.maxByOrNull { it.durationMs } ?: return null
        val zone = ZoneId.systemDefault()
        val bedtime = Instant.ofEpochMilli(primary.bedtimeMs).atZone(zone).format(timeFormatter)
        val wake = Instant.ofEpochMilli(primary.wakeMs).atZone(zone).format(timeFormatter)
        val duration = formatDurationMs(primary.durationMs)
        val napSuffix = if (sessions.size > 1) " · + nap" else ""
        return "$bedtime – $wake · $duration$napSuffix"
    }

    fun formatCalendarTime(event: CalendarEvent): String {
        if (event.allDay) return "All day"
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(event.startMs).atZone(zone).format(timeFormatter)
    }
}
