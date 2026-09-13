package com.foxtrotalpha.reelsblocker.ui

import com.foxtrotalpha.reelsblocker.data.CalendarEvent

data class WeekBar(
    val date: String,
    val label: String,
    val value: Long,
    val isToday: Boolean,
)

data class ScreenTimeRow(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
)

data class DashboardState(
    val todayDateLabel: String = "",
    val greeting: String = "",
    val heroSummary: String = "",
    val blocksToday: Int = 0,
    val stepsToday: Long? = null,
    val activeCaloriesToday: Double? = null,
    val unproductiveMsToday: Long = 0L,
    val weekStepBars: List<WeekBar> = emptyList(),
    val weekUnproductiveBars: List<WeekBar> = emptyList(),
    val topAppsToday: List<ScreenTimeRow> = emptyList(),
    val sleepSummary: String? = null,
    val sleepBedtime: String? = null,
    val sleepWake: String? = null,
    val sleepDurationMs: Long? = null,
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val calendarOverflowCount: Int = 0,
    val healthConnectAvailable: Boolean = false,
    val healthPermissionGranted: Boolean = false,
    val calendarPermissionGranted: Boolean = false,
    val hasStepData: Boolean = false,
    val hasSleepData: Boolean = false,
    val serviceEnabled: Boolean = false,
    val blockingEnabled: Boolean = true,
)
