package com.exambase.ai.auth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exambase.ai.AppContainer
import com.exambase.ai.ExamBaseApp
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import com.exambase.core.network.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ExamBaseApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

fun Context.appContainer(): AppContainer = (applicationContext as ExamBaseApp).container

/** Shared plumbing for simple "submit a form" view models. */
abstract class FormViewModel : ViewModel() {
    protected val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    protected fun <T> submit(call: suspend () -> ApiResult<T>, onSuccess: (T) -> Unit = {}) {
        if (_state.value.loading) return
        _state.value = FormState(loading = true)
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Success -> { onSuccess(r.value); _state.value = FormState(success = true) }
                is ApiResult.Failure -> _state.value = r.failure.toFormState()
            }
        }
    }

    fun consumeSuccess() { _state.value = FormState() }
}

class LoginViewModel(private val repo: AuthRepository) : FormViewModel() {
    fun login(email: String, password: String) = submit({ repo.login(email, password) })
    fun continueAsGuest() = submit({ repo.continueAsGuest() })
}

class RegisterViewModel(private val repo: AuthRepository) : FormViewModel() {
    fun register(name: String, email: String, password: String) = submit({ repo.register(name, email, password) })
}

class VerifyEmailViewModel(private val repo: AuthRepository) : FormViewModel() {
    val email: String? get() = repo.currentUser.value?.email
    fun verify(code: String) = submit({ repo.verifyEmail(code) })
    fun resend() = submit({ repo.resendVerification() })
}

class ResetPasswordViewModel(private val repo: AuthRepository) : FormViewModel() {
    /** Step 1 = ask for a code, step 2 = enter code and new password. */
    private val _step = MutableStateFlow(1)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun requestCode(email: String) = submit({ repo.requestPasswordReset(email) }, onSuccess = { _step.value = 2 })
    fun confirm(email: String, code: String, newPassword: String) =
        submit({ repo.confirmPasswordReset(email, code, newPassword) })
}

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(
        val user: UserDto,
        val dashboard: com.exambase.core.network.DashboardResponse? = null,
        val offline: Boolean = false,
    ) : HomeState
    data class Error(val network: Boolean, val message: String?) : HomeState
    data object SignedOut : HomeState
}

class HomeViewModel(
    private val repo: AuthRepository,
    private val dashboardLoader: (suspend () -> com.exambase.core.network.ApiResult<com.exambase.core.network.DashboardResponse>)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(
        repo.currentUser.value?.let { HomeState.Ready(it) } ?: HomeState.Loading,
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (_state.value !is HomeState.Ready) _state.value = HomeState.Loading
            when (val r = repo.refreshMe()) {
                is ApiResult.Success -> {
                    val dash = dashboardLoader?.invoke()
                    val dashboard = (dash as? ApiResult.Success)?.value
                    _state.value = HomeState.Ready(r.value, dashboard)
                }
                is ApiResult.Failure -> _state.value = when {
                    r.failure.kind == FailureKind.UNAUTHORIZED -> HomeState.SignedOut
                    r.failure.kind == FailureKind.NETWORK && repo.currentUser.value != null ->
                        HomeState.Ready(repo.currentUser.value!!, offline = true)
                    else -> HomeState.Error(r.failure.kind == FailureKind.NETWORK, r.failure.message)
                }
            }
        }
    }
}

class ProfileViewModel(private val repo: AuthRepository) : FormViewModel() {
    val user = repo.currentUser

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    private val _exportSummary = MutableStateFlow<String?>(null)
    val exportSummary: StateFlow<String?> = _exportSummary.asStateFlow()

    fun save(name: String, theme: String) = submit({ repo.updateProfile(name, theme) })

    fun logout() {
        viewModelScope.launch { repo.logout(); _signedOut.value = true }
    }

    fun deleteAccount(password: String) = submit({ repo.deleteAccount(password) }, onSuccess = { _signedOut.value = true })

    fun clearAvatar() = submit({ repo.clearAvatar() })

    fun requestExport() {
        viewModelScope.launch {
            when (val r = repo.exportData()) {
                is ApiResult.Success -> {
                    val d = r.value
                    _exportSummary.value =
                        "Exported ${d.exportedAt}: notes=${d.noteCount}, quizzes=${d.quizSetCount}, " +
                            "flashcards=${d.flashcardSetCount}, bookmarks=${d.bookmarkCount}, practice=${d.practiceAttemptCount}"
                }
                is ApiResult.Failure -> _exportSummary.value = r.failure.message
            }
        }
    }
}

data class SessionsUiState(
    val loading: Boolean = false,
    val sessions: List<com.exambase.core.network.SessionDto> = emptyList(),
    val error: String? = null,
)

class SessionsViewModel(private val repo: AuthRepository) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState(loading = true))
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val r = repo.listSessions()) {
                is ApiResult.Success -> _state.value = SessionsUiState(sessions = r.value)
                is ApiResult.Failure -> _state.value = SessionsUiState(error = r.failure.message)
            }
        }
    }

    fun revoke(sessionId: String) {
        viewModelScope.launch {
            when (val r = repo.revokeSession(sessionId)) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> _state.value = _state.value.copy(error = r.failure.message)
            }
        }
    }
}
