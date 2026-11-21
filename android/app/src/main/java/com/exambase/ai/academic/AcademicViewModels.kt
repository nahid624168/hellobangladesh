package com.exambase.ai.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.core.network.ApiResult
import com.exambase.core.network.CategoryDetail
import com.exambase.core.network.FailureKind
import com.exambase.core.network.LessonDetail
import com.exambase.core.network.LessonSummary
import com.exambase.core.network.ProgressStatus
import com.exambase.core.network.SubcategoryDetail
import com.exambase.core.network.SubjectDetail
import com.exambase.core.network.SubjectSort
import com.exambase.core.network.SubjectSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectDetailViewModel(id: String, repo: AcademicRepository) : LoadViewModel<SubjectDetail>({ repo.subject(id) })
class CategoryViewModel(id: String, repo: AcademicRepository) : LoadViewModel<CategoryDetail>({ repo.category(id) })
class SubcategoryViewModel(id: String, repo: AcademicRepository) : LoadViewModel<SubcategoryDetail>({ repo.subcategory(id) })

data class SubjectsUiState(
    val query: String = "",
    val sort: String = SubjectSort.ORDER,
    val featuredOnly: Boolean = false,
    val list: PagedList<SubjectSummary> = PagedList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
)

class SubjectsViewModel(private val repo: AcademicRepository) : ViewModel() {
    private val _state = MutableStateFlow(SubjectsUiState())
    val state: StateFlow<SubjectsUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Bumped on every new search so a slow, older response can never overwrite a newer one. */
    private var generation = 0

    init { reload() }

    fun onQueryChange(text: String) { _state.update { it.copy(query = text) }; reload(debounce = true) }
    fun onSortChange(sort: String) { _state.update { it.copy(sort = sort) }; reload() }
    fun onFeaturedChange(on: Boolean) { _state.update { it.copy(featuredOnly = on) }; reload() }

    fun reload(debounce: Boolean = false) {
        job?.cancel()
        val gen = ++generation
        _state.update { it.copy(loading = true, loadingMore = false, loadMoreFailed = false, error = null) }
        job = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val s = _state.value
            val result = repo.subjects(s.query, s.sort, s.featuredOnly, cursor = null)
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
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            val result = repo.subjects(s.query, s.sort, s.featuredOnly, cursor)
            if (gen != generation) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(list = it.list.append(result.value) { subject -> subject.id }, loadingMore = false) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

class LessonViewModel(private val id: String, private val repo: AcademicRepository) :
    LoadViewModel<LessonDetail>({ repo.lesson(id) }) {

    private val _actionFailed = MutableStateFlow(false)
    /** True after a bookmark or progress change could not be saved (the screen shows a short message). */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()

    init {
        // Fire and forget: opening a lesson adds it to "recently viewed". A failure here is not worth showing.
        viewModelScope.launch { repo.recordView(id) }
    }

    fun dismissActionError() { _actionFailed.value = false }

    fun toggleBookmark() {
        val before = (state.value as? LoadState.Ready)?.data?.lesson?.bookmarked ?: return
        val target = !before
        update { it.copy(lesson = it.lesson.copy(bookmarked = target)) }
        viewModelScope.launch {
            if (repo.setBookmark(id, target) is ApiResult.Failure) {
                update { it.copy(lesson = it.lesson.copy(bookmarked = before)) }
                _actionFailed.value = true
            }
        }
    }

    fun setCompleted(completed: Boolean) {
        val before = (state.value as? LoadState.Ready)?.data?.lesson?.progress ?: return
        val target = if (completed) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS
        update { it.copy(lesson = it.lesson.copy(progress = target)) }
        viewModelScope.launch {
            if (repo.setProgress(id, target) is ApiResult.Failure) {
                update { it.copy(lesson = it.lesson.copy(progress = before)) }
                _actionFailed.value = true
            }
        }
    }
}

/** "Saved" (bookmarks, paged) and "Recent" (last viewed) lessons. */
class LibraryViewModel(private val repo: AcademicRepository) : ViewModel() {
    private val _recent = MutableStateFlow<LoadState<List<LessonSummary>>>(LoadState.Loading)
    val recent: StateFlow<LoadState<List<LessonSummary>>> = _recent.asStateFlow()

    private val _saved = MutableStateFlow<LoadState<PagedList<LessonSummary>>>(LoadState.Loading)
    val saved: StateFlow<LoadState<PagedList<LessonSummary>>> = _saved.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    init { load(silent = false) }

    fun load(silent: Boolean) {
        if (!silent || _recent.value !is LoadState.Ready) _recent.value = LoadState.Loading
        if (!silent || _saved.value !is LoadState.Ready) _saved.value = LoadState.Loading
        viewModelScope.launch { keepOnSilentFailure(_recent, repo.recent().toLoadState(), silent) }
        viewModelScope.launch {
            keepOnSilentFailure(_saved, repo.bookmarks(cursor = null).toLoadState { page -> PagedList.first(page) }, silent)
        }
    }

    fun loadMoreSaved() {
        val current = (_saved.value as? LoadState.Ready)?.data ?: return
        val cursor = current.nextCursor ?: return
        if (_loadingMore.value) return
        _loadingMore.value = true
        viewModelScope.launch {
            val result = repo.bookmarks(cursor)
            if (result is ApiResult.Success) {
                _saved.update { st ->
                    if (st is LoadState.Ready) LoadState.Ready(st.data.append(result.value) { it.id }) else st
                }
            }
            _loadingMore.value = false
        }
    }

    /** A silent refresh that fails leaves the old list on screen instead of replacing it with an error. */
    private fun <T> keepOnSilentFailure(target: MutableStateFlow<LoadState<T>>, fresh: LoadState<T>, silent: Boolean) {
        val keep = silent && target.value is LoadState.Ready && fresh is LoadState.Error
        if (!keep) target.value = fresh
    }
}
