package com.dailyenglish.nativeapp.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyenglish.nativeapp.core.learning.DailyPlan
import com.dailyenglish.nativeapp.core.learning.LearningEngine
import com.dailyenglish.nativeapp.core.learning.Statistics
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.createState
import com.dailyenglish.nativeapp.data.content.Content
import com.dailyenglish.nativeapp.data.content.ContentRepository
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.data.content.WordDto
import com.dailyenglish.nativeapp.data.repository.ProgressRepository
import com.dailyenglish.nativeapp.data.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
  val running: Boolean = false,
  val remainingSeconds: Int = 25 * 60,
  val totalSeconds: Int = 25 * 60,
)

data class StudyUiState(
  val loading: Boolean = true,
  val error: String = "",
  val content: Content? = null,
  val progress: AppState = createState(),
  val stats: Statistics? = null,
  val plan: DailyPlan? = null,
  val wordMode: String = "new",
  val wordQueue: List<String> = emptyList(),
  val wordIndex: Int = 0,
  val revealed: Boolean = false,
  val spellingInput: String = "",
  val spellingChecked: Boolean = false,
  val spellingCorrect: Boolean? = null,
  val sentenceId: String? = null,
  val sentenceRevealed: Boolean = false,
  val sentenceDraft: String = "",
  val focus: FocusUiState = FocusUiState(),
)

@HiltViewModel
class StudyViewModel @Inject constructor(
  private val contentRepository: ContentRepository,
  private val progressRepository: ProgressRepository,
  private val tts: TtsManager,
) : ViewModel() {
  private val _state = MutableStateFlow(StudyUiState())
  val state: StateFlow<StudyUiState> = _state.asStateFlow()
  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages = _messages.asSharedFlow()

  private var observing = false
  private var draftJob: Job? = null
  private var focusJob: Job? = null
  private var focusDeadline = 0L
  private var focusLastRecord = 0L

  init {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    try {
      val content = contentRepository.load()
      _state.update { it.copy(content = content, loading = false, error = "") }
      progressRepository.load()
      observeProgress()
      refresh()
    } catch (error: Exception) {
      _state.update { it.copy(loading = false, error = "学习内容读取失败，请重试") }
    }
  }

  fun reload() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = "") }
      load()
    }
  }

  private fun observeProgress() {
    if (observing) return
    observing = true
    viewModelScope.launch {
      progressRepository.state.collect {
        _state.update { current -> current.copy(progress = it) }
        refresh()
      }
    }
  }

  private fun refresh() {
    val current = _state.value
    val content = current.content ?: return
    val state = current.progress
    val stats = LearningEngine.statistics(state, content.words, sentences = content.sentences)
    val plan = LearningEngine.dailyPlan(state, content.words, content.sentences)
    val focusTarget = state.settings.focusMinutes * 60
    _state.update {
      val focus = if (it.focus.running) {
        it.focus
      } else if (it.focus.totalSeconds != focusTarget && it.focus.remainingSeconds == it.focus.totalSeconds) {
        it.focus.copy(totalSeconds = focusTarget, remainingSeconds = focusTarget)
      } else {
        it.focus.copy(totalSeconds = focusTarget)
      }
      it.copy(stats = stats, plan = plan, focus = focus, loading = false)
    }
  }

  fun startSession(mode: String, wordId: String? = null) {
    val content = _state.value.content ?: return
    val state = _state.value.progress
    if (wordId != null) {
      _state.update {
        it.copy(
          wordMode = "browse",
          wordQueue = listOf(wordId),
          wordIndex = 0,
          revealed = false,
          spellingInput = "",
          spellingChecked = false,
          spellingCorrect = null,
        )
      }
      return
    }
    val plan = LearningEngine.dailyPlan(state, content.words, content.sentences)
    val queue = when (mode) {
      "review" -> plan.review
      "saved" -> LearningEngine.buildQueue(content.words, state, "saved")
      "mistakes" -> LearningEngine.buildQueue(content.words, state, "mistakes")
      "spell" -> LearningEngine.buildQueue(content.words, state, "all").take(state.settings.dailyGoal)
      "library" -> emptyList()
      else -> plan.newWords.ifEmpty {
        LearningEngine.buildQueue(content.words, state, "new").take(state.settings.dailyGoal)
      }
    }
    _state.update {
      it.copy(
        wordMode = mode,
        wordQueue = queue.map { word -> word.id },
        wordIndex = 0,
        revealed = false,
        spellingInput = "",
        spellingChecked = false,
        spellingCorrect = null,
      )
    }
  }

  fun currentWord(): WordDto? {
    val current = _state.value
    val id = current.wordQueue.getOrNull(current.wordIndex) ?: return null
    return current.content?.wordById?.get(id)
  }

  fun revealWord() {
    _state.update { it.copy(revealed = true) }
  }

  fun setSpellingInput(value: String) {
    _state.update { it.copy(spellingInput = value, spellingChecked = false, spellingCorrect = null) }
  }

  fun checkSpelling() {
    val word = currentWord() ?: return
    _state.update {
      it.copy(
        spellingChecked = true,
        spellingCorrect = LearningEngine.checkSpelling(it.spellingInput, word.id),
      )
    }
  }

  fun rateWord(quality: String) {
    val word = currentWord() ?: return
    viewModelScope.launch {
      runCatching { progressRepository.rateWord(word.id, quality) }
      advanceWord()
    }
  }

  fun nextWord() = advanceWord()

  fun previousWord() {
    val current = _state.value
    if (current.wordIndex <= 0) return
    _state.update {
      it.copy(
        wordIndex = it.wordIndex - 1,
        revealed = false,
        spellingInput = "",
        spellingChecked = false,
        spellingCorrect = null,
      )
    }
  }

  private fun advanceWord() {
    val current = _state.value
    if (current.wordIndex < current.wordQueue.size) {
      _state.update {
        it.copy(
          wordIndex = it.wordIndex + 1,
          revealed = false,
          spellingInput = "",
          spellingChecked = false,
          spellingCorrect = null,
        )
      }
      if (current.wordIndex + 1 >= current.wordQueue.size) emit("这一组完成了。可以换一组继续。")
    }
  }

  fun newWordSession() {
    startSession(_state.value.wordMode.takeIf { it != "browse" } ?: "new")
  }

  fun toggleSavedWord(id: String) {
    viewModelScope.launch {
      progressRepository.toggleSavedWord(id)
      emit(if (_state.value.progress.savedWords.contains(id)) "已加入收藏" else "已移出收藏")
    }
  }

  fun speakWord(word: WordDto) {
    if (!tts.speak(word.id)) emit("英语朗读暂不可用，请检查系统英语语音包。")
  }

  fun sentenceBank(): List<SentenceDto> {
    return _state.value.content?.sentences.orEmpty()
  }

  fun currentSentence(): SentenceDto? {
    val current = _state.value
    val id = current.sentenceId ?: return null
    return current.content?.sentenceById?.get(id)
  }

  fun ensureSentence() {
    val current = _state.value
    if (current.sentenceId != null && current.content?.sentenceById?.containsKey(current.sentenceId) == true) return
    val bank = sentenceBank()
    val id = current.plan?.sentences?.firstOrNull()?.id ?: bank.firstOrNull()?.id ?: return
    selectSentence(id)
  }

  fun selectSentence(id: String) {
    val current = _state.value
    val draft = current.progress.drafts[id] ?: current.progress.sentenceRecords[id]?.answer ?: ""
    _state.update {
      it.copy(sentenceId = id, sentenceRevealed = false, sentenceDraft = draft)
    }
  }

  fun updateSentenceDraft(text: String) {
    val id = _state.value.sentenceId ?: return
    _state.update { it.copy(sentenceDraft = text) }
    draftJob?.cancel()
    draftJob = viewModelScope.launch {
      delay(400)
      progressRepository.setDraft(id, text)
    }
  }

  fun revealSentence() {
    _state.update { it.copy(sentenceRevealed = !it.sentenceRevealed) }
  }

  fun rateSentence(rating: String) {
    val current = _state.value
    val id = current.sentenceId ?: return
    val draft = current.sentenceDraft
    viewModelScope.launch {
      progressRepository.recordSentence(id, draft, rating)
      emit("这句话已经记录。")
      nextSentence()
    }
  }

  fun nextSentence() {
    val bank = sentenceBank()
    if (bank.isEmpty()) return
    val currentId = _state.value.sentenceId
    val index = bank.indexOfFirst { it.id == currentId }
    val next = bank.getOrNull((index + 1).takeIf { it >= 0 } ?: 0) ?: bank.first()
    selectSentence(next.id)
  }

  fun toggleSavedSentence() {
    val id = _state.value.sentenceId ?: return
    viewModelScope.launch {
      progressRepository.toggleSavedSentence(id)
      emit(if (_state.value.progress.savedSentences.contains(id)) "已收藏句子" else "已取消收藏")
    }
  }

  fun speakSentence() {
    val sentence = currentSentence() ?: return
    if (sentence.direction == "zh2en") return
    if (!tts.speak(sentence.en)) emit("英语朗读暂不可用，请检查系统英语语音包。")
  }

  fun startFocus() {
    val current = _state.value
    if (current.focus.running) return
    val remaining = if (current.focus.remainingSeconds <= 0) current.focus.totalSeconds else current.focus.remainingSeconds
    val now = System.currentTimeMillis()
    focusDeadline = now + remaining * 1000L
    focusLastRecord = now
    _state.update { it.copy(focus = it.focus.copy(running = true, remainingSeconds = remaining)) }
    focusJob?.cancel()
    focusJob = viewModelScope.launch {
      while (true) {
        delay(1000)
        val nowAt = System.currentTimeMillis()
        val left = ((focusDeadline - nowAt + 999) / 1000).toInt().coerceAtLeast(0)
        val elapsed = ((nowAt - focusLastRecord) / 1000).toInt()
        if (elapsed >= 60) {
          val whole = elapsed / 60 * 60
          progressRepository.addFocus(whole)
          focusLastRecord += whole * 1000L
        }
        _state.update { it.copy(focus = it.focus.copy(remainingSeconds = left)) }
        if (left <= 0) {
          val extra = ((System.currentTimeMillis() - focusLastRecord) / 1000).toInt()
          if (extra > 0) progressRepository.addFocus(extra)
          _state.update { it.copy(focus = it.focus.copy(running = false, remainingSeconds = 0)) }
          emit("这段专注已完成。站起来，给眼睛一点休息。")
          break
        }
      }
    }
  }

  fun pauseFocus() {
    val current = _state.value
    if (!current.focus.running) return
    val now = System.currentTimeMillis()
    val extra = ((now - focusLastRecord) / 1000).toInt()
    if (extra > 0) viewModelScope.launch { progressRepository.addFocus(extra) }
    focusJob?.cancel()
    focusDeadline = 0
    _state.update {
      it.copy(focus = it.focus.copy(running = false, remainingSeconds = it.focus.remainingSeconds.coerceAtLeast(0)))
    }
  }

  fun resetFocus() {
    pauseFocus()
    val target = _state.value.progress.settings.focusMinutes * 60
    _state.update { it.copy(focus = FocusUiState(running = false, remainingSeconds = target, totalSeconds = target)) }
  }

  private fun emit(message: String) {
    _messages.tryEmit(message)
  }
}
