package com.dailyenglish.nativeapp.data.repository

import com.dailyenglish.nativeapp.data.local.SessionStore
import com.dailyenglish.nativeapp.data.remote.ApiResult
import com.dailyenglish.nativeapp.data.remote.AuthResponse
import com.dailyenglish.nativeapp.data.remote.RemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
  data class Anonymous(val guest: Boolean) : AuthState
  data class Member(val email: String, val offline: Boolean = false) : AuthState
}

interface SessionStateProvider {
  val state: StateFlow<AuthState>
  suspend fun markSignedOut()
}

class AuthException(message: String, val status: Int = 0) : Exception(message)

@Singleton
class AuthRepository @Inject constructor(
  private val remote: RemoteDataSource,
  private val session: SessionStore,
) : SessionStateProvider {
  private val _state = MutableStateFlow<AuthState>(AuthState.Anonymous(guest = false))
  override val state: StateFlow<AuthState> = _state.asStateFlow()

  suspend fun bootstrap() {
    val token = session.token()
    if (token == null) {
      _state.value = AuthState.Anonymous(session.guestMode())
      return
    }
    when (val result = remote.me()) {
      is ApiResult.Ok -> {
        _state.value = AuthState.Member(result.value.user.email)
        session.setGuestMode(false)
      }
      is ApiResult.Failure -> {
        if (result.status == 401) {
          session.clearAuth()
          _state.value = AuthState.Anonymous(session.guestMode())
        } else {
          _state.value = AuthState.Member(session.email().orEmpty(), offline = true)
        }
      }
    }
  }

  suspend fun login(email: String, password: String): String = authenticate(remote.login(email, password))

  suspend fun register(email: String, password: String): String = authenticate(remote.register(email, password))

  suspend fun logout() {
    remote.logout()
    session.clearAuth()
    session.setGuestMode(true)
    _state.value = AuthState.Anonymous(guest = true)
  }

  override suspend fun markSignedOut() {
    session.clearAuth()
    _state.value = AuthState.Anonymous(session.guestMode())
  }

  suspend fun continueAsGuest() {
    session.setGuestMode(true)
    _state.value = AuthState.Anonymous(guest = true)
  }

  suspend fun changePassword(current: String, next: String) {
    when (val result = remote.changePassword(current, next)) {
      is ApiResult.Ok -> Unit
      is ApiResult.Failure -> throw AuthException(result.message, result.status)
    }
  }

  private suspend fun authenticate(result: ApiResult<AuthResponse>): String {
    return when (result) {
      is ApiResult.Ok -> {
        val token = result.value.token
          ?: throw AuthException("服务器未返回登录凭证")
        val email = result.value.user.email
        session.saveAuth(token, email)
        _state.value = AuthState.Member(email)
        email
      }
      is ApiResult.Failure -> throw AuthException(result.message, result.status)
    }
  }
}
