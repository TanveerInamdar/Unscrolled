package com.foxtrotalpha.reelsblocker.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.DailyAppUsage
import com.foxtrotalpha.reelsblocker.data.LegacyDataCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Pulls live screen time from [UsageStatsManager] and writes it into
 * [com.foxtrotalpha.reelsblocker.data.DailyAppUsage].
 *
 * Field mapping:
 * - date            ← local calendar day (yyyy-MM-dd)
 * - package_name    ← UsageEvents.Event.packageName (all apps)
 * - foreground_ms   ← summed resume→pause foreground duration for that day
 */
object ScreenTimeSync {

    /** Android retains detailed usage events for a limited window; 30 days is the safe max. */
    const val SYNC_DAYS = 30

    suspend fun syncAllOnStartup(context: Context): SyncResult {
        return withContext(Dispatchers.IO) {
            if (!UsageAccessUtils.hasUsageAccess(context)) {
                return@withContext SyncResult.PermissionRequired
            }

            val database = AppDatabase.get(context)
            LegacyDataCleanup.clearLegacyMockDataIfNeeded(context, database)

            val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
                ?: return@withContext SyncResult.Failed

            val dayRanges = buildDayRanges(SYNC_DAYS)
            val rows = mutableListOf<DailyAppUsage>()

            dayRanges.forEach { (date, dayStartMs, dayEndMs) ->
                val perApp = ScreenTimeCollector.collectForDay(
                    usageStatsManager = usageStatsManager,
                    dayStartMs = dayStartMs,
                    dayEndMs = dayEndMs,
                )

                perApp.forEach { (packageName, foregroundMs) ->
                    rows += DailyAppUsage(
                        date = date,
                        packageName = packageName,
                        foregroundMs = foregroundMs,
                    )
                }
            }

            val usageDao = database.dailyAppUsageDao()
            usageDao.deleteAll()
            if (rows.isNotEmpty()) {
                usageDao.upsertAll(rows)
            }

            SyncResult.Success(rows.size)
        }
    }

    private data class DayRange(
        val date: String,
        val startMs: Long,
        val endMs: Long,
    )

    private fun buildDayRanges(dayCount: Int): List<DayRange> {
        val ranges = mutableListOf<DayRange>()
        val calendar = Calendar.getInstance()

        for (dayOffset in dayCount - 1 downTo 0) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -dayOffset)

            val dayStartMs = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEndMs = calendar.timeInMillis - 1

            ranges += DayRange(
                date = AppDatabase.isoDate(dayStartMs),
                startMs = dayStartMs,
                endMs = dayEndMs,
            )
        }

        return ranges
    }

    sealed class SyncResult {
        data object PermissionRequired : SyncResult()
        data object Failed : SyncResult()
        data class Success(val rowCount: Int) : SyncResult()
    }
}
