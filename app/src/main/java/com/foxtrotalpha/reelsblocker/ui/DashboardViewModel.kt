package com.foxtrotalpha.reelsblocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.CalendarEvent
import com.foxtrotalpha.reelsblocker.data.DailyAppUsage
import com.foxtrotalpha.reelsblocker.data.DailyHealthMetrics
import com.foxtrotalpha.reelsblocker.data.DailyUnproductiveTotal
import com.foxtrotalpha.reelsblocker.data.SleepSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val zone = ZoneId.systemDefault()

    private val healthPermissionGranted = MutableStateFlow(false)
    private val calendarPermissionGranted = MutableStateFlow(false)
    private val healthConnectAvailable = MutableStateFlow(false)

    private val today = LocalDate.now(zone)
    private val todayIso = today.toString()
    private val weekStartIso = today.minusDays(6).toString()
    private val weekDates = (0..6).map { today.minusDays(6 - it.toLong()) }

    private val coreData = combine(
        database.blockEventDao().countForDate(todayIso),
        database.dailyHealthMetricsDao().observeForDate(todayIso),
        database.dailyHealthMetricsDao().observeBetween(weekStartIso, todayIso),
        database.dailyAppUsageDao().observeUnproductiveTotalForDate(todayIso),
        database.dailyAppUsageDao().observeUnproductiveTotals(weekStartIso, todayIso),
    ) { blocksToday, healthToday, healthWeek, unproductiveToday, unproductiveWeek ->
        CoreData(
            blocksToday = blocksToday,
            healthToday = healthToday,
            healthWeek = healthWeek,
            unproductiveToday = unproductiveToday,
            unproductiveWeek = unproductiveWeek,
        )
    }

    private val detailData = combine(
        database.dailyAppUsageDao().observeForDate(todayIso),
        database.sleepSessionDao().observeForWakeDate(todayIso),
        database.calendarEventDao().observeForDate(todayIso),
        database.trackedAppDao().observeAll(),
    ) { usageToday, sleepSessions, calendarEvents, trackedApps ->
        DetailData(
            usageToday = usageToday,
            sleepSessions = sleepSessions,
            calendarEvents = calendarEvents,
            trackedLabels = trackedApps.associate { it.packageName to it.label },
        )
    }

    private val permissionData = combine(
        healthPermissionGranted,
        calendarPermissionGranted,
        healthConnectAvailable,
    ) { healthGranted, calendarGranted, hcAvailable ->
        PermissionData(
            healthGranted = healthGranted,
            calendarGranted = calendarGranted,
            hcAvailable = hcAvailable,
        )
    }

    val state: StateFlow<DashboardState> = combine(
        coreData,
        detailData,
        permissionData,
    ) { core, detail, permissions ->
        buildState(core, detail, permissions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setHealthPermissionGranted(granted: Boolean) {
        healthPermissionGranted.value = granted
    }

    fun setCalendarPermissionGranted(granted: Boolean) {
        calendarPermissionGranted.value = granted
    }

    fun setHealthConnectAvailable(available: Boolean) {
        healthConnectAvailable.value = available
    }

    private fun buildState(
        core: CoreData,
        detail: DetailData,
        permissions: PermissionData,
    ): DashboardState {
        val context = getApplication<Application>()
        val topApps = detail.usageToday
            .take(5)
            .map { usage ->
                ScreenTimeRow(
                    packageName = usage.packageName,
                    label = detail.trackedLabels[usage.packageName]
                        ?: AppLabelResolver.resolve(context, usage.packageName),
                    foregroundMs = usage.foregroundMs,
                )
            }

        val calendarCap = 8
        val calendarVisible = detail.calendarEvents.take(calendarCap)
        val calendarOverflow = (detail.calendarEvents.size - calendarCap).coerceAtLeast(0)

        return DashboardState(
            todayDateLabel = DashboardFormatter.formatTodayDate(today),
            blocksToday = core.blocksToday,
            stepsToday = core.healthToday?.stepCount,
            activeCaloriesToday = core.healthToday?.activeCaloriesKcal,
            unproductiveMsToday = core.unproductiveToday,
            weekStepBars = buildWeekBars(
                weekDates = weekDates,
                valuesByDate = core.healthWeek.associate { it.date to it.stepCount },
            ),
            weekUnproductiveBars = buildWeekBars(
                weekDates = weekDates,
                valuesByDate = core.unproductiveWeek.associate { it.date to it.totalMs },
            ),
            topAppsToday = topApps,
            sleepSummary = DashboardFormatter.formatSleepSummary(detail.sleepSessions),
            calendarEvents = calendarVisible,
            calendarOverflowCount = calendarOverflow,
            healthConnectAvailable = permissions.hcAvailable,
            healthPermissionGranted = permissions.healthGranted,
            calendarPermissionGranted = permissions.calendarGranted,
            hasStepData = core.healthToday != null,
            hasSleepData = detail.sleepSessions.isNotEmpty(),
        )
    }

    private fun buildWeekBars(
        weekDates: List<LocalDate>,
        valuesByDate: Map<String, Long>,
    ): List<WeekBar> {
        return weekDates.map { date ->
            val iso = date.toString()
            WeekBar(
                date = iso,
                label = DashboardFormatter.formatWeekdayLabel(date),
                value = valuesByDate[iso] ?: 0L,
                isToday = date == today,
            )
        }
    }

    private data class CoreData(
        val blocksToday: Int,
        val healthToday: DailyHealthMetrics?,
        val healthWeek: List<DailyHealthMetrics>,
        val unproductiveToday: Long,
        val unproductiveWeek: List<DailyUnproductiveTotal>,
    )

    private data class DetailData(
        val usageToday: List<DailyAppUsage>,
        val sleepSessions: List<SleepSession>,
        val calendarEvents: List<CalendarEvent>,
        val trackedLabels: Map<String, String> = emptyMap(),
    )

    private data class PermissionData(
        val healthGranted: Boolean,
        val calendarGranted: Boolean,
        val hcAvailable: Boolean,
    )
}
