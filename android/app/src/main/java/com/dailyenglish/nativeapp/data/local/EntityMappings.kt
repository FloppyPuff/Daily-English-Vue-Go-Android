package com.dailyenglish.nativeapp.data.local

import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.core.model.WordRecord

fun Settings.toEntity(): SettingsEntity = SettingsEntity(
  id = 1,
  exam = exam,
  dailyGoal = dailyGoal,
  sentenceGoal = sentenceGoal,
  focusMinutes = focusMinutes,
)

fun SettingsEntity.toModel(): Settings = Settings(
  exam = exam,
  dailyGoal = dailyGoal,
  sentenceGoal = sentenceGoal,
  focusMinutes = focusMinutes,
)

fun WordRecord.toEntity(id: String): WordRecordEntity = WordRecordEntity(
  id = id,
  attempts = attempts,
  interval = interval,
  due = due,
  streak = streak,
  lapses = lapses,
  difficult = difficult,
  lastReview = lastReview,
)

fun WordRecordEntity.toModel(): WordRecord = WordRecord(
  attempts = attempts,
  interval = interval,
  due = due,
  streak = streak,
  lapses = lapses,
  difficult = difficult,
  lastReview = lastReview,
)

fun SentenceRecord.toEntity(id: String): SentenceRecordEntity = SentenceRecordEntity(
  id = id,
  answer = answer,
  rating = rating,
  at = at,
)

fun SentenceRecordEntity.toModel(): SentenceRecord = SentenceRecord(
  answer = answer,
  rating = rating,
  at = at,
)

fun StudyEvent.toEntity(): StudyEventEntity = StudyEventEntity(
  type = type,
  itemId = id,
  quality = quality,
  rating = rating,
  seconds = seconds,
  correct = correct,
  total = total,
  exam = exam,
  at = at,
  isNew = isNew,
)

fun StudyEventEntity.toModel(): StudyEvent = StudyEvent(
  type = type,
  id = itemId,
  quality = quality,
  rating = rating,
  seconds = seconds,
  correct = correct,
  total = total,
  at = at,
  exam = exam,
  isNew = isNew,
)
