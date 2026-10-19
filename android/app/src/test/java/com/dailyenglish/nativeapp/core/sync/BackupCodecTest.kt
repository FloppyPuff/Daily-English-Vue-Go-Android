package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.WordRecord
import com.dailyenglish.nativeapp.core.model.createState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
  private val codec = BackupCodec(json)
  private val state = createState().copy(
    settings = Settings(exam = "postgrad", dailyGoal = 30),
    records = mapOf("abandon" to WordRecord(attempts = 2, streak = 1)),
    savedWords = listOf("abandon"),
  )
  private val words = setOf("abandon", "sustain")
  private val sentences = setOf("s001")

  @Test
  fun encodesPrettyJsonAndRestoresState() {
    val text = codec.encode(state)
    assertTrue(text.contains("\n"))
    val restored = codec.decode(text, words, sentences)
    assertTrue(restored is BackupResult.Ok)
    assertEquals(state.settings, (restored as BackupResult.Ok).state.settings)
    assertEquals(state.records, restored.state.records)
    assertEquals(listOf("abandon"), restored.state.savedWords)
  }

  @Test
  fun rejectsUnsupportedVersionAndInvalidSettings() {
    val badVersion = codec.encode(state.copy(version = 9))
    assertTrue(codec.decode(badVersion, words, sentences) is BackupResult.Error)
    val badGoal = codec.encode(state.copy(settings = Settings(dailyGoal = 0)))
    assertTrue(codec.decode(badGoal, words, sentences) is BackupResult.Error)
  }

  @Test
  fun rejectsUnknownContentAndOversizeFiles() {
    val unknown = codec.encode(state.copy(savedWords = listOf("missing")))
    val unknownResult = codec.decode(unknown, words, sentences)
    assertTrue(unknownResult is BackupResult.Error)
    assertTrue((unknownResult as BackupResult.Error).message.contains("不支持"))

    val tooLarge = codec.decode(codec.encode(state), words, sentences, maxBytes = 32)
    assertTrue(tooLarge is BackupResult.Error)
    assertTrue((tooLarge as BackupResult.Error).message.contains("5 MB"))
  }

  @Test
  fun normalizesLegacyWordIds() {
    val legacy = state.copy(version = 1, records = mapOf("w001" to WordRecord(attempts = 1)), savedWords = listOf("w001"))
    val restored = codec.decode(codec.encode(legacy), words, sentences)
    assertTrue(restored is BackupResult.Ok)
    assertEquals(setOf("sustain"), (restored as BackupResult.Ok).state.records.keys)
    assertEquals(3, restored.state.version)
    assertEquals(listOf("sustain"), restored.state.savedWords)
  }
}
