package com.dailyenglish.nativeapp.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordRulesTest {
  @Test
  fun rejectsShortNumericAndOverlongPasswords() {
    assertEquals("请填写密码", PasswordRules.problem(""))
    assertEquals("密码至少 8 位", PasswordRules.problem("abc1234"))
    assertEquals("密码需同时包含字母和数字", PasswordRules.problem("abcdefgh"))
    assertEquals("密码需同时包含字母和数字", PasswordRules.problem("12345678"))
    assertEquals("密码最长 128 位", PasswordRules.problem("a1" + "x".repeat(127)))
  }

  @Test
  fun acceptsLettersAndDigits() {
    assertNull(PasswordRules.problem("daily2026"))
  }
}
