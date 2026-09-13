package com.foxtrotalpha.reelsblocker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.DailyHealthMetrics
import com.foxtrotalpha.reelsblocker.data.SleepSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/**
 * Pulls steps, calories, and sleep from Health Connect and writes into Room.
 */
object HealthConnectSync {

    const val SYNC_DAYS = 7

    suspend fun syncAllOnStartup(context: Context): SyncResult {
        return withContext(Dispatchers.IO) {
            if (!HealthConnectPermissions.isHealthConnectAvailable(context)) {
                return@withContext SyncResult.Unavailable
            }

            val client = HealthConnectClient.getOrCreate(context)
            if (!HealthConnectPermissions.hasAnyPermission(client)) {
                return@withContext SyncResult.PermissionRequired
            }

            val syncWindow = buildSyncWindow(SYNC_DAYS)
            val dayRanges = syncWindow.dayRanges
            val syncedAtMs = System.currentTimeMillis()
            val sessions = mutableListOf<SleepSession>()

            val hasSteps = HealthConnectPermissions.hasStepsPermission(client)
            val hasActiveCalories = HealthConnectPermissions.hasActiveCaloriesPermission(client)
            val hasTotalCalories = HealthConnectPermissions.hasTotalCaloriesPermission(client)
            val hasSleep = HealthConnectPermissions.hasSleepPermission(client)

            val metrics = if (hasSteps || hasActiveCalories || hasTotalCalories) {
                aggregateMetricsByDay(
                    client = client,
                    periodStart = syncWindow.periodStart,
                    periodEnd = syncWindow.periodEnd,
                    validDates = syncWindow.validDates,
                    hasSteps = hasSteps,
                    hasActiveCalories = hasActiveCalories,
                    hasTotalCalories = hasTotalCalories,
                    syncedAtMs = syncedAtMs,
                )
            } else {
                emptyList()
            }

            if (hasSleep && dayRanges.isNotEmpty()) {
                val windowStart = Instant.ofEpochMilli(dayRanges.first().startMs)
                val windowEnd = Instant.ofEpochMilli(dayRanges.last().endMs)
                val sleepRecords = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd),
                    ),
                ).records

                sleepRecords.forEach { record ->
                    val wakeMs = record.endTime.toEpochMilli()
                    val bedtimeMs = record.startTime.toEpochMilli()
                    sessions += SleepSession(
                        date = AppDatabase.isoDate(wakeMs),
                        bedtimeMs = bedtimeMs,
                        wakeMs = wakeMs,
                        durationMs = wakeMs - bedtimeMs,
                        title = record.title,
                        syncedAtMs = syncedAtMs,
                    )
                }
            }

            val database = AppDatabase.get(context)
            val metricsDao = database.dailyHealthMetricsDao()
            val sleepDao = database.sleepSessionDao()

            val fromDate = dayRanges.first().date
            val toDate = dayRanges.last().date
            metricsDao.deleteRange(fromDate, toDate)
            if (metrics.isNotEmpty()) {
                metricsDao.upsertAll(metrics)
            }

            val fromWakeMs = dayRanges.first().startMs
            val toWakeMs = dayRanges.last().endMs
            sleepDao.deleteWakeRange(fromWakeMs, toWakeMs)
            if (sessions.isNotEmpty()) {
                sleepDao.insertAll(sessions)
            }

            SyncResult.Success(metrics.size, sessions.size)
        }
    }

    private suspend fun aggregateMetricsByDay(
        client: HealthConnectClient,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime,
        validDates: Set<String>,
        hasSteps: Boolean,
        hasActiveCalories: Boolean,
        hasTotalCalories: Boolean,
        syncedAtMs: Long,
    ): List<DailyHealthMetrics> {
        val requestedMetrics = buildSet {
            if (hasSteps) add(StepsRecord.COUNT_TOTAL)
            if (hasActiveCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (hasTotalCalories) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }

        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = requestedMetrics,
                timeRangeFilter = TimeRangeFilter.between(periodStart, periodEnd),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )

        return buckets
            .map { bucket ->
                val date = bucket.startTime.toLocalDate().toString()
                DailyHealthMetrics(
                    date = date,
                    stepCount = if (hasSteps) bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L else 0L,
                    activeCaloriesKcal = if (hasActiveCalories) {
                        bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    } else {
                        null
                    },
                    totalCaloriesKcal = if (hasTotalCalories) {
                        bucket.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    } else {
                        null
                    },
                    syncedAtMs = syncedAtMs,
                )
            }
            .filter { it.date in validDates }
    }

    private data class DayRange(
        val date: String,
        val startMs: Long,
        val endMs: Long,
    )

    private data class SyncWindow(
        val dayRanges: List<DayRange>,
        val periodStart: LocalDateTime,
        val periodEnd: LocalDateTime,
        val validDates: Set<String>,
    )

    private fun buildSyncWindow(dayCount: Int): SyncWindow {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val oldestDay = today.minusDays(dayCount - 1L)

        val dayRanges = (0 until dayCount).map { offset ->
            val day = oldestDay.plusDays(offset.toLong())
            val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

            DayRange(
                date = day.toString(),
                startMs = startMs,
                endMs = endMs,
            )
        }

        return SyncWindow(
            dayRanges = dayRanges,
            periodStart = oldestDay.atStartOfDay(),
            periodEnd = today.atTime(23, 59, 59, 999_000_000),
            validDates = dayRanges.map { it.date }.toSet(),
        )
    }

    sealed class SyncResult {
        data object Unavailable : SyncResult()
        data object PermissionRequired : SyncResult()
        data class Success(val metricCount: Int, val sessionCount: Int) : SyncResult()
    }
}
