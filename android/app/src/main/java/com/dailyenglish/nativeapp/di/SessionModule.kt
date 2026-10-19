package com.dailyenglish.nativeapp.di

import com.dailyenglish.nativeapp.data.local.DataStorePreferenceStore
import com.dailyenglish.nativeapp.data.local.EncryptedSessionStore
import com.dailyenglish.nativeapp.data.local.PreferenceStore
import com.dailyenglish.nativeapp.data.local.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
  @Binds
  @Singleton
  abstract fun bindSessionStore(impl: EncryptedSessionStore): SessionStore

  @Binds
  @Singleton
  abstract fun bindPreferenceStore(impl: DataStorePreferenceStore): PreferenceStore
}
