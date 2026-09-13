package com.foxtrotalpha.reelsblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CalendarEvent>)

    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun count(): Int

    @Query("SELECT * FROM calendar_events ORDER BY start_ms ASC")
    fun observeAll(): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY start_ms ASC")
    fun observeForDate(date: String): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY start_ms ASC")
    suspend fun forDate(date: String): List<CalendarEvent>

    @Query("DELETE FROM calendar_events WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}
