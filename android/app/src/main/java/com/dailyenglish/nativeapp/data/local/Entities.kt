package com.dailyenglish.nativeapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "word_records")
data class WordRecordEntity(
  @PrimaryKey val id: String,
  val attempts: Int,
  val interval: Int,
  val due: Long,
  val streak: Int,
  val lapses: Int,
  val difficult: Boolean,
  val lastReview: Long,
)

@Entity(tableName = "sentence_records")
data class SentenceRecordEntity(
  @PrimaryKey val id: String,
  val answer: String,
  val rating: String,
  val at: Long,
)

@Entity(tableName = "paper_records")
data class PaperRecordEntity(
  @PrimaryKey val key: String,
  val answersJson: String,
  val correct: Int,
  val total: Int,
  val wrongJson: String,
  val wordsJson: String,
  val rating: String,
  val at: Long,
)

@Entity(tableName = "drafts")
data class DraftEntity(
  @PrimaryKey val id: String,
  val text: String,
)

@Entity(tableName = "saved_words")
data class SavedWordEntity(@PrimaryKey val wordId: String)

@Entity(tableName = "saved_sentences")
data class SavedSentenceEntity(@PrimaryKey val sentenceId: String)

@Entity(
  tableName = "study_events",
  indices = [Index("at"), Index("type", "exam")],
)
data class StudyEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: String,
  val itemId: String?,
  val quality: String?,
  val rating: String?,
  val seconds: Int?,
  val correct: Int?,
  val total: Int?,
  val exam: String,
  val at: Long,
  val isNew: Boolean?,
)

@Entity(tableName = "settings")
data class SettingsEntity(
  @PrimaryKey val id: Int = 1,
  val exam: String,
  val dailyGoal: Int,
  val sentenceGoal: Int,
  val focusMinutes: Int,
)
