package com.dailyenglish.nativeapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferenceDataStore by preferencesDataStore(name = "preferences")

interface PreferenceStore {
  val dynamicColor: Flow<Boolean>
  suspend fun setDynamicColor(value: Boolean)
}

@Singleton
class DataStorePreferenceStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : PreferenceStore {
  private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

  override val dynamicColor: Flow<Boolean> = context.preferenceDataStore.data.map { it[dynamicColorKey] ?: false }

  override suspend fun setDynamicColor(value: Boolean) {
    context.preferenceDataStore.edit { it[dynamicColorKey] = value }
  }
}
