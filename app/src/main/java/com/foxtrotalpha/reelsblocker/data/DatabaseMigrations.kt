package com.foxtrotalpha.reelsblocker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations. Bump [AppDatabase] version and add a Migration here
 * whenever entities change — never rely on clearing app data in production.
 *
 * Example for a future v1 → v2:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE block_events ADD COLUMN note TEXT")
 *     }
 * }
 * ```
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_health_metrics (
                    date TEXT NOT NULL PRIMARY KEY,
                    step_count INTEGER NOT NULL,
                    active_calories_kcal REAL,
                    total_calories_kcal REAL,
                    synced_at_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sleep_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    date TEXT NOT NULL,
                    bedtime_ms INTEGER NOT NULL,
                    wake_ms INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    title TEXT,
                    synced_at_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS calendar_events (
                    date TEXT NOT NULL,
                    event_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    location TEXT,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER NOT NULL,
                    all_day INTEGER NOT NULL,
                    synced_at_ms INTEGER NOT NULL,
                    PRIMARY KEY (date, event_id)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
