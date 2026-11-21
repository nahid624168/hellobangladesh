package com.exambase.ai.ai

import com.exambase.core.network.AiConsentDto
import com.exambase.core.network.AiQuotaDto
import com.exambase.core.network.AiRunResponse
import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal const val VERSION = "2026-11-21"

internal fun quotaDto(used: Int = 0, limit: Int = 30, tokens: Int = 0, tokenLimit: Int = 60_000) =
    AiQuotaDto(used, limit, tokens, tokenLimit, "2026-11-22T00:00:00Z")

internal fun consentDto(active: Boolean, accepted: String? = if (active) VERSION else null) =
    AiConsentDto(required = true, currentVersion = VERSION, acceptedVersion = accepted, active = active)

internal fun fail(kind: FailureKind, code: String, fields: Map<String, String> = emptyMap()) =
    ApiResult.Failure(ApiFailure(kind, code, "server words", fields))

internal class FakeAiSource(
    var consent: ApiResult<AiConsentDto> = ApiResult.Success(consentDto(active = false)),
    var quota: ApiResult<AiQuotaDto> = ApiResult.Success(quotaDto()),
    var run: ApiResult<AiRunResponse> = ApiResult.Success(AiRunResponse("Answer", null, quotaDto(used = 1), "Be careful.")),
) : AiSource {
    var runCalls = 0
    var acceptedVersions = mutableListOf<String>()
    var revokeCalls = 0
    var lastTask: String? = null
    var lastText: String? = null
    var lastLanguage: String? = null
    var lastConversationId: String? = null

    override suspend fun consent() = consent
    override suspend fun quota() = quota
    override suspend fun accept(version: String): ApiResult<AiConsentDto> {
        acceptedVersions += version
        consent = ApiResult.Success(consentDto(active = true))
        return consent
    }
    override suspend fun revoke(): ApiResult<AiConsentDto> {
        revokeCalls++
        consent = ApiResult.Success(consentDto(active = false, accepted = VERSION))
        return consent
    }
    override suspend fun run(task: String, text: String, language: String?, conversationId: String?): ApiResult<AiRunResponse> {
        runCalls++
        lastTask = task; lastText = text; lastLanguage = language; lastConversationId = conversationId
        return run
    }
}

class AiConsentFlowTest {
    private fun engine(src: FakeAiSource) = AiAssistantEngine(src)

    @Test fun `tutor follow-up forwards the owner conversation id`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))
        val e = engine(src)
        e.load()
        e.onTask(AiTasks.CHAT)
        e.onInput("Explain this again")
        e.run("conversation-123")
        assertEquals("conversation-123", src.lastConversationId)
    }

    @Test fun `no consent - run opens the dialog and sends nothing`() = runBlocking {
        val src = FakeAiSource()
        val e = engine(src)
        e.load()
        e.onInput("Photosynthesis is how plants make food.")
        e.run()
        assertTrue(e.state.value.showConsent)
        assertEquals(0, src.runCalls)
    }

    @Test fun `agreeing records the current server version then runs the waiting request`() = runBlocking {
        val src = FakeAiSource()
        val e = engine(src)
        e.load()
        e.onInput("Some text")
        e.onTask(AiTasks.OUTLINE)
        e.run()
        e.acceptConsent()
        assertEquals(listOf(VERSION), src.acceptedVersions)
        assertEquals(1, src.runCalls)
        assertEquals(AiTasks.OUTLINE, src.lastTask)
        val s = e.state.value
        assertFalse(s.showConsent)
        assertTrue(s.hasOutput)
        assertEquals("Answer", s.output)
        assertTrue(AiRules.consentActive(s.consent))
    }

    @Test fun `declining sends nothing, keeps the text and does not run later`() = runBlocking {
        val src = FakeAiSource()
        val e = engine(src)
        e.load()
        e.onInput("Keep me")
        e.run()
        e.declineConsent()
        assertFalse(e.state.value.showConsent)
        assertEquals("Keep me", e.state.value.input)
        assertEquals(0, src.runCalls)
        // Agreeing later from some other place must not fire the old request.
        e.acceptConsent()
        assertEquals(0, src.runCalls)
    }

    @Test fun `active consent runs straight away without a dialog`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))
        val e = engine(src)
        e.load()
        e.onInput("Text")
        e.onLanguage("bn")
        e.run()
        assertFalse(e.state.value.showConsent)
        assertEquals(1, src.runCalls)
        assertEquals("bn", src.lastLanguage)
        assertTrue(src.acceptedVersions.isEmpty())
    }

    @Test fun `consent for an older wording counts as not given`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = false, accepted = "2025-01")))
        val e = engine(src)
        e.load()
        e.onInput("Text")
        e.run()
        assertTrue(e.state.value.showConsent)
        assertEquals(0, src.runCalls)
    }

    @Test fun `server says consent required - dialog opens again and agreeing retries`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))
        val e = engine(src)
        e.load()
        e.onInput("Text")
        src.run = fail(FailureKind.OTHER, "ai_consent_required")
        e.run()
        assertTrue(e.state.value.showConsent)
        assertNull(e.state.value.error)
        assertFalse(AiRules.consentActive(e.state.value.consent))
        src.run = ApiResult.Success(AiRunResponse("Now ok", null, quotaDto(used = 1), "n"))
        e.acceptConsent()
        assertEquals("Now ok", e.state.value.output)
        assertEquals(2, src.runCalls)
    }

    @Test fun `turning AI off revokes on the server and the next run asks again`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))
        val e = engine(src)
        e.load()
        e.revokeConsent()
        assertEquals(1, src.revokeCalls)
        assertFalse(AiRules.consentActive(e.state.value.consent))
        e.onInput("Text")
        e.run()
        assertTrue(e.state.value.showConsent)
        assertEquals(0, src.runCalls)
    }

    @Test fun `accept failure keeps the dialog open and shows the error`() = runBlocking {
        val src = object : AiSource by FakeAiSource() {
            override suspend fun accept(version: String): ApiResult<AiConsentDto> = fail(FailureKind.NETWORK, "network")
        }
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("Text")
        e.run()
        e.acceptConsent()
        assertTrue(e.state.value.showConsent)
        assertEquals(AiError.Offline, e.state.value.error)
        assertFalse(e.state.value.consentBusy)
    }
}

class AiFailureStatesTest {
    private fun ready(src: FakeAiSource): AiAssistantEngine = AiAssistantEngine(src).also {
        runBlocking { it.load(); it.onInput("Some lesson text") }
    }

    private fun activeSource() = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))

    @Test fun `empty and too long input are caught before any request`() = runBlocking {
        val src = activeSource()
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("   ")
        e.run()
        assertEquals(InputProblem.EMPTY, e.state.value.inputProblem)
        e.onInput("x".repeat(AiRules.MAX_INPUT_CHARS + 1))
        e.run()
        assertEquals(InputProblem.TOO_LONG, e.state.value.inputProblem)
        assertEquals(0, src.runCalls)
        e.onInput("x".repeat(AiRules.MAX_INPUT_CHARS))
        e.run()
        assertEquals(1, src.runCalls)
    }

    @Test fun `offline keeps the text and shows the offline error`() = runBlocking {
        val src = activeSource().apply { run = fail(FailureKind.NETWORK, "network") }
        val e = ready(src)
        e.run()
        val s = e.state.value
        assertEquals(AiError.Offline, s.error)
        assertEquals("Some lesson text", s.input)
        assertFalse(s.running)
        assertFalse(s.hasOutput)
    }

    @Test fun `each server code maps to its own error`() {
        fun code(kind: FailureKind, c: String) = AiRules.errorFor(ApiFailure(kind, c, "m"))
        assertEquals(AiError.QuotaExceeded, code(FailureKind.RATE_LIMITED, "ai_quota_exceeded"))
        assertEquals(AiError.TemporarilyBlocked, code(FailureKind.RATE_LIMITED, "ai_temporarily_blocked"))
        assertEquals(AiError.RateLimited, code(FailureKind.RATE_LIMITED, "rate_limited"))
        assertEquals(AiError.ContentBlocked, code(FailureKind.OTHER, "ai_blocked"))
        assertEquals(AiError.Disabled, code(FailureKind.OTHER, "ai_disabled"))
        assertEquals(AiError.Unavailable, code(FailureKind.OTHER, "ai_unavailable"))
        assertEquals(AiError.Unavailable, code(FailureKind.OTHER, "ai_busy"))
        assertEquals(AiError.Unavailable, code(FailureKind.OTHER, "ai_capacity"))
        assertEquals(AiError.Unavailable, code(FailureKind.OTHER, "ai_bad_response"))
        assertEquals(AiError.Other, code(FailureKind.OTHER, "http_500"))
        assertEquals(AiError.ConsentRequired, code(FailureKind.OTHER, "ai_consent_required"))
    }

    @Test fun `validation error shows the field message from the server`() {
        val e = AiRules.errorFor(ApiFailure(FailureKind.VALIDATION, "invalid_request", "bad", mapOf("text" to "Text is too long.")))
        assertEquals(AiError.InvalidInput("Text is too long."), e)
    }

    @Test fun `provider failure does not touch the quota shown`() = runBlocking {
        val src = activeSource().apply { run = fail(FailureKind.OTHER, "ai_unavailable") }
        val e = ready(src)
        e.run()
        assertEquals(AiError.Unavailable, e.state.value.error)
        assertEquals(0, e.state.value.quota?.requestsUsed)
    }

    @Test fun `blocked content re-reads the quota because safety blocks use allowance`() = runBlocking {
        val src = activeSource().apply { run = fail(FailureKind.OTHER, "ai_blocked") }
        val e = ready(src)
        src.quota = ApiResult.Success(quotaDto(used = 1))
        e.run()
        assertEquals(AiError.ContentBlocked, e.state.value.error)
        assertEquals(1, e.state.value.quota?.requestsUsed)
    }

    @Test fun `signed out when the session is gone`() = runBlocking {
        val src = activeSource().apply { run = fail(FailureKind.UNAUTHORIZED, "unauthorized") }
        val e = ready(src)
        e.run()
        assertTrue(e.state.value.signedOut)
    }

    @Test fun `load failure while offline shows an error and no data`() = runBlocking {
        val src = FakeAiSource(consent = fail(FailureKind.NETWORK, "network"), quota = fail(FailureKind.NETWORK, "network"))
        val e = AiAssistantEngine(src)
        e.load()
        val s = e.state.value
        assertFalse(s.loading)
        assertEquals(AiError.Offline, s.error)
        assertNull(s.consent)
        assertNull(s.quota)
    }

    @Test fun `a successful answer shows the server notice and updates quota`() = runBlocking {
        val src = activeSource()
        val e = ready(src)
        e.run()
        val s = e.state.value
        assertEquals("Be careful.", s.notice)
        assertEquals(1, s.quota?.requestsUsed)
        assertNotNull(s.output)
        e.onOutput("Edited answer")
        assertEquals("Edited answer", e.state.value.output)
    }

    @Test fun `blank server notice falls back to the standard warning`() = runBlocking {
        val src = activeSource().apply { run = ApiResult.Success(AiRunResponse("A", null, quotaDto(used = 1), "")) }
        val e = ready(src)
        e.run()
        assertEquals(AiRules.FALLBACK_NOTICE, e.state.value.notice)
    }

    @Test fun `input is trimmed before it is sent`() = runBlocking {
        val src = activeSource()
        val e = ready(src)
        e.onInput("  hello  ")
        e.run()
        assertEquals("hello", src.lastText)
    }
}

class AiQuotaDisplayTest {
    @Test fun `exhausted when either limit is reached`() {
        assertTrue(AiRules.quotaExhausted(quotaDto(used = 30)))
        assertTrue(AiRules.quotaExhausted(quotaDto(tokens = 60_000)))
        assertFalse(AiRules.quotaExhausted(quotaDto(used = 29, tokens = 59_999)))
    }

    @Test fun `requests left never goes below zero`() {
        assertEquals(25, AiRules.requestsLeft(quotaDto(used = 5)))
        assertEquals(0, AiRules.requestsLeft(quotaDto(used = 31)))
    }

    @Test fun `fraction follows the nearer limit and stays within 0 to 1`() {
        assertEquals(0.5f, AiRules.quotaUsedFraction(quotaDto(used = 15, tokens = 1_000)), 0.001f)
        assertEquals(0.9f, AiRules.quotaUsedFraction(quotaDto(used = 3, tokens = 54_000)), 0.001f)
        assertEquals(1f, AiRules.quotaUsedFraction(quotaDto(used = 99)), 0.001f)
        assertEquals(1f, AiRules.quotaUsedFraction(quotaDto(limit = 0)), 0.001f)
    }

    @Test fun `low warning from 80 percent but not once exhausted`() {
        assertFalse(AiRules.quotaLow(quotaDto(used = 23)))
        assertTrue(AiRules.quotaLow(quotaDto(used = 24)))
        assertTrue(AiRules.quotaLow(quotaDto(used = 1, tokens = 50_000)))
        assertFalse(AiRules.quotaLow(quotaDto(used = 30)))
    }

    @Test fun `run at the limit is refused locally without a request`() = runBlocking {
        val src = FakeAiSource(
            consent = ApiResult.Success(consentDto(active = true)),
            quota = ApiResult.Success(quotaDto(used = 30)),
        )
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("Text")
        assertTrue(e.state.value.quotaExhausted)
        e.run()
        assertEquals(AiError.QuotaExceeded, e.state.value.error)
        assertEquals(0, src.runCalls)
    }

    @Test fun `quota is loaded with the screen and refreshed by each answer`() = runBlocking {
        val src = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)), quota = ApiResult.Success(quotaDto(used = 4)))
        val e = AiAssistantEngine(src)
        e.load()
        assertEquals(4, e.state.value.quota?.requestsUsed)
        e.onInput("Text")
        e.run()
        assertEquals(1, e.state.value.quota?.requestsUsed) // value the answer carried
    }

    @Test fun `reset time label reads the server timestamp in the given zone`() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val dhaka = java.util.TimeZone.getTimeZone("Asia/Dhaka")
        val a = AiRules.resetTimeLabel("2026-11-22T00:00:00Z", utc, java.util.Locale.US)
        val b = AiRules.resetTimeLabel("2026-11-22T00:00:00Z", dhaka, java.util.Locale.US)
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a!!.startsWith("12:00"))
        assertTrue(b!!.startsWith("6:00"))
        assertNotNull(AiRules.resetTimeLabel("2026-11-22T00:00:00.123Z", utc, java.util.Locale.US))
        assertNull(AiRules.resetTimeLabel("tomorrow", utc, java.util.Locale.US))
    }
}
