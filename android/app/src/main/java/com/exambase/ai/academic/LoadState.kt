package com.exambase.ai.academic

import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen needs to show when a load does not succeed. */
data class LoadError(val network: Boolean, val message: String?)

fun ApiFailure.toLoadError() = LoadError(network = kind == FailureKind.NETWORK, message = message.takeIf { kind != FailureKind.NETWORK })

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    /** The session is gone (refresh token rejected); the screen should leave for the login screen. */
    data object SignedOut : LoadState<Nothing>
    data class Error(val error: LoadError) : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
}

fun <T> ApiResult<T>.toLoadState(): LoadState<T> = when (this) {
    is ApiResult.Success -> LoadState.Ready(value)
    is ApiResult.Failure ->
        if (failure.kind == FailureKind.UNAUTHORIZED) LoadState.SignedOut else LoadState.Error(failure.toLoadError())
}

fun <T, R> ApiResult<T>.toLoadState(transform: (T) -> R): LoadState<R> = when (this) {
    is ApiResult.Success -> LoadState.Ready(transform(value))
    is ApiResult.Failure ->
        if (failure.kind == FailureKind.UNAUTHORIZED) LoadState.SignedOut else LoadState.Error(failure.toLoadError())
}

/** Loads one thing on creation; [load] retries. Subclasses may change the loaded value with [update]. */
open class LoadViewModel<T>(private val loader: suspend () -> ApiResult<T>) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = loader().toLoadState() }
    }

    /**
     * Reloads without flashing a spinner: the current content stays on screen until fresh data arrives, and a
     * failed refresh keeps showing what we already have. Used when returning to a screen whose data may have changed.
     */
    fun refresh() {
        if (_state.value !is LoadState.Ready) { load(); return }
        viewModelScope.launch {
            when (val fresh = loader().toLoadState()) {
                is LoadState.Ready -> _state.value = fresh
                LoadState.SignedOut -> _state.value = LoadState.SignedOut
                else -> Unit
            }
        }
    }

    protected fun update(transform: (T) -> T) {
        val current = _state.value
        if (current is LoadState.Ready) _state.value = LoadState.Ready(transform(current.data))
    }
}
