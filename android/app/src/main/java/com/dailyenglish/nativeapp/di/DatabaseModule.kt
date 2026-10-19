package com.dailyenglish.nativeapp.di

import android.content.Context
import androidx.room.Room
import com.dailyenglish.nativeapp.data.local.DailyEnglishDatabase
import com.dailyenglish.nativeapp.data.local.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): DailyEnglishDatabase =
    Room.databaseBuilder(context, DailyEnglishDatabase::class.java, "daily-english-native.db").build()

  @Provides
  fun provideProgressDao(database: DailyEnglishDatabase): ProgressDao = database.progressDao()
}
