package com.foxtrotalpha.reelsblocker.data

import android.content.Context
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object DevDatabaseFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    data class Snapshot(
        val trackedApps: List<TrackedApp>,
        val blockEvents: List<BlockEvent>,
        val dailyUsage: List<DailyAppUsage>,
        val healthMetrics: List<DailyHealthMetrics>,
        val sleepSessions: List<SleepSession>,
        val calendarEvents: List<CalendarEvent>,
    )

    fun format(context: Context, snapshot: Snapshot): String {
        val trackedLabels = snapshot.trackedApps.associate { it.packageName to it.label }
        val lines = mutableListOf<String>()

        lines += "── tracked_apps (${snapshot.trackedApps.size}) ──"
        if (snapshot.trackedApps.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.trackedApps.forEach { app ->
                val flag = if (app.isUnproductive) "unproductive" else "neutral"
                lines += "  ${app.label} · $flag"
            }
        }

        lines += ""
        lines += "── block_events (${snapshot.blockEvents.size}) ──"
        if (snapshot.blockEvents.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.blockEvents.take(12).forEach { event ->
                lines += "  #${event.id} ${event.date} · ${labelFor(context, trackedLabels, event.packageName)} · ${event.reason}"
            }
            if (snapshot.blockEvents.size > 12) {
                lines += "  … +${snapshot.blockEvents.size - 12} more"
            }
        }

        lines += ""
        lines += "── daily_app_usage (${snapshot.dailyUsage.size}) ──"
        if (snapshot.dailyUsage.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.dailyUsage.take(12).forEach { usage ->
                lines += "  ${usage.date} · ${labelFor(context, trackedLabels, usage.packageName)} · ${formatMinutes(usage.foregroundMs)}"
            }
            if (snapshot.dailyUsage.size > 12) {
                lines += "  … +${snapshot.dailyUsage.size - 12} more"
            }
        }

        lines += ""
        lines += "── daily_health_metrics (${snapshot.healthMetrics.size}) ──"
        if (snapshot.healthMetrics.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.healthMetrics.take(12).forEach { metrics ->
                val active = metrics.activeCaloriesKcal?.let { "${it.toInt()} active kcal" } ?: "— active"
                val total = metrics.totalCaloriesKcal?.let { "${it.toInt()} total kcal" } ?: "— total"
                lines += "  ${metrics.date} · ${metrics.stepCount} steps · $active · $total"
            }
            if (snapshot.healthMetrics.size > 12) {
                lines += "  … +${snapshot.healthMetrics.size - 12} more"
            }
        }

        lines += ""
        lines += "── sleep_sessions (${snapshot.sleepSessions.size}) ──"
        if (snapshot.sleepSessions.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.sleepSessions.take(12).forEach { session ->
                val title = session.title ?: "Sleep"
                lines += "  ${session.date} · $title · ${formatMinutes(session.durationMs)}"
            }
            if (snapshot.sleepSessions.size > 12) {
                lines += "  … +${snapshot.sleepSessions.size - 12} more"
            }
        }

        lines += ""
        lines += "── calendar_events (${snapshot.calendarEvents.size}) ──"
        if (snapshot.calendarEvents.isEmpty()) {
            lines += "  (empty)"
        } else {
            snapshot.calendarEvents.take(12).forEach { event ->
                val whenLabel = formatEventTime(event)
                lines += "  ${event.date} · ${event.title} · $whenLabel"
            }
            if (snapshot.calendarEvents.size > 12) {
                lines += "  … +${snapshot.calendarEvents.size - 12} more"
            }
        }

        lines += ""
        lines += "total rows: ${snapshot.trackedApps.size + snapshot.blockEvents.size + snapshot.dailyUsage.size + snapshot.healthMetrics.size + snapshot.sleepSessions.size + snapshot.calendarEvents.size}"

        return lines.joinToString("\n")
    }

    private fun formatEventTime(event: CalendarEvent): String {
        if (event.allDay) {
            return "all-day"
        }
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(event.startMs).atZone(zone).format(timeFormatter)
        val end = Instant.ofEpochMilli(event.endMs).atZone(zone).format(timeFormatter)
        return "$start–$end"
    }

    private fun labelFor(
        context: Context,
        trackedLabels: Map<String, String>,
        packageName: String,
    ): String {
        return trackedLabels[packageName] ?: AppLabelResolver.resolve(context, packageName)
    }

    private fun formatMinutes(foregroundMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(foregroundMs)
        return "${minutes}m"
    }
}
