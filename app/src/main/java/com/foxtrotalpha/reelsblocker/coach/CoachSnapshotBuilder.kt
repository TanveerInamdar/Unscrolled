package com.foxtrotalpha.reelsblocker.coach

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import com.foxtrotalpha.reelsblocker.calendar.CalendarAccessUtils
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.health.HealthConnectPermissions
import com.foxtrotalpha.reelsblocker.ui.DashboardFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

internal object CoachSnapshotBuilder {

    const val PERSONA =
        "You are Foxtrot, a productivity coach in a Reels/Shorts blocker. " +
            "Read the user's message first. Answer what they said and what it clearly implies. " +
            "The CURRENT SNAPSHOT (when present) and remembered goals are private context, not a briefing to recap. " +
            "Cite a number or a goal only when it supports the topic of this turn. " +
            "If a topic is not raised or implied, do not mention it. " +
            "Match reply length to the ask. Be slightly blunt. Never invent numbers; if a needed source is missing, say so. " +
            "Use Markdown when it helps (short lists, bold labels); short replies can be plain sentences."

    const val FACT_EXTRACTION_PROMPT =
        "Extract ONLY durable user intent, goals, commitments, and coaching preferences from what the USER said. " +
            "Good examples: wants to sleep on time; wants to cut late-night scrolling; prefers blunt coaching. " +
            "Write each fact as a short present-tense statement about the user. " +
            "NEVER extract step counts, calories, sleep duration, bed/wake times from device data, " +
            "screen-time totals, app minutes, block counts, calendar events, JSON snapshots, or any statistic " +
            "the assistant recited. If the user only asked about stats and stated no goal, extract nothing."

    const val MEMORY_UPDATE_PROMPT =
        "Add or update memories only for lasting goals, commitments, and coaching style. " +
            "When the user restates a goal, update the existing memory instead of duplicating it. " +
            "Do not add or keep memories that are numeric metrics, daily/weekly stats, snapshots, or calendar contents. " +
            "If an existing memory is a metric or a one-day number, delete it."

    suspend fun buildSystemPrompt(context: Context): String {
        val snapshot = buildJson(context)
        return "$PERSONA\n\nCURRENT SNAPSHOT (private context; 7 days of local device data):\n$snapshot"
    }

    private suspend fun buildJson(context: Context): String {
        val db = AppDatabase.get(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayIso = today.toString()
        val weekStartIso = today.minusDays(6).toString()
        val now = System.currentTimeMillis()

        val healthAvailable = HealthConnectPermissions.isHealthConnectAvailable(context)
        var healthGranted = false
        if (healthAvailable) {
            val client = HealthConnectClient.getOrCreate(context.applicationContext)
            healthGranted = HealthConnectPermissions.hasAnyPermission(client)
        }
        val calendarGranted = CalendarAccessUtils.hasReadCalendarPermission(context)

        val healthToday = db.dailyHealthMetricsDao().forDate(todayIso)
        val healthWeek = db.dailyHealthMetricsDao().between(weekStartIso, todayIso)
            .associateBy { it.date }
        val unproductiveWeek = db.dailyAppUsageDao().unproductiveTotals(weekStartIso, todayIso)
            .associate { it.date to it.totalMs }
        val blockWeek = db.blockEventDao().countsBetween(weekStartIso, todayIso)
            .associate { it.date to it.total }
        val usageToday = db.dailyAppUsageDao().forDate(todayIso)
        val tracked = db.trackedAppDao().all().associate { it.packageName to it.label }
        val sleepSessions = db.sleepSessionDao().forWakeDate(todayIso)
        val events = if (calendarGranted) db.calendarEventDao().forDate(todayIso) else emptyList()
        val upcoming = events.filter { !it.allDay && it.endMs >= now }.sortedBy { it.startMs }

        val days = JSONArray()
        for (offset in 6 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val iso = date.toString()
            val health = healthWeek[iso]
            days.put(
                JSONObject()
                    .put("date", iso)
                    .put("weekday", DashboardFormatter.formatWeekdayLabel(date))
                    .put("isToday", date == today)
                    .put("unproductive", DashboardFormatter.formatDurationMs(unproductiveWeek[iso] ?: 0L))
                    .put("blocks", blockWeek[iso] ?: 0)
                    .put(
                        "steps",
                        if (healthGranted) health?.stepCount ?: JSONObject.NULL else JSONObject.NULL,
                    ),
            )
        }

        val topApps = JSONArray()
        usageToday.take(5).forEach { usage ->
            val label = tracked[usage.packageName]
                ?: AppLabelResolver.resolve(context, usage.packageName)
            topApps.put(
                JSONObject()
                    .put("app", label)
                    .put("time", DashboardFormatter.formatDurationMs(usage.foregroundMs)),
            )
        }

        val calendar = JSONArray()
        upcoming.take(8).forEach { event ->
            calendar.put(
                JSONObject()
                    .put("time", DashboardFormatter.formatCalendarTime(event))
                    .put("title", event.title)
                    .put("location", event.location ?: JSONObject.NULL),
            )
        }

        return JSONObject()
            .put("today", todayIso)
            .put(
                "permissions",
                JSONObject()
                    .put("healthConnectAvailable", healthAvailable)
                    .put("healthGranted", healthGranted)
                    .put("calendarGranted", calendarGranted),
            )
            .put(
                "todaySummary",
                JSONObject()
                    .put("unproductive", DashboardFormatter.formatDurationMs(unproductiveWeek[todayIso] ?: 0L))
                    .put("blocks", blockWeek[todayIso] ?: 0)
                    .put(
                        "steps",
                        if (healthGranted) healthToday?.stepCount ?: JSONObject.NULL else JSONObject.NULL,
                    )
                    .put(
                        "activeCalories",
                        if (healthGranted) healthToday?.activeCaloriesKcal ?: JSONObject.NULL else JSONObject.NULL,
                    )
                    .put(
                        "sleepLastNight",
                        DashboardFormatter.formatSleepSummary(sleepSessions) ?: JSONObject.NULL,
                    ),
            )
            .put("topAppsToday", topApps)
            .put("calendarRemainingToday", calendar)
            .put("last7Days", days)
            .toString(2)
    }
}
