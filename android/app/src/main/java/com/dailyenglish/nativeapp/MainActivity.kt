package com.dailyenglish.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyenglish.nativeapp.theme.DailyEnglishTheme
import com.dailyenglish.nativeapp.ui.AppRoot
import com.dailyenglish.nativeapp.ui.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val appViewModel: AppViewModel = hiltViewModel()
      val appState by appViewModel.uiState.collectAsStateWithLifecycle()
      DailyEnglishTheme(dynamicColor = appState.dynamicColor) {
        AppRoot(appViewModel = appViewModel)
      }
    }
  }
}
