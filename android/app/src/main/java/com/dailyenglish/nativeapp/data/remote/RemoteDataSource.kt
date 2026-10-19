package com.dailyenglish.nativeapp.data.remote

import com.dailyenglish.nativeapp.core.model.AppState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface ApiResult<out T> {
  data class Ok<T>(val value: T) : ApiResult<T>
  data class Failure(
    val status: Int,
    val message: String,
    val payload: AppState? = null,
    val revision: Long? = null,
  ) : ApiResult<Nothing>
}

@Singleton
class RemoteDataSource @Inject constructor(
  private val api: ApiService,
  private val json: Json,
) : SyncApi {
  suspend fun register(email: String, password: String): ApiResult<AuthResponse> =
    call { api.register(AuthRequest(email, password)) }

  suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
    call { api.login(AuthRequest(email, password)) }

  override suspend fun me(): ApiResult<UserResponse> = call { api.me() }

  override suspend fun getState(): ApiResult<StateResponse> = call { api.getState() }

  override suspend fun putState(revision: Long, payload: AppState): ApiResult<RevisionResponse> =
    call { api.putState(PutStateRequest(revision, payload)) }

  suspend fun changePassword(current: String, next: String): ApiResult<UserResponse> =
    call { api.changePassword(PasswordRequest(current, next)) }

  suspend fun logout() {
    runCatching { api.logout() }
  }

  private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): ApiResult<T> {
    return try {
      val response = block()
      if (response.isSuccessful) {
        val body = response.body()
        if (body != null) ApiResult.Ok(body) else ApiResult.Failure(response.code(), "服务器没有返回数据")
      } else {
        val text = response.errorBody()?.string().orEmpty()
        val error = runCatching { json.decodeFromString<ErrorResponse>(text) }.getOrNull()?.error
          ?.takeIf { it.isNotBlank() }
          ?: "请求失败（${response.code()}）"
        val conflict = if (response.code() == 409 && text.isNotBlank()) {
          runCatching { json.decodeFromString<StateResponse>(text) }.getOrNull()
        } else {
          null
        }
        ApiResult.Failure(response.code(), error, conflict?.payload, conflict?.revision)
      }
    } catch (error: Exception) {
      ApiResult.Failure(0, "无法连接服务器，请检查网络后重试")
    }
  }
}
