package com.exambase.ai.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.SearchHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val recent: List<String> = emptyList(),
    val suggested: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
    val typeFilter: String? = null,
)

class SearchViewModel(private val source: SearchSource) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val recent = (source.recent() as? ApiResult.Success)?.value?.items.orEmpty()
            val suggested = (source.suggested() as? ApiResult.Success)?.value?.items.orEmpty()
            _state.update { it.copy(recent = recent, suggested = suggested) }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun onTypeFilter(type: String?) {
        _state.update { it.copy(typeFilter = type) }
        if (_state.value.query.isNotBlank()) submit()
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val r = source.search(q, _state.value.typeFilter)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, results = r.value.items, recent = listOf(q.lowercase()) + it.recent.filter { x -> x != q.lowercase() }.take(9))
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

    fun useSuggestion(text: String) {
        _state.update { it.copy(query = text) }
        submit()
    }
}
