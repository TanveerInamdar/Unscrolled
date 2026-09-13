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
    val ALL: Array<Migration> = emptyArray()
}
