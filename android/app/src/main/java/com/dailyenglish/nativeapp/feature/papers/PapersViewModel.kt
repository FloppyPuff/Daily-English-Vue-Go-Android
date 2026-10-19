package com.dailyenglish.nativeapp.feature.papers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.core.learning.PaperEngine
import com.dailyenglish.nativeapp.core.learning.PaperGroup
import com.dailyenglish.nativeapp.data.content.Content
import com.dailyenglish.nativeapp.data.content.ContentRepository
import com.dailyenglish.nativeapp.data.content.PaperDto
import com.dailyenglish.nativeapp.data.content.PaperIndexDto
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PapersUiState(
  val loadingIndex: Boolean = true,
  val indexError: String = "",
  val paperIndex: List<PaperIndexDto> = emptyList(),
  val loadingPaper: Boolean = false,
  val paperError: String = "",
  val paper: PaperDto? = null,
  val group: PaperGroup? = null,
  val answers: Map<String, String> = emptyMap(),
  val checked: Boolean = false,
  val rating: String = "",
  val submitting: Boolean = false,
)

@HiltViewModel
class PapersViewModel @Inject constructor(
  private val contentRepository: ContentRepository,
  private val progressRepository: ProgressRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(PapersUiState())
  val state: StateFlow<PapersUiState> = _state.asStateFlow()
  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages = _messages.asSharedFlow()
  private var content: Content? = null

  init {
    loadIndex()
  }

  fun loadIndex() {
    viewModelScope.launch {
      _state.update { it.copy(loadingIndex = true, indexError = "") }
      runCatching { contentRepository.load() }
        .onSuccess { loaded ->
          content = loaded
          _state.update {
            it.copy(
              loadingIndex = false,
              paperIndex = loaded.paperIndex,
              indexError = if (loaded.paperIndex.isEmpty()) "内容中没有找到真题索引" else "",
            )
          }
        }
        .onFailure {
          _state.update { it.copy(loadingIndex = false, indexError = "真题索引读取失败，请重试") }
        }
    }
  }

  fun openPaper(id: String) {
    if (_state.value.paper?.id == id) return
    viewModelScope.launch {
      loadPaper(id)
      _state.update { it.copy(group = null, answers = emptyMap(), checked = false, rating = "") }
    }
  }

  fun openGroup(setId: String, index: String) {
    viewModelScope.launch {
      val paper = if (_state.value.paper?.id == setId) _state.value.paper else loadPaper(setId)
      if (paper == null) return@launch
      val group = PaperEngine.paperGroups(paper).firstOrNull { it.index == index }
      if (group == null) {
        _state.update { it.copy(paperError = "没有找到这一组练习") }
        return@launch
      }
      val record = progressRepository.state.value.papers[group.key]
      _state.update {
        it.copy(
          group = group,
          answers = record?.answers ?: emptyMap(),
          checked = record != null && (record.total > 0 || record.rating.isNotBlank()),
          rating = record?.rating ?: "",
          paperError = "",
        )
      }
    }
  }

  fun pick(number: Int, choice: String) {
    val state = _state.value
    val group = state.group ?: return
    if (group.translation) return
    if (group.questions.none { it.number == number }) return
    _state.update {
      it.copy(
        answers = it.answers + (number.toString() to choice),
        checked = false,
      )
    }
  }

  fun updateAnswer(number: Int, text: String) {
    val group = _state.value.group ?: return
    if (!group.translation) return
    _state.update {
      it.copy(answers = it.answers + (number.toString() to text.take(10_000)))
    }
  }

  fun toggleReference() {
    if (_state.value.group?.translation != true) return
    _state.update { it.copy(checked = !it.checked) }
  }

  fun submit() {
    val state = _state.value
    val group = state.group ?: return
    if (group.translation) return
    val answered = group.questions.count { (state.answers[it.number.toString()] ?: "").isNotBlank() }
    if (answered != group.questions.size) {
      _messages.tryEmit("先答完这一组，再提交批改。")
      return
    }
    if (state.submitting) return
    viewModelScope.launch {
      _state.update { it.copy(submitting = true) }
      val score = PaperEngine.gradePaper(state.answers, group.questions)
      val missed = missedWords(group, state.answers)
      runCatching {
        progressRepository.recordPaper(group.key, state.answers, group.questions, missed, "")
      }.onSuccess {
        _state.update { it.copy(submitting = false, checked = true) }
        _messages.tryEmit("这一组已批改：答对 ${score.correct} / ${score.total} 题。")
      }.onFailure {
        _state.update { it.copy(submitting = false) }
        _messages.tryEmit("批改保存失败，请稍后重试。")
      }
    }
  }

  fun rate(rating: String) {
    val state = _state.value
    val group = state.group ?: return
    if (!group.translation || rating !in setOf("review", "mastered")) return
    if (state.submitting) return
    viewModelScope.launch {
      _state.update { it.copy(submitting = true) }
      runCatching {
        progressRepository.recordPaper(group.key, state.answers, group.questions, emptyList(), rating)
      }.onSuccess {
        _state.update { it.copy(submitting = false, checked = true, rating = rating) }
        _messages.tryEmit(if (rating == "mastered") "这组已经理解。" else "已标记为待巩固。")
      }.onFailure {
        _state.update { it.copy(submitting = false) }
        _messages.tryEmit("自评保存失败，请稍后重试。")
      }
    }
  }

  fun reset() {
    _state.update { it.copy(answers = emptyMap(), checked = false, rating = "") }
  }

  fun message(text: String) {
    _messages.tryEmit(text)
  }

  private suspend fun loadPaper(id: String): PaperDto? {
    _state.update { it.copy(loadingPaper = true, paperError = "") }
    val paper = runCatching { contentRepository.paper(id) }.getOrNull()
    _state.update {
      it.copy(
        loadingPaper = false,
        paper = paper ?: it.paper?.takeIf { existing -> existing.id == id },
        paperError = if (paper == null) "这套试卷暂时打不开" else "",
      )
    }
    return paper
  }

  private fun missedWords(group: PaperGroup, answers: Map<String, String>): List<String> {
    if (group.data.type != "cloze") return emptyList()
    val content = content ?: return emptyList()
    return group.questions
      .filter { question -> question.answer != null && answers[question.number.toString()] != null && answers[question.number.toString()] != question.answer }
      .mapNotNull { question -> question.answer?.let { question.options[it] } }
      .map { it.trim().lowercase() }
      .mapNotNull { content.wordById[it]?.id }
      .distinct()
  }
}
