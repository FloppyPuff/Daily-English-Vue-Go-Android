package com.dailyenglish.nativeapp.core.sync

import androidx.room.withTransaction
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.data.local.DailyEnglishDatabase
import com.dailyenglish.nativeapp.data.local.DraftEntity
import com.dailyenglish.nativeapp.data.local.PaperRecordEntity
import com.dailyenglish.nativeapp.data.local.ProgressDao
import com.dailyenglish.nativeapp.data.local.SavedSentenceEntity
import com.dailyenglish.nativeapp.data.local.SavedWordEntity
import com.dailyenglish.nativeapp.data.local.SentenceRecordEntity
import com.dailyenglish.nativeapp.data.local.SettingsEntity
import com.dailyenglish.nativeapp.data.local.StudyEventEntity
import com.dailyenglish.nativeapp.data.local.WordRecordEntity
import com.dailyenglish.nativeapp.data.local.toEntity
import com.dailyenglish.nativeapp.data.local.toModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class StateMapper @Inject constructor(
  private val database: DailyEnglishDatabase,
  private val dao: ProgressDao,
  private val json: Json,
) : StateStore {
  override suspend fun snapshot(): AppState = database.withTransaction {
    AppState(
      version = 3,
      settings = dao.settings()?.toModel() ?: Settings(),
      records = dao.wordRecords().associate { it.id to it.toModel() },
      sentenceRecords = dao.sentenceRecords().associate { it.id to it.toModel() },
      papers = dao.paperRecords().associate { it.key to it.toModel(json) },
      drafts = dao.drafts().associate { it.id to it.text },
      savedWords = dao.savedWords().map { it.wordId },
      savedSentences = dao.savedSentences().map { it.sentenceId },
      events = dao.events().map { it.toModel() },
    )
  }

  suspend fun savePaper(key: String, record: PaperRecord) {
    dao.upsertPaper(record.toEntity(key))
  }

  override suspend fun replaceAll(state: AppState) {
    val normalized = normalizeLegacy(state)
    database.withTransaction {
      clearAll()
      dao.upsertSettings(normalized.settings.toEntity())
      if (normalized.records.isNotEmpty()) {
        dao.upsertWords(normalized.records.map { (id, record) -> record.toEntity(id) })
      }
      if (normalized.sentenceRecords.isNotEmpty()) {
        dao.upsertSentences(normalized.sentenceRecords.map { (id, record) -> record.toEntity(id) })
      }
      if (normalized.papers.isNotEmpty()) {
        dao.upsertPapers(normalized.papers.map { (key, record) -> record.toEntity(key) })
      }
      if (normalized.drafts.isNotEmpty()) {
        dao.upsertDrafts(normalized.drafts.map { (id, text) -> DraftEntity(id, text) })
      }
      if (normalized.savedWords.isNotEmpty()) {
        dao.insertSavedWords(normalized.savedWords.map(::SavedWordEntity))
      }
      if (normalized.savedSentences.isNotEmpty()) {
        dao.insertSavedSentences(normalized.savedSentences.map(::SavedSentenceEntity))
      }
      if (normalized.events.isNotEmpty()) {
        dao.insertEvents(normalized.events.map { it.toEntity() })
      }
    }
  }

  private suspend fun clearAll() {
    dao.clearWords()
    dao.clearSentences()
    dao.clearPapers()
    dao.clearDrafts()
    dao.clearSavedWords()
    dao.clearSavedSentences()
    dao.clearEvents()
    dao.clearSettings()
  }
}

private fun PaperRecord.toEntity(key: String): PaperRecordEntity = PaperRecordEntity(
  key = key,
  answersJson = Json.encodeToString(answers),
  correct = correct,
  total = total,
  wrongJson = Json.encodeToString(wrong),
  wordsJson = Json.encodeToString(words),
  rating = rating,
  at = at,
)

private fun PaperRecordEntity.toModel(json: Json): PaperRecord = PaperRecord(
  answers = decode(json, answersJson, emptyMap()),
  correct = correct,
  total = total,
  wrong = decode(json, wrongJson, emptyList()),
  words = decode(json, wordsJson, emptyList()),
  rating = rating,
  at = at,
)

private inline fun <reified T> decode(json: Json, text: String, fallback: T): T =
  runCatching { json.decodeFromString<T>(text) }.getOrDefault(fallback)
