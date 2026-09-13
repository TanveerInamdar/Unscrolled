package com.foxtrotalpha.reelsblocker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per block action performed by the accessibility service.
 * The raw event stream; everything else can be derived from it.
 */
@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Epoch millis when the block happened. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    /** Local calendar date in ISO format (yyyy-MM-dd) for cheap daily grouping. */
    @ColumnInfo(name = "date")
    val date: String,

    /** Package of the app that was blocked, e.g. com.instagram.android. */
    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Detection reason, e.g. PLAYER_VISIBLE or TAB_SELECTED. */
    @ColumnInfo(name = "reason")
    val reason: String,
)

/**
 * Daily foreground time per app, in milliseconds.
 *
 * Granular on purpose: one row per (date, app). "Unproductive time" is a
 * query-time aggregate over [TrackedApp.isUnproductive] — never stored
 * pre-grouped, so the category list can change without corrupting history.
 *
 * Filled by the screen-time integration (UsageStatsManager rollups).
 */
@Entity(tableName = "daily_app_usage", primaryKeys = ["date", "package_name"])
data class DailyAppUsage(
    /** Local calendar date in ISO format (yyyy-MM-dd). */
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "foreground_ms")
    val foregroundMs: Long,
)

/**
 * Apps the user cares about, with their productivity category.
 * Defines which apps count toward the "unproductive" aggregate.
 */
@Entity(tableName = "tracked_apps")
data class TrackedApp(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "is_unproductive")
    val isUnproductive: Boolean,
)

/** Aggregate row: total unproductive foreground millis for one date. */
data class DailyUnproductiveTotal(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total_ms")
    val totalMs: Long,
)

/** Aggregate row: block count for one date. */
data class DailyBlockTotal(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total")
    val total: Int,
)
