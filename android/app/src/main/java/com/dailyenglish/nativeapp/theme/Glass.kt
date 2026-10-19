package com.dailyenglish.nativeapp.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun QuietBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val dark = isSystemInDarkTheme()
  val scheme = MaterialTheme.colorScheme
  val glowA = if (dark) scheme.primary.copy(alpha = 0.16f) else scheme.primary.copy(alpha = 0.16f)
  val glowB = if (dark) scheme.tertiary.copy(alpha = 0.10f) else scheme.tertiary.copy(alpha = 0.14f)
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(scheme.background)
      .glow(glowA, glowB),
    content = content,
  )
}

private fun Modifier.glow(sage: Color, sand: Color): Modifier = this.then(
  Modifier.drawBehind {
    drawRadial(sage, center = Offset(size.width * 0.92f, size.height * 0.06f), radius = size.width * 0.95f)
    drawRadial(sand, center = Offset(size.width * 0.05f, size.height * 0.78f), radius = size.width * 0.85f)
  },
)

private fun DrawScope.drawRadial(color: Color, center: Offset, radius: Float) {
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(color, Color.Transparent),
      center = center,
      radius = radius,
    ),
    radius = radius,
    center = center,
  )
}

@Composable
fun glassSurfaceColor(): Color {
  val dark = isSystemInDarkTheme()
  return MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.82f else 0.78f)
}

@Composable
fun glassBorderBrush(): Brush {
  val dark = isSystemInDarkTheme()
  return Brush.linearGradient(
    listOf(
      Color.White.copy(alpha = if (dark) 0.22f else 0.85f),
      Color.White.copy(alpha = if (dark) 0.04f else 0.18f),
    ),
  )
}

@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
  tint: Color = glassSurfaceColor(),
  contentPadding: PaddingValues = PaddingValues(18.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    shape = shape,
    color = Color.Transparent,
    shadowElevation = 10.dp,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.72f))))
        .border(1.dp, glassBorderBrush(), shape)
        .padding(contentPadding),
      content = content,
    )
  }
}

@Composable
fun GlassCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
  tint: Color = glassSurfaceColor(),
  contentPadding: PaddingValues = PaddingValues(18.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = shape,
    color = Color.Transparent,
    shadowElevation = 8.dp,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.72f))))
        .border(1.dp, glassBorderBrush(), shape)
        .padding(contentPadding),
      content = content,
    )
  }
}

@Composable
fun GlassCircleButton(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color.Transparent,
    shadowElevation = 6.dp,
    modifier = modifier.size(size),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .background(glassSurfaceColor())
        .border(1.dp, glassBorderBrush(), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
fun GlassActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
  content: @Composable RowScope.() -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color.Transparent,
    shadowElevation = 6.dp,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier
        .clip(CircleShape)
        .background(glassSurfaceColor())
        .border(1.dp, glassBorderBrush(), CircleShape)
        .padding(contentPadding),
      verticalAlignment = Alignment.CenterVertically,
      content = content,
    )
  }
}
