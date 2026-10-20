package com.dailyenglish.nativeapp.feature.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.dailyenglish.nativeapp.core.learning.bestTier
import com.dailyenglish.nativeapp.data.content.WordDto
import com.dailyenglish.nativeapp.theme.NumberStyle
import com.dailyenglish.nativeapp.theme.WordHeadwordStyle
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import com.dailyenglish.nativeapp.ui.components.AppSegmentedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(
  ui: StudyUiState,
  mode: String,
  wordId: String?,
  onStartSession: (String, String?) -> Unit,
  onReveal: () -> Unit,
  onRate: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSpellingInput: (String) -> Unit,
  onCheckSpelling: () -> Unit,
  onToggleSave: (String) -> Unit,
  onSpeak: (WordDto) -> Unit,
  onNewSession: () -> Unit,
) {
  LaunchedEffect(mode, wordId) { onStartSession(mode, wordId) }
  if (ui.loading) {
    StudyLoading()
    return
  }
  val current = remember(ui.wordQueue, ui.wordIndex, ui.content) {
    ui.wordQueue.getOrNull(ui.wordIndex)?.let { ui.content?.wordById?.get(it) }
  }
  val selectedMode = when {
    ui.wordMode == "library" -> "library"
    ui.wordMode == "spell" -> "spell"
    else -> "new"
  }
  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      listOf("new" to "闪卡记忆", "spell" to "拼写自测", "library" to "词库浏览").forEachIndexed { index, (value, label) ->
        AppSegmentedButton(
          selected = selectedMode == value,
          onClick = { onStartSession(value, null) },
          shape = SegmentedButtonDefaults.itemShape(index, 3),
        ) { Text(label) }
      }
    }
    Spacer(Modifier.height(14.dp))
    when {
      selectedMode == "library" -> LibraryView(
        ui = ui,
        modifier = Modifier.weight(1f),
        onOpenWord = { onStartSession("browse", it) },
        onSpeak = onSpeak,
        onToggleSave = onToggleSave,
      )
      current == null -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, glassBorderBrush()),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("这一组学完了。", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNewSession) { Text("换一组") }
          }
        }
      }
      else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SwipeableWordCard(
          wordKey = current.id,
          swipeEnabled = ui.wordMode != "browse" && selectedMode != "spell",
          canGoPrevious = ui.wordIndex > 0,
          onPrevious = onPrevious,
          onNext = onNext,
        ) {
          WordCard(
            word = current,
            index = ui.wordIndex,
            total = ui.wordQueue.size,
            revealed = ui.revealed,
            spelling = selectedMode == "spell",
            spellingInput = ui.spellingInput,
            spellingChecked = ui.spellingChecked,
            spellingCorrect = ui.spellingCorrect,
            saved = ui.progress.savedWords.contains(current.id),
            onReveal = onReveal,
            onRate = onRate,
            onSpellingInput = onSpellingInput,
            onCheckSpelling = onCheckSpelling,
            onToggleSave = { onToggleSave(current.id) },
            onSpeak = { onSpeak(current) },
          )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${ui.wordQueue.size} 词 · 第 ${ui.wordIndex + 1} 词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onNewSession) { Text("换一组") }
          if (!ui.revealed) TextButton(onClick = onNext) { Text("跳过") }
        }
      }
    }
  }
}

@Composable
private fun SwipeableWordCard(
  wordKey: String,
  swipeEnabled: Boolean,
  canGoPrevious: Boolean,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  content: @Composable () -> Unit,
) {
  val offset = remember(wordKey) { Animatable(0f) }
  val scope = rememberCoroutineScope()
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        translationX = offset.value
        rotationZ = if (size.width > 0) offset.value / size.width * 2.5f else 0f
      }
      .pointerInput(wordKey, swipeEnabled, canGoPrevious) {
        if (!swipeEnabled) return@pointerInput
        detectHorizontalDragGestures(
          onHorizontalDrag = { change, dragAmount ->
            change.consume()
            scope.launch {
              offset.snapTo((offset.value + dragAmount).coerceIn(-size.width * 0.7f, size.width * 0.7f))
            }
          },
          onDragEnd = {
            val threshold = size.width * 0.22f
            scope.launch {
              when {
                offset.value <= -threshold -> {
                  offset.animateTo(-size.width * 1.1f, tween(140))
                  onNext()
                }
                offset.value >= threshold && canGoPrevious -> {
                  offset.animateTo(size.width * 1.1f, tween(140))
                  onPrevious()
                }
                else -> offset.animateTo(0f, spring(stiffness = 700f))
              }
            }
          },
          onDragCancel = {
            scope.launch { offset.animateTo(0f, tween(120)) }
          },
        )
      },
  ) { content() }
}

@Composable
private fun WordCard(
  word: WordDto,
  index: Int,
  total: Int,
  revealed: Boolean,
  spelling: Boolean,
  spellingInput: String,
  spellingChecked: Boolean,
  spellingCorrect: Boolean?,
  saved: Boolean,
  onReveal: () -> Unit,
  onRate: (String) -> Unit,
  onSpellingInput: (String) -> Unit,
  onCheckSpelling: () -> Unit,
  onToggleSave: () -> Unit,
  onSpeak: () -> Unit,
) {
  val tier = bestTier(word)
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.extraLarge,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (tier.isNotBlank()) {
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(tier, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
          }
        }
        Spacer(Modifier.weight(1f))
        Text(
          "${String.format("%02d", index + 1)} / ${String.format("%02d", total)}",
          style = NumberStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (!spelling || revealed || spellingChecked) {
          IconButton(onClick = onSpeak) {
            Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读 ${word.id}", tint = MaterialTheme.colorScheme.primary)
          }
        }
        IconButton(onClick = onToggleSave) {
          Icon(
            if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (saved) "取消收藏" else "收藏",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!spelling || revealed || spellingChecked) {
          Text(word.id, style = WordHeadwordStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          if (!spelling || revealed || spellingChecked) {
            word.phonetic?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          word.pos?.let {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
              Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
          }
        }
        if (!revealed) {
          Spacer(Modifier.height(8.dp))
          if (spelling) {
            Text(word.meaning, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
          } else {
            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("显示释义") }
          }
        } else {
          Text(word.meaning, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
          word.example?.en?.takeIf { it.isNotBlank() }?.let { example ->
            Spacer(Modifier.height(4.dp))
            Text(example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Serif)
            word.example.zh?.takeIf { it.isNotBlank() }?.let {
              Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (word.forms.isNotEmpty()) {
            Text(
              word.forms.joinToString(" · ") { "${it.label} ${it.value}" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          RatingButtons(onRate)
        }
      }
      if (spelling) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("根据释义拼出这个单词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
          value = spellingInput,
          onValueChange = onSpellingInput,
          singleLine = true,
          enabled = !spellingChecked,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { onCheckSpelling() }),
          modifier = Modifier.fillMaxWidth(),
        )
        if (!spellingChecked) {
          OutlinedButton(onClick = onCheckSpelling, enabled = spellingInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("检查拼写")
          }
        } else {
          Text(
            if (spellingCorrect == true) "拼写正确" else "正确拼写：${word.id}",
            color = if (spellingCorrect == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleSmall,
          )
          RatingButtons(onRate)
        }
      }
    }
  }
}

@Composable
private fun RatingButtons(onRate: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(onClick = { onRate("again") }, modifier = Modifier.weight(1f)) { Text("忘记") }
      OutlinedButton(onClick = { onRate("hard") }, modifier = Modifier.weight(1f)) { Text("模糊") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      FilledTonalButton(onClick = { onRate("good") }, modifier = Modifier.weight(1f)) { Text("记住") }
      Button(onClick = { onRate("easy") }, modifier = Modifier.weight(1f)) { Text("熟悉") }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryView(
  ui: StudyUiState,
  modifier: Modifier = Modifier,
  onOpenWord: (String) -> Unit,
  onSpeak: (WordDto) -> Unit,
  onToggleSave: (String) -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf("all") }
  var page by rememberSaveable { mutableIntStateOf(0) }
  val words = remember(ui.content, query, filter) {
    val all = ui.content?.words.orEmpty()
    val normalized = query.trim().lowercase()
    all.filter { word ->
      (normalized.isBlank() || listOf(word.id, word.meaning, word.pos.orEmpty(), word.topic.orEmpty(), word.senses.joinToString(" ")).joinToString(" ").lowercase().contains(normalized)) &&
        when (filter) {
          "new" -> !ui.progress.records.containsKey(word.id)
          "learned" -> ui.progress.records.containsKey(word.id)
          else -> true
        }
    }
  }
  val pages = ((words.size + 59) / 60).coerceAtLeast(1)
  val safePage = page.coerceIn(0, pages - 1)
  val rows = words.drop(safePage * 60).take(60)
  Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        page = 0
      },
      placeholder = { Text("搜索单词、释义或主题…") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(999.dp),
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = filter == "all", onClick = { filter = "all"; page = 0 }, label = { Text("全部") })
      FilterChip(selected = filter == "new", onClick = { filter = "new"; page = 0 }, label = { Text("未学习") })
      FilterChip(selected = filter == "learned", onClick = { filter = "learned"; page = 0 }, label = { Text("已学习") })
    }
    Text("${words.size} 词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      items(rows, key = { it.id }) { word ->
        WordRow(
          word = word,
          learned = ui.progress.records.containsKey(word.id),
          saved = ui.progress.savedWords.contains(word.id),
          onClick = { onOpenWord(word.id) },
          onSpeak = { onSpeak(word) },
          onToggleSave = { onToggleSave(word.id) },
        )
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      TextButton(onClick = { if (safePage > 0) page = safePage - 1 }, enabled = safePage > 0) { Text("上一页") }
      Text("${safePage + 1} / $pages", style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = { if (safePage < pages - 1) page = safePage + 1 }, enabled = safePage < pages - 1) { Text("下一页") }
    }
  }
}

@Composable
private fun WordRow(
  word: WordDto,
  learned: Boolean,
  saved: Boolean,
  onClick: () -> Unit,
  onSpeak: () -> Unit,
  onToggleSave: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(word.id, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
      Text(word.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    Text(
      if (learned) "已学习" else "未学习",
      style = MaterialTheme.typography.labelSmall,
      color = if (learned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    IconButton(onClick = onSpeak) { Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读 ${word.id}") }
    IconButton(onClick = onToggleSave) {
      Icon(
        if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription = "收藏 ${word.id}",
        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
