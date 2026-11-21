package com.exambase.backend.ai

import com.exambase.backend.academic.Page
import java.time.Instant
import java.util.UUID

enum class AiReservationResult { RESERVED, USER_LIMIT, GLOBAL_LIMIT }

interface AiRepository {
    fun consent(userId: UUID): ConsentRecord?
    fun grantConsent(userId: UUID, version: String, now: Instant)
    fun revokeConsent(userId: UUID, now: Instant)

    /** Counts only `ok` and `blocked` calls: provider failures do not use up the user's quota. */
    fun usageSince(userId: UUID, since: Instant): UsageTotals
    fun globalTokensSince(since: Instant): Int
    fun blockedSince(userId: UUID, since: Instant): Int
    /** Atomically reserves one request and its worst-case token budget for this UTC day. */
    fun reserveQuota(userId: UUID, dayStart: Instant, requestLimit: Int, userTokenLimit: Int, globalTokenLimit: Int, tokens: Int): AiReservationResult
    /** Commits actual usage, or releases the reservation when the provider failed. */
    fun settleQuota(userId: UUID, dayStart: Instant, reservedTokens: Int, actualTokens: Int, countRequest: Boolean)
    fun recordUsage(u: UsageRecord)

    fun createConversation(userId: UUID, task: String, title: String, now: Instant): UUID
    fun conversationTask(userId: UUID, id: UUID): String?
    fun recentMessages(userId: UUID, id: UUID, limit: Int): List<StoredMessage>
    fun addMessages(userId: UUID, id: UUID, user: String, assistant: String, now: Instant): Boolean
    fun listConversations(userId: UUID, limit: Int, offset: Int): Page<AiConversationItem>
    fun getConversation(userId: UUID, id: UUID): AiConversationDetail?
    fun deleteConversation(userId: UUID, id: UUID): Boolean
    fun deleteAllConversations(userId: UUID): Int
}
