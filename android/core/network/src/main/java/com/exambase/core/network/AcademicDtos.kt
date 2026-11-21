package com.exambase.core.network

import kotlinx.serialization.Serializable

/** Mirrors backend/academic/AcademicModels.kt. Unknown fields are ignored, so the server can add fields safely. */
object ProgressStatus {
    const val NOT_STARTED = "not_started"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
}

object SubjectSort {
    const val ORDER = "order"
    const val TITLE = "title"
    const val NEWEST = "newest"
}

@Serializable data class PageResponse<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable data class Crumb(val type: String, val id: String, val title: String, val titleBn: String? = null)

@Serializable
data class SubjectSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val icon: String? = null,
    val coverUrl: String? = null,
    val featured: Boolean = false,
    val categoryCount: Int = 0,
    val lessonCount: Int = 0,
)

@Serializable
data class CategorySummary(
    val id: String,
    val subjectId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val imageUrl: String? = null,
    val subcategoryCount: Int = 0,
)

@Serializable
data class SubcategorySummary(
    val id: String,
    val categoryId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val lessonCount: Int = 0,
)

@Serializable
data class LessonSummary(
    val id: String,
    val subcategoryId: String,
    val title: String,
    val titleBn: String? = null,
    val summary: String = "",
    val summaryBn: String? = null,
    val estimatedMinutes: Int? = null,
    val progress: String = ProgressStatus.NOT_STARTED,
    val bookmarked: Boolean = false,
)

@Serializable
data class AttachmentDto(
    val id: String,
    val kind: String,
    val title: String,
    val url: String,
    val sizeBytes: Long? = null,
    val urlExpiresAt: Long? = null,
)

@Serializable
data class RelatedQuestionSummary(
    val id: String,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val difficulty: String = "medium",
    val marks: Double = 1.0,
)

@Serializable
data class RelatedQuizSetSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val questionCount: Int = 0,
    val timeLimitSeconds: Int? = null,
    val isOwner: Boolean = false,
)

@Serializable
data class RelatedFlashcardSetSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val cardCount: Int = 0,
    val isOwner: Boolean = false,
)

@Serializable
data class RelatedNoteSummary(
    val id: String,
    val title: String,
    val updatedAt: String = "",
    val pinned: Boolean = false,
)

@Serializable data class SubjectDetail(val subject: SubjectSummary, val categories: List<CategorySummary>)

@Serializable data class CategoryDetail(val category: CategorySummary, val breadcrumb: List<Crumb> = emptyList(), val subcategories: List<SubcategorySummary>)

@Serializable data class SubcategoryDetail(val subcategory: SubcategorySummary, val breadcrumb: List<Crumb> = emptyList(), val lessons: List<LessonSummary>)

@Serializable
data class LessonDetail(
    val lesson: LessonSummary,
    val content: String = "",
    val contentBn: String? = null,
    val learningObjectives: List<String> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val breadcrumb: List<Crumb> = emptyList(),
    val previousLessonId: String? = null,
    val nextLessonId: String? = null,
    val relatedQuestions: List<RelatedQuestionSummary> = emptyList(),
    val relatedQuizSets: List<RelatedQuizSetSummary> = emptyList(),
    val relatedFlashcardSets: List<RelatedFlashcardSetSummary> = emptyList(),
    val relatedNotes: List<RelatedNoteSummary> = emptyList(),
)

@Serializable data class SetProgressRequest(val status: String)
