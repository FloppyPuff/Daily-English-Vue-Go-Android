package com.dailyenglish.nativeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    WordRecordEntity::class,
    SentenceRecordEntity::class,
    PaperRecordEntity::class,
    DraftEntity::class,
    SavedWordEntity::class,
    SavedSentenceEntity::class,
    StudyEventEntity::class,
    SettingsEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class DailyEnglishDatabase : RoomDatabase() {
  abstract fun progressDao(): ProgressDao
}
