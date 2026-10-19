package com.dailyenglish.nativeapp.di

import com.dailyenglish.nativeapp.core.sync.StateMapper
import com.dailyenglish.nativeapp.core.sync.StateStore
import com.dailyenglish.nativeapp.data.remote.RemoteDataSource
import com.dailyenglish.nativeapp.data.remote.SyncApi
import com.dailyenglish.nativeapp.data.repository.AuthRepository
import com.dailyenglish.nativeapp.data.repository.SessionStateProvider
import com.dailyenglish.nativeapp.data.repository.SyncManager
import com.dailyenglish.nativeapp.data.repository.SyncScheduler
import com.dailyenglish.nativeapp.data.repository.SyncTrigger
import com.dailyenglish.nativeapp.data.repository.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds
  @Singleton
  abstract fun bindSyncTrigger(impl: SyncManager): SyncTrigger

  @Binds
  @Singleton
  abstract fun bindSyncApi(impl: RemoteDataSource): SyncApi

  @Binds
  @Singleton
  abstract fun bindStateStore(impl: StateMapper): StateStore

  @Binds
  @Singleton
  abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

  @Binds
  @Singleton
  abstract fun bindSessionState(impl: AuthRepository): SessionStateProvider
}
