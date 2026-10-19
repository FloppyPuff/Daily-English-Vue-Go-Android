package com.dailyenglish.nativeapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.dailyenglish.nativeapp.feature.notebook.NotebookScreen
import com.dailyenglish.nativeapp.feature.papers.PaperGroupScreen
import com.dailyenglish.nativeapp.feature.papers.PaperSetScreen
import com.dailyenglish.nativeapp.feature.papers.PapersScreen
import com.dailyenglish.nativeapp.feature.papers.PapersViewModel
import com.dailyenglish.nativeapp.feature.settings.SettingsScreen
import com.dailyenglish.nativeapp.feature.stats.StatsScreen
import com.dailyenglish.nativeapp.feature.study.FocusSheet
import com.dailyenglish.nativeapp.feature.study.PracticeScreen
import com.dailyenglish.nativeapp.feature.study.ReviewScreen
import com.dailyenglish.nativeapp.feature.study.SentencesScreen
import com.dailyenglish.nativeapp.feature.study.StudyViewModel
import com.dailyenglish.nativeapp.feature.study.TodayScreen
import com.dailyenglish.nativeapp.feature.study.WordsScreen
import com.dailyenglish.nativeapp.theme.GlassActionButton
import com.dailyenglish.nativeapp.theme.GlassCircleButton
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import com.dailyenglish.nativeapp.theme.glassSurfaceColor

private data class TopDestination(val key: NavKey, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
  TopDestination(Today, "今日", Icons.Outlined.Home),
  TopDestination(Practice, "练习", Icons.Outlined.MenuBook),
  TopDestination(Notebook, "笔记", Icons.Outlined.BookmarkBorder),
  TopDestination(Stats, "记录", Icons.Outlined.BarChart),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
  studyViewModel: StudyViewModel,
  papersViewModel: PapersViewModel,
  snackbarHostState: SnackbarHostState,
  onLoginRequested: () -> Unit,
) {
  val ui by studyViewModel.state.collectAsStateWithLifecycle()
  val papersUi by papersViewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(papersViewModel) {
    papersViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
  }
  val backStack = rememberNavBackStack(Today)
  val current = backStack.lastOrNull()
  var showFocus by rememberSaveable { mutableStateOf(false) }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp
    val compactRail = maxHeight < 480.dp
    Scaffold(
      containerColor = Color.Transparent,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Text(titleFor(current), style = MaterialTheme.typography.titleLarge)
          },
          navigationIcon = {
            if (backStack.size > 1) {
              GlassCircleButton(
                onClick = { backStack.removeLastOrNull() },
                icon = Icons.Outlined.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          },
          actions = {
            GlassActionButton(onClick = { showFocus = true }) {
              Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                if (ui.focus.running) formatClock(ui.focus.remainingSeconds) else "专注",
                style = MaterialTheme.typography.labelLarge,
              )
            }
            Spacer(Modifier.width(8.dp))
            GlassCircleButton(
              onClick = { backStack.add(Settings) },
              icon = Icons.Outlined.Settings,
              contentDescription = "学习设置",
              modifier = Modifier.padding(end = 8.dp),
            )
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
      },
      bottomBar = {
        if (!wide) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            FloatingGlassNav(current) { key ->
              if (backStack.lastOrNull() != key) {
                backStack.clear()
                backStack.add(key)
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Row(Modifier.padding(innerPadding).fillMaxSize()) {
        if (wide) {
          Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(10.dp),
          ) {
            Box(
              Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(glassSurfaceColor())
                .border(1.dp, glassBorderBrush(), RoundedCornerShape(28.dp)),
            ) {
              NavigationRail(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0),
              ) {
                Spacer(Modifier.height(8.dp))
                topDestinations.forEach { destination ->
                  NavigationRailItem(
                    selected = isSelected(current, destination.key),
                    onClick = {
                      if (backStack.lastOrNull() != destination.key) {
                        backStack.clear()
                        backStack.add(destination.key)
                      }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = if (compactRail) null else {
                      { Text(destination.label) }
                    },
                    alwaysShowLabel = false,
                  )
                }
              }
            }
          }
        }
        Box(Modifier.weight(1f)) {
          NavDisplay(
            backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
          entry<Today> {
            TodayScreen(
              ui = ui,
              onStartWords = { backStack.add(Words("new")) },
              onReview = { backStack.add(Words("review")) },
              onSentences = { backStack.add(Sentences) },
            )
          }
          entry<Practice> {
            PracticeScreen(
              ui = ui,
              onWords = { backStack.add(Words("new")) },
              onSentences = { backStack.add(Sentences) },
              onReview = { backStack.add(Review) },
              onPapers = { backStack.add(Papers) },
            )
          }
          entry<Papers> {
            PapersScreen(ui = papersUi, progress = ui.progress, onOpenPaper = { backStack.add(PaperSet(it)) })
          }
          entry<PaperSet> { key ->
            PaperSetScreen(
              ui = papersUi,
              progress = ui.progress,
              setId = key.id,
              onLoad = papersViewModel::openPaper,
              onOpenGroup = { backStack.add(PaperGroup(key.id, it)) },
            )
          }
          entry<PaperGroup> { key ->
            PaperGroupScreen(
              ui = papersUi,
              setId = key.setId,
              groupIndex = key.index,
              onLoad = papersViewModel::openGroup,
              onPick = papersViewModel::pick,
              onAnswer = papersViewModel::updateAnswer,
              onCheck = papersViewModel::toggleReference,
              onSubmit = papersViewModel::submit,
              onRate = papersViewModel::rate,
              onReset = papersViewModel::reset,
            )
          }
          entry<Words> { key ->
            WordsScreen(
              ui = ui,
              mode = key.mode,
              wordId = key.wordId,
              onStartSession = studyViewModel::startSession,
              onReveal = studyViewModel::revealWord,
              onRate = studyViewModel::rateWord,
              onNext = studyViewModel::nextWord,
              onSpellingInput = studyViewModel::setSpellingInput,
              onCheckSpelling = studyViewModel::checkSpelling,
              onToggleSave = studyViewModel::toggleSavedWord,
              onSpeak = studyViewModel::speakWord,
              onNewSession = studyViewModel::newWordSession,
            )
          }
          entry<Review> {
            ReviewScreen(ui = ui, onStart = { backStack.add(Words("review")) })
          }
          entry<Sentences> {
            SentencesScreen(
              ui = ui,
              onEnsure = studyViewModel::ensureSentence,
              onSelect = studyViewModel::selectSentence,
              onDraft = studyViewModel::updateSentenceDraft,
              onReveal = studyViewModel::revealSentence,
              onRate = studyViewModel::rateSentence,
              onNext = studyViewModel::nextSentence,
              onSave = studyViewModel::toggleSavedSentence,
              onSpeak = studyViewModel::speakSentence,
            )
          }
          entry<Notebook> {
            NotebookScreen(
              onOpenWord = { backStack.add(Words("browse", it)) },
              onOpenSentence = {
                studyViewModel.selectSentence(it)
                backStack.add(Sentences)
              },
              onPracticeWords = {
                studyViewModel.startSession(it)
                backStack.add(Words(it))
              },
              onPracticeSentences = {
                studyViewModel.selectSentence(it)
                backStack.add(Sentences)
              },
            )
          }
          entry<Stats> {
            StatsScreen()
          }
          entry<Settings> {
            SettingsScreen(onLoginRequested = onLoginRequested)
          }
        },
          )
        }
      }
    }
  }

  if (showFocus) {
    FocusSheet(
      ui = ui,
      onStart = studyViewModel::startFocus,
      onPause = studyViewModel::pauseFocus,
      onReset = studyViewModel::resetFocus,
      onDismiss = { showFocus = false },
    )
  }
}

@Composable
private fun FloatingGlassNav(current: NavKey?, onSelect: (NavKey) -> Unit) {
  Surface(
    shape = CircleShape,
    color = Color.Transparent,
    shadowElevation = 14.dp,
    modifier = Modifier.fillMaxWidth().height(68.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .background(
          Brush.verticalGradient(
            listOf(glassSurfaceColor().copy(alpha = 0.94f), glassSurfaceColor().copy(alpha = 0.72f)),
          ),
        )
        .border(1.dp, glassBorderBrush(), CircleShape)
        .padding(6.dp)
        .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      topDestinations.forEach { destination ->
        val selected = isSelected(current, destination.key)
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(
              selected = selected,
              role = Role.Tab,
              onClick = { onSelect(destination.key) },
            )
            .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            destination.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
          )
          Text(
            destination.label,
            style = MaterialTheme.typography.labelSmall.copy(
              letterSpacing = 0.sp,
              fontWeight = FontWeight.Medium,
            ),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun isSelected(current: NavKey?, target: NavKey): Boolean = when (target) {  Today -> current is Today
  Practice -> current is Practice || current is Words || current is Sentences || current is Review || current is Papers || current is PaperSet || current is PaperGroup
  Notebook -> current is Notebook
  Stats -> current is Stats
  else -> false
}

private fun titleFor(key: NavKey?): String = when (key) {
  Today -> "今日学习"
  Practice -> "自由练习"
  is Words -> "词汇练习"
  Sentences -> "句子翻译"
  Review -> "复习计划"
  Papers -> "真题练习"
  is PaperSet -> "试卷"
  is PaperGroup -> "真题练习"
  Notebook -> "我的笔记"
  Stats -> "学习记录"
  Settings -> "学习设置"
  else -> "今日学习"
}

private fun formatClock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
