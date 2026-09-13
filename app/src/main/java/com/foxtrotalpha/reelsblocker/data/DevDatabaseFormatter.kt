package com.foxtrotalpha.reelsblocker.data

import android.content.Context
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import java.util.concurrent.TimeUnit

object DevDatabaseFormatter {

    fun format(
        context: Context,
        trackedApps: List<TrackedApp>,
        blockEvents: List<BlockEvent>,
        dailyUsage: List<DailyAppUsage>,
    ): String {
        val trackedLabels = trackedApps.associate { it.packageName to it.label }
        val lines = mutableListOf<String>()

        lines += "── tracked_apps (${trackedApps.size}) ──"
        if (trackedApps.isEmpty()) {
            lines += "  (empty)"
        } else {
            trackedApps.forEach { app ->
                val flag = if (app.isUnproductive) "unproductive" else "neutral"
                lines += "  ${app.label} · $flag"
            }
        }

        lines += ""
        lines += "── block_events (${blockEvents.size}) ──"
        if (blockEvents.isEmpty()) {
            lines += "  (empty)"
        } else {
            blockEvents.take(12).forEach { event ->
                lines += "  #${event.id} ${event.date} · ${labelFor(context, trackedLabels, event.packageName)} · ${event.reason}"
            }
            if (blockEvents.size > 12) {
                lines += "  … +${blockEvents.size - 12} more"
            }
        }

        lines += ""
        lines += "── daily_app_usage (${dailyUsage.size}) ──"
        if (dailyUsage.isEmpty()) {
            lines += "  (empty)"
        } else {
            dailyUsage.take(12).forEach { usage ->
                lines += "  ${usage.date} · ${labelFor(context, trackedLabels, usage.packageName)} · ${formatMinutes(usage.foregroundMs)}"
            }
            if (dailyUsage.size > 12) {
                lines += "  … +${dailyUsage.size - 12} more"
            }
        }

        lines += ""
        lines += "total rows: ${trackedApps.size + blockEvents.size + dailyUsage.size}"

        return lines.joinToString("\n")
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
