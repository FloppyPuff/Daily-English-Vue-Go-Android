package com.dailyenglish.nativeapp.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class Content(
  val words: List<WordDto>,
  val sentences: List<SentenceDto>,
  val paperIndex: List<PaperIndexDto>,
  val wordCounts: WordCounts = WordCounts(),
  val sentenceCounts: SentenceCounts = SentenceCounts(),
  val generatedAt: String? = null,
) {
  val wordById: Map<String, WordDto> by lazy { words.associateBy { it.id } }
  val sentenceById: Map<String, SentenceDto> by lazy { sentences.associateBy { it.id } }
}

@Singleton
class ContentRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val json: Json,
) {
  private val mutex = Mutex()
  private var cached: Content? = null
  private val papers = java.util.concurrent.ConcurrentHashMap<String, PaperDto>()

  suspend fun load(): Content {
    cached?.let { return it }
    return mutex.withLock {
      cached ?: withContext(Dispatchers.IO) {
        val words = read<WordDocument>("words.json")
        val sentences = read<SentenceDocument>("sentences.json")
        val papers = read<PaperIndexDocument>("papers/index.json")
        Content(
          words = words.words,
          sentences = sentences.items,
          paperIndex = papers.sets,
          wordCounts = words.counts,
          sentenceCounts = sentences.counts,
          generatedAt = words.generatedAt,
        ).also { cached = it }
      }
    }
  }

  suspend fun paper(id: String): PaperDto? {
    if (!PAPER_SET_ID.matches(id)) return null
    papers[id]?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching { read<PaperDto>("papers/$id.json") }.getOrNull()?.also { papers[id] = it }
    }
  }

  private inline fun <reified T> read(name: String): T {
    val text = context.assets.open("content/$name").bufferedReader().use { it.readText() }
    return json.decodeFromString(text)
  }

  companion object {
    private val PAPER_SET_ID = Regex("""^\d{4}-[12]$""")
  }
}
