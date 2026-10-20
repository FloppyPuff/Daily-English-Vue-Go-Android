package com.dailyenglish.nativeapp.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dailyenglish.nativeapp.data.content.SentenceDto
import com.dailyenglish.nativeapp.theme.NumberStyle
import com.dailyenglish.nativeapp.theme.glassBorderBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentencesScreen(
  ui: StudyUiState,
  onEnsure: () -> Unit,
  onSelect: (String) -> Unit,
  onDraft: (String) -> Unit,
  onReveal: () -> Unit,
  onRate: (String) -> Unit,
  onNext: () -> Unit,
  onSave: () -> Unit,
  onSpeak: () -> Unit,
) {
  LaunchedEffect(Unit) { onEnsure() }
  if (ui.loading) {
    StudyLoading()
    return
  }
  val bank = remember(ui.content) {
    ui.content?.sentences.orEmpty()
  }
  val current = ui.sentenceId?.let { ui.content?.sentenceById?.get(it) } ?: bank.firstOrNull()
  var catalogOpen by remember { mutableStateOf(false) }
  if (current == null) {
    StudyError("暂时没有句子")
    return
  }
  val index = bank.indexOfFirst { it.id == current.id }
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { catalogOpen = true }) { Text("选择句子 · ${bank.size}") }
      }
    }
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, glassBorderBrush()),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "${kindLabel(current)}${current.level?.let { " · $it" } ?: ""}${current.topic?.let { " · $it" } ?: ""}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
              "${String.format("%02d", index + 1)} / ${String.format("%02d", bank.size)}",
              style = NumberStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            if (current.direction == "zh2en") current.zh else current.en,
            style = if (current.direction == "zh2en") MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (current.direction != "zh2en") {
              TextButton(onClick = onSpeak) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("朗读原句")
              }
            }
            Spacer(Modifier.weight(1f))
            Text(current.source ?: "Daily English 原创长句", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          OutlinedTextField(
            value = ui.sentenceDraft,
            onValueChange = onDraft,
            label = { Text(if (current.direction == "zh2en") "你的英文译文" else "你的译文") },
            supportingText = { Text("自动保存") },
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onReveal) {
              Text(if (ui.sentenceRevealed) "收起参考" else "查看参考")
            }
          }
          if (ui.sentenceRevealed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("参考译文", style = MaterialTheme.typography.titleMedium)
            val reference = if (current.direction == "zh2en") current.en else current.zh
            if (reference.isBlank()) {
              Text("暂无参考译文，可对照题目自查。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
              Text(reference, style = MaterialTheme.typography.bodyLarge)
            }
            if (current.analysis.isNotEmpty() || current.keywords.isNotEmpty()) {
              Text("句法拆解与重点词", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
              current.analysis.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(56.dp))
                  Text(row.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
              }
              if (current.keywords.isNotEmpty()) {
                Text(current.keywords.joinToString("　") { "${it.word} ${it.meaning}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(onClick = { onRate("review") }, modifier = Modifier.weight(1f)) { Text("还需巩固") }
              Button(onClick = { onRate("mastered") }, modifier = Modifier.weight(1f)) { Text("已经理解") }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSave) {
              Icon(
                if (ui.progress.savedSentences.contains(current.id)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(if (ui.progress.savedSentences.contains(current.id)) "已收藏" else "收藏句子")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNext) { Text("下一句") }
          }
        }
      }
    }
  }
  if (catalogOpen) {
    ModalBottomSheet(onDismissRequest = { catalogOpen = false }) {
      Text("选择句子", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
      LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxWidth()) {
        items(bank, key = { it.id }) { sentence ->
          val selected = sentence.id == current.id
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(excerpt(sentence), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
              Text("${kindLabel(sentence)} · ${statusLabel(ui, sentence.id)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
              Icon(Icons.Outlined.ChevronRight, contentDescription = "当前句子", tint = MaterialTheme.colorScheme.primary)
            } else {
              TextButton(onClick = {
                onSelect(sentence.id)
                catalogOpen = false
              }) { Text("打开") }
            }
          }
        }
      }
    }
  }
}

private fun kindLabel(sentence: SentenceDto): String = when {
  sentence.kind == "paragraph" -> "段落翻译"
  sentence.direction == "zh2en" -> "汉译英"
  else -> "长难句"
}

private fun excerpt(sentence: SentenceDto): String {
  val text = if (sentence.direction == "zh2en") sentence.zh else sentence.en
  return if (text.length > 52) text.take(52) + "…" else text
}

private fun statusLabel(ui: StudyUiState, id: String): String = when {
  ui.progress.sentenceRecords[id]?.rating == "mastered" -> "已理解"
  ui.progress.sentenceRecords[id]?.rating == "review" -> "待巩固"
  ui.progress.drafts[id]?.isNotBlank() == true -> "草稿已保存"
  else -> "未完成"
}
