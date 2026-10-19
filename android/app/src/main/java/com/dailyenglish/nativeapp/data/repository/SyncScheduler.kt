package com.dailyenglish.nativeapp.data.repository

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface SyncScheduler {
  fun schedule()
  fun cancel()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) : SyncScheduler {
  override fun schedule() {
    val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      SyncWorker.NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  override fun cancel() {
    WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.NAME)
  }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    syncManager.syncNow()
    return if (syncManager.uiState.value.status == SyncStatus.OFFLINE) Result.retry() else Result.success()
  }

  companion object {
    const val NAME = "daily-english-sync"
  }
}
