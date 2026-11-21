package com.exambase.backend.quizzes

import com.exambase.backend.academic.InMemoryAcademicRepository
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.InMemoryQuestionRepository
import com.exambase.backend.questions.QuestionImageDto
import java.time.Instant
import java.util.UUID

/** Test double with the same ownership, soft-delete, one-running-attempt and "finish only once" rules as JdbcQuizRepository. */
class InMemoryQuizRepository(
    private val academic: InMemoryAcademicRepository,
    private val bank: InMemoryQuestionRepository,
) : QuizRepository {
    class S(val id: UUID, val ownerId: UUID, var draft: QuizSetDraft, val createdAt: Instant, var updatedAt: Instant) {
        var deleted = false
        var questions: List<UUID> = emptyList()
    }

    val sets = mutableListOf<S>()
    val attempts = mutableListOf<AttemptRecord>()
    private val answers = mutableMapOf<UUID, List<AttemptAnswerRow>>()

    private fun dto(s: S) = QuizSetDto(
        s.id.toString(), s.draft.title, s.draft.description, s.draft.lessonId?.toString(), s.draft.status,
        s.draft.settings, s.questions.size, s.createdAt.toString(), s.updatedAt.toString(),
    )

    private fun mine(ownerId: UUID, id: UUID) = sets.firstOrNull { it.id == id && it.ownerId == ownerId && !it.deleted }

    override fun create(ownerId: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto =
        S(UUID.randomUUID(), ownerId, draft, now, now).also { sets += it }.let(::dto)

    override fun find(ownerId: UUID, id: UUID) = mine(ownerId, id)?.let(::dto)

    override fun list(ownerId: UUID, filter: QuizSetFilter, limit: Int, offset: Int): Page<QuizSetListItem> {
        var rows = sets.filter { it.ownerId == ownerId && !it.deleted }
        rows = if (filter.status != null) rows.filter { it.draft.status == filter.status } else rows.filter { it.draft.status != QuizStatus.ARCHIVED }
        filter.lessonId?.let { id -> rows = rows.filter { it.draft.lessonId == id } }
        filter.search?.let { q -> rows = rows.filter { it.draft.title.contains(q, ignoreCase = true) } }
        fun finished(s: S) = attempts.filter { it.quizSetId == s.id && it.userId == ownerId && it.status != AttemptStatus.IN_PROGRESS }
        fun lastAt(s: S) = attempts.filter { it.quizSetId == s.id && it.userId == ownerId }.maxOfOrNull { it.startedAt }
        rows = when (filter.sort) {
            QuizSetFilter.SORT_TITLE -> rows.sortedBy { it.draft.title.lowercase() }
            QuizSetFilter.SORT_PLAYED -> rows.sortedWith(compareByDescending<S> { lastAt(it) ?: Instant.MIN }.thenByDescending { it.updatedAt })
            else -> rows.sortedByDescending { it.updatedAt }
        }
        val items = rows.drop(offset).take(limit + 1).map { s ->
            QuizSetListItem(dto(s), finished(s).size, lastAt(s)?.toString(), finished(s).mapNotNull { it.percent }.maxOrNull())
        }
        return Paging.page(items, limit, offset)
    }

    override fun update(ownerId: UUID, id: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto? =
        mine(ownerId, id)?.also { it.draft = draft; it.updatedAt = now }?.let(::dto)

    override fun softDelete(ownerId: UUID, id: UUID, now: Instant): Boolean = mine(ownerId, id)?.also { it.deleted = true } != null

    override fun duplicate(ownerId: UUID, id: UUID, newTitle: String, now: Instant): QuizSetDto? {
        val src = mine(ownerId, id) ?: return null
        val d = src.draft
        val copy = S(UUID.randomUUID(), ownerId, QuizSetDraft(newTitle, d.description, d.lessonId, QuizStatus.DRAFT, d.settings), now, now)
        copy.questions = src.questions
        sets += copy
        return dto(copy)
    }

    override fun lessonVisible(lessonId: UUID): Boolean {
        val l = academic.lessons.firstOrNull { it.id == lessonId } ?: return false
        val sc = academic.subcategories.first { it.id == l.subcategoryId }
        val c = academic.categories.first { it.id == sc.categoryId }
        val s = academic.subjects.first { it.id == c.subjectId }
        return listOf(l.status, sc.status, c.status, s.status).all { it == "published" }
    }

    override fun setQuestions(ownerId: UUID, id: UUID, questionIds: List<UUID>, now: Instant): Boolean =
        mine(ownerId, id)?.also { it.questions = questionIds; it.updatedAt = now } != null

    override fun questionIds(quizSetId: UUID): List<UUID> = sets.first { it.id == quizSetId }.questions

    override fun inProgress(userId: UUID, quizSetId: UUID) =
        attempts.firstOrNull { it.userId == userId && it.quizSetId == quizSetId && it.status == AttemptStatus.IN_PROGRESS }

    override fun finishedAttempts(userId: UUID, quizSetId: UUID) =
        attempts.count { it.userId == userId && it.quizSetId == quizSetId && it.status != AttemptStatus.IN_PROGRESS }

    override fun startAttempt(draft: AttemptDraft): AttemptRecord {
        inProgress(draft.userId, draft.quizSetId)?.let { return it }
        return AttemptRecord(
            UUID.randomUUID(), draft.userId, draft.quizSetId, AttemptStatus.IN_PROGRESS, draft.startedAt, draft.deadlineAt,
            null, null, null, null, null, draft.passingPercent, draft.showExplanations, draft.questions,
        ).also { attempts += it }
    }

    override fun findAttempt(userId: UUID, attemptId: UUID) = attempts.firstOrNull { it.id == attemptId && it.userId == userId }

    override fun finishAttempt(attemptId: UUID, outcome: AttemptOutcome): Boolean {
        val i = attempts.indexOfFirst { it.id == attemptId && it.status == AttemptStatus.IN_PROGRESS }
        if (i < 0) return false
        attempts[i] = attempts[i].copy(
            status = outcome.status, submittedAt = outcome.submittedAt, score = outcome.score, maxScore = outcome.maxScore,
            percent = outcome.percent, passed = outcome.passed,
        )
        answers[attemptId] = outcome.answers
        return true
    }

    override fun attemptAnswers(attemptId: UUID) = answers[attemptId].orEmpty()

    override fun attempts(userId: UUID, quizSetId: UUID, limit: Int, offset: Int): Page<QuizAttemptSummaryDto> {
        val rows = attempts.filter { it.userId == userId && it.quizSetId == quizSetId }.sortedByDescending { it.startedAt }
            .drop(offset).take(limit + 1).map {
                QuizAttemptSummaryDto(it.id.toString(), it.status, it.startedAt.toString(), it.submittedAt?.toString(), it.score, it.maxScore, it.percent, it.passed)
            }
        return Paging.page(rows, limit, offset)
    }

    /** Ignores visibility on purpose, like the JDBC version. */
    override fun answerKeys(questionIds: Collection<UUID>): Map<UUID, AnswerKey> =
        bank.questions.filter { it.id in questionIds }.associate { q ->
            q.id to AnswerKey(
                q.id, q.lessonId, q.type, 1, q.marks, q.negative,
                q.options.map { it.id }.toSet(), q.options.filter { it.correct }.map { it.id }.toSet(), q.answerKeys,
                q.explanation, null,
                q.images.filter { it.placement == "explanation" }.map { QuestionImageDto(it.id.toString(), it.kind, it.url) },
            )
        }
}
