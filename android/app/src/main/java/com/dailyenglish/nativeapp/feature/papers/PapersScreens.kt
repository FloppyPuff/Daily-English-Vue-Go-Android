package com.dailyenglish.nativeapp.feature.papers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dailyenglish.nativeapp.core.learning.PaperEngine
import com.dailyenglish.nativeapp.core.learning.PaperGroup
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.core.model.PaperRecord
import com.dailyenglish.nativeapp.data.content.PaperDto
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import com.dailyenglish.nativeapp.data.content.PaperGroupDto
import com.dailyenglish.nativeapp.data.content.PaperQuestionDto
import com.dailyenglish.nativeapp.feature.study.StudyError
import com.dailyenglish.nativeapp.feature.study.StudyLoading
import com.dailyenglish.nativeapp.theme.NumberStyle
import com.dailyenglish.nativeapp.ui.components.AppSegmentedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapersScreen(
  ui: PapersUiState,
  progress: AppState,
  onOpenPaper: (String) -> Unit,
) {
  if (ui.loadingIndex) {
    StudyLoading()
    return
  }
  if (ui.indexError.isNotBlank()) {
    StudyError(ui.indexError)
    return
  }
  var filter by rememberSaveable { mutableStateOf("all") }
  val exams = remember(ui.paperIndex) { listOf("all") + ui.paperIndex.map { it.exam }.distinct() }
  val sets = remember(ui.paperIndex, filter) {
    ui.paperIndex.filter { filter == "all" || it.exam == filter }.sortedByDescending { it.year }
  }
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        exams.forEachIndexed { index, exam ->
          AppSegmentedButton(
            selected = filter == exam,
            onClick = { filter = exam },
            shape = SegmentedButtonDefaults.itemShape(index, exams.size),
          ) { Text(if (exam == "all") "全部" else exam) }
        }
      }
    }
    item { Text("${sets.size} 套试卷", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(sets, key = { it.id }) { set ->
      val done = paperDoneCount(progress, set.id)
      Card(
        onClick = { onOpenPaper(set.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, glassBorderBrush()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
            Column(
              modifier = Modifier.fillMaxSize(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text("${set.year}", style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize))
            }
          }
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text("${set.year} 年 ${set.exam}", style = MaterialTheme.typography.titleMedium)
            Text(paperMeta(set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text(if (done > 0) "已练 $done 组" else "未开始", style = MaterialTheme.typography.labelSmall, color = if (done > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
          Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
fun PaperSetScreen(
  ui: PapersUiState,
  progress: AppState,
  setId: String,
  onLoad: (String) -> Unit,
  onOpenGroup: (String) -> Unit,
) {
  LaunchedEffect(setId) { onLoad(setId) }
  if (ui.loadingPaper) {
    StudyLoading()
    return
  }
  if (ui.paperError.isNotBlank()) {
    StudyError(ui.paperError)
    return
  }
  val paper = ui.paper?.takeIf { it.id == setId } ?: run { StudyLoading(); return }
  val groups = remember(paper) { PaperEngine.paperGroups(paper) }
  val done = groups.count { paperStatus(progress.papers[it.key]) != "未开始" }
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${paper.year} 年 ${paper.exam}", style = MaterialTheme.typography.headlineLarge)
        Text("${groups.size} 组练习，已完成 $done 组。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    items(groups, key = { it.key }) { group ->
      GroupRow(group, paperStatus(progress.papers[group.key])) { onOpenGroup(group.index) }
    }
    if (paper.source != null) {
      item {
        Text("来源：${paper.source} · 答案来自公开整理版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun GroupRow(group: PaperGroup, status: String, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(group.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(group.section.ifBlank { group.label }, style = MaterialTheme.typography.titleMedium)
        Text(
          "${group.questions.size} 题${if (group.sectionScore > 0) " · ${group.sectionScore} 分" else ""}${if (group.answerable) "" else " · 仅原文"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(status, style = MaterialTheme.typography.labelSmall, color = if (status == "未开始") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
      Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun PaperGroupScreen(
  ui: PapersUiState,
  setId: String,
  groupIndex: String,
  onLoad: (String, String) -> Unit,
  onPick: (Int, String) -> Unit,
  onAnswer: (Int, String) -> Unit,
  onCheck: () -> Unit,
  onSubmit: () -> Unit,
  onRate: (String) -> Unit,
  onReset: () -> Unit,
) {
  LaunchedEffect(setId, groupIndex) { onLoad(setId, groupIndex) }
  if (ui.loadingPaper) {
    StudyLoading()
    return
  }
  if (ui.paperError.isNotBlank()) {
    StudyError(ui.paperError)
    return
  }
  val group = ui.group?.takeIf { it.index == groupIndex } ?: run { StudyLoading(); return }
  val passage = remember(group, ui.answers, ui.checked) { passageText(group.data, ui.answers) }
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(group.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(group.section.ifBlank { group.label }, style = MaterialTheme.typography.headlineMedium)
        if (group.instructions.isNotBlank() && group.data.type != "reading") {
          Text(group.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          passage,
          style = if (group.data.type == "reading" || group.data.type == "cloze") MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(18.dp),
        )
      }
    }
    if (!group.answerable) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            "这套 Part B 的公开来源只给了答案字母，没有收录选项句子，所以这里不提供作答，只留原文精读。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
      return@LazyColumn
    }
    if (group.translation) {
      items(group.questions, key = { it.number }) { question ->
        TranslationQuestion(
          question = question,
          answer = ui.answers[question.number.toString()] ?: "",
          checked = ui.checked,
          onAnswer = { onAnswer(question.number, it) },
        )
      }
      item {
        TranslationFooter(
          checked = ui.checked,
          rating = ui.rating,
          onCheck = onCheck,
          onRate = onRate,
          submitting = ui.submitting,
        )
      }
    } else {
      items(group.questions, key = { it.number }) { question ->
        QuestionCard(
          question = question,
          picked = ui.answers[question.number.toString()] ?: "",
          checked = ui.checked,
          onPick = { onPick(question.number, it) },
        )
      }
      item {
        val answered = group.questions.count { (ui.answers[it.number.toString()] ?: "").isNotBlank() }
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, glassBorderBrush()),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.checked) {
              val score = PaperEngine.gradePaper(ui.answers, group.questions)
              Text("已批改 ${score.correct} / ${score.total} 题", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
              OutlinedButton(onClick = onReset) { Text("重新作答") }
            } else {
              Text("已作答 $answered / ${group.questions.size} 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Button(onClick = onSubmit, enabled = answered == group.questions.size && !ui.submitting, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.submitting) "保存中…" else "提交答案")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuestionCard(question: PaperQuestionDto, picked: String, checked: Boolean, onPick: (String) -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(30.dp)) {
          Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(question.number.toString(), style = NumberStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize))
          }
        }
        Spacer(Modifier.width(10.dp))
        if (!question.stem.isNullOrBlank()) {
          Text(question.stem, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
      }
      question.options.forEach { (letter, text) ->
        val right = checked && letter == question.answer
        val wrongPick = checked && picked == letter && letter != question.answer
        Card(
          onClick = { onPick(letter) },
          colors = CardDefaults.cardColors(
            containerColor = when {
              right -> MaterialTheme.colorScheme.primaryContainer
              wrongPick -> MaterialTheme.colorScheme.errorContainer
              picked == letter -> MaterialTheme.colorScheme.primaryContainer
              else -> MaterialTheme.colorScheme.surfaceVariant
            },
          ),
          border = if (picked == letter || right) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (right) Icon(Icons.Outlined.Check, contentDescription = "正确答案", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          }
        }
      }
      if (checked && picked.isNotBlank() && picked != question.answer) {
        Text("正确答案是 ${question.answer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun TranslationQuestion(
  question: PaperQuestionDto,
  answer: String,
  checked: Boolean,
  onAnswer: (String) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Text(question.number.toString(), style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
          question.stem ?: "把下面这段英文翻译成中文。",
          style = if (question.stem != null) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif) else MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
      }
      OutlinedTextField(
        value = answer,
        onValueChange = onAnswer,
        label = { Text(if (question.stem != null) "你的译文" else "全文译文") },
        placeholder = { Text("输入译文") },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
      )
      if (checked) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("参考译文", style = MaterialTheme.typography.titleSmall)
        Text(question.answer ?: "这一题暂时没有参考译文。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun TranslationFooter(
  checked: Boolean,
  rating: String,
  onCheck: () -> Unit,
  onRate: (String) -> Unit,
  submitting: Boolean,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (!checked) {
        Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("对照参考译文") }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { onRate("review") }, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("还需巩固") }
          Button(onClick = { onRate("mastered") }, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("已经理解") }
        }
        TextButton(onClick = onCheck) { Text("收起参考译文") }
      }
      if (rating.isNotBlank()) {
        Text(
          "本组自评已记录 · ${if (rating == "mastered") "已经理解" else "留待巩固"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

private fun passageText(group: PaperGroupDto, answers: Map<String, String>): String =
  group.passage.joinToString("\n\n")
    .replace("<u>", "")
    .replace("</u>", "")
    .replace(Regex("""\{\{(\d{1,3})\}\}""")) { match ->
      val number = match.groupValues[1]
      val picked = answers[number]
      if (picked.isNullOrBlank()) "($number)" else "($number $picked)"
    }

private fun paperDoneCount(progress: AppState, setId: String): Int =
  progress.papers.filterKeys { it.startsWith("$setId#") }.count { (_, record) -> record.total > 0 || record.rating.isNotBlank() }

private fun paperMeta(set: com.dailyenglish.nativeapp.data.content.PaperIndexDto): String = listOfNotNull(
  if (set.questions > 0) "${set.questions} 题" else null,
  set.counts["cloze"]?.let { "完形 $it" },
  set.counts["reading"]?.let { "阅读 $it" },
).joinToString(" · ")

private fun paperStatus(record: PaperRecord?): String = when {
  record == null -> "未开始"
  record.total > 0 -> "已批改 ${record.correct} / ${record.total}"
  record.rating == "mastered" -> "已理解"
  record.rating == "review" -> "待巩固"
  else -> "未开始"
}
