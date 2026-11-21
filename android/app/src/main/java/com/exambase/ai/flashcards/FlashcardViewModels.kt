package com.exambase.ai.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.PagedList
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CreateFlashcardRequest
import com.exambase.core.network.CreateFlashcardSetRequest
import com.exambase.core.network.FailureKind
import com.exambase.core.network.Flashcard
import com.exambase.core.network.FlashcardSet
import com.exambase.core.network.FlashcardSetDetail
import com.exambase.core.network.FlashcardSetListItem
import com.exambase.core.network.FlashcardSetStatus
import com.exambase.core.network.FlashcardSort
import com.exambase.core.network.ReviewRating
import com.exambase.core.network.StudyCard
import com.exambase.core.network.UpdateFlashcardSetRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashcardListUiState(
    val sort: String = FlashcardSort.RECENT,
    val query: String = "",
    val statusFilter: String? = null,
    val list: PagedList<FlashcardSetListItem> = PagedList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
    val actionError: String? = null,
)

class FlashcardListViewModel(private val source: FlashcardSource) : ViewModel() {
    private val _state = MutableStateFlow(FlashcardListUiState())
    val state: StateFlow<FlashcardListUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    init { reload() }

    fun onSortChange(sort: String) { _state.update { it.copy(sort = sort) }; reload() }
    fun onQueryChange(q: String) { _state.update { it.copy(query = q) } }
    fun submitSearch() { reload() }
    fun onStatusFilter(status: String?) { _state.update { it.copy(statusFilter = status) }; reload() }

    fun reload() {
        job?.cancel()
        val gen = ++generation
        val s = _state.value
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        job = viewModelScope.launch {
            val result = source.sets(s.sort, null, q = s.query.ifBlank { null }, status = s.statusFilter)
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

    fun loadMore() {
        val s = _state.value
        val cursor = s.list.nextCursor ?: return
        if (s.loading || s.loadingMore) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = source.sets(s.sort, cursor, q = s.query.ifBlank { null }, status = s.statusFilter)
            if (gen != generation) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(list = it.list.append(result.value) { it.set.id }, loadingMore = false) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun deleteSet(id: String) {
        viewModelScope.launch {
            when (val r = source.deleteSet(id)) {
                is ApiResult.Success -> reload()
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(signedOut = true)
                    else it.copy(actionError = r.failure.message ?: "Could not delete.")
                }
            }
        }
    }

    fun duplicateSet(id: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = source.duplicateSet(id)) {
                is ApiResult.Success -> { reload(); onCreated(r.value.id) }
                is ApiResult.Failure -> _state.update {
                    if (r.failure.kind == FailureKind.UNAUTHORIZED) it.copy(signedOut = true)
                    else it.copy(actionError = r.failure.message ?: "Could not duplicate.")
                }
            }
        }
    }
}

data class FlashcardDetailUiState(
    val detail: FlashcardSetDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val signedOut: Boolean = false,
    val saving: Boolean = false,
)

class FlashcardDetailViewModel(private val setId: String, private val source: FlashcardSource) : ViewModel() {
    private val _state = MutableStateFlow(FlashcardDetailUiState())
    val state: StateFlow<FlashcardDetailUiState> = _state.asStateFlow()
    init { reload() }
    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.set(setId)) {
                is ApiResult.Success -> _state.update { it.copy(detail = r.value, loading = false) }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message ?: "Unable to load.")
                }
            }
        }
    }
    fun addCard(front: String, back: String, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = source.addCard(setId, CreateFlashcardRequest(frontText = front, backText = back))) {
                is ApiResult.Success -> { _state.update { it.copy(saving = false) }; reload(); onDone() }
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message)
                }
            }
        }
    }
    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            when (source.deleteCard(cardId)) {
                is ApiResult.Success -> reload()
                is ApiResult.Failure -> { /* keep list */ }
            }
        }
    }
}

data class FlashcardEditorUiState(
    val title: String = "",
    val description: String = "",
    val status: String = FlashcardSetStatus.DRAFT,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
    val savedId: String? = null,
)

class FlashcardEditorViewModel(private val existingId: String?, private val source: FlashcardSource) : ViewModel() {
    private val _state = MutableStateFlow(FlashcardEditorUiState(loading = existingId != null))
    val state: StateFlow<FlashcardEditorUiState> = _state.asStateFlow()
    init { if (existingId != null) load() }
    private fun load() {
        viewModelScope.launch {
            when (val r = source.set(existingId!!)) {
                is ApiResult.Success -> _state.update {
                    it.copy(title = r.value.set.title, description = r.value.set.description.orEmpty(), status = r.value.set.status, loading = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message)
                }
            }
        }
    }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onStatus(v: String) = _state.update { it.copy(status = v) }
    fun save() {
        val s = _state.value
        if (s.saving || s.title.isBlank()) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (existingId == null) {
                source.createSet(CreateFlashcardSetRequest(s.title.trim(), s.description.ifBlank { null }, status = s.status))
            } else {
                source.updateSet(existingId, UpdateFlashcardSetRequest(title = s.title.trim(), description = s.description, status = s.status))
            }
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, savedId = result.value.id) }
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, signedOut = result.failure.kind == FailureKind.UNAUTHORIZED, error = result.failure.message)
                }
            }
        }
    }
}

data class StudyUiState(
    val queue: List<StudyCard> = emptyList(),
    val index: Int = 0,
    val flipped: Boolean = false,
    val loading: Boolean = true,
    val reviewing: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
    val signedOut: Boolean = false,
    val reviewedCount: Int = 0,
)

class StudyViewModel(private val setId: String, private val source: FlashcardSource) : ViewModel() {
    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()
    init { loadDue() }
    fun loadDue() {
        _state.update { it.copy(loading = true, error = null, done = false, index = 0, flipped = false, reviewedCount = 0) }
        viewModelScope.launch {
            when (val r = source.due(setId, 30)) {
                is ApiResult.Success -> _state.update {
                    it.copy(queue = r.value, loading = false, done = r.value.isEmpty())
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message)
                }
            }
        }
    }
    fun flip() = _state.update { it.copy(flipped = !it.flipped) }
    fun rate(rating: String) {
        val s = _state.value
        if (s.reviewing || s.queue.isEmpty() || s.index !in s.queue.indices) return
        val card = s.queue[s.index]
        _state.update { it.copy(reviewing = true) }
        viewModelScope.launch {
            when (val r = source.review(card.card.id, rating)) {
                is ApiResult.Success -> {
                    val nextIndex = s.index + 1
                    _state.update {
                        it.copy(
                            reviewing = false,
                            flipped = false,
                            reviewedCount = it.reviewedCount + 1,
                            index = nextIndex,
                            done = nextIndex >= s.queue.size,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(reviewing = false, signedOut = r.failure.kind == FailureKind.UNAUTHORIZED, error = r.failure.message)
                }
            }
        }
    }
}
