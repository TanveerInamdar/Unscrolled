package com.foxtrotalpha.reelsblocker.ui

import com.foxtrotalpha.reelsblocker.ui.chart.ChartPoint

enum class InsightsRange(val days: Int) {
    DAYS_7(7),
    DAYS_30(30),
}

data class InsightsDelta(
    val text: String,
)

data class RankedAppRow(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val fraction: Float,
)

data class InsightsState(
    val range: InsightsRange = InsightsRange.DAYS_7,
    val thirtyDayEnabled: Boolean = false,
    val totalUnproductiveMs: Long = 0L,
    val totalBlocks: Int = 0,
    val averageSteps: Long? = null,
    val averageSleepMs: Long? = null,
    val unproductiveDelta: InsightsDelta? = null,
    val blocksDelta: InsightsDelta? = null,
    val stepsDelta: InsightsDelta? = null,
    val sleepDelta: InsightsDelta? = null,
    val stepPoints: List<ChartPoint> = emptyList(),
    val screenPoints: List<ChartPoint> = emptyList(),
    val blockPoints: List<ChartPoint> = emptyList(),
    val sleepPoints: List<ChartPoint> = emptyList(),
    val blocksByApp: List<RankedAppRow> = emptyList(),
    val unproductiveApps: List<RankedAppRow> = emptyList(),
    val hasStepData: Boolean = false,
    val hasSleepData: Boolean = false,
    val hasScreenData: Boolean = false,
    val hasBlockData: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthPermissionGranted: Boolean = false,
)
