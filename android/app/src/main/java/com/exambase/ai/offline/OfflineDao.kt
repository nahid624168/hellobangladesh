package com.exambase.ai.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_notes WHERE deleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    suspend fun allNotes(): List<OfflineNoteEntity>

    @Query("SELECT * FROM offline_notes WHERE id = :id LIMIT 1")
    suspend fun note(id: String): OfflineNoteEntity?

    @Query("SELECT * FROM offline_notes WHERE localDirty = 1")
    suspend fun dirtyNotes(): List<OfflineNoteEntity>

    @Query("SELECT * FROM offline_notes WHERE conflict = 1")
    suspend fun conflictNotes(): List<OfflineNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<OfflineNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: OfflineNoteEntity)

    @Update
    suspend fun updateNote(note: OfflineNoteEntity)

    @Query("SELECT * FROM offline_folders WHERE deleted = 0 ORDER BY position, name")
    suspend fun allFolders(): List<OfflineFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<OfflineFolderEntity>)

    @Query("SELECT value FROM sync_meta WHERE key = :key LIMIT 1")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: SyncMetaEntity)

    // --- Lessons / subjects (Step 11b offline cache) ---

    @Query("SELECT * FROM offline_lessons WHERE id = :id LIMIT 1")
    suspend fun lesson(id: String): OfflineLessonEntity?

    @Query("SELECT * FROM offline_lessons ORDER BY downloadedAt DESC")
    suspend fun allLessons(): List<OfflineLessonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(lesson: OfflineLessonEntity)

    @Query("DELETE FROM offline_lessons WHERE id = :id")
    suspend fun deleteLesson(id: String)

    @Query("SELECT * FROM offline_subjects ORDER BY featured DESC, title ASC")
    suspend fun allSubjects(): List<OfflineSubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(subjects: List<OfflineSubjectEntity>)

    @Query("DELETE FROM offline_subjects")
    suspend fun clearSubjects()
}
