package com.exambase.backend.ai

import com.exambase.backend.ApiException
import com.exambase.backend.MessageResponse
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.auth.RateLimiter
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException

class AiGatewayConfig(
    val dailyRequestLimit: Int = 30,
    val dailyTokenLimit: Int = 60_000,
    /** Cost guard for all users together, per UTC day. */
    val globalDailyTokenLimit: Int = 2_000_000,
    /** Safety-blocked requests per user per day before the user is paused until tomorrow. */
    val maxBlockedPerDay: Int = 5,
)

class AiService(
    private val repo: AiRepository,
    private val provider: AiProvider,
    private val enabled: () -> Boolean,
    private val limiter: RateLimiter,
    private val config: AiGatewayConfig = AiGatewayConfig(),
    private val clock: Clock = Clock.systemUTC(),
) {
    // ---- Consent

    fun consent(userId: UUID): ConsentDto {
        val c = repo.consent(userId)
        return ConsentDto(
            required = true,
            currentVersion = AiConsentVersion.CURRENT,
            acceptedVersion = c?.version,
            active = c != null && c.revokedAt == null && c.version == AiConsentVersion.CURRENT,
        )
    }

    fun accept(userId: UUID, req: AcceptConsentRequest): ConsentDto {
        if (req.version != AiConsentVersion.CURRENT) {
            throw bad("version", "Please agree to the current terms (${AiConsentVersion.CURRENT}).")
        }
        repo.grantConsent(userId, req.version, clock.instant())
        return consent(userId)
    }

    fun revoke(userId: UUID): ConsentDto {
        repo.revokeConsent(userId, clock.instant())
        return consent(userId)
    }

    // ---- Quota

    fun quota(userId: UUID): QuotaDto {
        val used = repo.usageSince(userId, startOfDay())
        return QuotaDto(used.requests, config.dailyRequestLimit, used.tokens, config.dailyTokenLimit, startOfDay().plus(Duration.ofDays(1)).toString())
    }

    // ---- Run

    suspend fun run(userId: UUID, req: AiRunRequest): AiRunResponse {
        val task = req.task.trim().lowercase()
        val template = AiTemplates.get(task) ?: throw bad("task", "Unknown task. Use one of: ${AiTemplates.tasks.sorted().joinToString(", ")}.")
        val text = req.text.trim()
        if (text.isEmpty()) throw bad("text", "Enter some text first.")
        if (text.length > AiLimits.MAX_INPUT_CHARS) throw bad("text", "Text is too long. The limit is ${AiLimits.MAX_INPUT_CHARS} characters.")
        val language = req.language?.lowercase()?.also { if (it != "en" && it != "bn") throw bad("language", "Use en or bn.") }
        val convId = req.conversationId?.let { parseId(it) }
        if (convId != null && task !in AiTask.saved) throw bad("conversationId", "This task does not keep a conversation.")

        if (!enabled()) throw ApiException(HttpStatusCode.ServiceUnavailable, "ai_disabled", "AI features are switched off right now.")
        if (!provider.configured) throw ApiException(HttpStatusCode.ServiceUnavailable, "ai_unavailable", "AI is not available right now.")
        if (!consent(userId).active) {
            throw ApiException(HttpStatusCode.Forbidden, "ai_consent_required", "Please agree to the AI terms before using AI.")
        }
        if (!limiter.tryAcquire(userId.toString())) {
            throw ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Too many AI requests. Wait a minute and try again.")
        }

        val dayStart = startOfDay()
        if (repo.blockedSince(userId, dayStart) >= config.maxBlockedPerDay) {
            throw ApiException(HttpStatusCode.TooManyRequests, "ai_temporarily_blocked", "AI is paused for your account until tomorrow because several requests were blocked.")
        }
        val history = if (convId != null) {
            if (repo.conversationTask(userId, convId) == null) throw notFound()
            repo.recentMessages(userId, convId, AiLimits.HISTORY_MESSAGES)
        } else emptyList()

        val messages = history.map { AiChatMessage(it.role, if (it.role == "user") AiTemplates.wrapUserText(it.content) else it.content) } +
            AiChatMessage("user", AiTemplates.wrapUserText(text))
        val systemPrompt = AiTemplates.systemPrompt(template, language)
        val providerRequest = AiProviderRequest(systemPrompt, messages, template.maxOutputTokens)
        // Reserve the estimated prompt size plus the provider's full output allowance before calling Gemini.
        // This is intentionally conservative so parallel requests cannot all pass a stale quota snapshot.
        val reservedTokens = (estimateTokens(systemPrompt.length + messages.sumOf { it.text.length }) + template.maxOutputTokens)
            .coerceAtLeast(1)
        when (repo.reserveQuota(userId, dayStart, config.dailyRequestLimit, config.dailyTokenLimit, config.globalDailyTokenLimit, reservedTokens)) {
            AiReservationResult.USER_LIMIT -> throw ApiException(HttpStatusCode.TooManyRequests, "ai_quota_exceeded", "You have used today's AI allowance. It resets at midnight UTC.")
            AiReservationResult.GLOBAL_LIMIT -> throw ApiException(HttpStatusCode.ServiceUnavailable, "ai_capacity", "AI is at capacity today. Please try again tomorrow.")
            AiReservationResult.RESERVED -> Unit
        }

        val started = clock.instant()
        val result = try {
            provider.complete(providerRequest)
        } catch (e: CancellationException) {
            // Client disconnected / coroutine cancelled: release the reservation and propagate cancellation.
            repo.settleQuota(userId, dayStart, reservedTokens, 0, countRequest = false)
            repo.recordUsage(usage(userId, task, "cancelled", text.length, 0, 0, 0, started))
            throw e
        } catch (e: AiProviderException) {
            val blocked = e.kind == AiFailure.BLOCKED
            repo.settleQuota(userId, dayStart, reservedTokens, 0, countRequest = blocked)
            repo.recordUsage(usage(userId, task, if (blocked) "blocked" else "provider_error", text.length, 0, 0, 0, started))
            throw when (e.kind) {
                AiFailure.BLOCKED -> ApiException(HttpStatusCode.UnprocessableEntity, "ai_blocked", "The AI could not answer this request. Try rewording it.")
                AiFailure.RATE_LIMITED -> ApiException(HttpStatusCode.ServiceUnavailable, "ai_busy", "AI is busy. Please try again in a moment.")
                AiFailure.UNAVAILABLE -> ApiException(HttpStatusCode.ServiceUnavailable, "ai_unavailable", "AI is not available right now. Please try again later.")
                AiFailure.BAD_RESPONSE -> ApiException(HttpStatusCode.BadGateway, "ai_bad_response", "The AI sent an unusable answer. Please try again.")
            }
        } catch (e: Exception) {
            repo.settleQuota(userId, dayStart, reservedTokens, 0, countRequest = false)
            repo.recordUsage(usage(userId, task, "provider_error", text.length, 0, 0, 0, started))
            throw ApiException(HttpStatusCode.ServiceUnavailable, "ai_unavailable", "AI is not available right now. Please try again later.")
        }
        val promptTokens = if (result.promptTokens > 0) result.promptTokens else estimateTokens(systemPrompt.length + messages.sumOf { it.text.length })
        val completionTokens = if (result.completionTokens > 0) result.completionTokens else estimateTokens(result.text.length)
        repo.settleQuota(userId, dayStart, reservedTokens, promptTokens + completionTokens, countRequest = true)
        repo.recordUsage(usage(userId, task, "ok", text.length, result.text.length, promptTokens, completionTokens, started))

        var savedId: UUID? = convId
        if (task in AiTask.saved) {
            val now = clock.instant()
            val id = savedId ?: repo.createConversation(userId, task, titleOf(text), now)
            repo.addMessages(userId, id, text, result.text, now)
            savedId = id
        }
        return AiRunResponse(result.text, savedId?.toString(), quota(userId), NOTICE)
    }

    // ---- History

    fun conversations(userId: UUID, limitRaw: String?, cursor: String?): Page<AiConversationItem> {
        val limit = Paging.limit(limitRaw)
        return repo.listConversations(userId, limit, Paging.decode(cursor))
    }

    fun conversation(userId: UUID, id: String): AiConversationDetail =
        repo.getConversation(userId, parseId(id)) ?: throw notFound()

    fun deleteConversation(userId: UUID, id: String): MessageResponse {
        if (!repo.deleteConversation(userId, parseId(id))) throw notFound()
        return MessageResponse("Conversation deleted.")
    }

    fun deleteAllConversations(userId: UUID): MessageResponse {
        val n = repo.deleteAllConversations(userId)
        return MessageResponse("Deleted $n conversation(s).")
    }

    // ---- helpers

    private fun startOfDay(): Instant = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
    // Deliberately conservative fallback for providers that omit usage metadata.
    private fun estimateTokens(chars: Int) = ((chars + 1) / 2).coerceAtLeast(1)
    private fun titleOf(text: String): String = text.replace(Regex("\\s+"), " ").take(60)
    private fun usage(u: UUID, task: String, status: String, inChars: Int, outChars: Int, pt: Int, ct: Int, started: Instant) =
        UsageRecord(u, task, status, inChars, outChars, pt, ct, Duration.between(started, clock.instant()).toMillis().toInt(), clock.instant())

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw notFound()
    private fun notFound() = ApiException(HttpStatusCode.NotFound, "not_found", "Not found.")
    private fun bad(field: String, message: String) =
        ApiException(HttpStatusCode.BadRequest, "invalid_request", message, mapOf(field to message))

    companion object {
        const val NOTICE = "AI can make mistakes. Check important facts with your lesson or teacher."
    }
}
