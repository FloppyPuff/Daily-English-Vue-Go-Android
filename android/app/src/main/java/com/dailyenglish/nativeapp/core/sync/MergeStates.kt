package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.learning.MAX_EVENTS
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.core.model.SentenceRecord
import com.dailyenglish.nativeapp.core.model.StudyEvent
import com.dailyenglish.nativeapp.core.model.WordRecord

/**
 * Port of the web mergeStates contract. Neither device is the truth when both
 * studied since the last sync: keep every key, pick the later entry inside a
 * key, union bookmarks, and deduplicate events by their stable signature.
 */
fun mergeStates(local: AppState, remote: AppState): AppState {
  val mergedRecords = union(local.records, remote.records) { a, b -> later(a, b) { it.lastReview } }
  val mergedSentences = union(local.sentenceRecords, remote.sentenceRecords) { a, b -> later(a, b) { it.at } }
  val mergedPapers = union(local.papers, remote.papers) { a, b -> later(a, b) { it.at } }

  val drafts = LinkedHashMap<String, String>()
  for (id in local.drafts.keys + remote.drafts.keys) {
    val mine = local.drafts[id]
    val theirs = remote.drafts[id]
    val remoteIsNewer = (remote.sentenceRecords[id]?.at ?: 0) > (local.sentenceRecords[id]?.at ?: 0)
    val preferred = if (remoteIsNewer) theirs else mine
    val value = preferred ?: mine ?: theirs
    if (value != null) drafts[id] = value
  }

  val seen = HashSet<String>()
  val events = (local.events + remote.events)
    .sortedBy { it.at }
    .filter { event ->
      val signature = "${event.type}|${event.id.orEmpty()}|${event.quality ?: event.rating ?: event.seconds ?: ""}|${event.at}"
      seen.add(signature)
    }
    .takeLast(MAX_EVENTS)

  return AppState(
    version = 3,
    settings = local.settings,
    records = mergedRecords,
    sentenceRecords = mergedSentences,
    papers = mergedPapers,
    drafts = drafts,
    savedWords = (local.savedWords + remote.savedWords).distinct(),
    savedSentences = (local.savedSentences + remote.savedSentences).distinct(),
    events = events,
  )
}

private fun <T> union(a: Map<String, T>, b: Map<String, T>, choose: (T?, T?) -> T?): Map<String, T> {
  val out = LinkedHashMap<String, T>()
  for (key in a.keys + b.keys) {
    choose(a[key], b[key])?.let { out[key] = it }
  }
  return out
}

private fun <T> later(a: T?, b: T?, stamp: (T) -> Long): T? = when {
  a == null -> b
  b == null -> a
  stamp(a) >= stamp(b) -> a
  else -> b
}
