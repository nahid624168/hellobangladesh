package com.exambase.core.network

import kotlinx.serialization.Serializable

/**
 * Mirrors backend/quizzes (QuizModels.kt, Step 15). Unknown fields are ignored.
 * A running attempt carries NO correct answers or explanations; they only arrive inside [QuizQuestionResult.review]
 * after the attempt is finished, and only when the set shows explanations.
 */
object QuizStatus {
    const val DRAFT = "draft"
    const val PRIVATE = "private"
    const val ARCHIVED = "archived"
}

object AttemptStatus {
    const val IN_PROGRESS = "in_progress"
    const val SUBMITTED = "submitted"
    const val EXPIRED = "expired"
}

object QuizSort {
    const val RECENT = "recent"
    const val TITLE = "title"
    const val PLAYED = "played"
}

object QuizAnswerResult {
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"
    const val UNANSWERED = "unanswered"
    const val UNGRADED = "ungraded"
}

/** Error codes the quiz endpoints add (all HTTP 409, plus 429 rate_limited). */
object QuizErrorCodes {
    const val NOT_PLAYABLE = "not_playable"
    const val EMPTY_QUIZ = "empty_quiz"
    const val QUIZ_UNAVAILABLE = "quiz_unavailable"
    const val ATTEMPT_LIMIT_REACHED = "attempt_limit_reached"
    const val ATTEMPT_EXPIRED = "attempt_expired"
}

@Serializable
data class QuizSettings(
    val timeLimitSeconds: Int? = null,
    val passingPercent: Int? = null,
    val maxAttempts: Int? = null,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val showExplanations: Boolean = true,
)

@Serializable
data class QuizSet(
    val id: String,
    val title: String,
    val description: String? = null,
    val lessonId: String? = null,
    val status: String,
    val settings: QuizSettings = QuizSettings(),
    val questionCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** One card of the quiz list. [bestPercent] is the best finished attempt, null before the first one. */
@Serializable
data class QuizSetListItem(
    val quizSet: QuizSet,
    val attemptCount: Int = 0,
    val lastAttemptAt: String? = null,
    val bestPercent: Double? = null,
)

@Serializable
data class QuizSetDetail(val quizSet: QuizSet, val questionIds: List<String> = emptyList())

/** A question as served during an attempt. It cannot carry a correct answer or explanation. */
@Serializable
data class QuizPlayQuestion(
    val question: QuestionSummary,
    val options: List<OptionDto> = emptyList(),
    val images: List<QuestionImageDto> = emptyList(),
)

@Serializable
data class QuizReview(
    val correctOptionIds: List<String> = emptyList(),
    val acceptedAnswers: List<String> = emptyList(),
    val explanation: String = "",
    val explanationBn: String? = null,
    val explanationImages: List<QuestionImageDto> = emptyList(),
)

@Serializable
data class QuizQuestionResult(
    val position: Int,
    val questionId: String,
    val type: String,
    val text: String,
    val textBn: String? = null,
    val result: String,
    val awardedMarks: Double = 0.0,
    val selectedOptionIds: List<String> = emptyList(),
    val answerText: String? = null,
    /** Null when the quiz set hides explanations. */
    val review: QuizReview? = null,
)

@Serializable
data class QuizResult(
    val attemptId: String,
    val quizSetId: String,
    val status: String,
    val startedAt: String,
    val submittedAt: String? = null,
    val score: Double = 0.0,
    val maxScore: Double = 0.0,
    val percent: Double = 0.0,
    /** Null when no passing score is set or nothing was gradable. */
    val passed: Boolean? = null,
    val questions: List<QuizQuestionResult> = emptyList(),
)

/** In progress: [questions] are filled and [result] is null. Finished: [questions] is empty and [result] is filled. */
@Serializable
data class QuizAttemptView(
    val attemptId: String,
    val quizSetId: String,
    val status: String,
    val startedAt: String,
    val deadlineAt: String? = null,
    val serverNow: String,
    val questions: List<QuizPlayQuestion> = emptyList(),
    val result: QuizResult? = null,
)

@Serializable
data class QuizAttemptSummary(
    val attemptId: String,
    val status: String,
    val startedAt: String,
    val submittedAt: String? = null,
    val score: Double? = null,
    val maxScore: Double? = null,
    val percent: Double? = null,
    val passed: Boolean? = null,
)

@Serializable
data class QuizAnswerRequest(
    val questionId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val answerText: String? = null,
)

@Serializable
data class SubmitQuizRequest(val answers: List<QuizAnswerRequest> = emptyList())

// ---- Management requests (Step 17)
@Serializable
data class CreateQuizSetRequest(
    val title: String,
    val description: String? = null,
    val lessonId: String? = null,
    val status: String? = null,
    val settings: QuizSettings? = null,
)

/** Omitted fields keep their value. Send description "" to clear it; clearLesson true to unlink the lesson. */
@Serializable
data class UpdateQuizSetRequest(
    val title: String? = null,
    val description: String? = null,
    val lessonId: String? = null,
    val clearLesson: Boolean = false,
    val status: String? = null,
    val settings: QuizSettings? = null,
)

@Serializable
data class SetQuestionsRequest(val questionIds: List<String> = emptyList())
