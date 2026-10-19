package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.core.model.WordRecord
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.data.content.WordDto
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

const val DAY_MILLIS = 86_400_000L
const val MAX_EVENTS = 100_000
const val MAX_TEXT = 10_000

private val WORD_ID = Regex("^(?:w\\d{3}|[a-z][a-z'-]{1,31})$")
private val SENTENCE_ID = Regex("^(?:s\\d{3}|\\d{4}-[12]#\\d{1,3}|cet6-\\d{6}-\\d{1,2})$")
private val PAPER_KEY = Regex("^\\d{4}-[12]#\\d{1,2}-\\d{1,2}$")

fun isWordId(id: String): Boolean = WORD_ID.matches(id)
fun isSentenceId(id: String): Boolean = SENTENCE_ID.matches(id)
fun isPaperKey(id: String): Boolean = PAPER_KEY.matches(id)

private val TIER_RANK = mapOf("高频" to 3, "中频" to 2, "低频" to 1)

// A merged card shows the strongest tier either exam assigned instead of hiding
// the word's relevance in the other track.
fun bestTier(word: WordDto): String = listOf("cet6", "postgrad")
  .mapNotNull { word.tiers[it] }
  .maxByOrNull { TIER_RANK[it] ?: 0 }
  .orEmpty()

fun localDay(at: Long = System.currentTimeMillis()): String {
  val calendar = Calendar.getInstance().apply { timeInMillis = at }
  return String.format(
    Locale.US,
    "%04d-%02d-%02d",
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH),
  )
}

fun addDays(at: Long, days: Int): Long {
  val calendar = Calendar.getInstance().apply { timeInMillis = at }
  calendar.add(Calendar.DAY_OF_MONTH, days)
  return calendar.timeInMillis
}

data class WeekSlice(val day: String, val label: String, val words: Int, val sentences: Int)

data class HeatCell(val day: String, val count: Int, val level: Int)

data class Statistics(
  val todayWords: Int,
  val todayNew: Int,
  val todaySentences: Int,
  val todayPapers: Int,
  val paperCorrect: Int,
  val paperTotal: Int,
  val todayMinutes: Int,
  val totalMinutes: Int,
  val learned: Int,
  val mastered: Int,
  val due: Int,
  val difficult: Int,
  val total: Int,
  val streak: Int,
  val week: List<WeekSlice>,
  val activeDays: Int,
  val heatmap: List<HeatCell> = emptyList(),
)

data class PlanStep(
  val id: String,
  val label: String,
  val done: Int,
  val total: Int,
  val pending: Int,
  val unit: String,
)

data class DailyPlan(
  val active: String,
  val steps: List<PlanStep>,
  val review: List<WordDto>,
  val newWords: List<WordDto>,
  val sentences: List<SentenceDto>,
)

object LearningEngine {
  private val qualities = setOf("again", "hard", "good", "easy")
  private val ratings = setOf("review", "mastered")

  fun rateWord(state: AppState, id: String, quality: String, at: Long = System.currentTimeMillis()): AppState {
    require(isWordId(id)) { "无效的记忆记录" }
    require(quality in qualities) { "无效的记忆记录" }
    val old = state.records[id]
    val interval = when (quality) {
      "again" -> 0
      "hard" -> 1
      "good" -> max(3, ((old?.interval ?: 0) * 2.2).roundToInt())
      else -> max(7, ((old?.interval ?: 0) * 2.8).roundToInt())
    }.coerceAtMost(365)
    val record = WordRecord(
      attempts = (old?.attempts ?: 0) + 1,
      interval = interval,
      due = at + if (quality == "again") 600_000L else interval * DAY_MILLIS,
      streak = if (quality == "again" || quality == "hard") 0 else (old?.streak ?: 0) + 1,
      lapses = (old?.lapses ?: 0) + if (quality == "again") 1 else 0,
      difficult = quality == "again" || quality == "hard",
      lastReview = at,
    )
    val event = StudyEvent(
      type = "word",
      id = id,
      quality = quality,
      at = at,
      exam = state.settings.exam,
      isNew = old == null,
    )
    return state.copy(
      records = state.records + (id to record),
      events = state.events + event,
    )
  }

  fun recordSentence(
    state: AppState,
    id: String,
    answer: String,
    rating: String,
    at: Long = System.currentTimeMillis(),
  ): AppState {
    require(isSentenceId(id)) { "无效的翻译记录" }
    require(rating in ratings) { "无效的翻译记录" }
    val text = answer.take(MAX_TEXT)
    val record = SentenceRecord(answer = text, rating = rating, at = at)
    val event = StudyEvent(
      type = "sentence",
      id = id,
      rating = rating,
      at = at,
      exam = state.settings.exam,
    )
    return state.copy(
      sentenceRecords = state.sentenceRecords + (id to record),
      drafts = state.drafts + (id to text),
      events = state.events + event,
    )
  }

  // The word bank is studied as one merged CET-6 + 考研 track. Records are keyed
  // by headword, so a shared word already carries one review history.
  fun buildQueue(words: List<WordDto>, state: AppState, mode: String, at: Long = System.currentTimeMillis()): List<WordDto> {
    val bank = words
    val filtered = when (mode) {
      "review" -> bank.filter { state.records[it.id]?.due?.let { due -> due <= at } == true }
      "mistakes" -> {
        val paperWords = state.papers.values.flatMap { it.words }.toSet()
        bank.filter { state.records[it.id]?.difficult == true || paperWords.contains(it.id) }
      }
      "saved" -> bank.filter { state.savedWords.contains(it.id) }
      "all" -> bank
      else -> bank.filter { state.records[it.id] == null }
    }
    return when (mode) {
      "review" -> filtered.sortedBy { state.records[it.id]?.due ?: 0L }
      "all", "saved", "mistakes" -> filtered.sortedBy { state.records[it.id]?.lastReview ?: 0L }
      "new" -> filtered.sortedByDescending { paperHits(it) }
      else -> filtered
    }
  }

  private fun paperHits(word: WordDto): Int = word.hits.values.sum()

  // Stats can be viewed across both tracks or through one of them. A word or
  // sentence belongs to a track by its own exam tags, not by whichever track was
  // active when it was practiced. Focus time is not directional.
  fun statistics(
    state: AppState,
    words: List<WordDto>,
    at: Long = System.currentTimeMillis(),
    sentences: List<SentenceDto> = emptyList(),
    exam: String = "all",
  ): Statistics {
    val today = localDay(at)
    val wordById = words.associateBy { it.id }
    val sentenceById = sentences.associateBy { it.id }
    val inTrack: (StudyEvent) -> Boolean = { event ->
      when {
        exam == "all" || event.type == "focus" -> true
        event.type == "word" -> wordById[event.id]?.exams?.contains(exam) ?: (event.exam == exam)
        event.type == "sentence" -> sentenceById[event.id]?.exams?.contains(exam) ?: (event.exam == exam)
        // All shipped papers are 考研英语, including older events tagged with the
        // study direction that was active at the time.
        event.type == "paper" -> exam == "postgrad"
        else -> event.exam == exam
      }
    }
    val relevant = state.events.filter(inTrack)
    val todayEvents = relevant.filter { localDay(it.at) == today }
    val unique = { list: List<StudyEvent>, type: String -> list.filter { it.type == type }.mapNotNull { it.id }.toSet().size }
    val active = state.events.filter { it.type != "focus" }.map { localDay(it.at) }.toMutableSet()
    state.events.filter { it.type == "focus" }
      .groupBy { localDay(it.at) }
      .forEach { (day, events) -> if (events.sumOf { it.seconds ?: 0 } >= 60) active.add(day) }
    val cursor = Calendar.getInstance().apply { timeInMillis = at }
    if (!active.contains(today)) cursor.add(Calendar.DAY_OF_MONTH, -1)
    var streak = 0
    while (active.contains(localDay(cursor.timeInMillis))) {
      streak++
      cursor.add(Calendar.DAY_OF_MONTH, -1)
    }
    val bank = if (exam == "all") words else words.filter { it.exams.contains(exam) }
    val learned = bank.filter { state.records[it.id] != null }
    val week = (0 until 7).map { i ->
      val dayAt = addDays(at, -6 + i)
      val day = localDay(dayAt)
      val events = relevant.filter { localDay(it.at) == day }
      val calendar = Calendar.getInstance().apply { timeInMillis = dayAt }
      val label = listOf("日", "一", "二", "三", "四", "五", "六")[calendar.get(Calendar.DAY_OF_WEEK) - 1]
      WeekSlice(day, label, unique(events, "word"), unique(events, "sentence"))
    }
    val paperEvents = relevant.filter { it.type == "paper" }
    val heatCounts = relevant
      .filter { it.type != "focus" }
      .groupBy { localDay(it.at) }
      .mapValues { (_, events) -> events.map { "${it.type}:${it.id}" }.toSet().size }
    val heatmap = (0 until 84).map { offset ->
      val day = localDay(addDays(at, -83 + offset))
      val count = heatCounts[day] ?: 0
      HeatCell(
        day = day,
        count = count,
        level = when {
          count > 20 -> 3
          count > 5 -> 2
          count > 0 -> 1
          else -> 0
        },
      )
    }
    return Statistics(
      todayWords = unique(todayEvents, "word"),
      todayNew = unique(todayEvents.filter { it.isNew == true }, "word"),
      todaySentences = unique(todayEvents, "sentence"),
      todayPapers = unique(todayEvents, "paper"),
      paperCorrect = paperEvents.sumOf { it.correct ?: 0 },
      paperTotal = paperEvents.sumOf { it.total ?: 0 },
      todayMinutes = todayEvents.filter { it.type == "focus" }.sumOf { it.seconds ?: 0 } / 60,
      totalMinutes = relevant.filter { it.type == "focus" }.sumOf { it.seconds ?: 0 } / 60,
      learned = learned.size,
      mastered = learned.count { (state.records[it.id]?.streak ?: 0) >= 3 },
      due = bank.count { state.records[it.id]?.due?.let { dueAt -> dueAt <= at } == true },
      difficult = learned.count { state.records[it.id]?.difficult == true },
      total = bank.size,
      streak = streak,
      week = week,
      activeDays = active.size,
      heatmap = heatmap,
    )
  }

  fun dailyPlan(state: AppState, words: List<WordDto>, sentences: List<SentenceDto>, at: Long = System.currentTimeMillis()): DailyPlan {
    val today = localDay(at)
    // Today's plan is one merged queue, so practice from either track counts
    // toward the same review, new-word and translation targets.
    val events = state.events.filter { localDay(it.at) == today }
    val review = buildQueue(words, state, "review", at)
    val dueIds = review.map { it.id }.toSet()
    val reviewedIds = events.filter { it.type == "word" && it.isNew != true }.mapNotNull { it.id }.toSet()
    val reviewDone = reviewedIds.count { !dueIds.contains(it) }
    val newDone = events.filter { it.type == "word" && it.isNew == true }.mapNotNull { it.id }.toSet().size
    val unseen = buildQueue(words, state, "new", at)
    val newTarget = minOf(state.settings.dailyGoal, newDone + unseen.size)
    val newWords = unseen.take(max(0, newTarget - newDone))
    val bank = sentences
    val sentenceIds = events.filter { it.type == "sentence" }.mapNotNull { it.id }.toSet()
    val sentenceDone = bank.count { sentenceIds.contains(it.id) }
    val sentenceTarget = minOf(state.settings.sentenceGoal, bank.size)
    val pendingSentences = bank
      .filter { !sentenceIds.contains(it.id) }
      .sortedBy { state.sentenceRecords[it.id]?.at ?: 0L }
      .take(max(0, sentenceTarget - sentenceDone))
    val steps = listOf(
      PlanStep("review", "复习旧词", reviewDone, reviewDone + review.size, review.size, "词"),
      PlanStep("new", "学习新词", minOf(newDone, newTarget), newTarget, newWords.size, "词"),
      PlanStep("sentences", "翻译句子", minOf(sentenceDone, sentenceTarget), sentenceTarget, pendingSentences.size, "句"),
    )
    return DailyPlan(
      active = steps.firstOrNull { it.pending > 0 }?.id ?: "done",
      steps = steps,
      review = review,
      newWords = newWords,
      sentences = pendingSentences,
    )
  }

  fun checkSpelling(answer: String, word: String): Boolean = answer.trim().lowercase() == word.lowercase()
}
