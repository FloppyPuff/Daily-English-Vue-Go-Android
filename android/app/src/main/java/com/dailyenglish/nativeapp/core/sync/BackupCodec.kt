package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.learning.StateValidator
import com.dailyenglish.nativeapp.core.learning.ValidationResult
import com.dailyenglish.nativeapp.core.learning.localDay
import com.dailyenglish.nativeapp.core.model.AppState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface BackupResult {
  data class Ok(val state: AppState) : BackupResult
  data class Error(val message: String) : BackupResult
}

@Singleton
class BackupCodec @Inject constructor(json: Json) {
  private val pretty = Json(json) { prettyPrint = true }

  fun encode(state: AppState): String = pretty.encodeToString(state)

  fun decode(
    text: String,
    knownWords: Set<String>,
    knownSentences: Set<String>,
    maxBytes: Int = MAX_BYTES,
  ): BackupResult {
    if (text.toByteArray(Charsets.UTF_8).size > maxBytes) {
      return BackupResult.Error("备份文件超过 5 MB 上限。")
    }
    val parsed = runCatching { pretty.decodeFromString<AppState>(text) }.getOrElse {
      return BackupResult.Error("读取备份失败，原有数据未改变。")
    }
    when (val validation = StateValidator.validate(parsed)) {
      is ValidationResult.Invalid -> return BackupResult.Error(validation.message)
      ValidationResult.Valid -> Unit
    }
    val state = normalizeLegacy(parsed)
    if (state.records.keys.any { it !in knownWords } ||
      state.savedWords.any { it !in knownWords } ||
      state.sentenceRecords.keys.any { it !in knownSentences } ||
      state.savedSentences.any { it !in knownSentences }
    ) {
      return BackupResult.Error("备份包含当前版本不支持的词句，请使用对应版本恢复。")
    }
    return BackupResult.Ok(state)
  }

  fun fileName(at: Long = System.currentTimeMillis()): String = "daily-english-${localDay(at)}.json"

  companion object {
    const val MAX_BYTES = 5 * 1024 * 1024
  }
}
