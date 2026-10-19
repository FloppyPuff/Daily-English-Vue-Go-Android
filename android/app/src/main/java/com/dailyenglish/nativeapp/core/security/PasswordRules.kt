package com.dailyenglish.nativeapp.core.security

object PasswordRules {
  fun problem(password: String): String? = when {
    password.isEmpty() -> "请填写密码"
    password.length < 8 -> "密码至少 8 位"
    password.length > 128 -> "密码最长 128 位"
    !password.any { it.isLetter() } || !password.any { it.isDigit() } -> "密码需同时包含字母和数字"
    else -> null
  }
}
