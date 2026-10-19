package com.dailyenglish.nativeapp.data.remote

import com.dailyenglish.nativeapp.core.model.AppState
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class UserDto(val email: String)

@Serializable
data class AuthResponse(val user: UserDto, val token: String? = null)

@Serializable
data class UserResponse(val user: UserDto)

@Serializable
data class StateResponse(val revision: Long = 0, val payload: AppState? = null)

@Serializable
data class PutStateRequest(val revision: Long, val payload: AppState)

@Serializable
data class RevisionResponse(val revision: Long)

@Serializable
data class PasswordRequest(val current: String, val next: String)

@Serializable
data class ErrorResponse(val error: String = "")

interface ApiService {
  @POST("api/register")
  suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

  @POST("api/login")
  suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

  @POST("api/logout")
  suspend fun logout(): Response<Unit>

  @GET("api/me")
  suspend fun me(): Response<UserResponse>

  @GET("api/state")
  suspend fun getState(): Response<StateResponse>

  @PUT("api/state")
  suspend fun putState(@Body body: PutStateRequest): Response<RevisionResponse>

  @POST("api/password")
  suspend fun changePassword(@Body body: PasswordRequest): Response<UserResponse>
}
