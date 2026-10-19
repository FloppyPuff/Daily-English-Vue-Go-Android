package com.dailyenglish.nativeapp.data.remote

import com.dailyenglish.nativeapp.core.model.AppState

interface SyncApi {
  suspend fun me(): ApiResult<UserResponse>
  suspend fun getState(): ApiResult<StateResponse>
  suspend fun putState(revision: Long, payload: AppState): ApiResult<RevisionResponse>
}
