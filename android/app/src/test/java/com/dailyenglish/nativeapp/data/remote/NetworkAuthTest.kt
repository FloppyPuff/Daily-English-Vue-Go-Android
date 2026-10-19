package com.dailyenglish.nativeapp.data.remote

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.data.local.SessionStore
import com.dailyenglish.nativeapp.data.repository.AuthException
import com.dailyenglish.nativeapp.data.repository.AuthRepository
import com.dailyenglish.nativeapp.data.repository.AuthState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkAuthTest {
  private lateinit var server: MockWebServer
  private lateinit var json: Json
  private lateinit var session: FakeSessionStore
  private lateinit var remote: RemoteDataSource
  private lateinit var api: ApiService

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    session = FakeSessionStore(tokenValue = "token-1", emailValue = "a@b.com")
    val client = OkHttpClient.Builder()
      .addInterceptor(TokenInterceptor(session))
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(2, TimeUnit.SECONDS)
      .build()
    val retrofit = Retrofit.Builder()
      .baseUrl(server.url("/"))
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
    api = retrofit.create(ApiService::class.java)
    remote = RemoteDataSource(api, json)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun jsonResponse(code: Int, body: String): MockResponse =
    MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

  @Test
  fun loginSendsMobileHeadersAndParsesToken() = runBlocking {
    server.enqueue(jsonResponse(200, """{"user":{"email":"a@b.com"},"token":"new-token"}"""))
    val result = remote.login("a@b.com", "password123")
    assertTrue(result is ApiResult.Ok)
    val request = server.takeRequest()
    assertEquals("/api/login", request.path)
    assertEquals("POST", request.method)
    assertEquals("daily-english-mobile", request.getHeader("x-client"))
    assertEquals("Bearer token-1", request.getHeader("Authorization"))
    assertTrue(request.body.readUtf8().contains("a@b.com"))
  }

  @Test
  fun tokenInterceptorOmitsAuthorizationWhenSignedOut() {
    session.tokenValue = null
    server.enqueue(jsonResponse(200, "{}"))
    val client = OkHttpClient.Builder().addInterceptor(TokenInterceptor(session)).build()
    client.newCall(Request.Builder().url(server.url("/api/me")).build()).execute().use { response ->
      assertEquals(200, response.code)
    }
    val request = server.takeRequest()
    assertEquals("daily-english-mobile", request.getHeader("x-client"))
    assertNull(request.getHeader("Authorization"))
  }

  @Test
  fun unauthorizedResponseKeepsServerMessage() = runBlocking {
    server.enqueue(jsonResponse(401, """{"error":"邮箱或密码不正确"}"""))
    val result = remote.login("a@b.com", "wrong-pass")
    assertTrue(result is ApiResult.Failure)
    result as ApiResult.Failure
    assertEquals(401, result.status)
    assertEquals("邮箱或密码不正确", result.message)
  }

  @Test
  fun conflictResponseCarriesRemotePayload() = runBlocking {
    val payload = """{"version":3,"settings":{"exam":"cet6","dailyGoal":20,"sentenceGoal":3,"focusMinutes":25},"records":{},"sentenceRecords":{},"papers":{},"drafts":{},"savedWords":[],"savedSentences":[],"events":[]}"""
    server.enqueue(jsonResponse(409, """{"revision":3,"payload":$payload}"""))
    val result = remote.putState(2, AppState())
    assertTrue(result is ApiResult.Failure)
    result as ApiResult.Failure
    assertEquals(409, result.status)
    assertEquals(20, result.payload?.settings?.dailyGoal)
  }

  @Test
  fun repositoryBootstrapAndLoginPersistSession() = runBlocking {
    server.enqueue(jsonResponse(200, """{"user":{"email":"a@b.com"}}"""))
    val repository = AuthRepository(remote, session)
    repository.bootstrap()
    assertEquals(AuthState.Member("a@b.com"), repository.state.value)

    server.enqueue(jsonResponse(200, """{"user":{"email":"a@b.com"},"token":"fresh"}"""))
    val email = repository.login("a@b.com", "password123")
    assertEquals("a@b.com", email)
    assertEquals("fresh", session.tokenValue)
    assertEquals(false, session.guest)
    assertEquals(AuthState.Member("a@b.com"), repository.state.value)
  }

  @Test
  fun guestModeAndLogoutKeepLocalStateButClearAuth() = runBlocking {
    val repository = AuthRepository(remote, session)
    repository.continueAsGuest()
    assertEquals(AuthState.Anonymous(true), repository.state.value)
    assertTrue(session.guest)

    session.saveAuth("fresh", "a@b.com")
    server.enqueue(MockResponse().setResponseCode(204))
    repository.logout()
    assertNull(session.tokenValue)
    assertTrue(session.guest)
    assertEquals(AuthState.Anonymous(true), repository.state.value)
  }

  @Test
  fun failedLoginThrowsWithoutWritingSession() = runBlocking {
    server.enqueue(jsonResponse(401, """{"error":"邮箱或密码不正确"}"""))
    val repository = AuthRepository(remote, session)
    val error = runCatching { repository.login("a@b.com", "bad") }.exceptionOrNull()
    assertTrue(error is AuthException)
    assertEquals(401, (error as AuthException).status)
    assertEquals("token-1", session.tokenValue)
  }

  private class FakeSessionStore(
    var tokenValue: String? = null,
    var emailValue: String? = null,
    var guest: Boolean = false,
  ) : SessionStore {
    override suspend fun token(): String? = tokenValue
    override suspend fun email(): String? = emailValue
    override suspend fun guestMode(): Boolean = guest
    override suspend fun saveAuth(token: String, email: String) {
      tokenValue = token
      emailValue = email
      guest = false
    }
    override suspend fun clearAuth() {
      tokenValue = null
      emailValue = null
    }
    override suspend fun setGuestMode(value: Boolean) {
      guest = value
    }
  }
}
