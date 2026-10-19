package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.StudyEvent

sealed interface ValidationResult {
  data object Valid : ValidationResult
  data class Invalid(val message: String) : ValidationResult
}

object StateValidator {
  private const val MAX_TIME = 8_640_000_000_000_000L
  private val exams = setOf("cet6", "postgrad")
  private val wordQualities = setOf("again", "hard", "good", "easy")
  private val sentenceRatings = setOf("review", "mastered")
  private val paperRatings = setOf("", "review", "mastered")
  private val answerNumber = Regex("^\\d{1,3}$")

  fun validate(state: AppState): ValidationResult {
    if (state.version !in 1..3) return invalid("状态版本不支持")
    val settings = state.settings
    if (settings.exam !in exams) return invalid("考试方向无效")
    if (settings.dailyGoal !in 1..100) return invalid("每日新词目标应为 1–100")
    if (settings.sentenceGoal !in 1..20) return invalid("每日句子目标应为 1–20")
    if (settings.focusMinutes !in 1..120) return invalid("专注时长应为 1–120 分钟")

    for ((id, record) in state.records) {
      if (!isWordId(id)) return invalid("单词记录 ID 无效：$id")
      if (record.attempts < 0 || record.interval < 0 || record.streak < 0 || record.lapses < 0) return invalid("单词记录数值无效：$id")
      if (record.due < 0 || record.due > MAX_TIME || record.lastReview < 0 || record.lastReview > MAX_TIME) return invalid("单词记录时间无效：$id")
    }
    for ((id, record) in state.sentenceRecords) {
      if (!isSentenceId(id)) return invalid("句子记录 ID 无效：$id")
      if (record.answer.length > MAX_TEXT) return invalid("句子译文过长：$id")
      if (record.rating !in sentenceRatings) return invalid("句子自评无效：$id")
      if (record.at < 0 || record.at > MAX_TIME) return invalid("句子记录时间无效：$id")
    }
    for ((key, record) in state.papers) {
      if (!isPaperKey(key)) return invalid("真题记录 ID 无效：$key")
      if (record.correct < 0 || record.total < 0 || record.correct > record.total) return invalid("真题分数无效：$key")
      if (!record.answers.all { (number, value) -> answerNumber.matches(number) && value.length <= MAX_TEXT }) return invalid("真题答案无效：$key")
      if (!record.wrong.all { it in 1..100 }) return invalid("真题错题编号无效：$key")
      if (!record.words.all(::isWordId)) return invalid("真题词汇无效：$key")
      if (record.rating !in paperRatings) return invalid("真题自评无效：$key")
      if (record.at < 0 || record.at > MAX_TIME) return invalid("真题记录时间无效：$key")
    }
    for ((id, text) in state.drafts) {
      if (!isSentenceId(id)) return invalid("草稿 ID 无效：$id")
      if (text.length > MAX_TEXT) return invalid("草稿过长：$id")
    }
    if (!state.savedWords.all(::isWordId)) return invalid("收藏词 ID 无效")
    if (!state.savedSentences.all(::isSentenceId)) return invalid("收藏句 ID 无效")
    if (state.events.size > MAX_EVENTS) return invalid("学习事件过多")

    for (event in state.events) {
      if (event.at < 0 || event.at > MAX_TIME) return invalid("事件时间无效")
      if (event.exam !in exams) return invalid("事件考试方向无效")
      when (event.type) {
        "word" -> {
          val id = event.id ?: return invalid("单词事件缺少 ID")
          if (!isWordId(id) || event.quality !in wordQualities || event.isNew == null) return invalid("单词事件无效")
        }
        "sentence" -> {
          val id = event.id ?: return invalid("句子事件缺少 ID")
          if (!isSentenceId(id) || event.rating !in sentenceRatings) return invalid("句子事件无效")
        }
        "paper" -> {
          val id = event.id ?: return invalid("真题事件缺少 ID")
          val correct = event.correct ?: return invalid("真题事件缺少得分")
          val total = event.total ?: return invalid("真题事件缺少总分")
          if (!isPaperKey(id) || correct < 0 || total < 0 || correct > total) return invalid("真题事件无效")
        }
        "focus" -> {
          val seconds = event.seconds ?: return invalid("专注事件缺少时长")
          if (seconds < 0 || seconds > 7200) return invalid("专注事件时长无效")
        }
        else -> return invalid("未知事件类型：${event.type}")
      }
    }
    return ValidationResult.Valid
  }

  private fun invalid(message: String): ValidationResult = ValidationResult.Invalid(message)
}
