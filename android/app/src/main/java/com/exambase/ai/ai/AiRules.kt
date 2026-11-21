package com.exambase.ai.ai

import com.exambase.core.network.AiConsentDto
import com.exambase.core.network.AiQuotaDto
import com.exambase.core.network.ApiFailure
import com.exambase.core.network.FailureKind

/** Task names the server accepts (see `AiTask` in the backend). Step 33a offers the one-shot text tasks. */
object AiTasks {
    const val SUMMARIZE = "summarize"
    const val EXPLAIN = "explain"
    const val OUTLINE = "outline"
    const val REVISION_NOTES = "revision_notes"
    const val TRANSLATE = "translate"
    const val SIMPLIFY = "simplify"

    /** Order shown on screen. */
    val textTasks = listOf(SUMMARIZE, EXPLAIN, OUTLINE, REVISION_NOTES, SIMPLIFY, TRANSLATE)
}

object AiRules {
    /** Same limit as the server (`AiLimits.MAX_INPUT_CHARS`); the server still checks. */
    const val MAX_INPUT_CHARS = 8_000

    /** Shown when the server did not send its notice (for example before the first answer). */
    const val FALLBACK_NOTICE = "AI can make mistakes. Check important facts with your lesson or teacher."

    fun inputProblem(text: String): InputProblem? = when {
        text.isBlank() -> InputProblem.EMPTY
        text.length > MAX_INPUT_CHARS -> InputProblem.TOO_LONG
        else -> null
    }

    /** True when the server will accept AI calls for this user (consent given for the current wording). */
    fun consentActive(consent: AiConsentDto?): Boolean = consent != null && consent.active

    fun quotaExhausted(q: AiQuotaDto): Boolean =
        q.requestsUsed >= q.requestsLimit || q.tokensUsed >= q.tokensLimit

    fun requestsLeft(q: AiQuotaDto): Int = (q.requestsLimit - q.requestsUsed).coerceAtLeast(0)

    /** 0f..1f share of the tighter of the two limits that is already used (a bar should show the nearer limit). */
    fun quotaUsedFraction(q: AiQuotaDto): Float {
        val r = if (q.requestsLimit > 0) q.requestsUsed.toFloat() / q.requestsLimit else 1f
        val t = if (q.tokensLimit > 0) q.tokensUsed.toFloat() / q.tokensLimit else 1f
        return maxOf(r, t).coerceIn(0f, 1f)
    }

    /** Warn when 20% or less of the nearer limit is left, but not once it is already used up. */
    fun quotaLow(q: AiQuotaDto): Boolean = !quotaExhausted(q) && quotaUsedFraction(q) >= 0.8f

    /**
     * "resetsAt" from the server (UTC, e.g. 2026-11-22T00:00:00Z) as a short local time such as "6:00 AM".
     * Uses java.text only, because java.time needs desugaring on Android 7. Returns null if the value cannot be read.
     */
    fun resetTimeLabel(resetsAtIso: String, zone: java.util.TimeZone = java.util.TimeZone.getDefault(), locale: java.util.Locale = java.util.Locale.getDefault()): String? {
        val trimmed = resetsAtIso.replace(Regex("\\.\\d+Z$"), "Z")
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val date = runCatching { parser.parse(trimmed) }.getOrNull() ?: return null
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale).apply { timeZone = zone }.format(date)
    }

    /** Turns a server failure into the message category the screen shows. Never exposes provider detail. */
    fun errorFor(f: ApiFailure): AiError = when {
        f.kind == FailureKind.NETWORK -> AiError.Offline
        f.code == "ai_consent_required" -> AiError.ConsentRequired
        f.code == "ai_quota_exceeded" -> AiError.QuotaExceeded
        f.code == "ai_temporarily_blocked" -> AiError.TemporarilyBlocked
        f.code == "rate_limited" || f.kind == FailureKind.RATE_LIMITED -> AiError.RateLimited
        f.code == "ai_blocked" -> AiError.ContentBlocked
        f.code == "ai_disabled" -> AiError.Disabled
        f.code == "ai_unavailable" || f.code == "ai_busy" || f.code == "ai_capacity" || f.code == "ai_bad_response" -> AiError.Unavailable
        f.kind == FailureKind.VALIDATION -> AiError.InvalidInput(f.fields["text"] ?: f.message)
        else -> AiError.Other
    }

    /** Failures after which the user's allowance may have changed, so the quota bar should be re-read. */
    fun shouldRefreshQuota(e: AiError): Boolean =
        e == AiError.ContentBlocked || e == AiError.QuotaExceeded || e == AiError.TemporarilyBlocked
}

enum class InputProblem { EMPTY, TOO_LONG }

/** One entry per way an AI call can fail; each has its own words on screen. */
sealed interface AiError {
    data object Offline : AiError
    data object ConsentRequired : AiError
    data object QuotaExceeded : AiError
    data object TemporarilyBlocked : AiError
    data object RateLimited : AiError
    data object ContentBlocked : AiError
    data object Disabled : AiError
    data object Unavailable : AiError
    data class InvalidInput(val message: String) : AiError
    data object Other : AiError
}
