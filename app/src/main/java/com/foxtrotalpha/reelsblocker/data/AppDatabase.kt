package com.foxtrotalpha.reelsblocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        BlockEvent::class,
        DailyAppUsage::class,
        TrackedApp::class,
        DailyHealthMetrics::class,
        SleepSession::class,
        CalendarEvent::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockEventDao(): BlockEventDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun dailyHealthMetricsDao(): DailyHealthMetricsDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        private const val DB_NAME = "foxtrot_alpha.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*DatabaseMigrations.ALL)
                .addCallback(SeedCallback)
                .build()
        }

        /** Seed the default unproductive apps on first creation. */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val defaults = listOf(
                    Triple("com.instagram.android", "Instagram", 1),
                    Triple("com.google.android.youtube", "YouTube", 1),
                    Triple("app.revanced.android.youtube", "YouTube (ReVanced)", 1),
                    Triple("com.netflix.mediaclient", "Netflix", 1),
                    Triple("com.zhiliaoapp.musically", "TikTok", 1),
                    Triple("com.twitter.android", "X (Twitter)", 1),
                    Triple("com.snapchat.android", "Snapchat", 1),
                    Triple("com.reddit.frontpage", "Reddit", 1),
                )
                for ((pkg, label, unproductive) in defaults) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO tracked_apps (package_name, label, is_unproductive) VALUES (?, ?, ?)",
                        arrayOf(pkg, label, unproductive),
                    )
                }
            }
        }

        /** Local calendar date as yyyy-MM-dd, the format used in all date columns. */
        fun isoDate(timestampMs: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))
        }
    }
}
