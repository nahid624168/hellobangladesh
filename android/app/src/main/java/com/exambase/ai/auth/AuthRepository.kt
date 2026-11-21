package com.exambase.ai.auth

import android.os.Build
import com.exambase.core.datastore.TokenStore
import com.exambase.core.network.ApiResult
import com.exambase.core.network.AuthResponse
import com.exambase.core.network.DataExportDto
import com.exambase.core.network.ExamBaseApi
import com.exambase.core.network.FailureKind
import com.exambase.core.network.GuestRequest
import com.exambase.core.network.LoginRequest
import com.exambase.core.network.MessageResponse
import com.exambase.core.network.PasswordResetConfirmRequest
import com.exambase.core.network.RegisterRequest
import com.exambase.core.network.SessionDto
import com.exambase.core.network.UpdateProfileRequest
import com.exambase.core.network.UploadAvatarRequest
import com.exambase.core.network.UserDto
import com.exambase.core.network.VerifyEmailRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(
    private val api: ExamBaseApi,
    private val tokens: TokenStore,
    private val deviceName: String = runCatching { Build.MODEL }.getOrNull() ?: "Android",
) {
    private val refreshMutex = Mutex()
    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    private val _guestExpiresAt = MutableStateFlow(tokens.guestExpiresAt())
    /** Epoch seconds when the guest time limit ends, or null when not in guest mode. */
    val guestExpiresAt: StateFlow<Long?> = _guestExpiresAt.asStateFlow()

    fun hasSession(): Boolean {
        val guestEnd = tokens.guestExpiresAt()
        if (guestEnd != null && System.currentTimeMillis() / 1000 >= guestEnd) {
            clearLocal() // guest time is over: the server session has expired as well
            return false
        }
        return tokens.refreshToken() != null
    }

    /** Starts a temporary 20-minute guest session (the server enforces the limit). */
    suspend fun continueAsGuest(): ApiResult<UserDto> =
        api.guestLogin(GuestRequest(deviceName)).storeSession()

    /** Ends guest mode locally (timer finished or user chose to sign up). */
    fun endGuestSession() = clearLocal()

    suspend fun register(name: String, email: String, password: String): ApiResult<UserDto> =
        api.register(RegisterRequest(email.trim(), password, name.trim(), deviceName)).storeSession()

    suspend fun login(email: String, password: String): ApiResult<UserDto> =
        api.login(LoginRequest(email.trim(), password, deviceName)).storeSession()

    /** Always clears local tokens, even if the server cannot be reached. */
    suspend fun logout() {
        tokens.accessToken()?.let { api.logout(it) }
        clearLocal()
    }

    suspend fun refreshMe(): ApiResult<UserDto> = authed { api.me(it) }.also { r ->
        if (r is ApiResult.Success) _currentUser.value = r.value
    }

    suspend fun updateProfile(name: String?, theme: String?): ApiResult<UserDto> =
        authed { api.updateProfile(it, UpdateProfileRequest(displayName = name, theme = theme)) }.also { r ->
            if (r is ApiResult.Success) _currentUser.value = r.value
        }

    suspend fun deleteAccount(password: String): ApiResult<MessageResponse> =
        authed { api.deleteAccount(it, password) }.also { if (it is ApiResult.Success) clearLocal() }

    suspend fun listSessions(): ApiResult<List<SessionDto>> = authed { api.listSessions(it) }

    suspend fun revokeSession(sessionId: String): ApiResult<MessageResponse> =
        authed { api.revokeSession(it, sessionId) }

    suspend fun uploadAvatar(contentType: String, base64Data: String): ApiResult<UserDto> =
        authed { api.uploadAvatar(it, UploadAvatarRequest(contentType, base64Data)) }.also { r ->
            if (r is ApiResult.Success) _currentUser.value = r.value
        }

    suspend fun clearAvatar(): ApiResult<UserDto> =
        authed { api.clearAvatar(it) }.also { r ->
            if (r is ApiResult.Success) _currentUser.value = r.value
        }

    suspend fun exportData(): ApiResult<DataExportDto> = authed { api.exportData(it) }

    suspend fun verifyEmail(code: String): ApiResult<MessageResponse> {
        val email = _currentUser.value?.email
            ?: return ApiResult.Failure(com.exambase.core.network.ApiFailure(FailureKind.OTHER, "no_user", "Not signed in"))
        return api.verifyEmail(VerifyEmailRequest(email, code.trim())).also { if (it is ApiResult.Success) refreshMe() }
    }

    suspend fun resendVerification(): ApiResult<MessageResponse> = authed { api.resendVerification(it) }

    suspend fun requestPasswordReset(email: String) = api.requestPasswordReset(email.trim())

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) =
        api.confirmPasswordReset(PasswordResetConfirmRequest(email.trim(), code.trim(), newPassword))

    /**
     * Runs [block] with the access token; on 401 refreshes once and retries.
     * If the refresh token is rejected the local session is cleared.
     */
    suspend fun <T> authed(block: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val used = tokens.accessToken() ?: if (refreshIfStale(null)) tokens.accessToken() else null
        if (used == null) return unauthorized()
        val first = block(used)
        if (first !is ApiResult.Failure || first.failure.kind != FailureKind.UNAUTHORIZED) return first
        if (!refreshIfStale(used)) return first
        val fresh = tokens.accessToken() ?: return first
        return block(fresh)
    }

    /**
     * Only one refresh runs at a time. If another caller already replaced the token we failed with,
     * we reuse it; refreshing twice with the same refresh token would look like token theft to the server.
     */
    private suspend fun refreshIfStale(failedAccessToken: String?): Boolean = refreshMutex.withLock {
        if (failedAccessToken != null && tokens.accessToken() != failedAccessToken) return@withLock true
        val refresh = tokens.refreshToken() ?: return@withLock false
        when (val r = api.refresh(refresh)) {
            is ApiResult.Success -> {
                tokens.save(r.value.accessToken, r.value.refreshToken)
                r.value.guestExpiresAtEpochSeconds?.let { tokens.saveGuestExpiresAt(it); _guestExpiresAt.value = it }
                _currentUser.value = r.value.user
                true
            }
            is ApiResult.Failure -> {
                if (r.failure.kind == FailureKind.UNAUTHORIZED) clearLocal()
                false
            }
        }
    }

    private fun ApiResult<AuthResponse>.storeSession(): ApiResult<UserDto> = when (this) {
        is ApiResult.Success -> {
            tokens.save(value.accessToken, value.refreshToken)
            tokens.saveGuestExpiresAt(value.guestExpiresAtEpochSeconds)
            _guestExpiresAt.value = value.guestExpiresAtEpochSeconds
            _currentUser.value = value.user
            ApiResult.Success(value.user)
        }
        is ApiResult.Failure -> this
    }

    private fun clearLocal() {
        tokens.clear()
        _guestExpiresAt.value = null
        _currentUser.value = null
    }

    private fun <T> unauthorized(): ApiResult<T> =
        ApiResult.Failure(com.exambase.core.network.ApiFailure(FailureKind.UNAUTHORIZED, "unauthorized", "Please sign in again."))
}
