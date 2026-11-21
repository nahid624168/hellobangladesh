package com.exambase.ai.questions

import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CheckAnswerRequest
import com.exambase.core.network.CheckAnswerResponse
import com.exambase.core.network.FailureKind
import com.exambase.core.network.QuestionDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the practice engine needs from the outside; tests supply a fake. */
interface PracticeSource {
    /** Ids of the lesson's questions in display order. */
    suspend fun questionIds(lessonId: String): ApiResult<List<String>>
    suspend fun question(id: String): ApiResult<QuestionDetail>
    suspend fun check(id: String, request: CheckAnswerRequest): ApiResult<CheckAnswerResponse>
}

sealed interface Problem {
    data object Network : Problem
    data class Message(val text: String) : Problem
}

sealed interface PracticeStep {
    data object Loading : PracticeStep
    data object SignedOut : PracticeStep
    data object Empty : PracticeStep
    data class Error(val error: LoadError) : PracticeStep
    data class Asking(
        val detail: QuestionDetail,
        val selected: Set<String> = emptySet(),
        val text: String = "",
        val submitting: Boolean = false,
        val problem: Problem? = null,
    ) : PracticeStep
    data class Feedback(
        val detail: QuestionDetail,
        val selected: Set<String>,
        val text: String,
        val response: CheckAnswerResponse,
        val isLast: Boolean,
    ) : PracticeStep
    data class Done(val tally: Tally) : PracticeStep
}

data class PracticeUi(val step: PracticeStep = PracticeStep.Loading, val position: Int = 0, val total: Int = 0)

/**
 * One practice run over a lesson's questions: load list, show a question, check it, show feedback, next.
 * Plain class (no Android) so the whole flow is unit-tested with a fake [PracticeSource].
 * The correct answer is only ever known after [submit] succeeds, because that is when the server sends it.
 */
class PracticeEngine(private val source: PracticeSource, private val lessonId: String) {
    private val _state = MutableStateFlow(PracticeUi())
    val state: StateFlow<PracticeUi> = _state.asStateFlow()

    private var ids: List<String> = emptyList()
    private var index = 0
    private var tally = Tally()
    private var retryAction: suspend () -> Unit = { start() }

    suspend fun start() {
        ids = emptyList(); index = 0; tally = Tally()
        retryAction = { start() }
        publish(PracticeStep.Loading)
        when (val r = source.questionIds(lessonId)) {
            is ApiResult.Failure -> fail(r.failure.kind, r.failure.toLoadError())
            is ApiResult.Success -> {
                ids = r.value
                if (ids.isEmpty()) publish(PracticeStep.Empty) else loadCurrent()
            }
        }
    }

    suspend fun retry() = retryAction()

    fun select(optionId: String) {
        val s = _state.value.step as? PracticeStep.Asking ?: return
        if (s.submitting) return
        val type = s.detail.question.type
        publish(s.copy(selected = PracticeRules.toggle(type, s.selected, optionId), problem = null))
    }

    fun onText(text: String) {
        val s = _state.value.step as? PracticeStep.Asking ?: return
        if (s.submitting) return
        publish(s.copy(text = text.take(PracticeRules.MAX_TEXT), problem = null))
    }

    suspend fun submit() {
        val s = _state.value.step as? PracticeStep.Asking ?: return
        val type = s.detail.question.type
        if (s.submitting || !PracticeRules.canSubmit(type, s.selected, s.text)) return
        publish(s.copy(submitting = true, problem = null))
        when (val r = source.check(s.detail.question.id, PracticeRules.request(type, s.selected, s.text))) {
            is ApiResult.Success -> {
                tally = tally.add(r.value.result)
                publish(PracticeStep.Feedback(s.detail, s.selected, s.text, r.value, isLast = index >= ids.size - 1))
            }
            is ApiResult.Failure -> when (r.failure.kind) {
                FailureKind.UNAUTHORIZED -> publish(PracticeStep.SignedOut)
                FailureKind.NETWORK -> publish(s.copy(submitting = false, problem = Problem.Network))
                else -> publish(s.copy(submitting = false, problem = Problem.Message(r.failure.message)))
            }
        }
    }

    suspend fun next() {
        if (_state.value.step !is PracticeStep.Feedback) return
        index++
        loadCurrent()
    }

    private suspend fun loadCurrent() {
        retryAction = { loadCurrent() }
        while (index < ids.size) {
            publish(PracticeStep.Loading)
            when (val r = source.question(ids[index])) {
                is ApiResult.Success -> { publish(PracticeStep.Asking(r.value)); return }
                is ApiResult.Failure -> {
                    val kind = r.failure.kind
                    if (kind == FailureKind.NETWORK || kind == FailureKind.UNAUTHORIZED || kind == FailureKind.RATE_LIMITED) {
                        fail(kind, r.failure.toLoadError()); return
                    }
                    // Not found or invalid: the question was unpublished after the list loaded. Skip it.
                    ids = ids.toMutableList().also { it.removeAt(index) }
                }
            }
        }
        publish(if (ids.isEmpty()) PracticeStep.Empty else PracticeStep.Done(tally))
    }

    private fun fail(kind: FailureKind, error: LoadError) =
        publish(if (kind == FailureKind.UNAUTHORIZED) PracticeStep.SignedOut else PracticeStep.Error(error))

    private fun publish(step: PracticeStep) {
        val position = if (ids.isEmpty()) 0 else (index + 1).coerceAtMost(ids.size)
        _state.value = PracticeUi(step, position, ids.size)
    }
}
