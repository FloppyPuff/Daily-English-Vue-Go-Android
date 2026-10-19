package com.dailyenglish.nativeapp.data.content

import kotlinx.serialization.Serializable

@Serializable
data class WordDocument(
  val generatedAt: String? = null,
  val counts: WordCounts = WordCounts(),
  val words: List<WordDto> = emptyList(),
)

@Serializable
data class WordCounts(
  val total: Int = 0,
  val cet6: Int = 0,
  val postgrad: Int = 0,
)

@Serializable
data class WordDto(
  val id: String,
  val phonetic: String? = null,
  val pos: String? = null,
  val meaning: String = "",
  val senses: List<String> = emptyList(),
  val exams: List<String> = emptyList(),
  val base: Boolean = false,
  val extra: List<String> = emptyList(),
  val collins: Int? = null,
  val oxford: Boolean = false,
  val freq: Int? = null,
  val forms: List<WordFormDto> = emptyList(),
  val hits: Map<String, Int> = emptyMap(),
  val postgradYears: Int? = null,
  val cet6Years: Int? = null,
  val example: WordExampleDto? = null,
  val tiers: Map<String, String> = emptyMap(),
  val topic: String? = null,
  val curated: Boolean = false,
)

@Serializable
data class WordFormDto(
  val label: String = "",
  val value: String = "",
)

@Serializable
data class WordExampleDto(
  val en: String? = null,
  val zh: String? = null,
  val source: String? = null,
  val from: String? = null,
)

@Serializable
data class SentenceDocument(
  val generatedAt: String? = null,
  val counts: SentenceCounts = SentenceCounts(),
  val items: List<SentenceDto> = emptyList(),
)

@Serializable
data class SentenceCounts(
  val total: Int = 0,
  val en2zh: Int = 0,
  val zh2en: Int = 0,
  val pastPaper: Int = 0,
)

@Serializable
data class SentenceDto(
  val id: String,
  val exams: List<String> = emptyList(),
  val kind: String = "sentence",
  val direction: String = "en2zh",
  val level: String? = null,
  val topic: String? = null,
  val en: String = "",
  val zh: String = "",
  val analysis: List<SentenceAnalysisDto> = emptyList(),
  val keywords: List<SentenceKeywordDto> = emptyList(),
  val from: String? = null,
  val number: Int? = null,
  val source: String? = null,
)

@Serializable
data class SentenceAnalysisDto(
  val label: String = "",
  val text: String = "",
)

@Serializable
data class SentenceKeywordDto(
  val word: String = "",
  val meaning: String = "",
)

@Serializable
data class PaperIndexDocument(
  val sets: List<PaperIndexDto> = emptyList(),
)

@Serializable
data class PaperIndexDto(
  val id: String,
  val year: Int = 0,
  val exam: String = "",
  val sections: Int = 0,
  val questions: Int = 0,
  val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class PaperDto(
  val id: String,
  val year: Int = 0,
  val exam: String = "",
  val source: String? = null,
  val sections: List<PaperSectionDto> = emptyList(),
  val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class PaperSectionDto(
  val title: String = "",
  val instructions: String = "",
  val score: Int = 0,
  val groups: List<PaperGroupDto> = emptyList(),
)

@Serializable
data class PaperGroupDto(
  val type: String = "",
  val title: String? = null,
  val passage: List<String> = emptyList(),
  val questions: List<PaperQuestionDto> = emptyList(),
)

@Serializable
data class PaperQuestionDto(
  val number: Int = 0,
  val options: Map<String, String> = emptyMap(),
  val answer: String? = null,
  val stem: String? = null,
  val score: Int? = null,
)
