package com.exambase.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Mirrors `backend/.../ai/AiModels.kt`. Request fields have defaults; response fields come back always. */
@Serializable
data class AiAcceptConsentRequest(val version: String)

@Serializable
data class AiConsentDto(
    val required: Boolean,
    val currentVersion: String,
    val acceptedVersion: String? = null,
    val active: Boolean,
)

@Serializable
data class AiQuotaDto(
    val requestsUsed: Int,
    val requestsLimit: Int,
    val tokensUsed: Int,
    val tokensLimit: Int,
    /** Next UTC midnight, ISO-8601. */
    val resetsAt: String,
)

@Serializable
data class AiRunRequest(
    val task: String,
    val text: String,
    val language: String? = null,
    val conversationId: String? = null,
)

@Serializable
data class AiRunResponse(
    val output: String,
    val conversationId: String? = null,
    val quota: AiQuotaDto,
    val notice: String,
)


@Serializable
data class AiConversationItemDto(val id: String, val task: String, val title: String, val updatedAt: String)

@Serializable
data class AiMessageDto(val id: String, val role: String, val content: String, val createdAt: String)

@Serializable
data class AiConversationDetailDto(val id: String, val task: String, val title: String, val messages: List<AiMessageDto> = emptyList())

@Serializable
data class AiGeneratedQuestionDto(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String = "",
)

@Serializable
data class AiGeneratedQuizCreateRequest(val title: String, val description: String? = null, val questions: List<AiGeneratedQuestionDto>)

@Serializable
data class AiGeneratedQuizSetDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val questionCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class AiGeneratedQuizDetailDto(@SerialName("set") val quizSet: AiGeneratedQuizSetDto, val questions: List<AiGeneratedQuestionDto> = emptyList())
