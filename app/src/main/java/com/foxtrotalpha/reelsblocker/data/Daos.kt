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

    @Insert
    suspend fun insertAll(events: List<BlockEvent>)

    @Query("SELECT COUNT(*) FROM block_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    fun countForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    suspend fun countForDateOnce(date: String): Int

    @Query("SELECT * FROM block_events ORDER BY timestamp_ms DESC")
    fun observeAll(): Flow<List<BlockEvent>>

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

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}

@Dao
interface DailyAppUsageDao {

    /** Upsert so screen-time rollups can be re-written as the day progresses. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usages: List<DailyAppUsage>)

    @Query("SELECT COUNT(*) FROM daily_app_usage")
    suspend fun count(): Int

    @Query("SELECT * FROM daily_app_usage WHERE date = :date ORDER BY foreground_ms DESC")
    suspend fun forDate(date: String): List<DailyAppUsage>

    @Query("SELECT * FROM daily_app_usage WHERE date = :date ORDER BY foreground_ms DESC")
    fun observeForDate(date: String): Flow<List<DailyAppUsage>>

    @Query(
        """
        SELECT COALESCE(SUM(u.foreground_ms), 0)
        FROM daily_app_usage u
        JOIN tracked_apps t ON t.package_name = u.package_name
        WHERE t.is_unproductive = 1 AND u.date = :date
        """,
    )
    fun observeUnproductiveTotalForDate(date: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(u.foreground_ms), 0)
        FROM daily_app_usage u
        JOIN tracked_apps t ON t.package_name = u.package_name
        WHERE t.is_unproductive = 1 AND u.date = :date
        """,
    )
    suspend fun unproductiveTotalForDate(date: String): Long

    @Query("SELECT * FROM daily_app_usage ORDER BY date DESC, foreground_ms DESC")
    fun observeAll(): Flow<List<DailyAppUsage>>

    @Query("DELETE FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate")
    suspend fun deleteRange(fromDate: String, toDate: String)

    @Query("DELETE FROM daily_app_usage")
    suspend fun deleteAll()

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
    fun observeUnproductiveTotals(fromDate: String, toDate: String): Flow<List<DailyUnproductiveTotal>>
}

@Dao
interface TrackedAppDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<TrackedApp>)

    @Query("SELECT COUNT(*) FROM tracked_apps")
    suspend fun count(): Int

    @Query("SELECT * FROM tracked_apps ORDER BY label")
    suspend fun all(): List<TrackedApp>

    @Query("SELECT * FROM tracked_apps ORDER BY label")
    fun observeAll(): Flow<List<TrackedApp>>

    @Query("SELECT * FROM tracked_apps WHERE is_unproductive = 1")
    suspend fun unproductive(): List<TrackedApp>
}
