package com.exambase.backend.exams

import com.exambase.backend.ApiException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExamServiceTest {
    private val now = Instant.parse("2026-07-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun openExam(repo: InMemoryExamRepository, resultsPolicy: String = ResultsPolicy.IMMEDIATE): ExamRecord {
        val id = UUID.randomUUID()
        val secId = UUID.randomUUID()
        val record = ExamRecord(
            id = id, title = "Midterm", titleBn = null, instructions = "Read carefully", instructionsBn = null,
            status = ExamStatus.SCHEDULED, visibility = "public",
            opensAt = now.minusSeconds(60), closesAt = now.plusSeconds(7200),
            durationSeconds = 1800, maxAttempts = 2, requireEmailVerified = false,
            subjectId = null, shuffleQuestions = false, shuffleOptions = false,
            resultsPolicy = resultsPolicy, resultsPublishedAt = null, passPercent = 50, createdBy = null,
        )
        repo.exams[id] = record
        repo.sections.getOrPut(id) { mutableListOf() } += SectionRecord(secId, id, 1, "Section A", null, "", null)
        repo.seedMcq(id, secId, 1, marks = 2.0)
        return record
    }

    @Test
    fun startSaveSubmitWithImmediateResults() {
        val repo = InMemoryExamRepository()
        val svc = ExamService(repo, emailVerified = { true }, clock = clock)
        val exam = openExam(repo)
        val user = UUID.randomUUID()
        val started = svc.start(user, exam.id.toString())
        assertEquals(AttemptStatus.IN_PROGRESS, started.attempt.status)
        assertEquals(1, started.questions.size)

        val q = started.questions[0]
        val correct = repo.listQuestions(exam.id)[0].key.correctOptionIds.first().toString()
        svc.saveAnswer(user, started.attempt.id, SaveExamAnswerRequest(q.id, listOf(correct), clientKey = "k1"))

        val result = svc.submit(user, started.attempt.id)
        assertEquals(AttemptStatus.SUBMITTED, result.attempt.status)
        assertTrue(result.resultsVisible)
        assertEquals(2.0, result.attempt.score)
        assertEquals(true, result.attempt.passed)
        assertTrue(result.perQuestion.isNotEmpty())
    }

    @Test
    fun resultsHiddenUntilClose() {
        val repo = InMemoryExamRepository()
        val svc = ExamService(repo, clock = clock)
        val exam = openExam(repo, resultsPolicy = ResultsPolicy.AFTER_CLOSE)
        val user = UUID.randomUUID()
        val started = svc.start(user, exam.id.toString())
        val result = svc.submit(user, started.attempt.id)
        assertFalse(result.resultsVisible)
        assertEquals(null, result.attempt.score)
    }

    @Test
    fun maxAttemptsAndResume() {
        val repo = InMemoryExamRepository()
        val svc = ExamService(repo, clock = clock)
        val exam = openExam(repo)
        val user = UUID.randomUUID()
        val first = svc.start(user, exam.id.toString())
        val resume = svc.start(user, exam.id.toString())
        assertEquals(first.attempt.id, resume.attempt.id)
        svc.submit(user, first.attempt.id)
        val second = svc.start(user, exam.id.toString())
        assertEquals(2, second.attempt.attemptNumber)
        svc.submit(user, second.attempt.id)
        val ex = assertFailsWith<ApiException> { svc.start(user, exam.id.toString()) }
        assertEquals("max_attempts", ex.code)
    }

    @Test
    fun emailVerifiedRequired() {
        val repo = InMemoryExamRepository()
        val svc = ExamService(repo, emailVerified = { false }, clock = clock)
        val exam = openExam(repo).let {
            val locked = it.copy(requireEmailVerified = true)
            repo.exams[it.id] = locked
            locked
        }
        val ex = assertFailsWith<ApiException> { svc.start(UUID.randomUUID(), exam.id.toString()) }
        assertEquals("email_not_verified", ex.code)
    }

    @Test
    fun auditTrailOnStartAndSubmit() {
        val repo = InMemoryExamRepository()
        val svc = ExamService(repo, clock = clock)
        val exam = openExam(repo)
        val user = UUID.randomUUID()
        val started = svc.start(user, exam.id.toString())
        svc.submit(user, started.attempt.id)
        val events = repo.listAudit(exam.id, 10).map { it.second }
        assertTrue(events.contains("attempt.started"))
        assertTrue(events.contains("attempt.submitted"))
    }
}
