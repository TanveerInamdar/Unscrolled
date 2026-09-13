package com.foxtrotalpha.reelsblocker.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * Computes per-app foreground time for one calendar day using [UsageStatsManager.queryEvents].
 *
 * Pairing resume/pause events (not pre-aggregated [UsageStatsManager.queryUsageStats]) tracks
 * Digital Wellbeing more closely. Sessions are clamped to the day window and ignore time while
 * the screen is off or the keyguard is showing.
 */
object ScreenTimeCollector {

    private val LOOKBACK_BUFFER_MS = TimeUnit.HOURS.toMillis(1)
    private val MAX_OPEN_SESSION_MS = TimeUnit.HOURS.toMillis(1)

    fun collectForDay(
        usageStatsManager: UsageStatsManager,
        dayStartMs: Long,
        dayEndMs: Long,
    ): Map<String, Long> {
        val events = usageStatsManager.queryEvents(
            dayStartMs - LOOKBACK_BUFFER_MS,
            dayEndMs,
        )

        val usageMs = mutableMapOf<String, Long>()
        val foregroundStartMs = mutableMapOf<String, Long>()
        var screenInteractive = true
        var keyguardHidden = true

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val packageName = event.packageName ?: continue
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    keyguardHidden = false
                    flushForegroundSessions(
                        foregroundStartMs,
                        usageMs,
                        timestamp,
                        dayStartMs,
                        dayEndMs,
                    )
                }

                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    keyguardHidden = true
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    screenInteractive = false
                    flushForegroundSessions(
                        foregroundStartMs,
                        usageMs,
                        timestamp,
                        dayStartMs,
                        dayEndMs,
                    )
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenInteractive = true
                }

                else -> {
                    if (!screenInteractive || !keyguardHidden) {
                        continue
                    }

                    if (isResumeEvent(event.eventType)) {
                        foregroundStartMs[packageName] = timestamp
                    } else if (isPauseEvent(event.eventType)) {
                        val startMs = foregroundStartMs.remove(packageName) ?: continue
                        addSession(usageMs, packageName, startMs, timestamp, dayStartMs, dayEndMs)
                    }
                }
            }
        }

        if (screenInteractive && keyguardHidden) {
            val effectiveEndMs = minOf(System.currentTimeMillis(), dayEndMs)
            foregroundStartMs.forEach { (packageName, startMs) ->
                val cappedEndMs = minOf(
                    effectiveEndMs,
                    startMs + MAX_OPEN_SESSION_MS,
                )
                addSession(usageMs, packageName, startMs, cappedEndMs, dayStartMs, dayEndMs)
            }
        }

        return usageMs.filterValues { it > 0L }
    }

    private fun flushForegroundSessions(
        foregroundStartMs: MutableMap<String, Long>,
        usageMs: MutableMap<String, Long>,
        timestamp: Long,
        dayStartMs: Long,
        dayEndMs: Long,
    ) {
        foregroundStartMs.forEach { (packageName, startMs) ->
            addSession(usageMs, packageName, startMs, timestamp, dayStartMs, dayEndMs)
        }
        foregroundStartMs.clear()
    }

    private fun addSession(
        usageMs: MutableMap<String, Long>,
        packageName: String,
        startMs: Long,
        endMs: Long,
        dayStartMs: Long,
        dayEndMs: Long,
    ) {
        val clampedStart = maxOf(startMs, dayStartMs)
        val clampedEnd = minOf(endMs, dayEndMs)
        if (clampedEnd <= clampedStart) {
            return
        }

        usageMs[packageName] = (usageMs[packageName] ?: 0L) + (clampedEnd - clampedStart)
    }

    private fun isResumeEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun isPauseEvent(eventType: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }
}
