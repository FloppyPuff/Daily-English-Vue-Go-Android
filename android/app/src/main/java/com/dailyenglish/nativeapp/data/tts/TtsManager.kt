package com.dailyenglish.nativeapp.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
  @ApplicationContext context: Context,
) : TextToSpeech.OnInitListener {
  private var engine: TextToSpeech? = TextToSpeech(context, this)
  @Volatile private var ready = false
  @Volatile private var englishAvailable = false

  override fun onInit(status: Int) {
    if (status != TextToSpeech.SUCCESS) return
    val result = engine?.setLanguage(Locale.US)
    englishAvailable = result == TextToSpeech.LANG_AVAILABLE ||
      result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
      result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    engine?.setSpeechRate(0.86f)
    ready = true
  }

  fun speak(text: String): Boolean {
    if (!ready || !englishAvailable) return false
    val utteranceId = UUID.randomUUID().toString()
    return engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
  }
}
