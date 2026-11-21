package com.exambase.backend.contests

import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.QuestionType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryContestRepository : ContestRepository {
    val contests = ConcurrentHashMap<UUID, ContestRecord>()
    val questions = ConcurrentHashMap<UUID, MutableList<ContestQuestionRow>>()
    val participants = ConcurrentHashMap<UUID, ParticipantRecord>()
    val answers = ConcurrentHashMap<UUID, MutableMap<UUID, AnswerRow>>() // participant -> qid -> answer
    val displayNames = ConcurrentHashMap<UUID, String>()

    fun seedQuestion(
        contestId: UUID,
        questionId: UUID = UUID.randomUUID(),
        position: Int,
        correct: Set<UUID> = emptySet(),
        options: List<UUID> = correct.toList(),
        marks: Double = 1.0,
        type: String = QuestionType.SINGLE_CHOICE,
        text: String = "Q$position",
    ): UUID {
        val key = AnswerKey(
            questionId, UUID.randomUUID(), type, 1, marks, 0.0,
            options.toSet(), correct, emptyList(), "", null, emptyList(),
        )
        val optPairs = options.map { it to (it.toString().take(8) to null) }
        val list = questions.getOrPut(contestId) { mutableListOf() }
        list += ContestQuestionRow(questionId, position, null, key, text, null, optPairs)
        return questionId
    }

    override fun listSummaries(userId: UUID, statusFilter: String?, limit: Int, offset: Int): List<Triple<ContestRecord, Int, ParticipantRecord?>> {
        val now = Instant.now()
        return contests.values
            .filter { it.status != ContestStatus.DRAFT && it.status != ContestStatus.CANCELLED }
            .sortedBy { it.startsAt }
            .drop(offset).take(limit)
            .map { c ->
                val derived = derive(c, now)
                Triple(c.copy(status = derived), participantCount(c.id), latestParticipant(c.id, userId))
            }
    }

    override fun listForLearner(userId: UUID, statusFilter: String?, limit: Int, offset: Int) =
        listSummaries(userId, statusFilter, limit, offset).map { it.first to it.second } to 0

    override fun findContest(id: UUID) = contests[id]
    override fun participantCount(contestId: UUID) =
        participants.values.count { it.contestId == contestId && it.status != ParticipantStatus.DISQUALIFIED }

    override fun findParticipant(contestId: UUID, userId: UUID, attempt: Int?) =
        participants.values.filter { it.contestId == contestId && it.userId == userId }
            .let { list -> if (attempt != null) list.firstOrNull { it.attemptNumber == attempt } else list.maxByOrNull { it.attemptNumber } }

    override fun latestParticipant(contestId: UUID, userId: UUID) = findParticipant(contestId, userId, null)

    override fun createContest(record: ContestRecord, questionIds: List<UUID>): ContestRecord {
        contests[record.id] = record
        questionIds.forEachIndexed { i, qid -> seedQuestion(record.id, qid, i + 1) }
        return record
    }

    override fun setStatus(contestId: UUID, status: String) {
        contests.computeIfPresent(contestId) { _, c -> c.copy(status = status) }
    }

    override fun join(contestId: UUID, userId: UUID, attemptNumber: Int, now: Instant): ParticipantRecord {
        val p = ParticipantRecord(
            UUID.randomUUID(), contestId, userId, ParticipantStatus.JOINED, attemptNumber,
            now, null, null, null, null, null, null, null,
        )
        participants[p.id] = p
        displayNames.putIfAbsent(userId, "User-${userId.toString().take(8)}")
        return p
    }

    override fun start(participantId: UUID, startedAt: Instant, deadlineAt: Instant): ParticipantRecord {
        val p = participants[participantId]!!.copy(
            status = ParticipantStatus.IN_PROGRESS, startedAt = startedAt, deadlineAt = deadlineAt,
        )
        participants[participantId] = p
        return p
    }

    override fun leave(contestId: UUID, userId: UUID): Boolean {
        val p = latestParticipant(contestId, userId) ?: return false
        if (p.status != ParticipantStatus.JOINED) return false
        participants.remove(p.id)
        return true
    }

    override fun listQuestions(contestId: UUID) =
        questions[contestId]?.sortedBy { it.position } ?: emptyList()

    override fun upsertAnswer(
        participantId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant,
    ): Instant {
        val map = answers.getOrPut(participantId) { ConcurrentHashMap() }
        if (clientKey != null) {
            val existing = map.values.firstOrNull { it.clientKey == clientKey }
            if (existing != null && existing.questionId != questionId) {
                // ignore conflict for test simplicity
            }
        }
        map[questionId] = AnswerRow(questionId, selected, text, clientKey, null, null)
        return now
    }

    override fun listAnswers(participantId: UUID) =
        answers[participantId]?.values?.toList() ?: emptyList()

    override fun submit(
        participantId: UUID, status: String, submittedAt: Instant,
        score: Double, maxScore: Double, percent: Double, timeTakenMs: Long, graded: List<AnswerRow>,
    ): ParticipantRecord {
        val p = participants[participantId]!!.copy(
            status = status, submittedAt = submittedAt, score = score, maxScore = maxScore,
            percent = percent, timeTakenMs = timeTakenMs,
        )
        participants[participantId] = p
        val map = answers.getOrPut(participantId) { ConcurrentHashMap() }
        graded.forEach { map[it.questionId] = it }
        return p
    }

    override fun expireIfNeeded(participantId: UUID, now: Instant): ParticipantRecord? {
        val p = participants[participantId] ?: return null
        if (p.status != ParticipantStatus.IN_PROGRESS) return null
        val d = p.deadlineAt ?: return null
        if (now.isBefore(d)) return null
        val expired = p.copy(status = ParticipantStatus.EXPIRED, submittedAt = now, score = 0.0, maxScore = 0.0, percent = 0.0, timeTakenMs = 0)
        participants[participantId] = expired
        return expired
    }

    override fun leaderboard(contestId: UUID, tieBreak: String, limit: Int, offset: Int): List<LeaderboardRow> {
        val rows = participants.values.filter { it.contestId == contestId && it.status == ParticipantStatus.SUBMITTED }
            .sortedWith(rankComparator(tieBreak))
            .drop(offset).take(limit)
        return rows.map {
            LeaderboardRow(it.userId, displayNames[it.userId] ?: "User", it.score ?: 0.0, it.percent ?: 0.0, it.submittedAt, it.timeTakenMs)
        }
    }

    override fun leaderboardTotal(contestId: UUID) =
        participants.values.count { it.contestId == contestId && it.status == ParticipantStatus.SUBMITTED }

    override fun rankForUser(contestId: UUID, userId: UUID, tieBreak: String): Int? {
        val ordered = participants.values.filter { it.contestId == contestId && it.status == ParticipantStatus.SUBMITTED }
            .sortedWith(rankComparator(tieBreak))
        val idx = ordered.indexOfFirst { it.userId == userId }
        return if (idx >= 0) idx + 1 else null
    }

    override fun recordAbuse(contestId: UUID, userId: UUID, participantId: UUID?, reason: String) {}

    private fun rankComparator(tieBreak: String): Comparator<ParticipantRecord> =
        compareByDescending<ParticipantRecord> { it.score ?: 0.0 }
            .thenBy {
                when (tieBreak) {
                    TieBreak.FASTEST_TIME -> it.timeTakenMs ?: Long.MAX_VALUE
                    TieBreak.NONE -> 0L
                    else -> it.submittedAt?.toEpochMilli() ?: Long.MAX_VALUE
                }
            }

    private fun derive(c: ContestRecord, now: Instant) = when {
        now.isBefore(c.startsAt) -> ContestStatus.SCHEDULED
        !now.isBefore(c.endsAt) -> ContestStatus.ENDED
        else -> ContestStatus.LIVE
    }
}
