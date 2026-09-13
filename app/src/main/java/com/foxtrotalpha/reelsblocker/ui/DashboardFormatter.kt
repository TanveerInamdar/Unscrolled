package com.foxtrotalpha.reelsblocker.ui

import android.content.Context
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.data.CalendarEvent
import com.foxtrotalpha.reelsblocker.data.SleepSession
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    fun greetingFor(context: Context, time: LocalTime = LocalTime.now()): String {
        return when (time.hour) {
            in 5..11 -> context.getString(R.string.greeting_morning)
            in 12..16 -> context.getString(R.string.greeting_afternoon)
            else -> context.getString(R.string.greeting_evening)
        }
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

    fun formatClock(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    fun formatSleepSummary(sessions: List<SleepSession>): String? {
        val primary = primarySleep(sessions) ?: return null
        val napSuffix = if (sessions.size > 1) " · + nap" else ""
        return "${formatClock(primary.bedtimeMs)} – ${formatClock(primary.wakeMs)} · ${formatDurationMs(primary.durationMs)}$napSuffix"
    }

    fun primarySleep(sessions: List<SleepSession>): SleepSession? {
        if (sessions.isEmpty()) return null
        return sessions.maxByOrNull { it.durationMs }
    }

    fun formatCalendarTime(event: CalendarEvent): String {
        if (event.allDay) return "All day"
        return formatClock(event.startMs)
    }

    fun heroSummary(
        context: Context,
        blocksToday: Int,
        unproductiveMsToday: Long,
        stepsToday: Long?,
    ): String {
        return when {
            blocksToday > 0 -> context.getString(R.string.hero_summary_blocks, blocksToday)
            unproductiveMsToday > 0L -> context.getString(
                R.string.hero_summary_time,
                formatDurationMs(unproductiveMsToday),
            )
            stepsToday != null && stepsToday > 0L -> context.getString(R.string.hero_summary_default)
            else -> context.getString(R.string.hero_summary_clear)
        }
    }
}
