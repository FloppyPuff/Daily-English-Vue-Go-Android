package com.dailyenglish.nativeapp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
  val exam: String = "cet6",
  val dailyGoal: Int = 20,
  val sentenceGoal: Int = 3,
  val focusMinutes: Int = 25,
)

@Serializable
data class WordRecord(
  val attempts: Int = 0,
  val interval: Int = 0,
  val due: Long = 0,
  val streak: Int = 0,
  val lapses: Int = 0,
  val difficult: Boolean = false,
  val lastReview: Long = 0,
)

@Serializable
data class SentenceRecord(
  val answer: String = "",
  val rating: String = "review",
  val at: Long = 0,
)

@Serializable
data class PaperRecord(
  val answers: Map<String, String> = emptyMap(),
  val correct: Int = 0,
  val total: Int = 0,
  val wrong: List<Int> = emptyList(),
  val words: List<String> = emptyList(),
  val rating: String = "",
  val at: Long = 0,
)

@Serializable
data class StudyEvent(
  val type: String,
  val id: String? = null,
  val quality: String? = null,
  val rating: String? = null,
  val seconds: Int? = null,
  val correct: Int? = null,
  val total: Int? = null,
  val at: Long = 0,
  val exam: String = "cet6",
  val isNew: Boolean? = null,
)

@Serializable
data class AppState(
  val version: Int = 3,
  val settings: Settings = Settings(),
  val records: Map<String, WordRecord> = emptyMap(),
  val sentenceRecords: Map<String, SentenceRecord> = emptyMap(),
  val papers: Map<String, PaperRecord> = emptyMap(),
  val drafts: Map<String, String> = emptyMap(),
  val savedWords: List<String> = emptyList(),
  val savedSentences: List<String> = emptyList(),
  val events: List<StudyEvent> = emptyList(),
)

fun createState(): AppState = AppState()
