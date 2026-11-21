package com.exambase.backend.questions

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** Pure grading and input rules for practice. No I/O, so it is unit tested directly. */
object Grader {
    const val MAX_SHORT = 500
    const val MAX_WRITTEN = 5000

    /** Same folding the database uses for answer_normalized, done in Kotlin on both sides so they cannot disagree. */
    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ").lowercase()

    /** Turns the raw request into a checked [Submission] for this question type, or throws 400 naming the field. */
    fun validate(key: AnswerKey, request: CheckAnswerRequest): Submission {
        val ids = request.selectedOptionIds.map {
            runCatching { UUID.fromString(it.trim()) }.getOrNull() ?: bad("selectedOptionIds", "Each choice must be a valid id.")
        }
        if (ids.toSet().size != ids.size) bad("selectedOptionIds", "Do not repeat a choice.")
        if (!key.optionIds.containsAll(ids)) bad("selectedOptionIds", "A choice does not belong to this question.")
        val text = request.answerText?.trim()?.takeIf { it.isNotEmpty() }
        return when (key.type) {
            QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE, QuestionType.IMAGE_BASED -> {
                if (ids.size != 1) bad("selectedOptionIds", "Choose exactly one answer.")
                if (text != null) bad("answerText", "This question takes a choice, not text.")
                Submission(ids, null)
            }
            QuestionType.MULTIPLE_SELECT -> {
                if (ids.isEmpty()) bad("selectedOptionIds", "Choose at least one answer.")
                if (text != null) bad("answerText", "This question takes choices, not text.")
                Submission(ids, null)
            }
            QuestionType.SHORT_ANSWER -> {
                if (ids.isNotEmpty()) bad("selectedOptionIds", "This question takes text, not a choice.")
                if (text == null) bad("answerText", "Type your answer.")
                if (text.length > MAX_SHORT) bad("answerText", "At most $MAX_SHORT characters.")
                Submission(emptyList(), text)
            }
            else -> {
                if (ids.isNotEmpty()) bad("selectedOptionIds", "This question takes text, not a choice.")
                if (text == null) bad("answerText", "Write your answer.")
                if (text.length > MAX_WRITTEN) bad("answerText", "At most $MAX_WRITTEN characters.")
                Submission(emptyList(), text)
            }
        }
    }

    /** Multiple select is all-or-nothing: the chosen set must equal the correct set. */
    fun grade(key: AnswerKey, s: Submission): Graded {
        val correct = when (key.type) {
            QuestionType.WRITTEN -> return Graded(CheckResult.UNGRADED, 0.0)
            QuestionType.SHORT_ANSWER -> {
                val given = normalize(s.text.orEmpty())
                key.acceptedAnswers.any { normalize(it) == given }
            }
            else -> s.selected.toSet() == key.correctOptionIds
        }
        return if (correct) Graded(CheckResult.CORRECT, round2(key.marks)) else Graded(CheckResult.INCORRECT, round2(-key.negativeMarks))
    }

    /** The trailing + 0.0 turns -0.0 into 0.0 so a zero penalty never prints as "-0.0". */
    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0 + 0.0

    private fun bad(field: String, message: String): Nothing =
        throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid answer.", mapOf(field to message))
}
