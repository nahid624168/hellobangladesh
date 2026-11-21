package com.exambase.backend.questions

import kotlinx.serialization.Serializable
import java.util.UUID

object CheckResult {
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"

    /** Written questions are not auto-graded: the learner compares with the model answer. */
    const val UNGRADED = "ungraded"
}

@Serializable
data class CheckAnswerRequest(val selectedOptionIds: List<String> = emptyList(), val answerText: String? = null)

/**
 * Returned only AFTER an answer was submitted. This is the one place a correct answer and an explanation leave the server.
 * awardedMarks is a practice figure: +marks when correct, -negativeMarks when incorrect, 0 when ungraded.
 */
@Serializable
data class CheckAnswerResponse(
    val attemptId: String,
    val result: String,
    val awardedMarks: Double,
    val correctOptionIds: List<String>,
    val acceptedAnswers: List<String>,
    val explanation: String,
    val explanationBn: String? = null,
    val explanationImages: List<QuestionImageDto>,
    val version: Int,
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
    val awardedMarks: Double,
    val createdAt: String,
)

/** attemptedQuestions counts distinct questions; correctQuestions counts those whose LATEST attempt is correct. */
@Serializable
data class PracticeSummary(val lessonId: String, val totalQuestions: Int, val attemptedQuestions: Int, val correctQuestions: Int)

/** Server-side only. Never serialized. */
class AnswerKey(
    val questionId: UUID,
    val lessonId: UUID,
    val type: String,
    val version: Int,
    val marks: Double,
    val negativeMarks: Double,
    val optionIds: Set<UUID>,
    val correctOptionIds: Set<UUID>,
    val acceptedAnswers: List<String>,
    val explanation: String,
    val explanationBn: String?,
    val explanationImages: List<QuestionImageDto>,
)

class Submission(val selected: List<UUID>, val text: String?)

class Graded(val result: String, val awardedMarks: Double)
