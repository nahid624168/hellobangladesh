package com.exambase.backend.questions

import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.util.UUID

/** Test double with the same visibility and "latest attempt wins" rules as JdbcPracticeRepository. */
class InMemoryPracticeRepository(private val bank: InMemoryQuestionRepository) : PracticeRepository {
    class Attempt(val id: UUID, val userId: UUID, val q: InMemoryQuestionRepository.Q, val selected: List<UUID>, val text: String?, val graded: Graded, val seq: Int)

    val attempts = mutableListOf<Attempt>()

    override fun answerKey(id: UUID): AnswerKey? {
        val q = bank.questions.firstOrNull { it.id == id }?.takeIf(bank::visible) ?: return null
        return AnswerKey(
            q.id, q.lessonId, q.type, 1, q.marks, q.negative,
            q.options.map { it.id }.toSet(), q.options.filter { it.correct }.map { it.id }.toSet(), q.answerKeys,
            q.explanation, null,
            q.images.filter { it.placement == "explanation" }.map { QuestionImageDto(it.id.toString(), it.kind, it.url) },
        )
    }

    override fun recordAttempt(userId: UUID, key: AnswerKey, submission: Submission, graded: Graded): UUID {
        val q = bank.questions.first { it.id == key.questionId }
        return UUID.randomUUID().also { attempts += Attempt(it, userId, q, submission.selected, submission.text, graded, attempts.size) }
    }

    override fun history(userId: UUID, lessonId: UUID?, questionId: UUID?, limit: Int, offset: Int): Page<PracticeAttemptDto> {
        var rows = attempts.filter { it.userId == userId && bank.visible(it.q) }
        lessonId?.let { id -> rows = rows.filter { it.q.lessonId == id } }
        questionId?.let { id -> rows = rows.filter { it.q.id == id } }
        val dtos = rows.sortedByDescending { it.seq }.drop(offset).take(limit + 1).map {
            PracticeAttemptDto(
                it.id.toString(), it.q.id.toString(), it.q.lessonId.toString(), it.q.type, it.q.text, null,
                it.graded.result, it.graded.awardedMarks, "2026-01-01T00:00:00Z",
            )
        }
        return Paging.page(dtos, limit, offset)
    }

    override fun summary(userId: UUID, lessonId: UUID): PracticeSummary {
        val total = bank.questions.count { it.lessonId == lessonId && bank.visible(it) }
        val latest = attempts.filter { it.userId == userId && it.q.lessonId == lessonId && bank.visible(it.q) }
            .groupBy { it.q.id }.map { (_, v) -> v.maxByOrNull { it.seq }!! }
        return PracticeSummary(lessonId.toString(), total, latest.size, latest.count { it.graded.result == CheckResult.CORRECT })
    }
}
