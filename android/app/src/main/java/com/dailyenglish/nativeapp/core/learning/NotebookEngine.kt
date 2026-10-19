package com.dailyenglish.nativeapp.core.learning

import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.data.content.WordDto

data class NotebookWord(
  val word: WordDto,
  val saved: Boolean,
  val difficult: Boolean,
  val paperHit: Boolean,
)

data class NotebookSentence(
  val sentence: SentenceDto,
  val saved: Boolean,
  val review: Boolean,
  val rating: String = "",
)

data class NotebookData(
  val words: List<NotebookWord> = emptyList(),
  val sentences: List<NotebookSentence> = emptyList(),
)

object NotebookEngine {
  const val LIMIT = 60

  fun collect(
    state: AppState,
    words: List<WordDto>,
    sentences: List<SentenceDto>,
    tab: String,
    query: String = "",
  ): NotebookData {
    val mistakes = tab == "mistakes"
    val paperWords = if (mistakes) state.papers.values.flatMap { it.words }.toSet() else emptySet()
    val needle = query.trim().lowercase()
    val notebookWords = words.asSequence()
      .mapNotNull { word ->
        val saved = state.savedWords.contains(word.id)
        val difficult = state.records[word.id]?.difficult == true
        val paperHit = paperWords.contains(word.id)
        if (!(if (mistakes) difficult || paperHit else saved)) return@mapNotNull null
        NotebookWord(word, saved, difficult, paperHit)
      }
      .filter { needle.isEmpty() || matches(it.word, needle) }
      .take(LIMIT)
      .toList()
    val notebookSentences = sentences.asSequence()
      .mapNotNull { sentence ->
        val saved = state.savedSentences.contains(sentence.id)
        val rating = state.sentenceRecords[sentence.id]?.rating.orEmpty()
        val review = rating == "review"
        if (!(if (mistakes) review else saved)) return@mapNotNull null
        NotebookSentence(sentence, saved, review, rating)
      }
      .filter { needle.isEmpty() || matches(it.sentence, needle) }
      .take(LIMIT)
      .toList()
    return NotebookData(notebookWords, notebookSentences)
  }

  private fun matches(word: WordDto, needle: String): Boolean =
    word.id.lowercase().contains(needle) ||
      word.meaning.lowercase().contains(needle) ||
      word.phonetic?.lowercase()?.contains(needle) == true ||
      word.senses.any { it.lowercase().contains(needle) }

  private fun matches(sentence: SentenceDto, needle: String): Boolean =
    sentence.en.lowercase().contains(needle) ||
      sentence.zh.lowercase().contains(needle) ||
      sentence.source?.lowercase()?.contains(needle) == true
}
