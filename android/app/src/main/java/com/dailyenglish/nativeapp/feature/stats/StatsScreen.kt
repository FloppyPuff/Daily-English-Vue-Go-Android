package com.dailyenglish.nativeapp.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyenglish.nativeapp.core.learning.HeatCell
import com.dailyenglish.nativeapp.core.learning.Statistics
import com.dailyenglish.nativeapp.core.learning.WeekSlice
import com.dailyenglish.nativeapp.feature.study.StatsExamSwitch
import com.dailyenglish.nativeapp.feature.study.StudyError
import com.dailyenglish.nativeapp.feature.study.StudyLoading
import com.dailyenglish.nativeapp.theme.NumberStyle
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
  viewModel: StatsViewModel = hiltViewModel(),
) {
  val ui by viewModel.state.collectAsStateWithLifecycle()
  if (ui.loading) {
    StudyLoading()
    return
  }
  val stats = ui.stats
  if (ui.error.isNotBlank() || stats == null) {
    StudyError(ui.error.ifBlank { "学习记录暂时不可用" })
    return
  }
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    StatsExamSwitch(ui.statsExam, viewModel::setStatsExam)
    StatGrid(stats)
    BoxWithConstraints {
      val twoPane = maxWidth >= 720.dp
      val examLabel = when (ui.statsExam) {
        "cet6" -> "CET-6"
        "postgrad" -> "考研英语"
        else -> "综合词库"
      }
      if (twoPane) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          WeekPanel(stats.week, Modifier.weight(1f))
          MasteryPanel(stats, examLabel, Modifier.weight(1f))
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          WeekPanel(stats.week, Modifier.fillMaxWidth())
          MasteryPanel(stats, examLabel, Modifier.fillMaxWidth())
        }
      }
    }
    HeatmapPanel(stats.heatmap)
  }
}

@Composable
private fun StatGrid(stats: Statistics) {
  val items = listOf(
    StatItem("累计已学", "${stats.learned}", "词", "当前词库共 ${stats.total} 词"),
    StatItem("较熟悉词汇", "${stats.mastered}", "词", "连续 3 次记住或很熟悉"),
    StatItem("累计学习", "${stats.activeDays}", "天", "全部学习记录"),
    StatItem("累计专注", "${stats.totalMinutes}", "分钟", "今天 ${stats.todayMinutes} 分钟"),
    StatItem("真题练习", "${stats.paperTotal}", "题", "答对 ${stats.paperCorrect} 题 · 今天 ${stats.todayPapers} 组"),
  )
  BoxWithConstraints {
    val columns = if (maxWidth >= 720.dp) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          row.forEach { item ->
            StatCard(item, Modifier.weight(1f))
          }
          repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

private data class StatItem(val label: String, val value: String, val unit: String, val note: String)

@Composable
private fun StatCard(item: StatItem, modifier: Modifier) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier,
  ) {
    Column(Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.Bottom) {
        Text(item.value, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace))
        Text(" ${item.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WeekPanel(week: List<WeekSlice>, modifier: Modifier) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier,
  ) {
    Column(Modifier.padding(18.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("最近七天的节奏", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("词汇练习", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      val maxWords = max(1, week.maxOfOrNull { it.words } ?: 0)
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        week.forEach { slice ->
          Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (slice.words > 0) "${slice.words}" else "", style = MaterialTheme.typography.labelSmall)
            Box(
              modifier = Modifier.height(82.dp).width(18.dp),
              contentAlignment = Alignment.BottomCenter,
            ) {
              val barHeight = if (slice.words == 0) 4.dp else (10 + 72 * slice.words / maxWords).dp
              Box(
                Modifier
                  .width(18.dp)
                  .height(barHeight)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (slice.words == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
              )
            }
            Text(slice.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun MasteryPanel(stats: Statistics, examLabel: String, modifier: Modifier) {
  val percent = if (stats.total == 0) 0 else (stats.learned * 100.0 / stats.total).roundToInt()
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = modifier,
  ) {
    Column(Modifier.padding(18.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("词汇掌握情况", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(examLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Row(verticalAlignment = Alignment.Bottom) {
        Text("$percent", style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace))
        Text("%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
      }
      LinearProgressIndicator(
        progress = { if (stats.total == 0) 0f else stats.learned.toFloat() / stats.total },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
      )
      LegendRow("较熟悉", stats.mastered)
      LegendRow("学习中", stats.learned - stats.mastered)
      LegendRow("未开始", stats.total - stats.learned)
    }
  }
}

@Composable
private fun LegendRow(label: String, value: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (label == "较熟悉") 1f else 0.45f)))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    Text("$value", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
  }
}

@Composable
private fun HeatmapPanel(heatmap: List<HeatCell>) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, glassBorderBrush()),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(18.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("学习热力图", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("近 12 周", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      val firstOffset = heatmap.firstOrNull()?.let { mondayIndex(it.day) } ?: 0
      val padded = List<HeatCell?>(firstOffset) { null } + heatmap
      val weeks = padded.chunked(7).map { week -> week + List(7 - week.size) { null } }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          listOf("一", "", "三", "", "五", "", "日").forEach { label ->
            Box(Modifier.size(15.dp), contentAlignment = Alignment.CenterStart) {
              Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              week.forEach { cell ->
                if (cell == null) {
                  Box(Modifier.size(15.dp))
                } else {
                  Box(
                    Modifier
                      .size(15.dp)
                      .clip(RoundedCornerShape(3.dp))
                      .background(heatColor(cell.level)),
                  )
                }
              }
            }
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..3).forEach { level ->
          Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(level)))
        }
        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun heatColor(level: Int): Color = when (level) {
  3 -> MaterialTheme.colorScheme.primary
  2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
  1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
  else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun mondayIndex(day: String): Int {
  val parts = day.split("-")
  if (parts.size != 3) return 0
  val calendar = Calendar.getInstance()
  return runCatching {
    calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
  }.getOrDefault(0)
}
