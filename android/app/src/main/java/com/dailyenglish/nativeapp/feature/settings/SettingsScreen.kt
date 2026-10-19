package com.dailyenglish.nativeapp.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyenglish.nativeapp.core.sync.BackupCodec
import com.dailyenglish.nativeapp.data.repository.AuthState
import com.dailyenglish.nativeapp.data.repository.SyncMode
import com.dailyenglish.nativeapp.data.repository.SyncStatus
import com.dailyenglish.nativeapp.theme.glassBorderBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onLoginRequested: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var dailyGoal by rememberSaveable { mutableStateOf(state.settings.dailyGoal.toString()) }
  var sentenceGoal by rememberSaveable { mutableStateOf(state.settings.sentenceGoal.toString()) }
  var focusMinutes by rememberSaveable { mutableStateOf(state.settings.focusMinutes.toString()) }
  var currentPassword by rememberSaveable { mutableStateOf("") }
  var nextPassword by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(state.settings) {
    dailyGoal = state.settings.dailyGoal.toString()
    sentenceGoal = state.settings.sentenceGoal.toString()
    focusMinutes = state.settings.focusMinutes.toString()
  }
  LaunchedEffect(Unit) {
    viewModel.clearPasswordError()
  }
  LaunchedEffect(state.message) {
    if (state.message.startsWith("密码已修改")) {
      currentPassword = ""
      nextPassword = ""
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    viewModel.exportBackup { text ->
      scope.launch {
        runCatching {
          withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
              ?: error("无法写入文件")
          }
        }.onSuccess {
          viewModel.note("备份已导出，请妥善保存。")
        }.onFailure {
          viewModel.note("导出失败，请换一个位置再试。")
        }
      }
    }
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取文件")
        }
      }.onSuccess { bytes ->
        if (bytes.size > BackupCodec.MAX_BYTES) {
          viewModel.note("备份文件超过 5 MB 上限。")
        } else {
          viewModel.prepareImport(String(bytes, Charsets.UTF_8))
        }
      }.onFailure {
        viewModel.note("读取备份失败，原有数据未改变。")
      }
    }
  }

  LazyColumn(
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, glassBorderBrush()),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("每日目标", style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
            value = dailyGoal,
            onValueChange = { dailyGoal = it.filter(Char::isDigit) },
            label = { Text("每日新词目标") },
            supportingText = { Text("1–100 词") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = sentenceGoal,
            onValueChange = { sentenceGoal = it.filter(Char::isDigit) },
            label = { Text("每日翻译目标") },
            supportingText = { Text("1–20 句") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = focusMinutes,
            onValueChange = { focusMinutes = it.filter(Char::isDigit) },
            label = { Text("单次专注时长") },
            supportingText = { Text("1–120 分钟 · 下次计时生效") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            onClick = {
              viewModel.save(
                dailyGoal = dailyGoal.toIntOrNull() ?: state.settings.dailyGoal,
                sentenceGoal = sentenceGoal.toIntOrNull() ?: state.settings.sentenceGoal,
                focusMinutes = focusMinutes.toIntOrNull() ?: state.settings.focusMinutes,
                exam = state.settings.exam,
              )
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
          ) { Text(if (state.busy) "保存中…" else "保存学习计划") }
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(if (state.auth is AuthState.Member) "账号与同步" else "把进度带到任何设备", style = MaterialTheme.typography.titleMedium)
          when (val auth = state.auth) {
            is AuthState.Member -> {
              Text(auth.email, style = MaterialTheme.typography.titleSmall)
              Text(syncLabel(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              if (state.sync.problem.isNotBlank()) {
                Text(state.sync.problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::syncNow) { Text("立即同步") }
                OutlinedButton(onClick = viewModel::logout) { Text("退出登录") }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Text("修改密码", style = MaterialTheme.typography.titleSmall)
              OutlinedTextField(
                value = currentPassword,
                onValueChange = {
                  currentPassword = it
                  viewModel.clearPasswordError()
                },
                label = { Text("当前密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = nextPassword,
                onValueChange = {
                  nextPassword = it
                  viewModel.clearPasswordError()
                },
                label = { Text("新密码") },
                supportingText = { Text("至少 8 位，含字母与数字") },
                singleLine = true,
                isError = state.passwordError.isNotBlank(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
              )
              if (state.passwordError.isNotBlank()) {
                Text(state.passwordError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
              }
              OutlinedButton(
                onClick = { viewModel.changePassword(currentPassword, nextPassword) },
                enabled = !state.passwordBusy,
                modifier = Modifier.fillMaxWidth(),
              ) { Text(if (state.passwordBusy) "修改中…" else "修改密码") }
            }
            is AuthState.Anonymous -> {
              Text("登录后可跨设备同步；不登录则仅保存在本机。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Button(onClick = onLoginRequested) { Text("登录或注册") }
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
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("跟随壁纸取色", style = MaterialTheme.typography.titleMedium)
            Text(
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统 Material You 配色"
              else "Android 12 及以上可用",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = state.dynamicColor,
            onCheckedChange = viewModel::setDynamicColor,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
          )
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
          Text(
            "导出完整学习状态；导入会覆盖本机进度。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = { exportLauncher.launch(viewModel.fileName()) },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Outlined.FileDownload, contentDescription = null)
              Text("导出备份", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
              onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
              enabled = !state.importing,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Outlined.FileUpload, contentDescription = null)
              Text(if (state.importing) "读取中…" else "恢复备份", modifier = Modifier.padding(start = 6.dp))
            }
          }
          if (state.importing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
          }
          Text("上限 5 MB · 导入失败不覆盖", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    if (state.message.isNotBlank()) {
      item { Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
    item {
      Text("7,291 个大纲词汇 · 253 句真题句子 · 44 套考研真题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }

  val pending = state.pendingImport
  if (pending != null) {
    AlertDialog(
      onDismissRequest = viewModel::cancelImport,
      title = { Text("恢复备份？") },
      text = {
        Text("${state.pendingSummary}\n\n恢复会替换这台设备上的学习进度。建议先导出当前备份。确定恢复吗？")
      },
      confirmButton = {
        TextButton(onClick = viewModel::confirmImport, enabled = !state.importing) { Text("恢复备份") }
      },
      dismissButton = {
        TextButton(onClick = viewModel::cancelImport) { Text("取消") }
      },
    )
  }
}

private fun syncLabel(state: SettingsUiState): String = when (state.sync.status) {
  SyncStatus.IDLE -> if (state.sync.mode == SyncMode.MEMBER) "已同步" else "未登录"
  SyncStatus.SAVING -> "正在保存…"
  SyncStatus.DIRTY -> "有未同步的修改"
  SyncStatus.OFFLINE -> "离线，稍后自动重试"
  SyncStatus.SIGNED_OUT -> "登录已过期，进度仍在本机"
}
