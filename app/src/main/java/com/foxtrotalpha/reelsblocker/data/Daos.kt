package com.foxtrotalpha.reelsblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Insert
    suspend fun insert(event: BlockEvent)

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    fun countForDate(date: String): Flow<Int>

    @Query(
        """
        SELECT package_name, COUNT(*) AS blocks
        FROM block_events
        WHERE date = :date
        GROUP BY package_name
        ORDER BY blocks DESC
        """,
    )
    suspend fun blocksByAppForDate(date: String): Map<
        @MapColumn(columnName = "package_name") String,
        @MapColumn(columnName = "blocks") Int,
        >

    @Query("SELECT * FROM block_events ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BlockEvent>
}

@Dao
interface DailyAppUsageDao {

    /** Upsert so screen-time rollups can be re-written as the day progresses. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usages: List<DailyAppUsage>)

    @Query("SELECT * FROM daily_app_usage WHERE date = :date ORDER BY foreground_ms DESC")
    suspend fun forDate(date: String): List<DailyAppUsage>

    /**
     * The "unproductive apps" rollup: one total per day, grouping whatever
     * tracked_apps currently marks as unproductive.
     */
    @Query(
        """
        SELECT u.date AS date, SUM(u.foreground_ms) AS total_ms
        FROM daily_app_usage u
        JOIN tracked_apps t ON t.package_name = u.package_name
        WHERE t.is_unproductive = 1 AND u.date BETWEEN :fromDate AND :toDate
        GROUP BY u.date
        ORDER BY u.date
        """,
    )
    suspend fun unproductiveTotals(fromDate: String, toDate: String): List<DailyUnproductiveTotal>
}

@Dao
interface TrackedAppDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<TrackedApp>)

    @Query("SELECT * FROM tracked_apps ORDER BY label")
    suspend fun all(): List<TrackedApp>

    @Query("SELECT * FROM tracked_apps WHERE is_unproductive = 1")
    suspend fun unproductive(): List<TrackedApp>
}
