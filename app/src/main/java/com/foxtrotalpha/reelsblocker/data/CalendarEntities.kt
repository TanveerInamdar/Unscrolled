package com.foxtrotalpha.reelsblocker.data

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One calendar event occurrence for a synced day (today only in v1).
 * Composite key: partition date + stable event id from CalendarContract.
 */
@Entity(tableName = "calendar_events", primaryKeys = ["date", "event_id"])
data class CalendarEvent(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "event_id")
    val eventId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "location")
    val location: String?,

    @ColumnInfo(name = "start_ms")
    val startMs: Long,

    @ColumnInfo(name = "end_ms")
    val endMs: Long,

    @ColumnInfo(name = "all_day")
    val allDay: Boolean,

    @ColumnInfo(name = "synced_at_ms")
    val syncedAtMs: Long,
)
