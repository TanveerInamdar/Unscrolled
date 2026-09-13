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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val zone = ZoneId.systemDefault()

    private val healthPermissionGranted = MutableStateFlow(false)
    private val calendarPermissionGranted = MutableStateFlow(false)
    private val healthConnectAvailable = MutableStateFlow(false)
    private val serviceEnabled = MutableStateFlow(false)
    private val blockingEnabled = MutableStateFlow(true)

    private val todayIso = MutableStateFlow(LocalDate.now(zone).toString())

    fun refreshForToday() {
        todayIso.value = LocalDate.now(zone).toString()
    }

    private val coreData = todayIso.flatMapLatest { iso ->
        val today = LocalDate.parse(iso)
        combine(
            database.blockEventDao().countForDate(iso),
            database.dailyHealthMetricsDao().observeForDate(iso),
            database.dailyHealthMetricsDao().observeBetween(today.minusDays(6).toString(), iso),
            database.dailyAppUsageDao().observeUnproductiveTotalForDate(iso),
            database.dailyAppUsageDao().observeUnproductiveTotals(today.minusDays(6).toString(), iso),
        ) { blocksToday, healthToday, healthWeek, unproductiveToday, unproductiveWeek ->
            CoreData(
                blocksToday = blocksToday,
                healthToday = healthToday,
                healthWeek = healthWeek,
                unproductiveToday = unproductiveToday,
                unproductiveWeek = unproductiveWeek,
            )
        }
    }

    private val detailData = todayIso.flatMapLatest { iso ->
        combine(
            database.dailyAppUsageDao().observeForDate(iso),
            database.sleepSessionDao().observeForWakeDate(iso),
            database.calendarEventDao().observeForDate(iso),
            database.trackedAppDao().observeAll(),
        ) { usageToday, sleepSessions, calendarEvents, trackedApps ->
            DetailData(
                usageToday = usageToday,
                sleepSessions = sleepSessions,
                calendarEvents = calendarEvents,
                trackedLabels = trackedApps.associate { it.packageName to it.label },
            )
        }
    }

    private val permissionData = combine(
        healthPermissionGranted,
        calendarPermissionGranted,
        healthConnectAvailable,
        serviceEnabled,
        blockingEnabled,
    ) { healthGranted, calendarGranted, hcAvailable, serviceOn, blockingOn ->
        PermissionData(
            healthGranted = healthGranted,
            calendarGranted = calendarGranted,
            hcAvailable = hcAvailable,
            serviceEnabled = serviceOn,
            blockingEnabled = blockingOn,
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

    fun setFocusProtectionState(serviceEnabled: Boolean, blockingEnabled: Boolean) {
        this.serviceEnabled.value = serviceEnabled
        this.blockingEnabled.value = blockingEnabled
    }

    private fun buildState(
        core: CoreData,
        detail: DetailData,
        permissions: PermissionData,
    ): DashboardState {
        val context = getApplication<Application>()
        val today = LocalDate.parse(todayIso.value)
        val weekDates = (0..6).map { today.minusDays(6 - it.toLong()) }
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
        val primarySleep = DashboardFormatter.primarySleep(detail.sleepSessions)

        return DashboardState(
            todayDateLabel = DashboardFormatter.formatTodayDate(today),
            greeting = DashboardFormatter.greetingFor(context),
            heroSummary = DashboardFormatter.heroSummary(
                context = context,
                blocksToday = core.blocksToday,
                unproductiveMsToday = core.unproductiveToday,
                stepsToday = core.healthToday?.stepCount,
            ),
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
            sleepBedtime = primarySleep?.let { DashboardFormatter.formatClock(it.bedtimeMs) },
            sleepWake = primarySleep?.let { DashboardFormatter.formatClock(it.wakeMs) },
            sleepDurationMs = primarySleep?.durationMs,
            calendarEvents = calendarVisible,
            calendarOverflowCount = calendarOverflow,
            healthConnectAvailable = permissions.hcAvailable,
            healthPermissionGranted = permissions.healthGranted,
            calendarPermissionGranted = permissions.calendarGranted,
            hasStepData = core.healthToday != null,
            hasSleepData = detail.sleepSessions.isNotEmpty(),
            serviceEnabled = permissions.serviceEnabled,
            blockingEnabled = permissions.blockingEnabled,
        )
    }

    private fun buildWeekBars(
        weekDates: List<LocalDate>,
        valuesByDate: Map<String, Long>,
    ): List<WeekBar> {
        val today = LocalDate.parse(todayIso.value)
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
        val serviceEnabled: Boolean,
        val blockingEnabled: Boolean,
    )
}
