package com.dailyenglish.nativeapp.core.sync

import com.dailyenglish.nativeapp.core.model.AppState

// Version 1 keyed vocabulary by w001…w090; later versions key by headword.
// Keep this migration in one place so the Room mapper and remote apply agree.
fun normalizeLegacy(state: AppState): AppState {
  if (state.version == 3) return state
  val aliases = if (state.version == 1) LEGACY_WORD_IDS else emptyMap()
  fun id(value: String): String = aliases[value] ?: value
  return state.copy(
    version = 3,
    records = state.records.mapKeys { id(it.key) },
    savedWords = state.savedWords.map(::id),
    events = state.events.map { event ->
      if (event.type == "word") event.copy(id = event.id?.let(::id)) else event
    },
  )
}
