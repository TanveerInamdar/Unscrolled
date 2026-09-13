package com.foxtrotalpha.reelsblocker.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pulls today's calendar events from [CalendarContract.Instances] and writes into Room.
 */
object CalendarSync {

    suspend fun syncTodayOnStartup(context: Context): SyncResult {
        return withContext(Dispatchers.IO) {
            if (!CalendarAccessUtils.hasReadCalendarPermission(context)) {
                return@withContext SyncResult.PermissionRequired
            }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayDate = today.toString()
            val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val syncedAtMs = System.currentTimeMillis()

            val events = queryTodayEvents(context, dayStartMs, dayEndMs, todayDate, syncedAtMs)

            val database = AppDatabase.get(context)
            val dao = database.calendarEventDao()
            dao.deleteForDate(todayDate)
            if (events.isNotEmpty()) {
                dao.upsertAll(events)
            }

            SyncResult.Success(events.size)
        }
    }

    private fun queryTodayEvents(
        context: Context,
        dayStartMs: Long,
        dayEndMs: Long,
        todayDate: String,
        syncedAtMs: Long,
    ): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, dayStartMs)
            ContentUris.appendId(this, dayEndMs)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    date = todayDate,
                    eventId = cursor.getLong(eventIdIndex),
                    title = cursor.getString(titleIndex) ?: "(No title)",
                    description = cursor.getString(descriptionIndex),
                    location = cursor.getString(locationIndex),
                    startMs = cursor.getLong(beginIndex),
                    endMs = cursor.getLong(endIndex),
                    allDay = cursor.getInt(allDayIndex) != 0,
                    syncedAtMs = syncedAtMs,
                )
            }
        }

        return events
    }

    sealed class SyncResult {
        data object PermissionRequired : SyncResult()
        data class Success(val rowCount: Int) : SyncResult()
    }
}
