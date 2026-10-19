package com.dailyenglish.nativeapp.data.repository

import com.dailyenglish.nativeapp.core.learning.StateValidator
import com.dailyenglish.nativeapp.core.learning.ValidationResult
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.core.sync.StateStore
import com.dailyenglish.nativeapp.core.sync.mergeStates
import com.dailyenglish.nativeapp.core.sync.normalizeLegacy
import com.dailyenglish.nativeapp.data.local.SessionStore
import com.dailyenglish.nativeapp.data.remote.ApiResult
import com.dailyenglish.nativeapp.data.remote.SyncApi
import com.dailyenglish.nativeapp.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncMode { ANONYMOUS, MEMBER }

enum class SyncStatus { IDLE, SAVING, DIRTY, OFFLINE, SIGNED_OUT }

data class SyncUiState(
  val mode: SyncMode = SyncMode.ANONYMOUS,
  val status: SyncStatus = SyncStatus.IDLE,
  val email: String = "",
  val problem: String = "",
  val revision: Long = 0,
  val lastSyncedAt: Long = 0,
)

@Singleton
class SyncManager @Inject constructor(
  private val api: SyncApi,
  private val store: StateStore,
  private val session: SessionStore,
  private val auth: SessionStateProvider,
  private val scheduler: SyncScheduler,
  @ApplicationScope private val scope: CoroutineScope,
) : SyncTrigger {
  private val mutex = Mutex()
  private val _uiState = MutableStateFlow(SyncUiState())
  private val _remoteState = MutableStateFlow<AppState?>(null)
  val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()
  override val remoteState: StateFlow<AppState?> = _remoteState.asStateFlow()

  private var started = false
  private var reconciled = false
  private var debounce: Job? = null

  fun start() {
    if (started) return
    started = true
    scope.launch {
      auth.state.collect { state ->
        when (state) {
          is AuthState.Member -> enterMember(state.email)
          is AuthState.Anonymous -> enterAnonymous()
        }
      }
    }
  }

  override fun markDirty() {
    if (_uiState.value.mode != SyncMode.MEMBER) return
    _uiState.update { it.copy(status = SyncStatus.DIRTY) }
    debounce?.cancel()
    debounce = scope.launch {
      delay(1500)
      flush()
    }
  }

  suspend fun syncNow(): Boolean = mutex.withLock {
    if (_uiState.value.mode != SyncMode.MEMBER) return@withLock false
    if (!reconciled) reconcileLocked() else flushLocked()
  }

  private fun enterMember(email: String) {
    _uiState.update {
      it.copy(
        mode = SyncMode.MEMBER,
        email = email,
        status = if (it.status == SyncStatus.SIGNED_OUT) SyncStatus.IDLE else it.status,
        problem = if (it.status == SyncStatus.SIGNED_OUT) "" else it.problem,
      )
    }
    if (!reconciled) {
      scope.launch { reconcile() }
    } else if (_uiState.value.status == SyncStatus.DIRTY) {
      scope.launch { flush() }
    }
  }

  private fun enterAnonymous() {
    debounce?.cancel()
    scheduler.cancel()
    reconciled = false
    _uiState.update {
      val signedOut = it.status == SyncStatus.SIGNED_OUT
      it.copy(
        mode = SyncMode.ANONYMOUS,
        email = "",
        status = if (signedOut) SyncStatus.SIGNED_OUT else SyncStatus.IDLE,
        problem = if (signedOut) it.problem else "",
      )
    }
  }

  private suspend fun reconcile() {
    mutex.withLock {
      if (!reconciled) reconcileLocked()
    }
  }

  private suspend fun flush() {
    mutex.withLock {
      if (_uiState.value.mode == SyncMode.MEMBER) flushLocked()
    }
  }

  private suspend fun reconcileLocked(): Boolean {
    _uiState.update { it.copy(status = SyncStatus.SAVING) }
    return when (val result = api.getState()) {
      is ApiResult.Ok -> {
        val revision = result.value.revision
        if (revision <= 0) {
          reconciled = true
          _uiState.update { it.copy(revision = 0) }
          flushLocked()
        } else {
          val payload = normalizeLegacy(result.value.payload ?: createState())
          if (StateValidator.validate(payload) is ValidationResult.Invalid) {
            offline("服务器数据格式异常，已保留本机进度")
            false
          } else {
            store.replaceAll(payload)
            _remoteState.value = payload
            reconciled = true
            _uiState.update {
              it.copy(
                status = SyncStatus.IDLE,
                revision = revision,
                lastSyncedAt = System.currentTimeMillis(),
                problem = "",
              )
            }
            true
          }
        }
      }
      is ApiResult.Failure -> {
        if (result.status == 401) {
          signedOut()
        } else {
          offline(result.message)
        }
        false
      }
    }
  }

  private suspend fun flushLocked(): Boolean {
    var revision = _uiState.value.revision
    for (round in 0..1) {
      val snapshot = store.snapshot()
      _uiState.update { it.copy(status = SyncStatus.SAVING) }
      when (val result = api.putState(revision, snapshot)) {
        is ApiResult.Ok -> {
          reconciled = true
          scheduler.cancel()
          _uiState.update {
            it.copy(
              status = SyncStatus.IDLE,
              revision = result.value.revision,
              lastSyncedAt = System.currentTimeMillis(),
              problem = "",
            )
          }
          return true
        }
        is ApiResult.Failure -> {
          if (result.status == 401) {
            signedOut()
            return false
          }
          if (result.status == 409) {
            val remote = result.payload
            if (remote == null) {
              offline("服务器没有返回冲突数据，稍后重试")
              return false
            }
            val normalized = normalizeLegacy(remote)
            if (StateValidator.validate(normalized) is ValidationResult.Invalid) {
              offline("服务器数据格式异常，已保留本机进度")
              return false
            }
            val merged = mergeStates(snapshot, normalized)
            store.replaceAll(merged)
            _remoteState.value = merged
            revision = result.revision ?: revision
            _uiState.update { it.copy(revision = revision) }
            continue
          }
          offline(result.message)
          return false
        }
      }
    }
    offline("另一台设备正在同时学习，稍后会自动重试")
    return false
  }

  private fun offline(problem: String) {
    _uiState.update { it.copy(status = SyncStatus.OFFLINE, problem = problem) }
    scheduler.schedule()
  }

  private suspend fun signedOut() {
    session.clearAuth()
    auth.markSignedOut()
    scheduler.cancel()
    reconciled = false
    _uiState.update {
      it.copy(
        mode = SyncMode.ANONYMOUS,
        email = "",
        status = SyncStatus.SIGNED_OUT,
        problem = "登录已过期，进度仍保留在本机",
      )
    }
  }
}
