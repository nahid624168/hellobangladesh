package com.exambase.ai.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.PagedList
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.ExamDetail
import com.exambase.core.network.ExamQuestionDto
import com.exambase.core.network.ExamResultDto
import com.exambase.core.network.ExamSummary
import com.exambase.core.network.FailureKind
import com.exambase.core.network.SaveExamAnswerRequest
import com.exambase.core.network.StartExamResponse
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ExamListUiState(
    val list: PagedList<ExamSummary> = PagedList(),
    val loading: Boolean = true,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
)

class ExamListViewModel(private val source: ExamSource) : ViewModel() {
    private val _state = MutableStateFlow(ExamListUiState())
    val state: StateFlow<ExamListUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.exams(null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, list = PagedList(r.value.items, r.value.nextCursor))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.toLoadError(),
                    )
                }
            }
        }
    }
}

data class ExamDetailUiState(
    val loading: Boolean = true,
    val detail: ExamDetail? = null,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
)

class ExamDetailViewModel(private val examId: String, private val source: ExamSource) : ViewModel() {
    private val _state = MutableStateFlow(ExamDetailUiState())
    val state: StateFlow<ExamDetailUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.exam(examId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, detail = r.value) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.toLoadError(),
                    )
                }
            }
        }
    }
}

data class ExamPlayerUiState(
    val loading: Boolean = true,
    val attemptId: String? = null,
    val questions: List<ExamQuestionDto> = emptyList(),
    val index: Int = 0,
    val answers: Map<String, List<String>> = emptyMap(),
    val textAnswers: Map<String, String> = emptyMap(),
    val remainingMs: Long? = null,
    val error: String? = null,
    val result: ExamResultDto? = null,
    val submitting: Boolean = false,
    val signedOut: Boolean = false,
)

class ExamPlayerViewModel(private val examId: String, private val source: ExamSource) : ViewModel() {
    private val _state = MutableStateFlow(ExamPlayerUiState())
    val state: StateFlow<ExamPlayerUiState> = _state.asStateFlow()
    private var tickJob: Job? = null

    init { start() }

    fun start() {
        _state.update { it.copy(loading = true, error = null, result = null) }
        viewModelScope.launch {
            when (val r = source.start(examId)) {
                is ApiResult.Success -> {
                    applyStart(r.value)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message,
                    )
                }
            }
        }
    }

    private fun applyStart(resp: StartExamResponse) {
        _state.update {
            it.copy(
                loading = false,
                attemptId = resp.attempt.id,
                questions = resp.questions.sortedBy { q -> q.position },
                index = 0,
            )
        }
        startTicker(resp.attempt.deadlineAt)
    }

    fun selectOption(questionId: String, optionId: String, multi: Boolean) {
        _state.update { s ->
            val cur = s.answers[questionId].orEmpty()
            val next = if (multi) {
                if (optionId in cur) cur - optionId else cur + optionId
            } else listOf(optionId)
            s.copy(answers = s.answers + (questionId to next))
        }
        persist(questionId)
    }

    fun setText(questionId: String, text: String) {
        _state.update { it.copy(textAnswers = it.textAnswers + (questionId to text)) }
    }

    fun saveText(questionId: String) = persist(questionId)

    fun next() = _state.update { it.copy(index = (it.index + 1).coerceAtMost(it.questions.lastIndex.coerceAtLeast(0))) }
    fun prev() = _state.update { it.copy(index = (it.index - 1).coerceAtLeast(0)) }

    fun submit() {
        val attemptId = _state.value.attemptId ?: return
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = source.submit(attemptId)) {
                is ApiResult.Success -> {
                    tickJob?.cancel()
                    _state.update { it.copy(submitting = false, result = r.value) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message,
                    )
                }
            }
        }
    }

    private fun persist(questionId: String) {
        val attemptId = _state.value.attemptId ?: return
        val s = _state.value
        viewModelScope.launch {
            source.saveAnswer(
                attemptId,
                SaveExamAnswerRequest(
                    questionId = questionId,
                    selected = s.answers[questionId].orEmpty(),
                    text = s.textAnswers[questionId],
                    clientKey = UUID.randomUUID().toString(),
                ),
            )
        }
    }

    private fun startTicker(deadlineAt: String) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val end = runCatching { java.time.Instant.parse(deadlineAt).toEpochMilli() }.getOrNull()
                if (end != null) {
                    val left = (end - System.currentTimeMillis()).coerceAtLeast(0)
                    _state.update { it.copy(remainingMs = left) }
                    if (left == 0L) {
                        submit()
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}

data class ExamResultUiState(
    val loading: Boolean = true,
    val result: ExamResultDto? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
)

class ExamResultViewModel(private val attemptId: String, private val source: ExamSource) : ViewModel() {
    private val _state = MutableStateFlow(ExamResultUiState())
    val state: StateFlow<ExamResultUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.result(attemptId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, result = r.value) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message,
                    )
                }
            }
        }
    }
}
