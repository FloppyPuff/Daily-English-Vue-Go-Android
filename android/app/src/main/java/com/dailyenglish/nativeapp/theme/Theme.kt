package com.dailyenglish.nativeapp.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = Color(0xFF4F6A4B),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD9EAD1),
  onPrimaryContainer = Color(0xFF152611),
  secondary = Color(0xFF5E6B57),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE2E9DC),
  onSecondaryContainer = Color(0xFF1B2118),
  tertiary = Color(0xFFA86A42),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFF4E0C9),
  onTertiaryContainer = Color(0xFF2B1A08),
  background = Color(0xFFF1F4ED),
  onBackground = Color(0xFF1B211A),
  surface = Color(0xFFF9FBF6).copy(alpha = 0.82f),
  onSurface = Color(0xFF1B211A),
  surfaceVariant = Color(0xFFE6EBE0).copy(alpha = 0.78f),
  onSurfaceVariant = Color(0xFF44483F),
  outline = Color(0xFF74796E),
  outlineVariant = Color(0xFFC7CDBF),
  error = Color(0xFFB3261E),
  onError = Color.White,
  errorContainer = Color(0xFFF9DEDC),
  onErrorContainer = Color(0xFF410E0B),
  inverseSurface = Color(0xFF2E322B),
  inverseOnSurface = Color(0xFFEFF1EA),
  inversePrimary = Color(0xFFB3D0AB),
  scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFFB3D0AB),
  onPrimary = Color(0xFF1F371D),
  primaryContainer = Color(0xFF2D4429),
  onPrimaryContainer = Color(0xFFCFEBC6),
  secondary = Color(0xFFBFCBB8),
  onSecondary = Color(0xFF2A3325),
  secondaryContainer = Color(0xFF3D4838),
  onSecondaryContainer = Color(0xFFDBE6D3),
  tertiary = Color(0xFFE7C29C),
  onTertiary = Color(0xFF442B14),
  tertiaryContainer = Color(0xFF5D4128),
  onTertiaryContainer = Color(0xFFFFDCBA),
  background = Color(0xFF0E120F),
  onBackground = Color(0xFFE2E5DB),
  surface = Color(0xFF161C17).copy(alpha = 0.84f),
  onSurface = Color(0xFFE2E5DB),
  surfaceVariant = Color(0xFF3D453B).copy(alpha = 0.8f),
  onSurfaceVariant = Color(0xFFC4C9BD),
  outline = Color(0xFF8E9387),
  outlineVariant = Color(0xFF44483F),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
  inverseSurface = Color(0xFFE2E5DB),
  inverseOnSurface = Color(0xFF2E322B),
  inversePrimary = Color(0xFF4F6A4B),
  scrim = Color.Black,
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun DailyEnglishTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
  }
  MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
}
