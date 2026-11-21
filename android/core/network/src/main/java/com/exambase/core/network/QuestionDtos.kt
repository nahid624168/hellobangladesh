package com.exambase.core.network

import kotlinx.serialization.Serializable

/**
 * Mirrors backend/questions (QuestionModels.kt, PracticeModels.kt). Unknown fields are ignored.
 * The question list and detail carry NO correct answers; they only arrive in [CheckAnswerResponse], after submitting.
 */
object QuestionTypes {
    const val SINGLE_CHOICE = "single_choice"
    const val MULTIPLE_SELECT = "multiple_select"
    const val TRUE_FALSE = "true_false"
    const val SHORT_ANSWER = "short_answer"
    const val WRITTEN = "written"
    const val IMAGE_BASED = "image_based"
}

object CheckResults {
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"
    const val UNGRADED = "ungraded"
}

@Serializable
data class QuestionSummary(
    val id: String,
    val lessonId: String,
    val type: String,
    val text: String,
    val textBn: String? = null,
    val difficulty: String = "medium",
    val marks: Double = 1.0,
    val negativeMarks: Double = 0.0,
    val tags: List<String> = emptyList(),
    val hasImage: Boolean = false,
)

@Serializable data class OptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class QuestionImageDto(val id: String, val kind: String = "image", val url: String, val altText: String? = null, val altTextBn: String? = null)

@Serializable
data class QuestionDetail(
    val question: QuestionSummary,
    val options: List<OptionDto> = emptyList(),
    val images: List<QuestionImageDto> = emptyList(),
    val breadcrumb: List<Crumb> = emptyList(),
    val version: Int = 1,
)

@Serializable
data class CheckAnswerRequest(val selectedOptionIds: List<String> = emptyList(), val answerText: String? = null)

@Serializable
data class CheckAnswerResponse(
    val attemptId: String,
    val result: String,
    val awardedMarks: Double = 0.0,
    val correctOptionIds: List<String> = emptyList(),
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String = "",
    val explanationBn: String? = null,
    val explanationImages: List<QuestionImageDto> = emptyList(),
    val version: Int = 1,
)

@Serializable
data class PracticeSummary(val lessonId: String, val totalQuestions: Int = 0, val attemptedQuestions: Int = 0, val correctQuestions: Int = 0)

// Step 14b
@Serializable
data class ReportQuestionRequest(val reason: String, val details: String = "")

@Serializable
data class QuestionReportDto(
    val id: String,
    val questionId: String,
    val reason: String,
    val details: String = "",
    val status: String = "open",
    val createdAt: String = "",
)

@Serializable
data class QuestionBookmarkItem(
    val question: QuestionSummary,
    val bookmarkedAt: String = "",
)

@Serializable
data class PracticeAttemptDto(
    val attemptId: String,
    val questionId: String,
    val lessonId: String,
    val type: String,
    val text: String,
    val textBn: String? = null,
    val result: String,
    val awardedMarks: Double = 0.0,
    val createdAt: String = "",
)

object ReportReason {
    const val WRONG_ANSWER = "wrong_answer"
    const val UNCLEAR = "unclear"
    const val OFFENSIVE = "offensive"
    const val TYPO = "typo"
    const val DUPLICATE = "duplicate"
    const val OTHER = "other"
}
