package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.learning.ValidationResult
import com.dailyenglish.nativeapp.core.learning.isWordId
import com.dailyenglish.nativeapp.core.learning.StateValidator
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.core.model.WordRecord
import com.dailyenglish.nativeapp.core.model.createState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateValidatorTest {
  @Test
  fun defaultStateIsValid() {
    assertEquals(ValidationResult.Valid, StateValidator.validate(createState()))
  }

  @Test
  fun rejectsUnsupportedSettingsAndIds() {
    assertTrue(StateValidator.validate(createState().copy(settings = Settings(dailyGoal = 0))) is ValidationResult.Invalid)
    assertTrue(StateValidator.validate(createState().copy(records = mapOf("Native" to WordRecord()))) is ValidationResult.Invalid)
    assertTrue(StateValidator.validate(createState().copy(savedWords = listOf("Bad Id"))) is ValidationResult.Invalid)
  }

  @Test
  fun acceptsVersionOneLegacyWordIds() {
    val state = createState().copy(version = 1, records = mapOf("w001" to WordRecord(lastReview = 1)))
    assertEquals(ValidationResult.Valid, StateValidator.validate(state))
    assertTrue(isWordId("w001"))
  }

  @Test
  fun rejectsUnknownEventsAndBadFocus() {
    assertTrue(
      StateValidator.validate(createState().copy(events = listOf(StudyEvent(type = "unknown", at = 1)))) is ValidationResult.Invalid,
    )
    assertTrue(
      StateValidator.validate(createState().copy(events = listOf(StudyEvent(type = "focus", seconds = 9000, at = 1)))) is ValidationResult.Invalid,
    )
  }

  @Test
  fun mergeKeepsLaterRecordsAndUnionsBookmarks() {
    val local = AppState(
      settings = Settings(dailyGoal = 30),
      records = mapOf(
        "alpha" to WordRecord(lastReview = 100, streak = 1),
        "beta" to WordRecord(lastReview = 50),
      ),
      savedWords = listOf("alpha", "gamma"),
      events = listOf(
        StudyEvent(type = "word", id = "alpha", quality = "good", at = 100, exam = "cet6", isNew = true),
        StudyEvent(type = "focus", seconds = 60, at = 150, exam = "cet6"),
      ),
    )
    val remote = AppState(
      settings = Settings(dailyGoal = 20),
      records = mapOf(
        "alpha" to WordRecord(lastReview = 200, streak = 3),
        "gamma" to WordRecord(lastReview = 250),
      ),
      savedWords = listOf("beta", "alpha"),
      events = listOf(
        StudyEvent(type = "word", id = "alpha", quality = "good", at = 100, exam = "cet6", isNew = true),
        StudyEvent(type = "focus", seconds = 60, at = 300, exam = "cet6"),
      ),
    )
    val merged = mergeStates(local, remote)
    assertEquals(30, merged.settings.dailyGoal)
    assertEquals(3, merged.records.getValue("alpha").streak)
    assertEquals(250L, merged.records.getValue("gamma").lastReview)
    assertEquals(listOf("alpha", "gamma", "beta"), merged.savedWords)
    assertEquals(3, merged.events.size)
    assertEquals(1, merged.events.count { it.type == "word" })
    assertEquals(listOf(100L, 150L, 300L), merged.events.map { it.at })
  }

  @Test
  fun draftsFollowTheNewerSentenceRecord() {
    val local = createState().copy(
      sentenceRecords = mapOf("s001" to SentenceRecord(answer = "old", rating = "review", at = 100)),
      drafts = mapOf("s001" to "old"),
    )
    val remote = createState().copy(
      sentenceRecords = mapOf("s001" to SentenceRecord(answer = "new", rating = "mastered", at = 200)),
      drafts = mapOf("s001" to "new"),
    )
    assertEquals("new", mergeStates(local, remote).drafts.getValue("s001"))
  }
}
