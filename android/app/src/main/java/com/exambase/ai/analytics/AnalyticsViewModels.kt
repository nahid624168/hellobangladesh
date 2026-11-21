package com.exambase.ai.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exambase.ai.academic.LoadError
import com.exambase.ai.academic.toLoadError
import com.exambase.core.network.AchievementsDto
import com.exambase.core.network.AnalyticsOverview
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.SubjectPerformanceDto
import com.exambase.core.network.TrendsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val loading: Boolean = true,
    val overview: AnalyticsOverview? = null,
    val trends: TrendsDto? = null,
    val subjects: List<SubjectPerformanceDto> = emptyList(),
    val error: LoadError? = null,
    val signedOut: Boolean = false,
)

class AnalyticsViewModel(private val source: AnalyticsSource) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val o = source.overview()
            val t = source.trends(14)
            val s = source.subjects()
            if (o is ApiResult.Failure && o.failure.kind == FailureKind.UNAUTHORIZED) {
                _state.update { it.copy(loading = false, signedOut = true) }
                return@launch
            }
            val overview = (o as? ApiResult.Success)?.value
            val trends = (t as? ApiResult.Success)?.value
            val subjects = (s as? ApiResult.Success)?.value ?: emptyList()
            val err = (o as? ApiResult.Failure)?.failure?.toLoadError()
            _state.update {
                it.copy(loading = false, overview = overview, trends = trends, subjects = subjects, error = err)
            }
        }
    }
}

data class AchievementsUiState(
    val loading: Boolean = true,
    val data: AchievementsDto? = null,
    val error: LoadError? = null,
    val signedOut: Boolean = false,
)

class AchievementsViewModel(private val source: AnalyticsSource) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = source.achievements()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = r.value) }
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
