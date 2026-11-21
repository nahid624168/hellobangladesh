package com.exambase.backend.questions

import com.exambase.backend.academic.Crumb
import kotlinx.serialization.Serializable

object QuestionType {
    const val SINGLE_CHOICE = "single_choice"
    const val MULTIPLE_SELECT = "multiple_select"
    const val TRUE_FALSE = "true_false"
    const val SHORT_ANSWER = "short_answer"
    const val WRITTEN = "written"
    const val IMAGE_BASED = "image_based"
    val all = setOf(SINGLE_CHOICE, MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER, WRITTEN, IMAGE_BASED)
}

object Difficulty {
    const val EASY = "easy"
    const val MEDIUM = "medium"
    const val HARD = "hard"
    val all = setOf(EASY, MEDIUM, HARD)
}

object QuestionStatus {
    const val PUBLISHED = "published"
}

/**
 * Nothing in these learner-facing types can carry a correct answer, an answer key or an explanation.
 * That is deliberate: the leak is impossible by construction, not by remembering to blank a field.
 * Fields without defaults are always present in the JSON (the server omits default values).
 */
@Serializable
data class QuestionSummary(
    val id: String,
    val lessonId: String,
    val type: String,
    val text: String,
    val textBn: String? = null,
    val difficulty: String,
    val marks: Double,
    val negativeMarks: Double,
    val tags: List<String>,
    val hasImage: Boolean,
)

@Serializable
data class OptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class QuestionImageDto(
    val id: String,
    val kind: String,
    val url: String,
    val altText: String? = null,
    val altTextBn: String? = null,
)

@Serializable
data class QuestionDetail(
    val question: QuestionSummary,
    /** Empty for short_answer and written questions. Listed in display order. */
    val options: List<OptionDto>,
    val images: List<QuestionImageDto>,
    val breadcrumb: List<Crumb>,
    val version: Int,
)

@Serializable
data class TagDto(val slug: String, val name: String, val nameBn: String? = null, val questionCount: Int)

data class QuestionFilter(
    val subjectId: java.util.UUID? = null,
    val categoryId: java.util.UUID? = null,
    val subcategoryId: java.util.UUID? = null,
    val lessonId: java.util.UUID? = null,
    val type: String? = null,
    val difficulty: String? = null,
    val tag: String? = null,
    val search: String? = null,
    val sort: String = SORT_ORDER,
) {
    companion object {
        const val SORT_ORDER = "order"
        const val SORT_NEWEST = "newest"
        const val SORT_DIFFICULTY = "difficulty"
        val sorts = setOf(SORT_ORDER, SORT_NEWEST, SORT_DIFFICULTY)
    }
}

// ---- Step 14b: bookmarks and reports

object ReportReason {
    const val WRONG_ANSWER = "wrong_answer"
    const val UNCLEAR = "unclear"
    const val OFFENSIVE = "offensive"
    const val TYPO = "typo"
    const val DUPLICATE = "duplicate"
    const val OTHER = "other"
    val all = setOf(WRONG_ANSWER, UNCLEAR, OFFENSIVE, TYPO, DUPLICATE, OTHER)
}

@Serializable
data class ReportQuestionRequest(
    val reason: String,
    val details: String = "",
)

@Serializable
data class QuestionReportDto(
    val id: String,
    val questionId: String,
    val reason: String,
    val details: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class QuestionBookmarkItem(
    val question: QuestionSummary,
    val bookmarkedAt: String,
)
