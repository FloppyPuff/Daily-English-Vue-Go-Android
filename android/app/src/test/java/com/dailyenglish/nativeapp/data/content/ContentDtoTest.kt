package com.dailyenglish.nativeapp.data.content

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDtoTest {
  private val json = Json { ignoreUnknownKeys = true }

  // Unit tests run with the working directory set to android/app. The corpus is
  // a sibling of the Android project; the synced assets copy is the fallback so
  // the test also passes when someone builds the app module on its own.
  private val contentDir: File = listOf(File("../../content"), File("src/main/assets/content"))
    .firstOrNull { it.isDirectory }
    ?: error("Cannot find the content corpus; run ./gradlew syncContent from android/ first.")

  private fun source(name: String): String =
    File(contentDir, name).readText(Charsets.UTF_8)

  @Test
  fun wordsParseWithRealCorpusCounts() {
    val doc = json.decodeFromString<WordDocument>(source("words.json"))
    assertEquals(7291, doc.words.size)
    assertEquals(7291, doc.counts.total)
    assertEquals(6916, doc.counts.cet6)
    val abandon = doc.words.first { it.id == "abandon" }
    assertNotNull(abandon.example?.en)
    assertTrue(abandon.exams.contains("cet6"))
    assertTrue(abandon.forms.isNotEmpty())
  }

  @Test
  fun sentencesParseWithAnalysis() {
    val doc = json.decodeFromString<SentenceDocument>(source("sentences.json"))
    assertEquals(253, doc.items.size)
    val sample = doc.items.first { it.id == "s001" }
    assertTrue(sample.analysis.isNotEmpty())
    assertTrue(sample.exams.contains("cet6"))
  }

  @Test
  fun paperIndexHasAllSets() {
    val doc = json.decodeFromString<PaperIndexDocument>(source("papers/index.json"))
    assertEquals(44, doc.sets.size)
    assertTrue(doc.sets.any { it.id == "2025-2" })
  }

  @Test
  fun paperParsesWithClozeAndTranslation() {
    val paper = json.decodeFromString<PaperDto>(source("papers/1998-1.json"))
    assertEquals("1998-1", paper.id)
    assertTrue(paper.sections.isNotEmpty())
    val groups = paper.sections.flatMap { it.groups }
    assertTrue(groups.any { it.type == "cloze" && it.questions.first().options.isNotEmpty() })
    assertTrue(groups.any { it.type == "translation_sentences" && it.questions.first().answer != null })
  }
}
