package com.exambase.ai.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadViewModel
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.PracticeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PracticeViewModel(lessonId: String, source: PracticeSource) : ViewModel() {
    private val engine = PracticeEngine(source, lessonId)
    val state: StateFlow<PracticeUi> = engine.state

    init { viewModelScope.launch { engine.start() } }

    fun retry() { viewModelScope.launch { engine.retry() } }
    fun restart() { viewModelScope.launch { engine.start() } }
    fun select(optionId: String) = engine.select(optionId)
    fun onText(text: String) = engine.onText(text)
    fun submit() { viewModelScope.launch { engine.submit() } }
    fun next() { viewModelScope.launch { engine.next() } }
}

/** "3 of 10 tried" shown on the lesson screen. A failure just hides the card; it never blocks the lesson. */
class LessonPracticeViewModel(lessonId: String, repo: QuestionRepository) :
    LoadViewModel<PracticeSummary>({ repo.practiceSummary(lessonId) })

// ---- Step 14b: practice history, bookmarks, reports ----

data class PracticeHistoryUiState(
    val items: List<com.exambase.core.network.PracticeAttemptDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
)

class PracticeHistoryViewModel(
    private val lessonId: String?,
    private val repo: QuestionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeHistoryUiState())
    val state: StateFlow<PracticeHistoryUiState> = _state.asStateFlow()
    init { reload() }
    fun reload() {
        _state.update { it.copy(loading = true, error = null, loadingMore = false) }
        viewModelScope.launch {
            when (val r = repo.practiceHistory(lessonId = lessonId, cursor = null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(items = r.value.items.distinctBy { a -> a.attemptId }, nextCursor = r.value.nextCursor, loading = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        signedOut = r.failure.kind == FailureKind.UNAUTHORIZED,
                        error = r.failure.message ?: "Unable to load history.",
                    )
                }
            }
        }
    }
    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore || _state.value.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = repo.practiceHistory(lessonId = lessonId, cursor = cursor)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        items = (s.items + r.value.items).distinctBy { it.attemptId },
                        nextCursor = r.value.nextCursor,
                        loadingMore = false,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, error = r.failure.message ?: "Unable to load more.")
                }
            }
        }
    }
}
