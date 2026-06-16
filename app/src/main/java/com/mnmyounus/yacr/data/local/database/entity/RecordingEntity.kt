/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/local/database/entity/             ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mnmyounus.yacr.domain.model.CallType
import com.mnmyounus.yacr.domain.model.Recording

@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["start_timestamp_ms"]),
        Index(value = ["phone_number"]),
        Index(value = ["call_type"]),
        Index(value = ["is_flagged"])
    ]
)
data class RecordingEntity(
    @PrimaryKey @ColumnInfo(name = "id")                   val id: String,
    @ColumnInfo(name = "caller_name")                      val callerName: String,
    @ColumnInfo(name = "phone_number")                     val phoneNumber: String,
    @ColumnInfo(name = "start_timestamp_ms")               val startTimestampMs: Long,
    @ColumnInfo(name = "duration_ms")                      val durationMs: Long,
    @ColumnInfo(name = "encrypted_file_path")              val encryptedFilePath: String,
    @ColumnInfo(name = "file_size_bytes")                  val fileSizeBytes: Long,
    @ColumnInfo(name = "call_type")                        val callType: String,
    @ColumnInfo(name = "source_package")                   val sourcePackage: String?,
    @ColumnInfo(name = "is_flagged")                       val isFlagged: Boolean = false,
    @ColumnInfo(name = "created_at")                       val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = Recording(
        id = id, callerName = callerName, phoneNumber = phoneNumber,
        startTimestampMs = startTimestampMs, durationMs = durationMs,
        encryptedFilePath = encryptedFilePath, fileSizeBytes = fileSizeBytes,
        callType = CallType.valueOf(callType), sourcePackage = sourcePackage,
        isFlagged = isFlagged
    )

    companion object {
        fun fromDomain(r: Recording) = RecordingEntity(
            id = r.id, callerName = r.callerName, phoneNumber = r.phoneNumber,
            startTimestampMs = r.startTimestampMs, durationMs = r.durationMs,
            encryptedFilePath = r.encryptedFilePath, fileSizeBytes = r.fileSizeBytes,
            callType = r.callType.name, sourcePackage = r.sourcePackage, isFlagged = r.isFlagged
        )
    }
}
