package com.dailyenglish.nativeapp.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dailyenglish.nativeapp.core.learning.DailyPlan
import com.dailyenglish.nativeapp.core.learning.PlanStep
import com.dailyenglish.nativeapp.core.learning.Statistics
import com.dailyenglish.nativeapp.core.model.AppState
import com.dailyenglish.nativeapp.data.content.WordDto
import com.dailyenglish.nativeapp.theme.NumberStyle
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The study queue is merged; the only place the two tracks remain a choice is
// the statistics view, where they act as filters over the same history.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsExamSwitch(exam: String, onExam: (String) -> Unit, modifier: Modifier = Modifier) {
  SingleChoiceSegmentedButtonRow(modifier) {
    listOf("all" to "全部", "cet6" to "CET-6", "postgrad" to "考研英语").forEachIndexed { index, (value, label) ->
      SegmentedButton(
        selected = exam == value,
        onClick = { onExam(value) },
        shape = SegmentedButtonDefaults.itemShape(index, 3),
      ) { Text(label) }
    }
  }
}

@Composable
fun TodayScreen(
  ui: StudyUiState,
  onStartWords: () -> Unit,
  onReview: () -> Unit,
  onSentences: () -> Unit,
) {
  if (ui.loading) {
    StudyLoading()
    return
  }
  if (ui.error.isNotBlank()) {
    StudyError(ui.error)
    return
  }
  val plan = ui.plan ?: return
  val stats = ui.stats
  val day = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      Text("$day · CET-6 + 考研", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, glassBorderBrush()),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日计划", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.weight(1f))
            if (plan.active == "done") {
              Text("已完成", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
          }
          plan.steps.forEachIndexed { index, step ->
            PlanRow(step, index, active = plan.active == step.id, onClick = {
              when (step.id) {
                "review" -> onReview()
                "new" -> onStartWords()
                else -> onSentences()
              }
            })
            if (index < plan.steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
          }
          if (plan.active == "done") {
            Text("今天的计划完成了。", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
          } else {
            Button(
              onClick = {
                when (plan.active) {
                  "review" -> onReview()
                  "sentences" -> onSentences()
                  else -> onStartWords()
                }
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(if (plan.active == "review") "开始复习" else if (plan.active == "sentences") "开始翻译" else "开始学习")
            }
          }
        }
      }
    }
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, glassBorderBrush()),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
          }
          Spacer(Modifier.width(14.dp))
          Column {
            Text("连续学习 ${stats?.streak ?: 0} 天", style = MaterialTheme.typography.titleMedium)
            Text("今日专注 ${stats?.todayMinutes ?: 0} 分钟 · 已学 ${stats?.learned ?: 0} 词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun PlanRow(step: PlanStep, index: Int, active: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val completed = step.pending == 0 && step.total > 0
    val container = when {
      completed -> MaterialTheme.colorScheme.primary
      active -> MaterialTheme.colorScheme.primaryContainer
      else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
      completed -> MaterialTheme.colorScheme.onPrimary
      active -> MaterialTheme.colorScheme.onPrimaryContainer
      else -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(34.dp)) {
      Box(contentAlignment = Alignment.Center) {
        if (completed) {
          Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        } else {
          Text(String.format("%02d", index + 1), style = MaterialTheme.typography.labelMedium, color = content)
        }
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(step.label, style = MaterialTheme.typography.titleSmall)
      Text(planCaption(step), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (active) {
      FilledTonalButton(onClick = onClick) { Text("进入") }
    } else {
      TextButton(onClick = onClick) { Text(if (step.pending == 0) "查看" else "进入") }
    }
  }
}

private fun planCaption(step: PlanStep): String = when {
  step.total == 0 && step.id == "review" -> "暂无到期"
  step.total == 0 && step.id == "sentences" -> "暂无待练句子"
  step.total == 0 -> "词库已学完"
  else -> "${step.done} / ${step.total} ${step.unit}"
}

@Composable
fun PracticeScreen(
  ui: StudyUiState,
  onWords: () -> Unit,
  onSentences: () -> Unit,
  onReview: () -> Unit,
  onPapers: () -> Unit,
) {
  if (ui.loading) {
    StudyLoading()
    return
  }
  val stats = ui.stats
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PracticeCard(
        title = "词汇练习",
        caption = "已学 ${stats?.learned ?: 0} / ${stats?.total ?: 0} 词",
        icon = Icons.Outlined.MenuBook,
        onClick = onWords,
      )
    }
    item {
      PracticeCard(
        title = "句子翻译",
        caption = "253 句长难句与段落",
        icon = Icons.Outlined.Translate,
        onClick = onSentences,
      )
    }
    item {
      PracticeCard(
        title = "复习计划",
        caption = "${stats?.due ?: 0} 词到期",
        icon = Icons.Outlined.Repeat,
        onClick = onReview,
      )
    }
    item {
      PracticeCard("真题练习", "44 套 · 逐组练习", Icons.Outlined.School, onPapers)
    }
  }
}

@Composable
private fun PracticeCard(title: String, caption: String, icon: ImageVector, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun ReviewScreen(ui: StudyUiState, onStart: () -> Unit) {
  if (ui.loading) {
    StudyLoading()
    return
  }
  val plan = ui.plan
  val due = plan?.review ?: emptyList()
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (due.isNotEmpty()) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${due.size} 词到期", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
          Button(onClick = onStart) { Text("开始复习") }
        }
      }
    }
    if (due.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, glassBorderBrush()),
          elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("暂时没有到期单词", style = MaterialTheme.typography.titleMedium)
            Text("新词会在到期后自动出现。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    } else {
      items(due.take(30), key = { it.id }) { word ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, glassBorderBrush()),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(word.id, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
              Text(word.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
      if (due.size > 30) {
        item { Text("显示前 30 词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
  }
}

@Composable
fun ComingSoonScreen(
  icon: ImageVector,
  eyebrow: String,
  title: String,
  body: String,
  action: Pair<String, () -> Unit>? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.Start,
  ) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
      Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
      }
    }
    Spacer(Modifier.height(18.dp))
    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) {
      Spacer(Modifier.height(18.dp))
      Button(onClick = action.second) { Text(action.first) }
    }
  }
}

@Composable
fun StudyLoading() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
fun StudyError(message: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(28.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text("暂时无法加载", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
