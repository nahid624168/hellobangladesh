package com.exambase.backend.exams

import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.QuestionType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryExamRepository : ExamRepository {
    val exams = ConcurrentHashMap<UUID, ExamRecord>()
    val sections = ConcurrentHashMap<UUID, MutableList<SectionRecord>>()
    val questions = ConcurrentHashMap<UUID, MutableList<SectionQuestionRow>>()
    val attempts = ConcurrentHashMap<UUID, AttemptRecord>()
    val answers = ConcurrentHashMap<UUID, MutableMap<UUID, ExamAnswerRow>>()
    private val audit = mutableListOf<Triple<Long, UUID, Triple<String, String, Instant>>>()
    private val auditSeq = AtomicLong(0)

    fun seedMcq(examId: UUID, sectionId: UUID, position: Int, marks: Double = 1.0): UUID {
        val qid = UUID.randomUUID()
        val optA = UUID.randomUUID()
        val optB = UUID.randomUUID()
        val key = AnswerKey(
            qid, UUID.randomUUID(), QuestionType.SINGLE_CHOICE, 1, marks, 0.0,
            setOf(optA, optB), setOf(optA), emptyList(), "", null, emptyList(),
        )
        val list = questions.getOrPut(examId) { mutableListOf() }
        list += SectionQuestionRow(
            sectionId, qid, position, null, key, "Q$position", null,
            listOf(optA to ("A" to null), optB to ("B" to null)),
        )
        return qid
    }

    override fun listForLearner(userId: UUID, limit: Int, offset: Int): List<Triple<ExamRecord, Int, AttemptRecord?>> {
        val now = Instant.now()
        return exams.values
            .filter { it.status != ExamStatus.DRAFT && it.status != ExamStatus.ARCHIVED }
            .sortedByDescending { it.opensAt }
            .drop(offset).take(limit)
            .map { e ->
                val derived = derive(e, now)
                Triple(e.copy(status = derived), attemptsUsed(e.id, userId), latestAttempt(e.id, userId))
            }
    }

    override fun findExam(id: UUID) = exams[id]
    override fun listSections(examId: UUID) = sections[examId]?.sortedBy { it.position } ?: emptyList()
    override fun listQuestions(examId: UUID) = questions[examId]?.sortedBy { it.position } ?: emptyList()
    override fun attemptsUsed(examId: UUID, userId: UUID) =
        attempts.values.count { it.examId == examId && it.userId == userId && it.status != AttemptStatus.VOID }
    override fun latestAttempt(examId: UUID, userId: UUID) =
        attempts.values.filter { it.examId == examId && it.userId == userId }.maxByOrNull { it.attemptNumber }
    override fun findAttempt(id: UUID) = attempts[id]

    override fun createExam(record: ExamRecord, sectionDefs: List<Pair<SectionRecord, List<UUID>>>): ExamRecord {
        exams[record.id] = record
        val secList = sections.getOrPut(record.id) { mutableListOf() }
        sectionDefs.forEach { (sec, qids) ->
            secList += sec
            qids.forEachIndexed { i, qid ->
                // minimal placeholder if not seeded
                if (questions[record.id]?.none { it.questionId == qid } != false) {
                    seedMcq(record.id, sec.id, i + 1)
                }
            }
        }
        return record
    }

    override fun startAttempt(examId: UUID, userId: UUID, attemptNumber: Int, startedAt: Instant, deadlineAt: Instant): AttemptRecord {
        val a = AttemptRecord(
            UUID.randomUUID(), examId, userId, attemptNumber, AttemptStatus.IN_PROGRESS,
            startedAt, deadlineAt, null, null, null, null, null,
        )
        attempts[a.id] = a
        return a
    }

    override fun upsertAnswer(attemptId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant): Instant {
        val map = answers.getOrPut(attemptId) { ConcurrentHashMap() }
        map[questionId] = ExamAnswerRow(questionId, selected, text, clientKey, null, null)
        return now
    }

    override fun listAnswers(attemptId: UUID) = answers[attemptId]?.values?.toList() ?: emptyList()

    override fun submitAttempt(
        attemptId: UUID, status: String, submittedAt: Instant,
        score: Double, maxScore: Double, percent: Double, passed: Boolean?, graded: List<ExamAnswerRow>,
    ): AttemptRecord {
        val a = attempts[attemptId]!!.copy(
            status = status, submittedAt = submittedAt, score = score, maxScore = maxScore, percent = percent, passed = passed,
        )
        attempts[attemptId] = a
        val map = answers.getOrPut(attemptId) { ConcurrentHashMap() }
        graded.forEach { map[it.questionId] = it }
        return a
    }

    override fun expireIfNeeded(attemptId: UUID, now: Instant): AttemptRecord? {
        val a = attempts[attemptId] ?: return null
        if (a.status != AttemptStatus.IN_PROGRESS) return null
        if (now.isBefore(a.deadlineAt)) return null
        val expired = a.copy(status = AttemptStatus.EXPIRED, submittedAt = now, score = 0.0, maxScore = 0.0, percent = 0.0)
        attempts[attemptId] = expired
        return expired
    }

    override fun audit(examId: UUID, attemptId: UUID?, userId: UUID?, eventType: String, detailJson: String) {
        audit += Triple(auditSeq.incrementAndGet(), examId, Triple(eventType, detailJson, Instant.now()))
    }

    override fun listAudit(examId: UUID, limit: Int) =
        audit.filter { it.second == examId }.takeLast(limit).map { Triple(it.first, it.third.first, it.third.second to it.third.third) }

    private fun derive(e: ExamRecord, now: Instant) = when {
        now.isBefore(e.opensAt) -> ExamStatus.SCHEDULED
        !now.isBefore(e.closesAt) -> ExamStatus.CLOSED
        else -> ExamStatus.OPEN
    }
}
