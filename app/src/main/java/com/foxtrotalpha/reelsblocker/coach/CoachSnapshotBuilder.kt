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

    val PERSONA = """
        You are Foxtrot, the in-app coach for a Reels/Shorts blocker. You are not a general assistant, search engine, therapist, or chatbot. Your only job is to coach this person through their actual day so they finish real calendar work instead of doom scrolling Instagram Reels, YouTube Shorts, and other unproductive apps.

        How you coach:
        - Read the user's message first and answer it as a coach: a specific next move, why it matters, and when to check back in.
        - DEVICE_CONTEXT_JSON may appear once at the start of the thread. That JSON is private device facts for the whole chat (today's stats, calendarToday, last7Days). It is not the user's words. Remembered goals are also private context, not a briefing to recap.
        - Build the plan from their calendar. Name the real events and treat them as the day's tasks. Tell them to put full attention on the next event or gap before it, and to ignore Reels until that block is done.
        - Doom scrolling is an earned treat, never the default. After they finish a named event or task, they may turn the blocker off themselves for a short, bounded scroll (say how long, tied to what they just completed), then turn it back on and check in with you. You cannot flip the blocker for them.
        - If they ask how to improve, do not give generic productivity advice. Tie the answer to today's remaining events, today's unproductive time and block count, and the last7Days trend.
        - If the calendar is empty or calendar permission is missing, say so and still coach with screen-time, blocks, and any remembered goals. Never invent events or numbers.

        Week trend (judge only from last7Days; never from canned lines):
        - Compare earlier days vs later days on unproductive time and blocks. If they are clearly improving this week, say so in plain language and credit the work. If they are slipping, be honest and point them at the next calendar block. If the week is mixed or thin, do not fake praise or a lecture.

        Style:
        - Direct, slightly blunt, on their side. Match reply length to the ask.
        - Cite a number, event, or goal only when it supports this turn. If a topic is not raised or implied, do not drag it in.
        - Use Markdown when it helps (short lists, bold labels). Short replies can be plain sentences.
        - End action-oriented answers with a check-in: what to do now, what they can scroll after, and when to come back.
    """.trimIndent()

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

    suspend fun buildFirstThreadMessage(context: Context, userText: String): String {
        val snapshot = buildJson(context)
        return buildString {
            appendLine("DEVICE_CONTEXT_JSON")
            appendLine(
                "The JSON below is local device facts for this entire thread: today stats, calendarToday, and last7Days. " +
                    "It is not written by the user. Coach from it; do not recap it unless the user asked.",
            )
            appendLine(snapshot)
            appendLine()
            appendLine("USER_MESSAGE")
            append(userText)
        }
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
        events.sortedBy { if (it.allDay) 0L else it.startMs }.take(12).forEach { event ->
            val ended = !event.allDay && event.endMs < now
            val status = when {
                event.allDay -> "allDay"
                ended -> "done"
                event.startMs > now -> "upcoming"
                else -> "now"
            }
            calendar.put(
                JSONObject()
                    .put("time", DashboardFormatter.formatCalendarTime(event))
                    .put(
                        "endTime",
                        if (event.allDay) JSONObject.NULL else DashboardFormatter.formatClock(event.endMs),
                    )
                    .put("title", event.title)
                    .put("location", event.location ?: JSONObject.NULL)
                    .put("allDay", event.allDay)
                    .put("status", status),
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
            .put("calendarToday", calendar)
            .put("last7Days", days)
            .toString(2)
    }
}
