package com.dailyenglish.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyenglish.nativeapp.feature.auth.AuthScreen
import com.dailyenglish.nativeapp.feature.auth.AuthViewModel
import com.dailyenglish.nativeapp.feature.papers.PapersViewModel
import com.dailyenglish.nativeapp.feature.study.StudyViewModel
import com.dailyenglish.nativeapp.theme.QuietBackground
import com.dailyenglish.nativeapp.ui.navigation.MainNavigation

@Composable
fun AppRoot(
  appViewModel: AppViewModel = hiltViewModel(),
  studyViewModel: StudyViewModel = hiltViewModel(),
  papersViewModel: PapersViewModel = hiltViewModel(),
) {
  val appState by appViewModel.uiState.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  LaunchedEffect(studyViewModel) {
    studyViewModel.messages.collect { snackbar.showSnackbar(it) }
  }

  QuietBackground {
    when {
      appState.booting -> SplashScreen()
      appState.showAuth -> {
        val authViewModel: AuthViewModel = hiltViewModel()
        val authState by authViewModel.state.collectAsStateWithLifecycle()
        AuthScreen(
          state = authState,
          onMode = authViewModel::setMode,
          onEmail = authViewModel::updateEmail,
          onPassword = authViewModel::updatePassword,
          onSubmit = authViewModel::submit,
          onGuest = appViewModel::continueAsGuest,
        )
      }
      else -> MainNavigation(
        studyViewModel = studyViewModel,
        papersViewModel = papersViewModel,
        snackbarHostState = snackbar,
        onLoginRequested = appViewModel::requestAuth,
      )
    }
  }
}

@Composable
private fun SplashScreen() {
  Column(
    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
  ) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp)) {
      Icon(
        Icons.Outlined.MenuBook,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(16.dp),
      )
    }
    Text("Daily English.", style = MaterialTheme.typography.headlineMedium)
    Text("A LITTLE, EVERY DAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
  }
}
