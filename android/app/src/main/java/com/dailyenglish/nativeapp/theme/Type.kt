package com.dailyenglish.nativeapp.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif

// English display type is serif to echo the book-like brand; Chinese body text
// stays sans for legibility. Word headwords and numbers get dedicated styles.
val AppTypography = Typography(
  displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp),
  displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.25).sp),
  displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
  headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
  headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
  headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
  titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
  titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
  titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
  bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
  bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
  labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
  labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
)

val WordHeadwordStyle = TextStyle(
  fontFamily = Serif,
  fontWeight = FontWeight.SemiBold,
  fontSize = 56.sp,
  lineHeight = 62.sp,
)

val NumberStyle = TextStyle(
  fontFamily = FontFamily.Monospace,
  fontWeight = FontWeight.Medium,
  fontFeatureSettings = "tnum",
)
