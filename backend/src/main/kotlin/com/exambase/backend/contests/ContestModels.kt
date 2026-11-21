package com.exambase.backend.contests

import kotlinx.serialization.Serializable

object ContestStatus {
    const val DRAFT = "draft"
    const val SCHEDULED = "scheduled"
    const val LIVE = "live"
    const val ENDED = "ended"
    const val CANCELLED = "cancelled"
    val learnerVisible = setOf(SCHEDULED, LIVE, ENDED)
}

object ParticipantStatus {
    const val JOINED = "joined"
    const val IN_PROGRESS = "in_progress"
    const val SUBMITTED = "submitted"
    const val EXPIRED = "expired"
    const val DISQUALIFIED = "disqualified"
}

object TieBreak {
    const val EARLIEST_SUBMIT = "earliest_submit"
    const val FASTEST_TIME = "fastest_time"
    const val NONE = "none"
}

@Serializable
data class ContestSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val status: String,
    val startsAt: String,
    val endsAt: String,
    val durationSeconds: Int,
    val capacity: Int? = null,
    val participantCount: Int = 0,
    val joined: Boolean = false,
    val myStatus: String? = null,
)

@Serializable
data class ContestQuestionDto(
    val id: String,
    val position: Int,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val marks: Double,
    /** Options without isCorrect — only after results are published, correctness may appear elsewhere. */
    val options: List<ContestOptionDto> = emptyList(),
)

@Serializable
data class ContestOptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class ContestDetail(
    val contest: ContestSummary,
    val joinOpensAt: String? = null,
    val joinClosesAt: String? = null,
    val shuffleQuestions: Boolean = false,
    val showLeaderboard: Boolean = true,
    val showResultsAt: String? = null,
    val tieBreak: String = TieBreak.EARLIEST_SUBMIT,
    val maxAttempts: Int = 1,
    val subjectId: String? = null,
    /** Questions are only included once the participant has started (or results published). */
    val questions: List<ContestQuestionDto> = emptyList(),
    val participant: ParticipantDto? = null,
)

@Serializable
data class ParticipantDto(
    val id: String,
    val status: String,
    val attemptNumber: Int,
    val joinedAt: String,
    val startedAt: String? = null,
    val deadlineAt: String? = null,
    val submittedAt: String? = null,
    val score: Double? = null,
    val maxScore: Double? = null,
    val percent: Double? = null,
    val timeTakenMs: Long? = null,
    val rank: Int? = null,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val score: Double,
    val percent: Double,
    val submittedAt: String? = null,
    val timeTakenMs: Long? = null,
    val isMe: Boolean = false,
)

@Serializable
data class LeaderboardPage(
    val contestId: String,
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardEntry? = null,
    val totalSubmitted: Int = 0,
)

@Serializable
data class JoinContestResponse(val participant: ParticipantDto, val contest: ContestSummary)

@Serializable
data class StartContestResponse(val participant: ParticipantDto, val questions: List<ContestQuestionDto>)

@Serializable
data class SaveAnswerRequest(
    val questionId: String,
    val selected: List<String> = emptyList(),
    val text: String? = null,
    /** Optional client idempotency key for this write. */
    val clientKey: String? = null,
)

@Serializable
data class SaveAnswerResponse(val questionId: String, val updatedAt: String)

@Serializable
data class SubmitContestResponse(val participant: ParticipantDto, val leaderboard: LeaderboardPage? = null)

@Serializable
data class CreateContestRequest(
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val startsAt: String,
    val endsAt: String,
    val durationSeconds: Int,
    val capacity: Int? = null,
    val joinOpensAt: String? = null,
    val joinClosesAt: String? = null,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val showLeaderboard: Boolean = true,
    val showResultsAt: String? = null,
    val tieBreak: String = TieBreak.EARLIEST_SUBMIT,
    val maxAttempts: Int = 1,
    val subjectId: String? = null,
    val questionIds: List<String> = emptyList(),
    /** draft | scheduled — live/ended are derived from time or set by admin later. */
    val status: String = ContestStatus.SCHEDULED,
)
