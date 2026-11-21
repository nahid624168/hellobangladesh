package com.exambase.backend.exams

import kotlinx.serialization.Serializable

object ExamStatus {
    const val DRAFT = "draft"
    const val SCHEDULED = "scheduled"
    const val OPEN = "open"
    const val CLOSED = "closed"
    const val ARCHIVED = "archived"
}

object AttemptStatus {
    const val IN_PROGRESS = "in_progress"
    const val SUBMITTED = "submitted"
    const val EXPIRED = "expired"
    const val VOID = "void"
}

object ResultsPolicy {
    const val IMMEDIATE = "immediate"
    const val AFTER_CLOSE = "after_close"
    const val MANUAL = "manual"
}

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
data class ExamOptionDto(val id: String, val text: String, val textBn: String? = null)

@Serializable
data class ExamDetail(
    val exam: ExamSummary,
    val instructions: String = "",
    val instructionsBn: String? = null,
    val requireEmailVerified: Boolean = true,
    val resultsPolicy: String = ResultsPolicy.AFTER_CLOSE,
    val resultsVisible: Boolean = false,
    val passPercent: Int? = null,
    val sections: List<ExamSectionDto> = emptyList(),
    val attempt: ExamAttemptDto? = null,
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
data class StartExamResponse(
    val attempt: ExamAttemptDto,
    val sections: List<ExamSectionDto>,
    val questions: List<ExamQuestionDto>,
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
data class ExamResultDto(
    val attempt: ExamAttemptDto,
    val resultsVisible: Boolean,
    /** Populated only when results are visible. */
    val perQuestion: List<ExamAnswerResultDto> = emptyList(),
)

@Serializable
data class ExamAnswerResultDto(
    val questionId: String,
    val result: String?,
    val marksAwarded: Double?,
)

@Serializable
data class CreateExamRequest(
    val title: String,
    val titleBn: String? = null,
    val instructions: String = "",
    val instructionsBn: String? = null,
    val opensAt: String,
    val closesAt: String,
    val durationSeconds: Int,
    val maxAttempts: Int = 1,
    val requireEmailVerified: Boolean = true,
    val subjectId: String? = null,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val resultsPolicy: String = ResultsPolicy.AFTER_CLOSE,
    val passPercent: Int? = null,
    val sections: List<CreateExamSectionRequest> = emptyList(),
)

@Serializable
data class CreateExamSectionRequest(
    val title: String,
    val titleBn: String? = null,
    val instructions: String = "",
    val timeLimitSeconds: Int? = null,
    val questionIds: List<String> = emptyList(),
)

@Serializable
data class ExamAuditEventDto(
    val id: Long,
    val eventType: String,
    val detail: String,
    val createdAt: String,
)
