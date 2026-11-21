package com.exambase.ai.quizzes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.LoadViewModel
import com.exambase.ai.academic.PagedList
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.QuizSetListItem
import com.exambase.core.network.QuizSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizListUiState(
    val sort: String = QuizSort.RECENT,
    val query: String = "",
    val statusFilter: String? = null, // null = all (non-archived default on server)
    val list: PagedList<QuizSetListItem> = PagedList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
    val actionError: String? = null,
    val actionBusy: Boolean = false,
)

/**
 * The quiz list shown from Home. Search, filters, create and edit belong to My Quiz Sets (Step 17), which will
 * extend this list; here it is paged and sortable only.
 */
class QuizListViewModel(private val source: QuizSource) : ViewModel() {
    private val _state = MutableStateFlow(QuizListUiState())
    val state: StateFlow<QuizListUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Bumped on every reload so a slow, older response can never overwrite a newer one. */
    private var generation = 0

    init { reload() }

    fun onSortChange(sort: String) { _state.update { it.copy(sort = sort) }; reload() }

    fun onQueryChange(query: String) { _state.update { it.copy(query = query) } }

    fun submitSearch() { reload() }

    fun onStatusFilter(status: String?) { _state.update { it.copy(statusFilter = status) }; reload() }

    fun clearActionError() { _state.update { it.copy(actionError = null) } }

    fun reload() {
        job?.cancel()
        val gen = ++generation
        val s = _state.value
        _state.update { it.copy(loading = true, loadingMore = false, loadMoreFailed = false, error = null, actionError = null) }
        job = viewModelScope.launch {
            val result = source.quizSets(
                sort = s.sort,
                cursor = null,
                q = s.query.ifBlank { null },
                status = s.statusFilter,
            )
            if (gen != generation) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(list = PagedList.first(result.value), loading = false) }
                is ApiResult.Failure -> _state.update {
                    if (result.failure.kind == FailureKind.UNAUTHORIZED) it.copy(loading = false, signedOut = true)
                    else it.copy(list = PagedList(), loading = false, error = result.failure.toLoadError())
                }
            }
        }
    }

    /** Refreshes in place when returning from a quiz: the card counts may have changed, and the list stays visible. */
    fun refreshQuietly() {
        if (_state.value.loading || _state.value.list.items.isEmpty()) return
        val gen = ++generation
        val s = _state.value
        viewModelScope.launch {
            val result = source.quizSets(
                sort = s.sort,
                cursor = null,
                q = s.query.ifBlank { null },
                status = s.statusFilter,
            )
            if (gen != generation) return@launch
            if (result is ApiResult.Success) _state.update { it.copy(list = PagedList.first(result.value)) }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.list.nextCursor ?: return
        if (s.loading || s.loadingMore) return
        val gen = generation
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            val result = source.quizSets(
                sort = s.sort,
                cursor = cursor,
                q = s.query.ifBlank { null },
                status = s.statusFilter,
            )
            if (gen != generation) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(list = it.list.append(result.value) { item -> item.quizSet.id }, loadingMore = false) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    fun deleteSet(id: String, onDone: () -> Unit = {}) {
        if (_state.value.actionBusy) return
        _state.update { it.copy(actionBusy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = source.deleteQuizSet(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionBusy = false) }
                    reload()
                    onDone()
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(actionBusy = false, signedOut = true)
                    else it.copy(actionBusy = false, actionError = r.failure.message ?: "Could not delete.")
                }
            }
        }
    }

    fun duplicateSet(id: String, onCreated: (String) -> Unit) {
        if (_state.value.actionBusy) return
        _state.update { it.copy(actionBusy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = source.duplicateQuizSet(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionBusy = false) }
                    reload()
                    onCreated(r.value.id)
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(actionBusy = false, signedOut = true)
                    else it.copy(actionBusy = false, actionError = r.failure.message ?: "Could not duplicate.")
                }
            }
        }
    }

    fun archiveSet(id: String) {
        if (_state.value.actionBusy) return
        _state.update { it.copy(actionBusy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = source.updateQuizSet(id, com.exambase.core.network.UpdateQuizSetRequest(status = com.exambase.core.network.QuizStatus.ARCHIVED))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionBusy = false) }
                    reload()
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(actionBusy = false, signedOut = true)
                    else it.copy(actionBusy = false, actionError = r.failure.message ?: "Could not archive.")
                }
            }
        }
    }
}

class QuizDetailViewModel(id: String, source: QuizSource) : LoadViewModel<QuizDetailData>({ source.loadDetail(id) })

/** Starts or resumes through the server, and reloads the same attempt after navigation/process recreation. */
data class QuizPlayerUiState(
    val attempt: com.exambase.core.network.QuizAttemptView? = null,
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val result: com.exambase.core.network.QuizResult? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
    val expired: Boolean = false,
)

class QuizPlayerViewModel(
    private val quizSetId: String,
    private val attemptId: String?,
    private val source: QuizSource,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizPlayerUiState())
    val state: StateFlow<QuizPlayerUiState> = _state.asStateFlow()

    init { if (attemptId == null) start() else reload() }

    fun start() {
        if (!_state.value.loading) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.startAttempt(quizSetId)) {
                is ApiResult.Success -> applyAttempt(r.value)
                is ApiResult.Failure -> handleFailure(r.failure.kind.name, r.failure.code, r.failure.message)
            }
        }
    }

    fun reload() {
        val id = attemptId ?: return start()
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.attempt(id)) {
                is ApiResult.Success -> applyAttempt(r.value)
                is ApiResult.Failure -> handleFailure(r.failure.kind.name, r.failure.code, r.failure.message)
            }
        }
    }

    fun submit(answers: List<com.exambase.core.network.QuizAnswerRequest>) {
        val id = _state.value.attempt?.attemptId ?: return
        if (_state.value.submitting || _state.value.result != null) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = source.submit(id, com.exambase.core.network.SubmitQuizRequest(answers))) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, result = r.value) }
                is ApiResult.Failure -> {
                    val expired = r.failure.message?.contains("attempt_expired", ignoreCase = true) == true
                    _state.update { it.copy(submitting = false, expired = expired, error = r.failure.message ?: "Unable to submit. Please retry.") }
                    if (expired) reload()
                }
            }
        }
    }

    private fun applyAttempt(value: com.exambase.core.network.QuizAttemptView) {
        _state.update {
            it.copy(loading = false, attempt = value, result = value.result,
                expired = value.status == com.exambase.core.network.AttemptStatus.EXPIRED, error = null)
        }
    }

    private fun handleFailure(kind: String, code: String, message: String?) {
        val expired = code == com.exambase.core.network.QuizErrorCodes.ATTEMPT_EXPIRED || message?.contains("attempt_expired", ignoreCase = true) == true
        _state.update { it.copy(loading = false, signedOut = kind == "UNAUTHORIZED", expired = expired,
            error = message ?: if (expired) "This attempt has expired." else "Unable to load this attempt.") }
    }
}

/** Loads a finished attempt for the result and answer-review screen. */
data class QuizResultUiState(
    val attempt: com.exambase.core.network.QuizAttemptView? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val signedOut: Boolean = false,
)

class QuizResultViewModel(private val attemptId: String, private val source: QuizSource) : ViewModel() {
    private val _state = MutableStateFlow(QuizResultUiState())
    val state: StateFlow<QuizResultUiState> = _state.asStateFlow()
    init { reload() }
    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.attempt(attemptId)) {
                is ApiResult.Success -> _state.update { it.copy(attempt = r.value, loading = false, error = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message ?: "Unable to load result.") }
            }
        }
    }
}

data class QuizHistoryUiState(
    val items: List<com.exambase.core.network.QuizAttemptSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
)

class QuizHistoryViewModel(private val quizSetId: String, private val source: QuizSource) : ViewModel() {
    private val _state = MutableStateFlow(QuizHistoryUiState())
    val state: StateFlow<QuizHistoryUiState> = _state.asStateFlow()
    init { reload() }
    fun reload() {
        _state.update { it.copy(loading = true, error = null, loadingMore = false) }
        viewModelScope.launch {
            when (val r = source.attempts(quizSetId, null)) {
                is ApiResult.Success -> _state.update { it.copy(items = r.value.items.distinctBy { a -> a.attemptId }, nextCursor = r.value.nextCursor, loading = false) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message ?: "Unable to load history.") }
            }
        }
    }
    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore || _state.value.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = source.attempts(quizSetId, cursor)) {
                is ApiResult.Success -> _state.update { s -> s.copy(items = (s.items + r.value.items).distinctBy { it.attemptId }, nextCursor = r.value.nextCursor, loadingMore = false) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, error = r.failure.message ?: "Unable to load more attempts.") }
            }
        }
    }
}

// ---- Step 17: create / edit / manage questions ---------------------------------

data class QuizEditorUiState(
    val title: String = "",
    val description: String = "",
    val status: String = QuizStatus.DRAFT,
    val timeLimitSeconds: String = "",
    val passingPercent: String = "",
    val maxAttempts: String = "",
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val showExplanations: Boolean = true,
    val questionIds: List<String> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val signedOut: Boolean = false,
    val savedId: String? = null,
)

/**
 * Create a new quiz set or edit an existing one. Question order is managed on the
 * dedicated "add questions" screen; this editor only keeps the current id list.
 */
class QuizEditorViewModel(
    private val existingId: String?,
    private val source: QuizSource,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizEditorUiState(loading = existingId != null))
    val state: StateFlow<QuizEditorUiState> = _state.asStateFlow()

    init {
        if (existingId != null) load()
    }

    private fun load() {
        val id = existingId ?: return
        viewModelScope.launch {
            when (val r = source.quizSet(id)) {
                is ApiResult.Success -> {
                    val qs = r.value.quizSet
                    val s = qs.settings
                    _state.update {
                        it.copy(
                            title = qs.title,
                            description = qs.description.orEmpty(),
                            status = qs.status,
                            timeLimitSeconds = s.timeLimitSeconds?.toString().orEmpty(),
                            passingPercent = s.passingPercent?.toString().orEmpty(),
                            maxAttempts = s.maxAttempts?.toString().orEmpty(),
                            shuffleQuestions = s.shuffleQuestions,
                            shuffleOptions = s.shuffleOptions,
                            showExplanations = s.showExplanations,
                            questionIds = r.value.questionIds,
                            loading = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(loading = false, signedOut = true)
                    else it.copy(loading = false, error = r.failure.message ?: "Unable to load quiz set.")
                }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v, fieldErrors = it.fieldErrors - "title") }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onStatus(v: String) = _state.update { it.copy(status = v) }
    fun onTimeLimit(v: String) = _state.update { it.copy(timeLimitSeconds = v.filter { c -> c.isDigit() }) }
    fun onPassingPercent(v: String) = _state.update { it.copy(passingPercent = v.filter { c -> c.isDigit() }) }
    fun onMaxAttempts(v: String) = _state.update { it.copy(maxAttempts = v.filter { c -> c.isDigit() }) }
    fun onShuffleQuestions(v: Boolean) = _state.update { it.copy(shuffleQuestions = v) }
    fun onShuffleOptions(v: Boolean) = _state.update { it.copy(shuffleOptions = v) }
    fun onShowExplanations(v: Boolean) = _state.update { it.copy(showExplanations = v) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val title = s.title.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(fieldErrors = mapOf("title" to "Title is required")) }
            return
        }
        val settings = com.exambase.core.network.QuizSettings(
            timeLimitSeconds = s.timeLimitSeconds.toIntOrNull()?.takeIf { it > 0 },
            passingPercent = s.passingPercent.toIntOrNull()?.takeIf { it in 1..100 },
            maxAttempts = s.maxAttempts.toIntOrNull()?.takeIf { it > 0 },
            shuffleQuestions = s.shuffleQuestions,
            shuffleOptions = s.shuffleOptions,
            showExplanations = s.showExplanations,
        )
        _state.update { it.copy(saving = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = if (existingId == null) {
                source.createQuizSet(
                    com.exambase.core.network.CreateQuizSetRequest(
                        title = title,
                        description = s.description.ifBlank { null },
                        status = s.status,
                        settings = settings,
                    ),
                )
            } else {
                source.updateQuizSet(
                    existingId,
                    com.exambase.core.network.UpdateQuizSetRequest(
                        title = title,
                        description = s.description,
                        status = s.status,
                        settings = settings,
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, savedId = result.value.id) }
                is ApiResult.Failure -> _state.update {
                    if (result.failure.kind == FailureKind.UNAUTHORIZED) it.copy(saving = false, signedOut = true)
                    else it.copy(
                        saving = false,
                        error = result.failure.message ?: "Could not save.",
                        fieldErrors = result.failure.fields,
                    )
                }
            }
        }
    }
}

data class QuizQuestionsUiState(
    val selectedIds: List<String> = emptyList(),
    val bankItems: List<com.exambase.core.network.QuestionSummary> = emptyList(),
    val bankCursor: String? = null,
    val query: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Pick ordered questions from the bank for a quiz set. Uses the existing question list API
 * with an optional search string; the caller is expected to have a lesson context or leave
 * the bank empty until the user searches (server allows empty filters).
 */
class QuizQuestionsViewModel(
    private val quizSetId: String,
    private val source: QuizSource,
    private val questions: com.exambase.ai.questions.QuestionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizQuestionsUiState())
    val state: StateFlow<QuizQuestionsUiState> = _state.asStateFlow()

    init { loadExisting() }

    private fun loadExisting() {
        viewModelScope.launch {
            when (val r = source.quizSet(quizSetId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedIds = r.value.questionIds, loading = false) }
                    searchBank()
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(loading = false, signedOut = true)
                    else it.copy(loading = false, error = r.failure.message ?: "Unable to load questions.")
                }
            }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    fun searchBank() {
        val q = _state.value.query.ifBlank { null }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = questions.search(q = q, cursor = null, limit = 50)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        bankItems = r.value.items,
                        bankCursor = r.value.nextCursor,
                        loading = false,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(loading = false, signedOut = true)
                    else it.copy(loading = false, error = r.failure.message ?: "Unable to load question bank.", bankItems = emptyList())
                }
            }
        }
    }

    fun add(id: String) {
        _state.update { s ->
            if (s.selectedIds.contains(id) || s.selectedIds.size >= QuizRepository.MAX_QUESTIONS) s
            else s.copy(selectedIds = s.selectedIds + id)
        }
    }

    fun remove(id: String) = _state.update { it.copy(selectedIds = it.selectedIds - id) }

    fun moveUp(id: String) {
        _state.update { s ->
            val idx = s.selectedIds.indexOf(id)
            if (idx <= 0) s else s.copy(selectedIds = s.selectedIds.toMutableList().apply {
                val t = this[idx - 1]; this[idx - 1] = this[idx]; this[idx] = t
            })
        }
    }

    fun moveDown(id: String) {
        _state.update { s ->
            val idx = s.selectedIds.indexOf(id)
            if (idx < 0 || idx >= s.selectedIds.lastIndex) s else s.copy(selectedIds = s.selectedIds.toMutableList().apply {
                val t = this[idx + 1]; this[idx + 1] = this[idx]; this[idx] = t
            })
        }
    }

    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = source.setQuizQuestions(quizSetId, _state.value.selectedIds)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true, selectedIds = r.value.questionIds) }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(saving = false, signedOut = true)
                    else it.copy(saving = false, error = r.failure.message ?: "Could not save questions.")
                }
            }
        }
    }
}
