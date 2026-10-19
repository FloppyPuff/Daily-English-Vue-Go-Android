package com.dailyenglish.nativeapp.feature.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.core.learning.NotebookData
import com.dailyenglish.nativeapp.core.learning.NotebookEngine
import com.dailyenglish.nativeapp.data.content.Content
import com.dailyenglish.nativeapp.data.content.ContentRepository
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotebookUiState(
  val loading: Boolean = true,
  val error: String = "",
  val tab: String = "saved",
  val query: String = "",
  val data: NotebookData = NotebookData(),
)

@HiltViewModel
class NotebookViewModel @Inject constructor(
  private val contentRepository: ContentRepository,
  private val progressRepository: ProgressRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(NotebookUiState())
  val state: StateFlow<NotebookUiState> = _state.asStateFlow()
  private var content: Content? = null

  init {
    viewModelScope.launch {
      runCatching {
        content = contentRepository.load()
        progressRepository.load()
        refresh()
      }.onFailure {
        _state.update { it.copy(loading = false, error = "笔记内容读取失败，请重试") }
      }
    }
    viewModelScope.launch {
      progressRepository.state.collect { refresh() }
    }
  }

  fun setTab(tab: String) {
    _state.update { it.copy(tab = if (tab == "mistakes") "mistakes" else "saved") }
    refresh()
  }

  fun setQuery(query: String) {
    _state.update { it.copy(query = query) }
    refresh()
  }

  fun retry() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = "") }
      runCatching {
        content = contentRepository.load()
        progressRepository.load()
        refresh()
      }.onFailure {
        _state.update { it.copy(loading = false, error = "笔记内容读取失败，请重试") }
      }
    }
  }

  private fun refresh() {
    val loaded = content ?: return
    val current = _state.value
    val data = NotebookEngine.collect(
      state = progressRepository.state.value,
      words = loaded.words,
      sentences = loaded.sentences,
      tab = current.tab,
      query = current.query,
    )
    _state.value = current.copy(data = data, loading = false, error = "")
  }
}
