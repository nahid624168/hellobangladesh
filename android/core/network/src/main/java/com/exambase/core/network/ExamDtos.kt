package com.exambase.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ExamSummary(
    val id: String,
    val title: String,
    val titleBn: String? = null,
    val status: String,
    val opensAt: String,
    val closesAt: String,
    val durationSeconds: Int,
    val maxAttempts: Int,
    val attemptsUsed: Int = 0,
    val myStatus: String? = null,
)

@Serializable
data class ExamSectionDto(
    val id: String,
    val position: Int,
    val title: String,
    val titleBn: String? = null,
    val instructions: String = "",
    val timeLimitSeconds: Int? = null,
    val questionCount: Int = 0,
)

@Serializable
data class ExamOptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class ExamQuestionDto(
    val id: String,
    val sectionId: String,
    val position: Int,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val marks: Double,
    val options: List<ExamOptionDto> = emptyList(),
)

@Serializable
data class ExamAttemptDto(
    val id: String,
    val status: String,
    val attemptNumber: Int,
    val startedAt: String,
    val deadlineAt: String,
    val submittedAt: String? = null,
    val score: Double? = null,
    val maxScore: Double? = null,
    val percent: Double? = null,
    val passed: Boolean? = null,
)

@Serializable
data class ExamDetail(
    val exam: ExamSummary,
    val instructions: String = "",
    val instructionsBn: String? = null,
    val requireEmailVerified: Boolean = true,
    val resultsPolicy: String = "after_close",
    val resultsVisible: Boolean = false,
    val passPercent: Int? = null,
    val sections: List<ExamSectionDto> = emptyList(),
    val attempt: ExamAttemptDto? = null,
)

@Serializable
data class StartExamResponse(
    val attempt: ExamAttemptDto,
    val sections: List<ExamSectionDto> = emptyList(),
    val questions: List<ExamQuestionDto> = emptyList(),
)

@Serializable
data class SaveExamAnswerRequest(
    val questionId: String,
    val selected: List<String> = emptyList(),
    val text: String? = null,
    val clientKey: String? = null,
)

@Serializable
data class SaveExamAnswerResponse(val questionId: String, val updatedAt: String)

@Serializable
data class ExamAnswerResultDto(
    val questionId: String,
    val result: String? = null,
    val marksAwarded: Double? = null,
)

@Serializable
data class ExamResultDto(
    val attempt: ExamAttemptDto,
    val resultsVisible: Boolean = false,
    val perQuestion: List<ExamAnswerResultDto> = emptyList(),
)
