package com.dailyenglish.nativeapp.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.core.learning.LearningEngine
import com.dailyenglish.nativeapp.core.learning.Statistics
import com.dailyenglish.nativeapp.data.content.ContentRepository
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.data.content.WordDto
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
  val loading: Boolean = true,
  val error: String = "",
  val stats: Statistics? = null,
  val statsExam: String = "all",
)

@HiltViewModel
class StatsViewModel @Inject constructor(
  private val contentRepository: ContentRepository,
  private val progressRepository: ProgressRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(StatsUiState())
  val state: StateFlow<StatsUiState> = _state.asStateFlow()
  private var words: List<WordDto>? = null
  private var sentences: List<SentenceDto>? = null

  init {
    viewModelScope.launch {
      load()
    }
    viewModelScope.launch {
      progressRepository.state.collect {
        refresh()
      }
    }
  }

  fun retry() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = "") }
      load()
    }
  }

  fun setStatsExam(exam: String) {
    val next = exam.takeIf { it == "cet6" || it == "postgrad" } ?: "all"
    if (_state.value.statsExam == next) return
    _state.value = _state.value.copy(statsExam = next)
    refresh()
  }

  private suspend fun load() {
    runCatching {
      val content = contentRepository.load()
      words = content.words
      sentences = content.sentences
      progressRepository.load()
      val state = progressRepository.state.value
      _state.value = StatsUiState(
        loading = false,
        stats = LearningEngine.statistics(state, content.words, sentences = content.sentences, exam = _state.value.statsExam),
        statsExam = _state.value.statsExam,
      )
    }.onFailure {
      _state.update { it.copy(loading = false, error = "学习记录读取失败，请重试") }
    }
  }

  private fun refresh() {
    val bank = words ?: return
    val state = progressRepository.state.value
    _state.value = _state.value.copy(
      loading = false,
      error = "",
      stats = LearningEngine.statistics(state, bank, sentences = sentences.orEmpty(), exam = _state.value.statsExam),
    )
  }
}
