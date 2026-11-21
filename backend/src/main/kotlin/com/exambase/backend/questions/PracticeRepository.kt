package com.exambase.backend.questions

import com.exambase.backend.academic.Page
import java.util.UUID

/**
 * Practice side of the question bank. Same visibility rule as [QuestionRepository]: a hidden question behaves as missing,
 * and history for a question that later became hidden is not returned.
 */
interface PracticeRepository {
    /** Null when the question is missing or not visible. This is the only method that reads correct answers. */
    fun answerKey(id: UUID): AnswerKey?

    fun recordAttempt(userId: UUID, key: AnswerKey, submission: Submission, graded: Graded): UUID

    fun history(userId: UUID, lessonId: UUID?, questionId: UUID?, limit: Int, offset: Int): Page<PracticeAttemptDto>

    fun summary(userId: UUID, lessonId: UUID): PracticeSummary
}
