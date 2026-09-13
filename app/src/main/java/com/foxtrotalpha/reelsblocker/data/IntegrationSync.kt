package com.foxtrotalpha.reelsblocker.data

import android.content.Context
import com.foxtrotalpha.reelsblocker.calendar.CalendarSync
import com.foxtrotalpha.reelsblocker.health.HealthConnectSync

/**
 * Orchestrates health and calendar integrations on app open.
 */
object IntegrationSync {

    suspend fun syncOnStartup(context: Context) {
        HealthConnectSync.syncAllOnStartup(context)
        CalendarSync.syncTodayOnStartup(context)
    }
}
