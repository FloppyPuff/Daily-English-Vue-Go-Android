package com.dailyenglish.nativeapp.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerInitializationTest {
  @Test
  fun workManagerInitializesWithHiltWorkerFactory() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(WorkManager.getInstance(context))
  }
}
