/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  di/CryptoModule.kt                      ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.di

import com.mnmyounus.yacr.data.crypto.EncryptionPipeline
import com.mnmyounus.yacr.data.crypto.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideEncryptionPipeline(keystoreManager: KeystoreManager): EncryptionPipeline =
        EncryptionPipeline(keystoreManager)
}
