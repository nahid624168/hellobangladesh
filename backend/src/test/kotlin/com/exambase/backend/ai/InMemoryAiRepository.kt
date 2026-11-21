package com.exambase.backend.ai

import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.time.Instant
import java.util.UUID

/** Same rules as JdbcAiRepository: quota counts ok+blocked only; everything is scoped to the owner. */
class InMemoryAiRepository : AiRepository {
    val consents = mutableMapOf<UUID, ConsentRecord>()
    val usage = mutableListOf<UsageRecord>()
    private data class Daily(var requests: Int = 0, var tokens: Int = 0)
    private val daily = mutableMapOf<Pair<UUID, Instant>, Daily>()
    private val globalDaily = mutableMapOf<Instant, Int>()
    private class Conv(val id: UUID, val userId: UUID, val task: String, val title: String, var updatedAt: Instant, val messages: MutableList<StoredMessage> = mutableListOf())
    private val convs = mutableMapOf<UUID, Conv>()

    override fun consent(userId: UUID) = consents[userId]
    override fun grantConsent(userId: UUID, version: String, now: Instant) { consents[userId] = ConsentRecord(version, now, null) }
    override fun revokeConsent(userId: UUID, now: Instant) { consents[userId]?.let { consents[userId] = it.copy(revokedAt = it.revokedAt ?: now) } }

    private fun counted(u: UsageRecord) = u.status == "ok" || u.status == "blocked"
    override fun usageSince(userId: UUID, since: Instant): UsageTotals {
        val rows = usage.filter { it.userId == userId && !it.at.isBefore(since) && counted(it) }
        return UsageTotals(rows.size, rows.sumOf { it.promptTokens + it.completionTokens })
    }
    override fun globalTokensSince(since: Instant) = usage.filter { !it.at.isBefore(since) }.sumOf { it.promptTokens + it.completionTokens }
    override fun blockedSince(userId: UUID, since: Instant) = usage.count { it.userId == userId && !it.at.isBefore(since) && it.status == "blocked" }
    @Synchronized override fun reserveQuota(userId: UUID, dayStart: Instant, requestLimit: Int, userTokenLimit: Int, globalTokenLimit: Int, tokens: Int): AiReservationResult {
        val user = daily.getOrPut(userId to dayStart) { Daily() }
        val global = globalDaily[dayStart] ?: 0
        val budget = tokens.coerceAtLeast(1)
        if (user.requests >= requestLimit || user.tokens + budget > userTokenLimit) return AiReservationResult.USER_LIMIT
        if (global + budget > globalTokenLimit) return AiReservationResult.GLOBAL_LIMIT
        user.requests++; user.tokens += budget; globalDaily[dayStart] = global + budget
        return AiReservationResult.RESERVED
    }
    @Synchronized override fun settleQuota(userId: UUID, dayStart: Instant, reservedTokens: Int, actualTokens: Int, countRequest: Boolean) {
        val user = daily.getOrPut(userId to dayStart) { Daily() }
        val delta = actualTokens.coerceAtLeast(0) - reservedTokens.coerceAtLeast(0)
        if (!countRequest) user.requests = (user.requests - 1).coerceAtLeast(0)
        user.tokens = (user.tokens + delta).coerceAtLeast(0)
        globalDaily[dayStart] = ((globalDaily[dayStart] ?: 0) + delta).coerceAtLeast(0)
    }
    override fun recordUsage(u: UsageRecord) { usage += u }

    override fun createConversation(userId: UUID, task: String, title: String, now: Instant): UUID {
        val id = UUID.randomUUID(); convs[id] = Conv(id, userId, task, title, now); return id
    }
    override fun conversationTask(userId: UUID, id: UUID) = convs[id]?.takeIf { it.userId == userId }?.task
    override fun recentMessages(userId: UUID, id: UUID, limit: Int) =
        convs[id]?.takeIf { it.userId == userId }?.messages?.takeLast(limit) ?: emptyList()
    override fun addMessages(userId: UUID, id: UUID, user: String, assistant: String, now: Instant): Boolean {
        val c = convs[id]?.takeIf { it.userId == userId } ?: return false
        c.messages += StoredMessage(UUID.randomUUID(), "user", user, now)
        c.messages += StoredMessage(UUID.randomUUID(), "assistant", assistant, now.plusMillis(1))
        c.updatedAt = now
        return true
    }
    override fun listConversations(userId: UUID, limit: Int, offset: Int): Page<AiConversationItem> {
        val rows = convs.values.filter { it.userId == userId }.sortedByDescending { it.updatedAt }
            .map { AiConversationItem(it.id.toString(), it.task, it.title, it.updatedAt.toString()) }
        return Paging.page(rows.drop(offset).take(limit + 1), limit, offset)
    }
    override fun getConversation(userId: UUID, id: UUID): AiConversationDetail? {
        val c = convs[id]?.takeIf { it.userId == userId } ?: return null
        return AiConversationDetail(c.id.toString(), c.task, c.title, c.messages.map { AiMessageDto(it.id.toString(), it.role, it.content, it.createdAt.toString()) })
    }
    override fun deleteConversation(userId: UUID, id: UUID): Boolean =
        convs[id]?.takeIf { it.userId == userId }?.let { convs.remove(id); true } ?: false
    override fun deleteAllConversations(userId: UUID): Int {
        val ids = convs.values.filter { it.userId == userId }.map { it.id }
        ids.forEach { convs.remove(it) }
        return ids.size
    }
}

class FakeAiProvider(var behaviour: (AiProviderRequest) -> AiProviderResult = { AiProviderResult("answer", 10, 20) }) : AiProvider {
    override var configured = true
    val requests = mutableListOf<AiProviderRequest>()
    override suspend fun complete(request: AiProviderRequest): AiProviderResult { requests += request; return behaviour(request) }
}
