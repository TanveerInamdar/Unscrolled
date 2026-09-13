package com.foxtrotalpha.reelsblocker.data

import android.content.Context

/**
 * One-time wipe of hard-coded seed rows left over from early development builds.
 */
object LegacyDataCleanup {

    private const val PREFS_NAME = "data_cleanup"
    private const val KEY_MOCK_DATA_CLEARED = "legacy_mock_data_cleared_v1"

    suspend fun clearLegacyMockDataIfNeeded(context: Context, database: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MOCK_DATA_CLEARED, false)) {
            return
        }

        database.blockEventDao().deleteAll()
        database.dailyAppUsageDao().deleteAll()

        prefs.edit().putBoolean(KEY_MOCK_DATA_CLEARED, true).apply()
    }
}
