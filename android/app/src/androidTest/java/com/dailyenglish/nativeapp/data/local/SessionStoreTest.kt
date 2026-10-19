package com.dailyenglish.nativeapp.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
  @Test
  fun keystoreBackedTokenRoundTripsAndClears() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = EncryptedSessionStore(context, TokenCipher())
    // These tests share the real DataStore with the app; never leave the
    // developer's signed-in session cleared after the round trip.
    val previousToken = store.token()
    val previousEmail = store.email()
    val previousGuest = store.guestMode()
    try {
      store.clearAuth()
      store.saveAuth("token-round-trip", "learner@example.com")
      assertEquals("token-round-trip", store.token())
      assertEquals("learner@example.com", store.email())
      assertFalse(store.guestMode())

      store.setGuestMode(true)
      assertTrue(store.guestMode())

      store.clearAuth()
      assertNull(store.token())
      assertNull(store.email())
      store.setGuestMode(false)
    } finally {
      if (previousToken != null && previousEmail != null) {
        store.saveAuth(previousToken, previousEmail)
      } else {
        store.clearAuth()
      }
      store.setGuestMode(previousGuest)
    }
  }
}
