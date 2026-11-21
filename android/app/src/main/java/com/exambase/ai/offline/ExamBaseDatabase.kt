package com.exambase.ai.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflineNoteEntity::class,
        OfflineFolderEntity::class,
        SyncMetaEntity::class,
        OfflineLessonEntity::class,
        OfflineSubjectEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ExamBaseDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: ExamBaseDatabase? = null

        fun get(context: Context): ExamBaseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExamBaseDatabase::class.java,
                    "exambase_offline.db",
                )
                    // Only the old v1 cache may be dropped. From v2 on, every change needs a real Migration,
                    // otherwise unsynced offline notes would be lost.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build().also { instance = it }
            }
    }
}
