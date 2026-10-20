package com.dailyenglish.nativeapp.feature.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyenglish.nativeapp.core.learning.NotebookSentence
import com.dailyenglish.nativeapp.core.learning.NotebookWord
import com.dailyenglish.nativeapp.feature.study.StudyError
import com.dailyenglish.nativeapp.feature.study.StudyLoading
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import com.dailyenglish.nativeapp.ui.components.AppSegmentedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
  onOpenWord: (String) -> Unit,
  onOpenSentence: (String) -> Unit,
  onPracticeWords: (String) -> Unit,
  onPracticeSentences: (String) -> Unit,
  viewModel: NotebookViewModel = hiltViewModel(),
) {
  val ui by viewModel.state.collectAsStateWithLifecycle()
  if (ui.loading) {
    StudyLoading()
    return
  }
  if (ui.error.isNotBlank()) {
    StudyError(ui.error)
    return
  }
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      listOf("saved" to "我的收藏", "mistakes" to "薄弱 & 错题").forEachIndexed { index, (key, label) ->
        AppSegmentedButton(
          selected = ui.tab == key,
          onClick = { viewModel.setTab(key) },
          shape = SegmentedButtonDefaults.itemShape(index, 2),
        ) { Text(label) }
      }
    }
    OutlinedTextField(
      value = ui.query,
      onValueChange = viewModel::setQuery,
      placeholder = { Text("搜索单词、释义或句子…") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(999.dp),
      modifier = Modifier.fillMaxWidth(),
    )
    if (ui.data.words.isEmpty() && ui.data.sentences.isEmpty()) {
      EmptyNotebook(ui.tab)
    } else {
      BoxWithConstraints {
        val twoPane = maxWidth >= 720.dp
        if (twoPane) {
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WordSection(
              words = ui.data.words,
              tab = ui.tab,
              modifier = Modifier.weight(1f),
              onOpenWord = onOpenWord,
              onPractice = { onPracticeWords(ui.tab) },
            )
            SentenceSection(
              sentences = ui.data.sentences,
              modifier = Modifier.weight(1f),
              onOpenSentence = onOpenSentence,
              onPractice = { onPracticeSentences(ui.data.sentences.first().sentence.id) },
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WordSection(
              words = ui.data.words,
              tab = ui.tab,
              modifier = Modifier.fillMaxWidth(),
              onOpenWord = onOpenWord,
              onPractice = { onPracticeWords(ui.tab) },
            )
            SentenceSection(
              sentences = ui.data.sentences,
              modifier = Modifier.fillMaxWidth(),
              onOpenSentence = onOpenSentence,
              onPractice = { onPracticeSentences(ui.data.sentences.first().sentence.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WordSection(
  words: List<NotebookWord>,
  tab: String,
  modifier: Modifier,
  onOpenWord: (String) -> Unit,
  onPractice: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier,
  ) {
    Column(Modifier.padding(18.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("单词", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("${words.size} 词", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (words.isEmpty()) {
        Text(
          if (tab == "mistakes") "还没有薄弱词。" else "在词卡或词库点书签，单词会留在这里。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 10.dp),
        )
      } else {
        TextButton(onClick = onPractice) { Text("练习这些单词") }
        words.forEachIndexed { index, item ->
          if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          NotebookWordRow(item, onClick = { onOpenWord(item.word.id) })
        }
      }
    }
  }
}

@Composable
private fun NotebookWordRow(item: NotebookWord, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.word.id, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
        item.word.phonetic?.let {
          Text("  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Text(item.word.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(
      listOfNotNull(
        "收藏".takeIf { item.saved },
        "薄弱".takeIf { item.difficult },
        "真题".takeIf { item.paperHit },
      ).joinToString(" · "),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
private fun SentenceSection(
  sentences: List<NotebookSentence>,
  modifier: Modifier,
  onOpenSentence: (String) -> Unit,
  onPractice: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier,
  ) {
    Column(Modifier.padding(18.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("句子", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("${sentences.size} 句", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (sentences.isEmpty()) {
        Text(
          "收藏句子，或在翻译自评里选“还需巩固”。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 10.dp),
        )
      } else {
        TextButton(onClick = onPractice) { Text("从第一句开始复习") }
        sentences.forEachIndexed { index, item ->
          if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          NotebookSentenceRow(item, onClick = { onOpenSentence(item.sentence.id) })
        }
      }
    }
  }
}

@Composable
private fun NotebookSentenceRow(item: NotebookSentence, onClick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      item.sentence.en.take(150),
      style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        when (item.rating) {
          "mastered" -> "已理解"
          "review" -> "待巩固"
          else -> "未练习"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (item.review) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
      Text("查看练习", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun EmptyNotebook(tab: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(22.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(if (tab == "mistakes") "还没有需要补的地方。" else "先留住一点点。", style = MaterialTheme.typography.titleMedium)
      Text(
        if (tab == "mistakes") "标记为薄弱或答错的内容会出现在这里。"
        else "在词卡、词库或句子里点书签即可。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
