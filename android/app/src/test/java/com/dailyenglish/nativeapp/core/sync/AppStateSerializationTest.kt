package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.core.model.WordRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateSerializationTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
  private val fixture = """
    {
      "version": 3,
      "settings": {"exam":"cet6","dailyGoal":20,"sentenceGoal":3,"focusMinutes":25},
      "records": {"abandon":{"attempts":2,"interval":7,"due":1760000000000,"streak":2,"lapses":0,"difficult":false,"lastReview":1760000000000}},
      "sentenceRecords": {"s001":{"answer":"参考译文","rating":"mastered","at":1760000000000}},
      "papers": {"2024-1#0-0":{"answers":{"31":"A"},"correct":1,"total":1,"wrong":[],"words":["abandon"],"rating":"mastered","at":1760000000000}},
      "drafts": {"s001":"参考译文"},
      "savedWords": ["abandon"],
      "savedSentences": ["s001"],
      "events": [
        {"type":"word","id":"abandon","quality":"good","at":1760000000000,"exam":"cet6","isNew":true},
        {"type":"focus","seconds":60,"at":1760000000000,"exam":"cet6"}
      ]
    }
  """.trimIndent()

  @Test
  fun v3FixtureRoundTrips() {
    val state = json.decodeFromString<AppState>(fixture)
    assertEquals(3, state.version)
    assertEquals(20, state.settings.dailyGoal)
    assertEquals(2, state.records.getValue("abandon").attempts)
    assertEquals("mastered", state.sentenceRecords.getValue("s001").rating)
    assertEquals("A", state.papers.getValue("2024-1#0-0").answers.getValue("31"))
    assertEquals(2, state.events.size)
    val encoded = json.encodeToString(state)
    assertEquals(state, json.decodeFromString<AppState>(encoded))
  }

  @Test
  fun versionOneRecordsRemapToHeadwords() {
    val state = AppState(
      version = 1,
      records = mapOf("w001" to WordRecord(lastReview = 10), "alpha" to WordRecord(lastReview = 20)),
      savedWords = listOf("w001", "alpha"),
      events = listOf(StudyEvent(type = "word", id = "w001", quality = "good", at = 10, exam = "cet6", isNew = true)),
    )
    val normalized = normalizeLegacy(state)
    assertEquals(3, normalized.version)
    assertTrue(normalized.records.containsKey("sustain"))
    assertTrue(normalized.records.containsKey("alpha"))
    assertEquals(listOf("sustain", "alpha"), normalized.savedWords)
    assertEquals("sustain", normalized.events.single().id)
  }

  @Test
  fun missingOptionalFieldsUseNativeDefaults() {
    val state = json.decodeFromString<AppState>("""{"version":3,"settings":{},"records":{},"sentenceRecords":{},"papers":{},"drafts":{},"savedWords":[],"savedSentences":[],"events":[]}""")
    assertEquals(Settings(), state.settings)
    assertEquals(emptyMap<String, PaperRecord>(), state.papers)
    assertEquals(emptyMap<String, SentenceRecord>(), state.sentenceRecords)
  }
}
