package com.foxtrotalpha.reelsblocker.voice

import android.content.Context
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector

internal object RoastContextBuilder {

    suspend fun build(
        context: Context,
        packageName: String,
        reason: ReelsDetector.Result.Reason?,
        recentLines: List<String>,
    ): RoastContext {
        val db = AppDatabase.get(context)
        val date = AppDatabase.isoDate()
        val now = System.currentTimeMillis()

        val health = db.dailyHealthMetricsDao().forDate(date)
        val sleepMs = db.sleepSessionDao().forWakeDate(date).maxOfOrNull { it.durationMs }
        val events = db.calendarEventDao().forDate(date)
        val upcoming = events
            .filter { !it.allDay && it.endMs >= now }
            .sortedBy { it.startMs }
        val next = upcoming.firstOrNull { it.startMs > now }

        return RoastContext(
            appLabel = AppLabelResolver.resolve(context, packageName),
            reason = reason?.name ?: "UNKNOWN",
            blocksToday = db.blockEventDao().countForDateOnce(date),
            unproductiveMsToday = db.dailyAppUsageDao().unproductiveTotalForDate(date),
            stepsToday = health?.stepCount,
            sleepMsLastNight = sleepMs,
            remainingEventsToday = upcoming.size,
            minutesUntilNextEvent = next?.let { ((it.startMs - now) / 60_000L).coerceAtLeast(0) },
            recentLines = recentLines,
        )
    }
}
