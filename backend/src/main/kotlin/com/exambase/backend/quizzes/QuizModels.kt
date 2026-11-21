package com.exambase.backend.quizzes

import com.exambase.backend.questions.OptionDto
import com.exambase.backend.questions.QuestionImageDto
import com.exambase.backend.questions.QuestionSummary
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

object QuizStatus {
    const val DRAFT = "draft"
    const val PRIVATE = "private"
    const val ARCHIVED = "archived"
    val all = setOf(DRAFT, PRIVATE, ARCHIVED)
}

object AttemptStatus {
    const val IN_PROGRESS = "in_progress"
    const val SUBMITTED = "submitted"
    const val EXPIRED = "expired"
}

object AnswerResult {
    /** The learner left the question blank. Scores 0 (never negative). */
    const val UNANSWERED = "unanswered"
}

@Serializable
data class QuizSettingsDto(
    val timeLimitSeconds: Int? = null,
    val passingPercent: Int? = null,
    val maxAttempts: Int? = null,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val showExplanations: Boolean = true,
)

// ---- Requests
@Serializable
data class CreateQuizSetRequest(
    val title: String,
    val description: String? = null,
    val lessonId: String? = null,
    val status: String? = null,
    val settings: QuizSettingsDto? = null,
)

/** Omitted fields keep their value. Send description "" to clear it, clearLesson true to unlink the lesson. */
@Serializable
data class UpdateQuizSetRequest(
    val title: String? = null,
    val description: String? = null,
    val lessonId: String? = null,
    val clearLesson: Boolean = false,
    val status: String? = null,
    val settings: QuizSettingsDto? = null,
)

@Serializable
data class SetQuestionsRequest(val questionIds: List<String> = emptyList())

@Serializable
data class AttemptAnswerRequest(
    val questionId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val answerText: String? = null,
)

@Serializable
data class SubmitAttemptRequest(val answers: List<AttemptAnswerRequest> = emptyList())

// ---- Responses (fields without defaults are always present in the JSON)
@Serializable
data class QuizSetDto(
    val id: String,
    val title: String,
    val description: String?,
    val lessonId: String?,
    val status: String,
    val settings: QuizSettingsDto,
    val questionCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

/** One card in My Quiz Sets. bestPercent is the best finished attempt, or null before the first one. */
@Serializable
data class QuizSetListItem(val quizSet: QuizSetDto, val attemptCount: Int, val lastAttemptAt: String?, val bestPercent: Double?)

@Serializable
data class QuizSetDetail(val quizSet: QuizSetDto, val questionIds: List<String>)

/** A question exactly as served to a learner during an attempt. It cannot carry a correct answer or explanation. */
@Serializable
data class QuizPlayQuestion(val question: QuestionSummary, val options: List<OptionDto>, val images: List<QuestionImageDto>)

@Serializable
data class QuizReview(
    val correctOptionIds: List<String>,
    val acceptedAnswers: List<String>,
    val explanation: String,
    val explanationBn: String?,
    val explanationImages: List<QuestionImageDto>,
)

@Serializable
data class QuizQuestionResult(
    val position: Int,
    val questionId: String,
    val type: String,
    val text: String,
    val textBn: String?,
    val result: String,
    val awardedMarks: Double,
    val selectedOptionIds: List<String>,
    val answerText: String?,
    /** Null when the quiz set hides explanations. */
    val review: QuizReview?,
)

@Serializable
data class QuizResultDto(
    val attemptId: String,
    val quizSetId: String,
    val status: String,
    val startedAt: String,
    val submittedAt: String?,
    val score: Double,
    val maxScore: Double,
    val percent: Double,
    /** Null when no passing score is set or nothing was gradable. */
    val passed: Boolean?,
    val questions: List<QuizQuestionResult>,
)

/** In progress: questions are filled and result is null. Finished: questions is empty and result is filled. */
@Serializable
data class QuizAttemptView(
    val attemptId: String,
    val quizSetId: String,
    val status: String,
    val startedAt: String,
    val deadlineAt: String?,
    val serverNow: String,
    val questions: List<QuizPlayQuestion>,
    val result: QuizResultDto?,
)

@Serializable
data class QuizAttemptSummaryDto(
    val attemptId: String,
    val status: String,
    val startedAt: String,
    val submittedAt: String?,
    val score: Double?,
    val maxScore: Double?,
    val percent: Double?,
    val passed: Boolean?,
)

// ---- Server-side only
class QuizSetDraft(
    val title: String,
    val description: String?,
    val lessonId: UUID?,
    val status: String,
    val settings: QuizSettingsDto,
)

class QuizSetFilter(val search: String?, val status: String?, val lessonId: UUID?, val sort: String) {
    companion object {
        const val SORT_RECENT = "recent"
        const val SORT_TITLE = "title"
        const val SORT_PLAYED = "played"
        val sorts = setOf(SORT_RECENT, SORT_TITLE, SORT_PLAYED)
    }
}

data class AttemptRecord(
    val id: UUID,
    val userId: UUID,
    val quizSetId: UUID,
    val status: String,
    val startedAt: Instant,
    val deadlineAt: Instant?,
    val submittedAt: Instant?,
    val score: Double?,
    val maxScore: Double?,
    val percent: Double?,
    val passed: Boolean?,
    val passingPercent: Int?,
    val showExplanations: Boolean,
    val questions: List<QuizPlayQuestion>,
)

class AttemptDraft(
    val userId: UUID,
    val quizSetId: UUID,
    val startedAt: Instant,
    val deadlineAt: Instant?,
    val passingPercent: Int?,
    val showExplanations: Boolean,
    val questions: List<QuizPlayQuestion>,
)

class AttemptAnswerRow(
    val position: Int,
    val questionId: UUID,
    val selected: List<UUID>,
    val text: String?,
    val result: String,
    val awardedMarks: Double,
)

class AttemptOutcome(
    val status: String,
    val submittedAt: Instant,
    val score: Double,
    val maxScore: Double,
    val percent: Double,
    val passed: Boolean?,
    val answers: List<AttemptAnswerRow>,
)
