package com.foxtrotalpha.reelsblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHealthMetricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<DailyHealthMetrics>)

    @Query("SELECT COUNT(*) FROM daily_health_metrics")
    suspend fun count(): Int

    @Query("SELECT * FROM daily_health_metrics ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyHealthMetrics>>

    @Query("SELECT * FROM daily_health_metrics WHERE date = :date LIMIT 1")
    fun observeForDate(date: String): Flow<DailyHealthMetrics?>

    @Query("SELECT * FROM daily_health_metrics WHERE date = :date LIMIT 1")
    suspend fun forDate(date: String): DailyHealthMetrics?

    @Query(
        "SELECT * FROM daily_health_metrics WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC",
    )
    fun observeBetween(fromDate: String, toDate: String): Flow<List<DailyHealthMetrics>>

    @Query(
        "SELECT * FROM daily_health_metrics WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC",
    )
    suspend fun between(fromDate: String, toDate: String): List<DailyHealthMetrics>

    @Query("DELETE FROM daily_health_metrics WHERE date BETWEEN :fromDate AND :toDate")
    suspend fun deleteRange(fromDate: String, toDate: String)

    @Query("DELETE FROM daily_health_metrics")
    suspend fun deleteAll()
}

@Dao
interface SleepSessionDao {

    @Insert
    suspend fun insertAll(sessions: List<SleepSession>)

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun count(): Int

    @Query("SELECT * FROM sleep_sessions ORDER BY wake_ms DESC")
    fun observeAll(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE date = :date ORDER BY duration_ms DESC")
    fun observeForWakeDate(date: String): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE date = :date ORDER BY duration_ms DESC")
    suspend fun forWakeDate(date: String): List<SleepSession>

    @Query("DELETE FROM sleep_sessions WHERE wake_ms BETWEEN :fromWakeMs AND :toWakeMs")
    suspend fun deleteWakeRange(fromWakeMs: Long, toWakeMs: Long)

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
