package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.WordRecord
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.data.content.WordDto
import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookEngineTest {
  private val words = listOf(
    WordDto(id = "alpha", meaning = "阿尔法", exams = listOf("cet6")),
    WordDto(id = "beta", meaning = "贝塔", exams = listOf("cet6")),
    WordDto(id = "gamma", meaning = "伽马", exams = listOf("cet6")),
    WordDto(id = "delta", meaning = "德尔塔", exams = listOf("postgrad")),
  )
  private val sentences = listOf(
    SentenceDto(id = "s001", exams = listOf("cet6"), en = "English one", zh = "中文一"),
    SentenceDto(id = "s002", exams = listOf("cet6"), en = "English two", zh = "中文二"),
    SentenceDto(id = "s003", exams = listOf("cet6"), en = "English three", zh = "中文三"),
  )

  private val state = createState().copy(
    settings = Settings(exam = "cet6"),
    savedWords = listOf("alpha"),
    savedSentences = listOf("s001"),
    records = mapOf("beta" to WordRecord(difficult = true)),
    sentenceRecords = mapOf("s002" to SentenceRecord(rating = "review")),
    papers = mapOf("2025-2#0-0" to PaperRecord(words = listOf("gamma"))),
  )

  @Test
  fun savedTabUsesBookmarksOnly() {
    val saved = NotebookEngine.collect(state, words, sentences, "saved")
    assertEquals(listOf("alpha"), saved.words.map { it.word.id })
    assertEquals(listOf("s001"), saved.sentences.map { it.sentence.id })
  }

  @Test
  fun mistakesTabCombinesDifficultPaperAndReviewItems() {
    val mistakes = NotebookEngine.collect(state, words, sentences, "mistakes")
    assertEquals(listOf("beta", "gamma"), mistakes.words.map { it.word.id })
    assertEquals(listOf("s002"), mistakes.sentences.map { it.sentence.id })
    assertEquals(true, mistakes.words.first { it.word.id == "gamma" }.paperHit)
  }

  @Test
  fun mergedNotebookStillSupportsQuery() {
    val saved = NotebookEngine.collect(state.copy(savedWords = listOf("delta")), words, sentences, "saved")
    assertEquals(listOf("delta"), saved.words.map { it.word.id })
    val search = NotebookEngine.collect(state, words, sentences, "saved", query = "阿尔")
    assertEquals(listOf("alpha"), search.words.map { it.word.id })
    val sentenceSearch = NotebookEngine.collect(state, words, sentences, "saved", query = "中文一")
    assertEquals(listOf("s001"), sentenceSearch.sentences.map { it.sentence.id })
  }

  @Test
  fun resultsAreCappedAtSixty() {
    val many = (0 until 70).map { WordDto(id = "w${it.toString().padStart(3, '0')}", meaning = "词$it", exams = listOf("cet6")) }
    val state = createState().copy(savedWords = many.map { it.id })
    assertEquals(60, NotebookEngine.collect(state, many, emptyList(), "saved").words.size)
  }
}
