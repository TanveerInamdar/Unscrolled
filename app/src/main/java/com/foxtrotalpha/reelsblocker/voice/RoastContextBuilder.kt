package com.foxtrotalpha.reelsblocker.voice

import android.content.Context
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector
import kotlin.random.Random

internal object RoastContextBuilder {

    private val youtubePackages = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "app.revanced.android.youtube",
    )

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
        val usageByPackage = db.dailyAppUsageDao().forDate(date)
            .associate { it.packageName to it.foregroundMs }

        val steps = health?.stepCount
        return RoastContext(
            appLabel = AppLabelResolver.resolve(context, packageName),
            reason = reason?.name ?: "UNKNOWN",
            blocksToday = db.blockEventDao().countForDateOnce(date),
            unproductiveMsToday = db.dailyAppUsageDao().unproductiveTotalForDate(date),
            blockedAppMsToday = usageByPackage[packageName] ?: 0L,
            instagramMsToday = usageByPackage["com.instagram.android"] ?: 0L,
            youtubeMsToday = youtubePackages.sumOf { usageByPackage[it] ?: 0L },
            stepsToday = steps,
            sleepMsLastNight = sleepMs,
            remainingEventsToday = upcoming.size,
            minutesUntilNextEvent = next?.let { ((it.startMs - now) / 60_000L).coerceAtLeast(0) },
            focus = pickFocus(sleepMs != null, steps != null),
            recentLines = recentLines,
        )
    }

    private fun pickFocus(hasSleep: Boolean, hasSteps: Boolean): RoastFocus {
        val weighted = buildList {
            add(RoastFocus.BLOCKS)
            add(RoastFocus.BLOCKS)
            add(RoastFocus.APP_SCREEN_TIME)
            add(RoastFocus.APP_SCREEN_TIME)
            add(RoastFocus.UNPRODUCTIVE)
            if (hasSleep) {
                add(RoastFocus.SLEEP)
                add(RoastFocus.SLEEP)
            }
            if (hasSteps) {
                add(RoastFocus.STEPS)
            }
        }
        return weighted[Random.nextInt(weighted.size)]
    }
}
