package com.exambase.backend.contests

import com.exambase.backend.questions.AnswerKey
import java.time.Instant
import java.util.UUID

data class ContestRecord(
    val id: UUID,
    val title: String,
    val titleBn: String?,
    val description: String,
    val descriptionBn: String?,
    val status: String,
    val visibility: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val joinOpensAt: Instant?,
    val joinClosesAt: Instant?,
    val capacity: Int?,
    val durationSeconds: Int,
    val shuffleQuestions: Boolean,
    val shuffleOptions: Boolean,
    val showLeaderboard: Boolean,
    val showResultsAt: Instant?,
    val tieBreak: String,
    val maxAttempts: Int,
    val subjectId: UUID?,
    val createdBy: UUID?,
)

data class ParticipantRecord(
    val id: UUID,
    val contestId: UUID,
    val userId: UUID,
    val status: String,
    val attemptNumber: Int,
    val joinedAt: Instant,
    val startedAt: Instant?,
    val deadlineAt: Instant?,
    val submittedAt: Instant?,
    val score: Double?,
    val maxScore: Double?,
    val percent: Double?,
    val timeTakenMs: Long?,
)

data class ContestQuestionRow(
    val questionId: UUID,
    val position: Int,
    val marksOverride: Double?,
    val key: AnswerKey,
    val questionText: String,
    val questionTextBn: String?,
    val options: List<Pair<UUID, Pair<String, String?>>>, // id to (text, textBn)
)

data class AnswerRow(
    val questionId: UUID,
    val selected: List<String>,
    val textAnswer: String?,
    val clientKey: String?,
    val result: String?,
    val marksAwarded: Double?,
)

data class LeaderboardRow(
    val userId: UUID,
    val displayName: String,
    val score: Double,
    val percent: Double,
    val submittedAt: Instant?,
    val timeTakenMs: Long?,
)

interface ContestRepository {
    fun listForLearner(userId: UUID, statusFilter: String?, limit: Int, offset: Int): Pair<List<Pair<ContestRecord, Int /*count*/>>, Int /*joined flag via separate*/>
    fun listSummaries(userId: UUID, statusFilter: String?, limit: Int, offset: Int): List<Triple<ContestRecord, Int, ParticipantRecord?>>

    fun findContest(id: UUID): ContestRecord?
    fun participantCount(contestId: UUID): Int
    fun findParticipant(contestId: UUID, userId: UUID, attempt: Int? = null): ParticipantRecord?
    fun latestParticipant(contestId: UUID, userId: UUID): ParticipantRecord?

    fun createContest(record: ContestRecord, questionIds: List<UUID>): ContestRecord
    fun setStatus(contestId: UUID, status: String)

    fun join(contestId: UUID, userId: UUID, attemptNumber: Int, now: Instant): ParticipantRecord
    fun start(participantId: UUID, startedAt: Instant, deadlineAt: Instant): ParticipantRecord
    fun leave(contestId: UUID, userId: UUID): Boolean

    fun listQuestions(contestId: UUID): List<ContestQuestionRow>
    fun upsertAnswer(participantId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant): Instant
    fun listAnswers(participantId: UUID): List<AnswerRow>

    fun submit(
        participantId: UUID,
        status: String,
        submittedAt: Instant,
        score: Double,
        maxScore: Double,
        percent: Double,
        timeTakenMs: Long,
        graded: List<AnswerRow>,
    ): ParticipantRecord

    fun expireIfNeeded(participantId: UUID, now: Instant): ParticipantRecord?

    fun leaderboard(contestId: UUID, tieBreak: String, limit: Int, offset: Int): List<LeaderboardRow>
    fun leaderboardTotal(contestId: UUID): Int
    fun rankForUser(contestId: UUID, userId: UUID, tieBreak: String): Int?

    fun recordAbuse(contestId: UUID, userId: UUID, participantId: UUID?, reason: String)
}
