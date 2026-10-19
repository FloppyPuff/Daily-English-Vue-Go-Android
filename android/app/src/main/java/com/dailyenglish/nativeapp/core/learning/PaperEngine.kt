package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.data.content.PaperDto
import com.dailyenglish.nativeapp.data.content.PaperGroupDto
import com.dailyenglish.nativeapp.data.content.PaperQuestionDto

data class PaperGroup(
  val index: String,
  val key: String,
  val data: PaperGroupDto,
  val section: String,
  val instructions: String,
  val sectionScore: Int,
  val answerable: Boolean,
  val translation: Boolean,
) {
  val questions: List<PaperQuestionDto> get() = data.questions
  val label: String get() = paperGroupLabel(data)
}

data class PaperScore(
  val correct: Int,
  val total: Int,
  val wrong: List<Int>,
)

object PaperEngine {
  private val objective = { question: PaperQuestionDto ->
    question.options.isNotEmpty() && question.answer?.length == 1
  }

  fun paperGroups(paper: PaperDto): List<PaperGroup> {
    val list = mutableListOf<PaperGroup>()
    paper.sections.forEachIndexed { sectionIndex, section ->
      section.groups.forEachIndexed { groupIndex, group ->
        val index = "$sectionIndex-$groupIndex"
        val choice = group.questions.any { it.options.isNotEmpty() }
        // Part B sometimes ships only answer letters, with no option sentences.
        val written = !choice && (group.type == "translation_full" || group.questions.any { it.stem != null })
        list += PaperGroup(
          index = index,
          key = "${paper.id}#$index",
          data = group,
          section = section.title,
          instructions = section.instructions,
          sectionScore = section.score,
          answerable = choice || written,
          translation = written,
        )
      }
    }
    return list
  }

  fun gradePaper(answers: Map<String, String>, questions: List<PaperQuestionDto>): PaperScore {
    val scored = questions.filter(objective)
    val wrong = scored.filter { (answers[it.number.toString()] ?: "").trim() != it.answer }
      .map { it.number }
    return PaperScore(scored.count { (answers[it.number.toString()] ?: "").trim() == it.answer }, scored.size, wrong)
  }

  fun recordPaper(
    state: AppState,
    key: String,
    answers: Map<String, String>,
    questions: List<PaperQuestionDto>,
    words: List<String>,
    rating: String = "",
    at: Long = System.currentTimeMillis(),
  ): AppState {
    require(isPaperKey(key)) { "无效的真题记录" }
    require(rating in setOf("", "review", "mastered")) { "无效的真题自评" }
    val cleanAnswers = answers.entries
      .filter { (number, value) -> Regex("^\\d{1,3}$").matches(number) && value.length <= MAX_TEXT }
      .take(60)
      .associate { it.key to it.value }
    val score = gradePaper(cleanAnswers, questions)
    val record = PaperRecord(
      answers = cleanAnswers,
      correct = score.correct,
      total = score.total,
      wrong = score.wrong,
      words = words.filter(::isWordId).distinct().take(60),
      rating = rating,
      at = at,
    )
    // Every shipped paper set is 考研英语, so its history belongs to that track
    // regardless of the (now merged) study direction.
    val event = StudyEvent(
      type = "paper",
      id = key,
      at = at,
      exam = "postgrad",
      correct = score.correct,
      total = score.total,
    )
    return state.copy(papers = state.papers + (key to record), events = state.events + event)
  }
}

fun paperGroupLabel(group: PaperGroupDto): String = when (group.type) {
  "cloze" -> "完形填空"
  "reading" -> group.title ?: "阅读理解"
  "translation_full" -> "段落翻译"
  "translation_sentences" -> "划线句子翻译"
  "gap_fill" -> "新题型 Part B"
  else -> group.title ?: "真题"
}
