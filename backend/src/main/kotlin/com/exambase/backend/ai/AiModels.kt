package com.exambase.backend.ai

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

object AiTask {
    const val SUMMARIZE = "summarize"
    const val EXPLAIN = "explain"
    const val OUTLINE = "outline"
    const val REVISION_NOTES = "revision_notes"
    const val MAKE_FLASHCARDS = "make_flashcards"
    const val MAKE_QUESTIONS = "make_questions"
    const val TRANSLATE = "translate"
    const val SIMPLIFY = "simplify"
    const val TUTOR_HINT = "tutor_hint"
    const val TUTOR_STEPS = "tutor_steps"
    const val CHAT = "chat"

    /** Tasks whose messages are saved as a conversation the user can read and delete. */
    val saved = setOf(TUTOR_HINT, TUTOR_STEPS, CHAT)
}

object AiConsentVersion {
    /** Bump when the wording the user agreed to changes; everyone must agree again. */
    const val CURRENT = "2026-11-21"
}

object AiLimits {
    const val MAX_INPUT_CHARS = 8_000
    const val HISTORY_MESSAGES = 10
}

// ---- Requests / responses (response fields have no defaults; request fields do)

@Serializable
data class AcceptConsentRequest(val version: String = "")

@Serializable
data class AiRunRequest(
    val task: String = "",
    val text: String = "",
    val language: String? = null,
    val conversationId: String? = null,
)

@Serializable
data class ConsentDto(val required: Boolean, val currentVersion: String, val acceptedVersion: String?, val active: Boolean)

@Serializable
data class QuotaDto(
    val requestsUsed: Int,
    val requestsLimit: Int,
    val tokensUsed: Int,
    val tokensLimit: Int,
    val resetsAt: String,
)

@Serializable
data class AiRunResponse(
    val output: String,
    val conversationId: String?,
    val quota: QuotaDto,
    val notice: String,
)

@Serializable
data class AiMessageDto(val id: String, val role: String, val content: String, val createdAt: String)

@Serializable
data class AiConversationItem(val id: String, val task: String, val title: String, val updatedAt: String)

@Serializable
data class AiConversationDetail(val id: String, val task: String, val title: String, val messages: List<AiMessageDto>)

// ---- Internal

data class ConsentRecord(val version: String, val grantedAt: Instant, val revokedAt: Instant?)

data class UsageTotals(val requests: Int, val tokens: Int)

data class UsageRecord(
    val userId: UUID,
    val task: String,
    val status: String, // ok | blocked | provider_error
    val inputChars: Int,
    val outputChars: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val latencyMs: Int,
    val at: Instant,
)

data class StoredMessage(val id: UUID, val role: String, val content: String, val createdAt: Instant)
