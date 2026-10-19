package com.dailyenglish.nativeapp.data.repository

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.WordRecord
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.core.sync.StateStore
import com.dailyenglish.nativeapp.data.local.SessionStore
import com.dailyenglish.nativeapp.data.remote.ApiResult
import com.dailyenglish.nativeapp.data.remote.RevisionResponse
import com.dailyenglish.nativeapp.data.remote.StateResponse
import com.dailyenglish.nativeapp.data.remote.SyncApi
import com.dailyenglish.nativeapp.data.remote.UserDto
import com.dailyenglish.nativeapp.data.remote.UserResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {
  @Test
  fun initialReconcileUploadsLocalStateAtRevisionZero() = runTest {
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(0, null))
    fixture.api.putResults.add(ApiResult.Ok(RevisionResponse(1)))
    fixture.sync.start()

    advanceUntilIdle()

    assertEquals(1, fixture.api.putCalls)
    assertEquals(SyncStatus.IDLE, fixture.sync.uiState.value.status)
    assertEquals(1L, fixture.sync.uiState.value.revision)
    assertFalse(fixture.scheduler.scheduled)
  }

  @Test
  fun remoteRevisionWinsAndIsAppliedToTheStore() = runTest {
    val remote = createState().copy(records = mapOf("beta" to WordRecord(lastReview = 20)))
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(4, remote))
    fixture.sync.start()

    advanceUntilIdle()

    assertEquals(remote, fixture.store.current)
    assertEquals(4L, fixture.sync.uiState.value.revision)
    assertEquals(remote, fixture.sync.remoteState.value)
    assertEquals(0, fixture.api.putCalls)
  }

  @Test
  fun conflictMergesAndRetriesWithReturnedRevision() = runTest {
    val remote = createState().copy(records = mapOf("gamma" to WordRecord(lastReview = 30)))
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(1, createState()))
    fixture.api.putResults.add(ApiResult.Failure(409, "冲突", remote, 2))
    fixture.api.putResults.add(ApiResult.Ok(RevisionResponse(3)))
    fixture.sync.start()

    advanceUntilIdle()
    fixture.sync.markDirty()
    advanceTimeBy(1500)
    advanceUntilIdle()

    assertTrue(fixture.store.current.records.containsKey("gamma"))
    assertEquals(2, fixture.api.putCalls)
    assertEquals(3L, fixture.sync.uiState.value.revision)
    assertEquals(SyncStatus.IDLE, fixture.sync.uiState.value.status)
  }

  @Test
  fun unauthorizedClearsSessionAndStopsScheduler() = runTest {
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(0, null))
    fixture.api.putResults.add(ApiResult.Failure(401, "请先登录"))
    fixture.sync.start()

    advanceUntilIdle()

    assertEquals(SyncStatus.SIGNED_OUT, fixture.sync.uiState.value.status)
    assertNull(fixture.session.tokenValue)
    assertTrue(fixture.scheduler.cancelled)
  }

  @Test
  fun offlineUploadSchedulesRetryWithoutMarkingSignedOut() = runTest {
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(0, null))
    fixture.api.putResults.add(ApiResult.Failure(0, "无法连接服务器"))
    fixture.sync.start()

    advanceUntilIdle()

    assertEquals(SyncStatus.OFFLINE, fixture.sync.uiState.value.status)
    assertTrue(fixture.scheduler.scheduled)
    assertEquals("a@b.com", fixture.session.emailValue)
  }

  @Test
  fun markDirtyDebouncesAndUploads() = runTest {
    val fixture = newFixture()
    fixture.api.getStateResult = ApiResult.Ok(StateResponse(0, null))
    fixture.api.putResults.add(ApiResult.Ok(RevisionResponse(1)))
    fixture.sync.start()
    advanceUntilIdle()

    fixture.api.putResults.add(ApiResult.Ok(RevisionResponse(2)))
    fixture.sync.markDirty()
    advanceTimeBy(1000)
    assertEquals(1, fixture.api.putCalls)
    advanceTimeBy(600)
    advanceUntilIdle()
    assertEquals(2, fixture.api.putCalls)
    assertEquals(2L, fixture.sync.uiState.value.revision)
  }

  private fun TestScope.newFixture(): Fixture =
    Fixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()))

  private class Fixture(scope: CoroutineScope) {
    val api = FakeSyncApi()
    val store = FakeStateStore()
    val session = FakeSession()
    val auth = FakeAuth()
    val scheduler = FakeScheduler()
    val sync = SyncManager(api, store, session, auth, scheduler, scope)
  }

  private class FakeSyncApi : SyncApi {
    var getStateResult: ApiResult<StateResponse> = ApiResult.Ok(StateResponse(0, null))
    val putResults = ArrayDeque<ApiResult<RevisionResponse>>()
    var putCalls = 0

    override suspend fun me(): ApiResult<UserResponse> = ApiResult.Ok(UserResponse(UserDto("a@b.com")))
    override suspend fun getState(): ApiResult<StateResponse> = getStateResult
    override suspend fun putState(revision: Long, payload: AppState): ApiResult<RevisionResponse> {
      putCalls++
      return putResults.removeFirstOrNull() ?: ApiResult.Ok(RevisionResponse(revision + 1))
    }
  }

  private class FakeStateStore : StateStore {
    var current: AppState = createState()
    override suspend fun snapshot(): AppState = current
    override suspend fun replaceAll(state: AppState) { current = state }
  }

  private class FakeSession : SessionStore {
    var tokenValue: String? = "token"
    var emailValue: String? = "a@b.com"
    var guest = false
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
    override suspend fun setGuestMode(value: Boolean) { guest = value }
  }

  private class FakeAuth : SessionStateProvider {
    private val mutable = MutableStateFlow<AuthState>(AuthState.Member("a@b.com"))
    override val state: StateFlow<AuthState> = mutable
    override suspend fun markSignedOut() {
      mutable.value = AuthState.Anonymous(true)
    }
  }

  private class FakeScheduler : SyncScheduler {
    var scheduled = false
    var cancelled = false
    override fun schedule() { scheduled = true }
    override fun cancel() { cancelled = true }
  }
}
