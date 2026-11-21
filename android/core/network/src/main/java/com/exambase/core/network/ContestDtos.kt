package com.exambase.core.network

import kotlinx.serialization.Serializable

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
data class ContestOptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class ContestQuestionDto(
    val id: String,
    val position: Int,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val marks: Double,
    val options: List<ContestOptionDto> = emptyList(),
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
data class ContestDetail(
    val contest: ContestSummary,
    val joinOpensAt: String? = null,
    val joinClosesAt: String? = null,
    val shuffleQuestions: Boolean = false,
    val showLeaderboard: Boolean = true,
    val showResultsAt: String? = null,
    val tieBreak: String = "earliest_submit",
    val maxAttempts: Int = 1,
    val subjectId: String? = null,
    val questions: List<ContestQuestionDto> = emptyList(),
    val participant: ParticipantDto? = null,
)

@Serializable
data class JoinContestResponse(val participant: ParticipantDto, val contest: ContestSummary)

@Serializable
data class StartContestResponse(val participant: ParticipantDto, val questions: List<ContestQuestionDto>)

@Serializable
data class SaveContestAnswerRequest(
    val questionId: String,
    val selected: List<String> = emptyList(),
    val text: String? = null,
    val clientKey: String? = null,
)

@Serializable
data class SaveContestAnswerResponse(val questionId: String, val updatedAt: String)

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
    val entries: List<LeaderboardEntry> = emptyList(),
    val me: LeaderboardEntry? = null,
    val totalSubmitted: Int = 0,
)

@Serializable
data class SubmitContestResponse(
    val participant: ParticipantDto,
    val leaderboard: LeaderboardPage? = null,
)
