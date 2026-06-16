/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  di/AppModule.kt                         ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.di

import android.content.Context
import com.mnmyounus.yacr.data.local.datastore.YACRPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideYACRPreferences(@ApplicationContext context: Context): YACRPreferences =
        YACRPreferences(context)

    @Provides
    @Named("IO")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Named("Default")
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Named("Main")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
