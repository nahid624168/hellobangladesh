package com.exambase.ai.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.PagedList
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.ContestDetail
import com.exambase.core.network.ContestQuestionDto
import com.exambase.core.network.ContestSummary
import com.exambase.core.network.FailureKind
import com.exambase.core.network.LeaderboardPage
import com.exambase.core.network.ParticipantDto
import com.exambase.core.network.SaveContestAnswerRequest
import com.exambase.core.network.SubmitContestResponse
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ContestListUiState(
    val list: PagedList<ContestSummary> = PagedList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
    val onlineOnlyHint: Boolean = true,
)

class ContestListViewModel(private val source: ContestSource) : ViewModel() {
    private val _state = MutableStateFlow(ContestListUiState())
    val state: StateFlow<ContestListUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    init { reload() }

    fun reload() {
        job?.cancel()
        val gen = ++generation
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val r = source.contests(null, null)) {
                is ApiResult.Success -> if (gen == generation) {
                    _state.update {
                        it.copy(
                            loading = false,
                            list = PagedList(items = r.value.items, nextCursor = r.value.nextCursor),
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> if (gen == generation) {
                    _state.update {
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

    fun loadMore() {
        val cursor = _state.value.list.nextCursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = source.contests(null, cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        list = it.list.copy(
                            items = it.list.items + r.value.items,
                            nextCursor = r.value.nextCursor,
                        ),
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED)
                }
            }
        }
    }
}

data class ContestDetailUiState(
    val loading: Boolean = true,
    val detail: ContestDetail? = null,
    val error: LoadError? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    val signedOut: Boolean = false,
    val onlineOnlyHint: Boolean = true,
)

class ContestDetailViewModel(
    private val contestId: String,
    private val source: ContestSource,
) : ViewModel() {
    private val _state = MutableStateFlow(ContestDetailUiState())
    val state: StateFlow<ContestDetailUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        viewModelScope.launch {
            when (val r = source.contest(contestId)) {
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

    fun join() = act { source.join(contestId).also { if (it is ApiResult.Success) reload() } }
    fun leave() = act { source.leave(contestId).also { if (it is ApiResult.Success) reload() } }

    private fun act(block: suspend () -> ApiResult<*>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = block()) {
                is ApiResult.Success -> _state.update { it.copy(busy = false) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        busy = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        actionError = r.failure.message,
                    )
                }
            }
        }
    }
}

data class ContestPlayerUiState(
    val loading: Boolean = true,
    val participant: ParticipantDto? = null,
    val questions: List<ContestQuestionDto> = emptyList(),
    val index: Int = 0,
    /** questionId → selected option ids or text */
    val answers: Map<String, List<String>> = emptyMap(),
    val textAnswers: Map<String, String> = emptyMap(),
    val deadlineAt: String? = null,
    val remainingMs: Long? = null,
    val saving: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val result: SubmitContestResponse? = null,
    val signedOut: Boolean = false,
    val onlineOnlyHint: Boolean = true,
)

class ContestPlayerViewModel(
    private val contestId: String,
    private val source: ContestSource,
) : ViewModel() {
    private val _state = MutableStateFlow(ContestPlayerUiState())
    val state: StateFlow<ContestPlayerUiState> = _state.asStateFlow()
    private var tickJob: Job? = null

    init { start() }

    fun start() {
        _state.update { it.copy(loading = true, error = null, result = null) }
        viewModelScope.launch {
            when (val r = source.start(contestId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            participant = r.value.participant,
                            questions = r.value.questions.sortedBy { q -> q.position },
                            deadlineAt = r.value.participant.deadlineAt,
                            index = 0,
                        )
                    }
                    startTicker(r.value.participant.deadlineAt)
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

    fun selectOption(questionId: String, optionId: String, multi: Boolean) {
        _state.update { s ->
            val current = s.answers[questionId].orEmpty()
            val next = if (multi) {
                if (optionId in current) current - optionId else current + optionId
            } else listOf(optionId)
            s.copy(answers = s.answers + (questionId to next))
        }
        persist(questionId)
    }

    fun setText(questionId: String, text: String) {
        _state.update { it.copy(textAnswers = it.textAnswers + (questionId to text)) }
    }

    fun saveText(questionId: String) {
        persist(questionId)
    }

    fun next() {
        _state.update { s -> s.copy(index = (s.index + 1).coerceAtMost(s.questions.lastIndex.coerceAtLeast(0))) }
    }

    fun prev() {
        _state.update { s -> s.copy(index = (s.index - 1).coerceAtLeast(0)) }
    }

    fun submit() {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = source.submit(contestId)) {
                is ApiResult.Success -> {
                    tickJob?.cancel()
                    _state.update { it.copy(submitting = false, result = r.value, participant = r.value.participant) }
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
        val s = _state.value
        val selected = s.answers[questionId].orEmpty()
        val text = s.textAnswers[questionId]
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            source.saveAnswer(
                contestId,
                SaveContestAnswerRequest(
                    questionId = questionId,
                    selected = selected,
                    text = text,
                    clientKey = UUID.randomUUID().toString(),
                ),
            )
            _state.update { it.copy(saving = false) }
        }
    }

    private fun startTicker(deadlineAt: String?) {
        tickJob?.cancel()
        if (deadlineAt == null) return
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

data class LeaderboardUiState(
    val loading: Boolean = true,
    val page: LeaderboardPage? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
    val onlineOnlyHint: Boolean = true,
)

class ContestLeaderboardViewModel(
    private val contestId: String,
    private val source: ContestSource,
) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        reload()
        // Lightweight poll while on screen (WS client lands with full realtime later)
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                reload(silent = true)
            }
        }
    }

    fun reload(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.leaderboard(contestId, null)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, page = r.value) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = if (silent) it.error else r.failure.message,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
