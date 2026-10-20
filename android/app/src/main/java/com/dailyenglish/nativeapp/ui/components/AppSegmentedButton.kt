package com.dailyenglish.nativeapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Segmented choice that marks the selection with color alone. The Material
 * default draws a check next to the selected label; the web version uses a
 * tonal fill with stronger text instead, so the icon slot stays empty and the
 * content remains centered.
 */
@Composable
fun SingleChoiceSegmentedButtonRowScope.AppSegmentedButton(
  selected: Boolean,
  onClick: () -> Unit,
  shape: Shape,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  label: @Composable () -> Unit,
) {
  SegmentedButton(
    selected = selected,
    onClick = onClick,
    shape = shape,
    modifier = modifier,
    enabled = enabled,
    colors = SegmentedButtonDefaults.colors(
      activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
      activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    icon = {},
    label = label,
  )
}
