package com.exambase.ai.ai

import com.exambase.core.network.AiConsentDto
import com.exambase.core.network.AiQuotaDto
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException

data class AiUiState(
    val loading: Boolean = true,
    val consent: AiConsentDto? = null,
    val quota: AiQuotaDto? = null,
    val task: String = AiTasks.SUMMARIZE,
    /** "en" or "bn": the language the answer is written in. */
    val language: String = "en",
    val input: String = "",
    val inputProblem: InputProblem? = null,
    val running: Boolean = false,
    /** The answer. The user may edit it before copying or saving, so it lives in state as plain text. */
    val output: String = "",
    val hasOutput: Boolean = false,
    val conversationId: String? = null,
    val notice: String = AiRules.FALLBACK_NOTICE,
    val error: AiError? = null,
    /** The consent dialog is open (the user pressed Run before agreeing, or the server asked again). */
    val showConsent: Boolean = false,
    val consentBusy: Boolean = false,
    val signedOut: Boolean = false,
) {
    val canRun: Boolean get() = !loading && !running && !consentBusy
    val quotaExhausted: Boolean get() = quota?.let(AiRules::quotaExhausted) == true
}

/**
 * All AI-screen rules without Android types, so plain JUnit can test them.
 * The view model only launches these functions in `viewModelScope`.
 *
 * Privacy: the engine never logs or stores the input or the answer; both live only in [state].
 */
class AiAssistantEngine(private val source: AiSource) {
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    /** The text that was waiting for consent when the dialog opened. */
    private var waitingForConsent = false
    private var pendingConversationId: String? = null

    suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val consent = source.consent()
        val quota = source.quota()
        val failure = (consent as? ApiResult.Failure)?.failure ?: (quota as? ApiResult.Failure)?.failure
        if (failure?.kind == FailureKind.UNAUTHORIZED) {
            _state.update { it.copy(loading = false, signedOut = true) }
            return
        }
        _state.update {
            it.copy(
                loading = false,
                consent = (consent as? ApiResult.Success)?.value ?: it.consent,
                quota = (quota as? ApiResult.Success)?.value ?: it.quota,
                error = failure?.let(AiRules::errorFor),
            )
        }
    }

    fun onTask(task: String) = _state.update { it.copy(task = task, error = null) }
    fun onLanguage(language: String) = _state.update { it.copy(language = language) }
    fun onInput(text: String) = _state.update { it.copy(input = text, inputProblem = null, error = null) }
    fun onOutput(text: String) = _state.update { it.copy(output = text) }
    fun dismissError() = _state.update { it.copy(error = null) }

    /** Forget the last answer (a new selection, or the user discarded it). Consent, quota and task stay. */
    fun clearOutput() {
        pendingConversationId = null
        _state.update { it.copy(output = "", hasOutput = false, conversationId = null, error = null, inputProblem = null) }
    }

    /** Run pressed. Checks input, then consent, then asks the server. Nothing is sent without consent. */
    suspend fun run(conversationId: String? = pendingConversationId) {
        pendingConversationId = conversationId
        val s = _state.value
        if (!s.canRun) return
        val problem = AiRules.inputProblem(s.input)
        if (problem != null) {
            _state.update { it.copy(inputProblem = problem) }
            return
        }
        if (!AiRules.consentActive(s.consent)) {
            waitingForConsent = true
            _state.update { it.copy(showConsent = true, error = null) }
            return
        }
        if (s.quotaExhausted) {
            _state.update { it.copy(error = AiError.QuotaExceeded) }
            return
        }
        send(conversationId)
    }

    /** "I agree" in the dialog: record consent for the version the server told us, then continue the pending run. */
    suspend fun acceptConsent() {
        if (_state.value.consent == null) load() // consent state never loaded: read the real current version first
        val version = _state.value.consent?.currentVersion ?: return
        _state.update { it.copy(consentBusy = true, error = null) }
        when (val r = source.accept(version)) {
            is ApiResult.Success -> {
                _state.update { it.copy(consent = r.value, consentBusy = false, showConsent = false) }
                if (waitingForConsent) {
                    waitingForConsent = false
                    run(pendingConversationId)
                }
            }
            is ApiResult.Failure -> {
                if (r.failure.kind == FailureKind.UNAUTHORIZED) {
                    _state.update { it.copy(consentBusy = false, showConsent = false, signedOut = true) }
                } else {
                    _state.update { it.copy(consentBusy = false, error = AiRules.errorFor(r.failure)) }
                }
            }
        }
    }

    /** "Not now": nothing is sent, the text stays where the user typed it. */
    fun declineConsent() {
        waitingForConsent = false
        _state.update { it.copy(showConsent = false) }
    }

    /** "Turn off AI": revoke on the server; calls stop immediately. */
    suspend fun revokeConsent() {
        _state.update { it.copy(consentBusy = true, error = null) }
        when (val r = source.revoke()) {
            is ApiResult.Success -> _state.update { it.copy(consent = r.value, consentBusy = false) }
            is ApiResult.Failure ->
                if (r.failure.kind == FailureKind.UNAUTHORIZED) _state.update { it.copy(consentBusy = false, signedOut = true) }
                else _state.update { it.copy(consentBusy = false, error = AiRules.errorFor(r.failure)) }
        }
    }

    private suspend fun send(conversationId: String? = null) {
        val s = _state.value
        _state.update { it.copy(running = true, error = null) }
        val response = try {
            source.run(s.task, s.input.trim(), s.language, conversationId)
        } catch (e: CancellationException) {
            _state.update { it.copy(running = false) }
            throw e
        }
        when (val r = response) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    running = false,
                    output = r.value.output,
                    hasOutput = true,
                    conversationId = r.value.conversationId ?: conversationId,
                    notice = r.value.notice.ifBlank { AiRules.FALLBACK_NOTICE },
                    quota = r.value.quota,
                )
            }
            is ApiResult.Failure -> {
                if (r.failure.kind == FailureKind.UNAUTHORIZED) {
                    _state.update { it.copy(running = false, signedOut = true) }
                    return
                }
                val error = AiRules.errorFor(r.failure)
                // The server says consent is needed (for example the wording changed): ask again, do not just fail.
                _state.update {
                    it.copy(
                        running = false,
                        error = if (error == AiError.ConsentRequired) null else error,
                        showConsent = error == AiError.ConsentRequired || it.showConsent,
                        consent = if (error == AiError.ConsentRequired) it.consent?.copy(active = false) else it.consent,
                    )
                }
                if (error == AiError.ConsentRequired) waitingForConsent = true
                if (AiRules.shouldRefreshQuota(error)) {
                    (source.quota() as? ApiResult.Success)?.let { q -> _state.update { it.copy(quota = q.value) } }
                }
            }
        }
    }
}
