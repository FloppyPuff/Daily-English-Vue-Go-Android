package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.model.AppState

interface StateStore {
  suspend fun snapshot(): AppState
  suspend fun replaceAll(state: AppState)
}
