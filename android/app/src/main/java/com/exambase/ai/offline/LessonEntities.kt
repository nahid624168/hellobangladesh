package com.exambase.ai.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached lesson content for offline reading (Step 11b).
 * Attachments are stored as a compact JSON array of {id,kind,title,url,sizeBytes}.
 */
@Entity(tableName = "offline_lessons")
data class OfflineLessonEntity(
    @PrimaryKey val id: String,
    val subcategoryId: String,
    val title: String,
    val titleBn: String?,
    val summary: String,
    val summaryBn: String?,
    val content: String,
    val contentBn: String?,
    val learningObjectivesCsv: String,
    val attachmentsJson: String,
    val estimatedMinutes: Int?,
    val progress: String,
    val bookmarked: Boolean,
    val downloadedAt: Long,
    val contentHash: String,
)

@Entity(tableName = "offline_subjects")
data class OfflineSubjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleBn: String?,
    val description: String,
    val descriptionBn: String?,
    val icon: String?,
    val coverUrl: String?,
    val featured: Boolean,
    val categoryCount: Int,
    val lessonCount: Int,
    val cachedAt: Long,
)
