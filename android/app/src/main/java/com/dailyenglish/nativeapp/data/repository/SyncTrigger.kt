package com.dailyenglish.nativeapp.data.repository

import com.dailyenglish.nativeapp.core.model.AppState
import kotlinx.coroutines.flow.StateFlow

interface SyncTrigger {
  fun markDirty()
  val remoteState: StateFlow<AppState?>
}
