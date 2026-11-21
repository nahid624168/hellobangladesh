package com.exambase.backend.academic

import kotlinx.serialization.Serializable

/** Lesson progress as seen by one learner. "not_started" means no row exists. */
object ProgressStatus {
    const val NOT_STARTED = "not_started"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    val settable = setOf(IN_PROGRESS, COMPLETED)
}

@Serializable
data class Page<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class Crumb(val type: String, val id: String, val title: String, val titleBn: String? = null)

@Serializable
data class SubjectSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val description: String,
    val descriptionBn: String? = null,
    val icon: String? = null,
    val coverUrl: String? = null,
    val featured: Boolean,
    val categoryCount: Int,
    val lessonCount: Int,
)

@Serializable
data class CategorySummary(
    val id: String,
    val subjectId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String,
    val descriptionBn: String? = null,
    val imageUrl: String? = null,
    val subcategoryCount: Int,
)

@Serializable
data class SubcategorySummary(
    val id: String,
    val categoryId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String,
    val descriptionBn: String? = null,
    val lessonCount: Int,
)

@Serializable
data class LessonSummary(
    val id: String,
    val subcategoryId: String,
    val title: String,
    val titleBn: String? = null,
    val summary: String,
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
    /** Time-limited signed URL (or original public URL when signing is disabled). */
    val url: String,
    val sizeBytes: Long? = null,
    /** Epoch millis when the signed URL expires; null if not signed. */
    val urlExpiresAt: Long? = null,
)

/** Lightweight question card shown on a lesson (no answers / options). */
@Serializable
data class RelatedQuestionSummary(
    val id: String,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val difficulty: String,
    val marks: Double,
)

@Serializable
data class RelatedQuizSetSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val questionCount: Int,
    val timeLimitSeconds: Int? = null,
    val isOwner: Boolean = false,
)

@Serializable
data class RelatedFlashcardSetSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val cardCount: Int,
    val isOwner: Boolean = false,
)

@Serializable
data class RelatedNoteSummary(
    val id: String,
    val title: String,
    val updatedAt: String,
    val pinned: Boolean = false,
)

@Serializable
data class SubjectDetail(val subject: SubjectSummary, val categories: List<CategorySummary>)

@Serializable
data class CategoryDetail(val category: CategorySummary, val breadcrumb: List<Crumb>, val subcategories: List<SubcategorySummary>)

@Serializable
data class SubcategoryDetail(val subcategory: SubcategorySummary, val breadcrumb: List<Crumb>, val lessons: List<LessonSummary>)

/**
 * Lesson detail including related content and signed attachment URLs (Step 11b).
 * Clients that do not understand the new fields simply ignore them.
 */
@Serializable
data class LessonDetail(
    val lesson: LessonSummary,
    val content: String,
    val contentBn: String? = null,
    val learningObjectives: List<String>,
    val attachments: List<AttachmentDto>,
    val breadcrumb: List<Crumb>,
    val previousLessonId: String? = null,
    val nextLessonId: String? = null,
    val relatedQuestions: List<RelatedQuestionSummary> = emptyList(),
    val relatedQuizSets: List<RelatedQuizSetSummary> = emptyList(),
    val relatedFlashcardSets: List<RelatedFlashcardSetSummary> = emptyList(),
    val relatedNotes: List<RelatedNoteSummary> = emptyList(),
)

@Serializable
data class SetProgressRequest(val status: String)

@Serializable
data class SubjectQuery(
    val search: String? = null,
    val sort: String = SORT_ORDER,
    val featuredOnly: Boolean = false,
) {
    companion object {
        const val SORT_ORDER = "order"
        const val SORT_TITLE = "title"
        const val SORT_NEWEST = "newest"
        val sorts = setOf(SORT_ORDER, SORT_TITLE, SORT_NEWEST)
    }
}
