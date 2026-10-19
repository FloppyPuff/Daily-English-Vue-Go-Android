package com.dailyenglish.nativeapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.data.local.PreferenceStore
import com.dailyenglish.nativeapp.data.repository.AuthRepository
import com.dailyenglish.nativeapp.data.repository.AuthState
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import com.dailyenglish.nativeapp.data.repository.SyncManager
import com.dailyenglish.nativeapp.data.repository.SyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
  val booting: Boolean = true,
  val showAuth: Boolean = false,
  val auth: AuthState = AuthState.Anonymous(guest = false),
  val sync: SyncUiState = SyncUiState(),
  val dynamicColor: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
  private val auth: AuthRepository,
  private val sync: SyncManager,
  private val progress: ProgressRepository,
  preference: PreferenceStore,
) : ViewModel() {
  private val booting = MutableStateFlow(true)
  private val showAuth = MutableStateFlow(false)

  val uiState: StateFlow<AppUiState> = combine(
    booting,
    showAuth,
    auth.state,
    sync.uiState,
    preference.dynamicColor,
  ) { loading, requested, authState, syncState, dynamic ->
    val needsAuth = !loading && authState is AuthState.Anonymous && !authState.guest
    AppUiState(
      booting = loading,
      showAuth = requested || needsAuth,
      auth = authState,
      sync = syncState,
      dynamicColor = dynamic,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

  init {
    viewModelScope.launch {
      runCatching { progress.load() }
      runCatching { auth.bootstrap() }
      sync.start()
      booting.value = false
      val current = auth.state.value
      if (current is AuthState.Anonymous && !current.guest) {
        showAuth.value = true
      }
    }
    viewModelScope.launch {
      auth.state.collect { state ->
        if (state is AuthState.Member) showAuth.value = false
      }
    }
  }

  fun requestAuth() {
    showAuth.value = true
  }

  fun continueAsGuest() {
    viewModelScope.launch {
      auth.continueAsGuest()
      showAuth.value = false
    }
  }
}
