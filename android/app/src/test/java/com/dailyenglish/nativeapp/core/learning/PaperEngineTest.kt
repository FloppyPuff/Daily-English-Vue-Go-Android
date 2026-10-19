package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.Settings
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.data.content.PaperDto
import com.dailyenglish.nativeapp.data.content.PaperGroupDto
import com.dailyenglish.nativeapp.data.content.PaperQuestionDto
import com.dailyenglish.nativeapp.data.content.PaperSectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperEngineTest {
  private fun q(number: Int, answer: String? = null, options: Map<String, String> = emptyMap(), stem: String? = null) =
    PaperQuestionDto(number = number, answer = answer, options = options, stem = stem)

  private val paper = PaperDto(
    id = "2025-2",
    year = 2025,
    exam = "英语二",
    sections = listOf(
      PaperSectionDto(
        title = "Section I",
        instructions = "Choose the best word.",
        score = 10,
        groups = listOf(
          PaperGroupDto(
            type = "cloze",
            passage = listOf("A {{1}} and {{2}}."),
            questions = listOf(q(1, "A", mapOf("A" to "one", "B" to "two")), q(2, "B", mapOf("A" to "one", "B" to "two"))),
          ),
          PaperGroupDto(
            type = "reading",
            title = "Text 1",
            questions = listOf(q(11, "C", mapOf("A" to "a", "B" to "b", "C" to "c"), "What does it imply?")),
          ),
          PaperGroupDto(
            type = "translation_sentences",
            questions = listOf(q(31, "参考答案", stem = "Translate this sentence.")),
          ),
          PaperGroupDto(
            type = "gap_fill",
            questions = listOf(q(41, "E")),
          ),
        ),
      ),
    ),
  )

  @Test
  fun groupsCarryWebCompatibleKeysAndAnswerability() {
    val groups = PaperEngine.paperGroups(paper)
    assertEquals(listOf("0-0", "0-1", "0-2", "0-3"), groups.map { it.index })
    assertEquals("2025-2#0-0", groups[0].key)
    assertTrue(groups[0].answerable)
    assertTrue(groups[1].answerable)
    assertTrue(groups[2].answerable)
    assertTrue(groups[2].translation)
    assertFalse(groups[3].answerable)
    assertEquals("完形填空", groups[0].label)
    assertEquals("Text 1", groups[1].label)
  }

  @Test
  fun gradingIgnoresTranslationAndCountsWrongNumbers() {
    val score = PaperEngine.gradePaper(mapOf("1" to "A", "2" to "A", "11" to "C", "31" to "参考"), paper.sections[0].groups.flatMap { it.questions })
    assertEquals(2, score.correct)
    assertEquals(3, score.total)
    assertEquals(listOf(2), score.wrong)
  }

  @Test
  fun recordPaperStoresAnswersWordsAndEvent() {
    val state = createState().copy(settings = Settings(exam = "postgrad"))
    val next = PaperEngine.recordPaper(
      state = state,
      key = "2025-2#0-0",
      answers = mapOf("1" to "A", "2" to "A"),
      questions = paper.sections[0].groups[0].questions,
      words = listOf("abandon", "bad id", "abandon"),
      rating = "",
    )
    val record = next.papers.getValue("2025-2#0-0")
    assertEquals(1, record.correct)
    assertEquals(2, record.total)
    assertEquals(listOf(2), record.wrong)
    assertEquals(listOf("abandon"), record.words)
    assertEquals(1, next.events.count { it.type == "paper" && it.correct == 1 && it.total == 2 })
    // Papers are 考研 content even when the legacy settings value says otherwise.
    assertEquals("postgrad", next.events.last().exam)
  }
}
