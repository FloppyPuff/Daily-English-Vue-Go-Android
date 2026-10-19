package com.dailyenglish.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProgressDao {
  @Query("SELECT * FROM word_records")
  suspend fun wordRecords(): List<WordRecordEntity>

  @Query("SELECT * FROM sentence_records")
  suspend fun sentenceRecords(): List<SentenceRecordEntity>

  @Query("SELECT * FROM paper_records")
  suspend fun paperRecords(): List<PaperRecordEntity>

  @Query("SELECT * FROM drafts")
  suspend fun drafts(): List<DraftEntity>

  @Query("SELECT wordId FROM saved_words")
  suspend fun savedWords(): List<SavedWordEntity>

  @Query("SELECT sentenceId FROM saved_sentences")
  suspend fun savedSentences(): List<SavedSentenceEntity>

  @Query("SELECT * FROM study_events ORDER BY at, id")
  suspend fun events(): List<StudyEventEntity>

  @Query("SELECT * FROM settings WHERE id = 1")
  suspend fun settings(): SettingsEntity?

  @Upsert
  suspend fun upsertWord(record: WordRecordEntity)

  @Upsert
  suspend fun upsertWords(records: List<WordRecordEntity>)

  @Upsert
  suspend fun upsertSentence(record: SentenceRecordEntity)

  @Upsert
  suspend fun upsertSentences(records: List<SentenceRecordEntity>)

  @Upsert
  suspend fun upsertPaper(record: PaperRecordEntity)

  @Upsert
  suspend fun upsertPapers(records: List<PaperRecordEntity>)

  @Upsert
  suspend fun upsertDraft(draft: DraftEntity)

  @Upsert
  suspend fun upsertDrafts(drafts: List<DraftEntity>)

  @Upsert
  suspend fun upsertSettings(settings: SettingsEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSavedWord(word: SavedWordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSavedWords(words: List<SavedWordEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSavedSentence(sentence: SavedSentenceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSavedSentences(sentences: List<SavedSentenceEntity>)

  @Query("DELETE FROM saved_words WHERE wordId = :id")
  suspend fun deleteSavedWord(id: String)

  @Query("DELETE FROM saved_sentences WHERE sentenceId = :id")
  suspend fun deleteSavedSentence(id: String)

  @Insert
  suspend fun insertEvent(event: StudyEventEntity)

  @Insert
  suspend fun insertEvents(events: List<StudyEventEntity>)

  @Query("DELETE FROM word_records")
  suspend fun clearWords()

  @Query("DELETE FROM sentence_records")
  suspend fun clearSentences()

  @Query("DELETE FROM paper_records")
  suspend fun clearPapers()

  @Query("DELETE FROM drafts")
  suspend fun clearDrafts()

  @Query("DELETE FROM saved_words")
  suspend fun clearSavedWords()

  @Query("DELETE FROM saved_sentences")
  suspend fun clearSavedSentences()

  @Query("DELETE FROM study_events")
  suspend fun clearEvents()

  @Query("DELETE FROM settings")
  suspend fun clearSettings()

  @Query("SELECT COUNT(*) FROM study_events")
  suspend fun eventCount(): Int
}
