package com.foxtrotalpha.reelsblocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foxtrotalpha.reelsblocker.AppLabelResolver
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.data.AppBlockTotal
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.AppUsageTotal
import com.foxtrotalpha.reelsblocker.data.DailyBlockTotal
import com.foxtrotalpha.reelsblocker.data.DailyHealthMetrics
import com.foxtrotalpha.reelsblocker.data.DailyUnproductiveTotal
import com.foxtrotalpha.reelsblocker.data.SleepSession
import com.foxtrotalpha.reelsblocker.ui.chart.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val zone = ZoneId.systemDefault()
    private val range = MutableStateFlow(InsightsRange.DAYS_7)
    private val todayIso = MutableStateFlow(LocalDate.now(zone).toString())
    private val healthPermissionGranted = MutableStateFlow(false)
    private val healthConnectAvailable = MutableStateFlow(false)

    fun refreshForToday() {
        todayIso.value = LocalDate.now(zone).toString()
    }

    fun setRange(value: InsightsRange) {
        if (value == InsightsRange.DAYS_7) {
            range.value = value
        }
    }

    fun setHealthPermissionGranted(granted: Boolean) {
        healthPermissionGranted.value = granted
    }

    fun setHealthConnectAvailable(available: Boolean) {
        healthConnectAvailable.value = available
    }

    private val currentWindow = combine(todayIso, range) { iso, selected ->
        Window(LocalDate.parse(iso), selected)
    }

    private val currentData = currentWindow.flatMapLatest { window ->
        val healthFlow = combine(
            database.dailyHealthMetricsDao().observeBetween(window.fromIso, window.toIso),
            database.dailyAppUsageDao().observeUnproductiveTotals(window.fromIso, window.toIso),
            database.blockEventDao().observeCountsBetween(window.fromIso, window.toIso),
            database.sleepSessionDao().observeBetween(window.fromIso, window.toIso),
        ) { health, unproductive, blocks, sleep ->
            Quad(health, unproductive, blocks, sleep)
        }
        val appsFlow = combine(
            database.blockEventDao().observeBlocksByAppBetween(window.fromIso, window.toIso),
            database.dailyAppUsageDao().observeUnproductiveByAppBetween(window.fromIso, window.toIso),
            database.trackedAppDao().observeAll(),
        ) { blocksByApp, apps, tracked ->
            Triple(blocksByApp, apps, tracked.associate { it.packageName to it.label })
        }
        combine(healthFlow, appsFlow) { health, apps ->
            CurrentBundle(
                health = health.first,
                unproductive = health.second,
                blocks = health.third,
                sleep = health.fourth,
                blocksByApp = apps.first,
                apps = apps.second,
                labels = apps.third,
            )
        }
    }

    private val previousData = currentWindow.flatMapLatest { window ->
        combine(
            database.dailyHealthMetricsDao().observeBetween(window.prevFromIso, window.prevToIso),
            database.dailyAppUsageDao().observeUnproductiveTotals(window.prevFromIso, window.prevToIso),
            database.blockEventDao().observeCountsBetween(window.prevFromIso, window.prevToIso),
            database.sleepSessionDao().observeBetween(window.prevFromIso, window.prevToIso),
        ) { health, unproductive, blocks, sleep ->
            PreviousBundle(health, unproductive, blocks, sleep)
        }
    }

    val state: StateFlow<InsightsState> = combine(
        currentWindow,
        currentData,
        previousData,
        healthPermissionGranted,
        healthConnectAvailable,
    ) { window, current, previous, healthGranted, hcAvailable ->
        buildState(window, current, previous, healthGranted, hcAvailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    private fun buildState(
        window: Window,
        current: CurrentBundle,
        previous: PreviousBundle,
        healthGranted: Boolean,
        hcAvailable: Boolean,
    ): InsightsState {
        val context = getApplication<Application>()
        val dates = window.dates
        val stepsByDate = current.health.associate { it.date to it.stepCount }
        val screenByDate = current.unproductive.associate { it.date to it.totalMs }
        val blocksByDate = current.blocks.associate { it.date to it.total.toLong() }
        val sleepByDate = current.sleep.groupBy { it.date }.mapValues { (_, sessions) ->
            sessions.sumOf { it.durationMs }
        }

        val stepPoints = dates.map { date ->
            ChartPoint(
                label = DashboardFormatter.formatWeekdayLabel(date),
                value = (stepsByDate[date.toString()] ?: 0L).toFloat(),
                highlight = date == window.today,
            )
        }
        val screenPoints = dates.map { date ->
            ChartPoint(
                label = DashboardFormatter.formatWeekdayLabel(date),
                value = (screenByDate[date.toString()] ?: 0L).toFloat(),
                highlight = date == window.today,
            )
        }
        val blockPoints = dates.map { date ->
            ChartPoint(
                label = DashboardFormatter.formatWeekdayLabel(date),
                value = (blocksByDate[date.toString()] ?: 0L).toFloat(),
                highlight = date == window.today,
            )
        }
        val sleepPoints = dates.map { date ->
            ChartPoint(
                label = DashboardFormatter.formatWeekdayLabel(date),
                value = (sleepByDate[date.toString()] ?: 0L).toFloat(),
                highlight = date == window.today,
            )
        }

        val totalUnproductive = current.unproductive.sumOf { it.totalMs }
        val totalBlocks = current.blocks.sumOf { it.total }
        val stepValues = current.health.map { it.stepCount }.filter { it > 0 }
        val averageSteps = if (stepValues.isEmpty()) null else stepValues.average().toLong()
        val sleepDurations = sleepByDate.values.filter { it > 0 }
        val averageSleep = if (sleepDurations.isEmpty()) null else sleepDurations.average().toLong()

        val prevUnproductive = previous.unproductive.sumOf { it.totalMs }
        val prevBlocks = previous.blocks.sumOf { it.total }
        val prevSteps = previous.health.map { it.stepCount }.filter { it > 0 }
        val prevSleep = previous.sleep.groupBy { it.date }.mapValues { it.value.sumOf { s -> s.durationMs } }
            .values.filter { it > 0 }

        val appMax = current.apps.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
        val blockMax = current.blocksByApp.maxOfOrNull { it.total.toLong() }?.coerceAtLeast(1L) ?: 1L

        return InsightsState(
            range = window.range,
            thirtyDayEnabled = false,
            totalUnproductiveMs = totalUnproductive,
            totalBlocks = totalBlocks,
            averageSteps = averageSteps,
            averageSleepMs = averageSleep,
            unproductiveDelta = durationDelta(context, totalUnproductive, prevUnproductive, previous.unproductive.isNotEmpty()),
            blocksDelta = blocksDelta(context, totalBlocks, prevBlocks, previous.blocks.isNotEmpty()),
            stepsDelta = averageSteps?.let { currentAvg ->
                if (prevSteps.isEmpty()) null
                else {
                    val prevAvg = prevSteps.average().toLong()
                    durationOrCountDelta(context, currentAvg, prevAvg, isDuration = false)
                }
            },
            sleepDelta = averageSleep?.let { currentAvg ->
                if (prevSleep.isEmpty()) null
                else durationDelta(context, currentAvg, prevSleep.average().toLong(), true)
            },
            stepPoints = stepPoints,
            screenPoints = screenPoints,
            blockPoints = blockPoints,
            sleepPoints = sleepPoints,
            blocksByApp = current.blocksByApp.map { row ->
                RankedAppRow(
                    packageName = row.packageName,
                    label = current.labels[row.packageName]
                        ?: AppLabelResolver.resolve(context, row.packageName),
                    totalMs = row.total.toLong(),
                    fraction = row.total.toFloat() / blockMax.toFloat(),
                )
            },
            unproductiveApps = current.apps.take(6).map { row ->
                RankedAppRow(
                    packageName = row.packageName,
                    label = current.labels[row.packageName]
                        ?: AppLabelResolver.resolve(context, row.packageName),
                    totalMs = row.totalMs,
                    fraction = row.totalMs.toFloat() / appMax.toFloat(),
                )
            },
            hasStepData = current.health.any { it.stepCount > 0 },
            hasSleepData = current.sleep.isNotEmpty(),
            hasScreenData = totalUnproductive > 0L,
            hasBlockData = totalBlocks > 0,
            healthConnectAvailable = hcAvailable,
            healthPermissionGranted = healthGranted,
        )
    }

    private fun durationDelta(
        context: Application,
        current: Long,
        previous: Long,
        previousHasData: Boolean,
    ): InsightsDelta? {
        if (!previousHasData) return null
        val diff = current - previous
        if (diff == 0L) return null
        val formatted = DashboardFormatter.formatDurationMs(kotlin.math.abs(diff))
        val text = if (diff < 0) {
            context.getString(R.string.insights_delta_less, formatted)
        } else {
            context.getString(R.string.insights_delta_more, formatted)
        }
        return InsightsDelta(text)
    }

    private fun blocksDelta(
        context: Application,
        current: Int,
        previous: Int,
        previousHasData: Boolean,
    ): InsightsDelta? {
        if (!previousHasData) return null
        val diff = current - previous
        if (diff == 0) return null
        val text = if (diff < 0) {
            context.getString(R.string.insights_delta_blocks_less, kotlin.math.abs(diff))
        } else {
            context.getString(R.string.insights_delta_blocks_more, diff)
        }
        return InsightsDelta(text)
    }

    private fun durationOrCountDelta(
        context: Application,
        current: Long,
        previous: Long,
        isDuration: Boolean,
    ): InsightsDelta? {
        val diff = current - previous
        if (diff == 0L) return null
        val formatted = if (isDuration) {
            DashboardFormatter.formatDurationMs(kotlin.math.abs(diff))
        } else {
            DashboardFormatter.formatSteps(kotlin.math.abs(diff))
        }
        val text = if (diff < 0) {
            context.getString(R.string.insights_delta_less, formatted)
        } else {
            context.getString(R.string.insights_delta_more, formatted)
        }
        return InsightsDelta(text)
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private data class Window(
        val today: LocalDate,
        val range: InsightsRange,
    ) {
        val toIso: String = today.toString()
        val fromIso: String = today.minusDays(range.days - 1L).toString()
        val prevToIso: String = today.minusDays(range.days.toLong()).toString()
        val prevFromIso: String = today.minusDays((range.days * 2 - 1).toLong()).toString()
        val dates: List<LocalDate> = (0 until range.days).map { today.minusDays(range.days - 1L - it) }
    }

    private data class CurrentBundle(
        val health: List<DailyHealthMetrics>,
        val unproductive: List<DailyUnproductiveTotal>,
        val blocks: List<DailyBlockTotal>,
        val sleep: List<SleepSession>,
        val blocksByApp: List<AppBlockTotal>,
        val apps: List<AppUsageTotal>,
        val labels: Map<String, String>,
    )

    private data class PreviousBundle(
        val health: List<DailyHealthMetrics>,
        val unproductive: List<DailyUnproductiveTotal>,
        val blocks: List<DailyBlockTotal>,
        val sleep: List<SleepSession>,
    )
}
