/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/local/database/dao/RecordingDao.kt ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mnmyounus.yacr.data.local.database.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    // ─── Read Operations ────────────────────────────────────────────────────

    @Query("SELECT * FROM recordings ORDER BY start_timestamp_ms DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("""
        SELECT * FROM recordings
        WHERE caller_name LIKE '%' || :query || '%'
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY start_timestamp_ms DESC
    """)
    fun search(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecordingEntity?

    @Query("SELECT COUNT(*) FROM recordings")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun getCount(): Int

    @Query("SELECT SUM(file_size_bytes) FROM recordings")
    suspend fun getTotalFileSizeBytes(): Long?

    @Query("SELECT encrypted_file_path FROM recordings WHERE id = :id")
    suspend fun getFilePath(id: String): String?

    // ─── Write Operations ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecordingEntity)

    @Query("UPDATE recordings SET is_flagged = NOT is_flagged WHERE id = :id")
    suspend fun toggleFlag(id: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM recordings WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("""
        DELETE FROM recordings
        WHERE start_timestamp_ms < :cutoffMs
    """)
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
