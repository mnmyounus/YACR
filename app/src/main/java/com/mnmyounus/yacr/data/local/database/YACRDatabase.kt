/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/local/database/YACRDatabase.kt     ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mnmyounus.yacr.data.local.database.dao.RecordingDao
import com.mnmyounus.yacr.data.local.database.entity.RecordingEntity

/**
 * YACR Room database.
 *
 * Versioning policy:
 *  - Increment version on any schema change.
 *  - Add a Migration object for each version bump.
 *  - fallbackToDestructiveMigration() is intentionally NOT used —
 *    silently dropping user recordings is unacceptable.
 */
@Database(
    entities = [RecordingEntity::class],
    version  = 1,
    exportSchema = true
)
abstract class YACRDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
