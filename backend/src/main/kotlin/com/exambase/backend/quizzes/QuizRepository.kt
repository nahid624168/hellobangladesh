package com.exambase.backend.quizzes

import com.exambase.backend.academic.Page
import com.exambase.backend.questions.AnswerKey
import java.time.Instant
import java.util.UUID

/**
 * Every method that takes an owner or user id only ever sees that person's rows. A set or attempt that belongs to someone
 * else behaves exactly like a missing one (null / false), which is what the routes turn into 404.
 */
interface QuizRepository {
    fun create(ownerId: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto
    fun find(ownerId: UUID, id: UUID): QuizSetDto?
    fun list(ownerId: UUID, filter: QuizSetFilter, limit: Int, offset: Int): Page<QuizSetListItem>
    fun update(ownerId: UUID, id: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto?

    /** Soft delete: the set disappears everywhere, attempt history is kept. False when missing. */
    fun softDelete(ownerId: UUID, id: UUID, now: Instant): Boolean
    fun duplicate(ownerId: UUID, id: UUID, newTitle: String, now: Instant): QuizSetDto?

    /** True when the lesson and everything above it is published. */
    fun lessonVisible(lessonId: UUID): Boolean

    /** Replaces the whole ordered list. False when the set is missing. */
    fun setQuestions(ownerId: UUID, id: UUID, questionIds: List<UUID>, now: Instant): Boolean
    fun questionIds(quizSetId: UUID): List<UUID>

    fun inProgress(userId: UUID, quizSetId: UUID): AttemptRecord?

    /** Attempts that are submitted or expired. */
    fun finishedAttempts(userId: UUID, quizSetId: UUID): Int

    /** If another running attempt already exists for this user and set, returns that one instead of creating a second. */
    fun startAttempt(draft: AttemptDraft): AttemptRecord
    fun findAttempt(userId: UUID, attemptId: UUID): AttemptRecord?

    /** Only finishes an attempt that is still in progress. False means someone else finished it first; nothing changes. */
    fun finishAttempt(attemptId: UUID, outcome: AttemptOutcome): Boolean
    fun attemptAnswers(attemptId: UUID): List<AttemptAnswerRow>
    fun attempts(userId: UUID, quizSetId: UUID, limit: Int, offset: Int): Page<QuizAttemptSummaryDto>

    /** Answer keys regardless of visibility (an attempt in progress must still be markable). Missing questions are absent from the map. */
    fun answerKeys(questionIds: Collection<UUID>): Map<UUID, AnswerKey>
}
