package com.dailyenglish.nativeapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.security.PasswordRules
import com.dailyenglish.nativeapp.core.sync.BackupCodec
import com.dailyenglish.nativeapp.core.sync.BackupResult
import com.dailyenglish.nativeapp.data.content.ContentRepository
import com.dailyenglish.nativeapp.data.local.PreferenceStore
import com.dailyenglish.nativeapp.data.repository.AuthRepository
import com.dailyenglish.nativeapp.data.repository.AuthState
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import com.dailyenglish.nativeapp.data.repository.SyncManager
import com.dailyenglish.nativeapp.data.repository.SyncStatus
import com.dailyenglish.nativeapp.data.repository.SyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: Settings = Settings(),
  val sync: SyncUiState = SyncUiState(),
  val auth: AuthState = AuthState.Anonymous(guest = false),
  val busy: Boolean = false,
  val message: String = "",
  val dynamicColor: Boolean = false,
  val importing: Boolean = false,
  val pendingImport: AppState? = null,
  val pendingSummary: String = "",
  val passwordBusy: Boolean = false,
  val passwordError: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val progress: ProgressRepository,
  private val sync: SyncManager,
  private val auth: AuthRepository,
  private val preference: PreferenceStore,
  private val backups: BackupCodec,
  private val contentRepository: ContentRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(SettingsUiState())
  val state: StateFlow<SettingsUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      progress.state.collect { value ->
        _state.update { it.copy(settings = value.settings) }
      }
    }
    viewModelScope.launch {
      sync.uiState.collect { value ->
        _state.update { it.copy(sync = value) }
      }
    }
    viewModelScope.launch {
      auth.state.collect { value ->
        _state.update { it.copy(auth = value) }
      }
    }
    viewModelScope.launch {
      preference.dynamicColor.collect { value ->
        _state.update { it.copy(dynamicColor = value) }
      }
    }
  }

  fun save(dailyGoal: Int, sentenceGoal: Int, focusMinutes: Int, exam: String) {
    if (_state.value.busy) return
    viewModelScope.launch {
      _state.update { it.copy(busy = true, message = "") }
      runCatching { progress.updateSettings(dailyGoal, sentenceGoal, focusMinutes, exam) }
        .onSuccess { _state.update { it.copy(busy = false, message = "学习计划已保存。") } }
        .onFailure { _state.update { it.copy(busy = false, message = "保存失败，请稍后再试。") } }
    }
  }

  fun syncNow() {
    viewModelScope.launch {
      sync.syncNow()
      val status = _state.value.sync.status
      _state.update {
        it.copy(
          message = when (status) {
            SyncStatus.OFFLINE -> "仍然离线，进度已留在本机。"
            SyncStatus.SIGNED_OUT -> "登录已过期，请重新登录。"
            else -> "已与账号同步。"
          },
        )
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      auth.logout()
      _state.update { it.copy(message = "已退出登录，本机进度仍然保留。") }
    }
  }

  fun setDynamicColor(value: Boolean) {
    viewModelScope.launch {
      preference.setDynamicColor(value)
      _state.update { it.copy(dynamicColor = value) }
    }
  }

  fun note(message: String) {
    _state.update { it.copy(message = message) }
  }

  fun exportBackup(onReady: (String) -> Unit) {
    onReady(backups.encode(progress.state.value))
  }

  fun fileName(): String = backups.fileName()

  fun prepareImport(text: String) {
    if (_state.value.importing) return
    viewModelScope.launch {
      _state.update { it.copy(importing = true, message = "") }
      runCatching { contentRepository.load() }
        .onSuccess { content ->
          val result = backups.decode(
            text = text,
            knownWords = content.words.map { it.id }.toSet(),
            knownSentences = content.sentences.map { it.id }.toSet(),
          )
          when (result) {
            is BackupResult.Ok -> _state.update {
              it.copy(
                importing = false,
                pendingImport = result.state,
                pendingSummary = "包含 ${result.state.records.size} 个单词记录、${result.state.sentenceRecords.size} 条句子记录、${result.state.papers.size} 组真题记录。",
                message = "",
              )
            }
            is BackupResult.Error -> _state.update { it.copy(importing = false, message = result.message) }
          }
        }
        .onFailure {
          _state.update { it.copy(importing = false, message = "学习内容读取失败，请稍后再试。") }
        }
    }
  }

  fun confirmImport() {
    val pending = _state.value.pendingImport ?: return
    viewModelScope.launch {
      _state.update { it.copy(importing = true) }
      runCatching { progress.importState(pending) }
        .onSuccess {
          _state.update {
            it.copy(
              importing = false,
              pendingImport = null,
              pendingSummary = "",
              message = "备份已恢复。",
            )
          }
        }
        .onFailure {
          _state.update { it.copy(importing = false, message = "恢复失败，原有数据未改变。") }
        }
    }
  }

  fun cancelImport() {
    _state.update { it.copy(pendingImport = null, pendingSummary = "") }
  }

  fun clearPasswordError() {
    if (_state.value.passwordError.isNotEmpty()) {
      _state.update { it.copy(passwordError = "") }
    }
  }

  fun changePassword(current: String, next: String) {    if (_state.value.passwordBusy) return
    if (current.isEmpty()) {
      _state.update { it.copy(passwordError = "请填写当前密码") }
      return
    }
    val problem = PasswordRules.problem(next)
    if (problem != null) {
      _state.update { it.copy(passwordError = problem) }
      return
    }
    viewModelScope.launch {
      _state.update { it.copy(passwordBusy = true, passwordError = "", message = "") }
      runCatching { auth.changePassword(current, next) }
        .onSuccess {
          _state.update {
            it.copy(
              passwordBusy = false,
              message = "密码已修改，其他设备需要重新登录。",
            )
          }
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              passwordBusy = false,
              passwordError = error.message ?: "修改密码失败，请稍后再试",
            )
          }
        }
    }
  }
}
