package com.dailyenglish.nativeapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.data.content.PaperQuestionDto
import com.dailyenglish.nativeapp.core.sync.StateMapper
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.data.repository.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
  private lateinit var db: DailyEnglishDatabase
  private lateinit var dao: ProgressDao
  private lateinit var mapper: StateMapper
  private lateinit var trigger: FakeTrigger

  private class FakeTrigger : SyncTrigger {
    var count = 0
    override val remoteState: StateFlow<AppState?> = MutableStateFlow(null)
    override fun markDirty() { count++ }
  }

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, DailyEnglishDatabase::class.java).allowMainThreadQueries().build()
    dao = db.progressDao()
    mapper = StateMapper(db, dao, Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
    trigger = FakeTrigger()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun progressSurvivesRepositoryReloadAndMarksDirty() = runBlocking {
    val repository = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    repository.load()
    repository.rateWord("abandon", "good")

    assertEquals(1, trigger.count)
    assertEquals(1, dao.eventCount())
    assertEquals(1, repository.state.value.records.size)

    val reloaded = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    reloaded.load()
    assertEquals(1, reloaded.state.value.records.size)
    assertEquals(3, reloaded.state.value.records.getValue("abandon").interval)
  }

  @Test
  fun savedItemsSettingsAndFocusPersist() = runBlocking {
    val repository = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    repository.load()
    repository.toggleSavedWord("abandon")
    repository.toggleSavedSentence("s001")
    repository.updateSettings(dailyGoal = 30, sentenceGoal = 4, focusMinutes = 40, exam = "postgrad")
    repository.addFocus(65)

    val snapshot = mapper.snapshot()
    assertEquals(listOf("abandon"), snapshot.savedWords)
    assertEquals(listOf("s001"), snapshot.savedSentences)
    assertEquals(30, snapshot.settings.dailyGoal)
    assertEquals("postgrad", snapshot.settings.exam)
    assertEquals(1, snapshot.events.count { it.type == "focus" && it.seconds == 65 })
    assertTrue(trigger.count >= 4)
  }

  @Test
  fun remoteReplaceClearsStaleRows() = runBlocking {
    val repository = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    repository.load()
    repository.rateWord("abandon", "good")
    repository.toggleSavedWord("abandon")
    assertEquals(1, dao.eventCount())

    repository.replaceFromRemote(createState())
    assertEquals(0, dao.eventCount())
    assertTrue(repository.state.value.records.isEmpty())
    assertTrue(repository.state.value.savedWords.isEmpty())
  }

  @Test
  fun paperRecordSurvivesReload() = runBlocking {
    val repository = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    repository.load()
    val questions = listOf(
      PaperQuestionDto(1, mapOf("A" to "one", "B" to "two"), "A"),
      PaperQuestionDto(2, mapOf("A" to "one", "B" to "two"), "B"),
    )
    repository.recordPaper("2025-2#0-0", mapOf("1" to "A", "2" to "A"), questions, listOf("abandon"))

    val record = repository.state.value.papers.getValue("2025-2#0-0")
    assertEquals(1, record.correct)
    assertEquals(2, record.total)
    assertEquals(listOf(2), record.wrong)

    val reloaded = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    reloaded.load()
    assertEquals(1, reloaded.state.value.papers.getValue("2025-2#0-0").correct)
  }

  @Test
  fun importedStateReplacesLocalRowsAndMarksDirty() = runBlocking {
    val repository = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    repository.load()
    repository.rateWord("abandon", "good")
    repository.toggleSavedWord("abandon")
    val before = trigger.count

    val imported = createState().copy(savedWords = listOf("sustain"))
    repository.importState(imported)
    assertTrue(trigger.count > before)
    assertTrue(repository.state.value.records.isEmpty())
    assertEquals(listOf("sustain"), repository.state.value.savedWords)

    val reloaded = ProgressRepository(dao, mapper, trigger, CoroutineScope(Dispatchers.Unconfined))
    reloaded.load()
    assertTrue(reloaded.state.value.records.isEmpty())
    assertEquals(listOf("sustain"), reloaded.state.value.savedWords)
  }
}
