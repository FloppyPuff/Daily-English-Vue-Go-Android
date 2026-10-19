package com.dailyenglish.nativeapp.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "session")

interface SessionStore {
  suspend fun token(): String?
  suspend fun email(): String?
  suspend fun guestMode(): Boolean
  suspend fun saveAuth(token: String, email: String)
  suspend fun clearAuth()
  suspend fun setGuestMode(value: Boolean)
}

@Singleton
class EncryptedSessionStore @Inject constructor(
  @ApplicationContext private val context: Context,
  private val cipher: TokenCipher,
) : SessionStore {
  private val tokenKey = stringPreferencesKey("token_ciphertext")
  private val emailKey = stringPreferencesKey("email")
  private val guestKey = booleanPreferencesKey("guest_mode")

  override suspend fun token(): String? {
    val encoded = context.sessionDataStore.data.first()[tokenKey] ?: return null
    return runCatching { cipher.decrypt(encoded) }.getOrNull()
  }

  override suspend fun email(): String? = context.sessionDataStore.data.first()[emailKey]

  override suspend fun guestMode(): Boolean = context.sessionDataStore.data.first()[guestKey] ?: false

  override suspend fun saveAuth(token: String, email: String) {
    val encrypted = cipher.encrypt(token)
    context.sessionDataStore.edit {
      it[tokenKey] = encrypted
      it[emailKey] = email
      it[guestKey] = false
    }
  }

  override suspend fun clearAuth() {
    context.sessionDataStore.edit {
      it.remove(tokenKey)
      it.remove(emailKey)
    }
  }

  override suspend fun setGuestMode(value: Boolean) {
    context.sessionDataStore.edit { it[guestKey] = value }
  }
}

@Singleton
class TokenCipher @Inject constructor() {
  private val alias = "daily_english_session"
  private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  fun encrypt(value: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val buffer = ByteBuffer.allocate(4 + iv.size + encrypted.size)
      .putInt(iv.size)
      .put(iv)
      .put(encrypted)
    return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
  }

  fun decrypt(encoded: String): String? = runCatching {
    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
    val buffer = ByteBuffer.wrap(bytes)
    val iv = ByteArray(buffer.int).also { buffer.get(it) }
    val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
    String(cipher.doFinal(encrypted), Charsets.UTF_8)
  }.getOrNull()

  private fun key(): SecretKey {
    (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build(),
    )
    return generator.generateKey()
  }
}
