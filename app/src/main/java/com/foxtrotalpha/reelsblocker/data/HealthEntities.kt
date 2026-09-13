package com.foxtrotalpha.reelsblocker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily health rollups from Health Connect (steps + calories).
 * One row per calendar day in the synced window.
 */
@Entity(tableName = "daily_health_metrics")
data class DailyHealthMetrics(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "step_count")
    val stepCount: Long,

    @ColumnInfo(name = "active_calories_kcal")
    val activeCaloriesKcal: Double?,

    @ColumnInfo(name = "total_calories_kcal")
    val totalCaloriesKcal: Double?,

    @ColumnInfo(name = "synced_at_ms")
    val syncedAtMs: Long,
)

/**
 * One sleep session from Health Connect (main sleep or nap).
 * [date] is the local wake date for grouping.
 */
@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "bedtime_ms")
    val bedtimeMs: Long,

    @ColumnInfo(name = "wake_ms")
    val wakeMs: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "synced_at_ms")
    val syncedAtMs: Long,
)
