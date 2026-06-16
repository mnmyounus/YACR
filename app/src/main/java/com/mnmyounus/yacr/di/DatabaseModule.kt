/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  di/DatabaseModule.kt                    ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.di

import android.content.Context
import androidx.room.Room
import com.mnmyounus.yacr.data.local.database.YACRDatabase
import com.mnmyounus.yacr.data.local.database.dao.RecordingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "yacr_recordings.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YACRDatabase =
        Room.databaseBuilder(
            context,
            YACRDatabase::class.java,
            DATABASE_NAME
        )
            // Enforce explicit migrations — no silent data loss
            .fallbackToDestructiveMigrationFrom()  // Only from version 0 (fresh installs)
            .enableMultiInstanceInvalidation()
            .build()

    @Provides
    @Singleton
    fun provideRecordingDao(database: YACRDatabase): RecordingDao =
        database.recordingDao()
}
