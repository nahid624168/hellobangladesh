package com.exambase.backend.contests

import com.exambase.backend.ApiException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContestServiceTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun service(repo: InMemoryContestRepository = InMemoryContestRepository()) =
        ContestService(repo, clock = clock) to repo

    private fun scheduledContest(
        repo: InMemoryContestRepository,
        starts: Instant = now.minusSeconds(60),
        ends: Instant = now.plusSeconds(3600),
        capacity: Int? = null,
        duration: Int = 600,
        maxAttempts: Int = 1,
        tieBreak: String = TieBreak.EARLIEST_SUBMIT,
    ): ContestRecord {
        val id = UUID.randomUUID()
        val optA = UUID.randomUUID()
        val optB = UUID.randomUUID()
        val q1 = UUID.randomUUID()
        val record = ContestRecord(
            id = id, title = "Weekly Math", titleBn = null, description = "desc", descriptionBn = null,
            status = ContestStatus.SCHEDULED, visibility = "public",
            startsAt = starts, endsAt = ends, joinOpensAt = null, joinClosesAt = null,
            capacity = capacity, durationSeconds = duration,
            shuffleQuestions = false, shuffleOptions = false, showLeaderboard = true,
            showResultsAt = null, tieBreak = tieBreak, maxAttempts = maxAttempts,
            subjectId = null, createdBy = null,
        )
        repo.contests[id] = record
        repo.seedQuestion(id, q1, 1, correct = setOf(optA), options = listOf(optA, optB), marks = 2.0)
        return record
    }

    @Test
    fun joinAndStartAndSubmitCorrectAnswer() {
        val (svc, repo) = service()
        val c = scheduledContest(repo)
        val user = UUID.randomUUID()
        val joined = svc.join(user, c.id.toString())
        assertEquals(ParticipantStatus.JOINED, joined.participant.status)

        val started = svc.start(user, c.id.toString())
        assertEquals(ParticipantStatus.IN_PROGRESS, started.participant.status)
        assertEquals(1, started.questions.size)

        val qid = started.questions[0].id
        val correctOpt = repo.listQuestions(c.id)[0].key.correctOptionIds.first().toString()
        svc.saveAnswer(user, c.id.toString(), SaveAnswerRequest(qid, listOf(correctOpt), clientKey = "k1"))
        // Idempotent re-save
        svc.saveAnswer(user, c.id.toString(), SaveAnswerRequest(qid, listOf(correctOpt), clientKey = "k1"))

        val result = svc.submit(user, c.id.toString())
        assertEquals(ParticipantStatus.SUBMITTED, result.participant.status)
        assertEquals(2.0, result.participant.score)
        assertEquals(100.0, result.participant.percent)
        assertNotNull(result.participant.rank)
    }

    @Test
    fun capacityEnforced() {
        val (svc, repo) = service()
        val c = scheduledContest(repo, capacity = 1)
        svc.join(UUID.randomUUID(), c.id.toString())
        val ex = assertFailsWith<ApiException> { svc.join(UUID.randomUUID(), c.id.toString()) }
        assertEquals("contest_full", ex.code)
    }

    @Test
    fun cannotLeaveAfterStart() {
        val (svc, repo) = service()
        val c = scheduledContest(repo)
        val user = UUID.randomUUID()
        svc.join(user, c.id.toString())
        svc.start(user, c.id.toString())
        val ex = assertFailsWith<ApiException> { svc.leave(user, c.id.toString()) }
        assertEquals("contest_started", ex.code)
    }

    @Test
    fun tieBreakEarliestSubmit() {
        val (svc, repo) = service()
        val c = scheduledContest(repo, tieBreak = TieBreak.EARLIEST_SUBMIT)
        val u1 = UUID.randomUUID()
        val u2 = UUID.randomUUID()
        // Both join and start
        svc.join(u1, c.id.toString()); svc.start(u1, c.id.toString())
        svc.join(u2, c.id.toString()); svc.start(u2, c.id.toString())
        val qid = repo.listQuestions(c.id)[0].questionId.toString()
        val correct = repo.listQuestions(c.id)[0].key.correctOptionIds.first().toString()
        svc.saveAnswer(u1, c.id.toString(), SaveAnswerRequest(qid, listOf(correct)))
        svc.saveAnswer(u2, c.id.toString(), SaveAnswerRequest(qid, listOf(correct)))
        svc.submit(u1, c.id.toString())
        // Second submit at same fixed clock still ordered by first insert; rank should exist
        svc.submit(u2, c.id.toString())
        val board = svc.leaderboard(u1, c.id.toString(), "10", null)
        assertEquals(2, board.totalSubmitted)
        assertTrue(board.entries.isNotEmpty())
        assertEquals(100.0, board.entries[0].percent)
    }

    @Test
    fun maxAttemptsRespected() {
        val (svc, repo) = service()
        val c = scheduledContest(repo, maxAttempts = 1)
        val user = UUID.randomUUID()
        svc.join(user, c.id.toString())
        svc.start(user, c.id.toString())
        svc.submit(user, c.id.toString())
        val ex = assertFailsWith<ApiException> { svc.join(user, c.id.toString()) }
        assertEquals("max_attempts", ex.code)
    }
}
