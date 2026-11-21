package com.exambase.ai.auth

import com.exambase.core.datastore.TokenStore
import com.exambase.core.network.ApiFailure
import com.exambase.core.network.ApiResult
import com.exambase.core.network.AuthResponse
import com.exambase.core.network.ExamBaseApi
import com.exambase.core.network.FailureKind
import com.exambase.core.network.LoginRequest
import com.exambase.core.network.MessageResponse
import com.exambase.core.network.PasswordResetConfirmRequest
import com.exambase.core.network.RegisterRequest
import com.exambase.core.network.UpdateProfileRequest
import com.exambase.core.network.UserDto
import com.exambase.core.network.VerifyEmailRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val user = UserDto("1", "a@b.com", "Rafi", false, "en", "system", "2026-01-01T00:00:00Z")
private fun auth(access: String, refresh: String) = AuthResponse(access, refresh, 900, user)
private fun failure(kind: FailureKind) = ApiResult.Failure(ApiFailure(kind, kind.name, kind.name))

private class FakeTokens : TokenStore {
    var access: String? = null
    var refresh: String? = null
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun save(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
    override fun clear() { access = null; refresh = null }
}

private class FakeApi : ExamBaseApi {
    var validAccess = "access-1"
    var refreshResult: ApiResult<AuthResponse> = ApiResult.Success(auth("access-2", "refresh-2"))
    var refreshCalls = 0
    var meCalls = 0

    override suspend fun login(req: LoginRequest): ApiResult<AuthResponse> = ApiResult.Success(auth("access-1", "refresh-1"))
    override suspend fun register(req: RegisterRequest): ApiResult<AuthResponse> = ApiResult.Success(auth("access-1", "refresh-1"))
    override suspend fun refresh(refreshToken: String): ApiResult<AuthResponse> {
        refreshCalls++
        (refreshResult as? ApiResult.Success)?.let { validAccess = it.value.accessToken }
        return refreshResult
    }
    override suspend fun me(accessToken: String): ApiResult<UserDto> {
        meCalls++
        return if (accessToken == validAccess) ApiResult.Success(user) else failure(FailureKind.UNAUTHORIZED)
    }
    override suspend fun logout(accessToken: String) = ApiResult.Success(MessageResponse("ok"))
    override suspend fun verifyEmail(req: VerifyEmailRequest) = ApiResult.Success(MessageResponse("ok"))
    override suspend fun resendVerification(accessToken: String) = ApiResult.Success(MessageResponse("ok"))
    override suspend fun requestPasswordReset(email: String) = ApiResult.Success(MessageResponse("ok"))
    override suspend fun confirmPasswordReset(req: PasswordResetConfirmRequest) = ApiResult.Success(MessageResponse("ok"))
    override suspend fun updateProfile(accessToken: String, req: UpdateProfileRequest): ApiResult<UserDto> = ApiResult.Success(user)
    override suspend fun deleteAccount(accessToken: String, password: String) = ApiResult.Success(MessageResponse("ok"))
}

class AuthRepositoryTest {
    private val api = FakeApi()
    private val tokens = FakeTokens()
    private val repo = AuthRepository(api, tokens, deviceName = "test")

    @Test fun loginStoresTokensAndUser() = runBlocking {
        repo.login("a@b.com", "study2026")
        assertEquals("refresh-1", tokens.refresh)
        assertEquals("Rafi", repo.currentUser.value?.displayName)
        assertTrue(repo.hasSession())
    }

    @Test fun expiredAccessTokenIsRefreshedOnceAndRetried() = runBlocking {
        repo.login("a@b.com", "study2026")
        api.validAccess = "something-else" // server no longer accepts access-1
        val result = repo.refreshMe()
        assertTrue(result is ApiResult.Success)
        assertEquals(1, api.refreshCalls)
        assertEquals("access-2", tokens.access)
        assertEquals("refresh-2", tokens.refresh)
    }

    @Test fun rejectedRefreshTokenClearsSession() = runBlocking {
        repo.login("a@b.com", "study2026")
        api.validAccess = "something-else"
        api.refreshResult = failure(FailureKind.UNAUTHORIZED)
        val result = repo.refreshMe()
        assertTrue(result is ApiResult.Failure)
        assertNull(tokens.refresh)
        assertNull(repo.currentUser.value)
    }

    @Test fun networkErrorDuringRefreshKeepsSession() = runBlocking {
        repo.login("a@b.com", "study2026")
        api.validAccess = "something-else"
        api.refreshResult = failure(FailureKind.NETWORK)
        repo.refreshMe()
        assertEquals("refresh-1", tokens.refresh)
    }

    @Test fun logoutClearsLocalTokens() = runBlocking {
        repo.login("a@b.com", "study2026")
        repo.logout()
        assertNull(tokens.access)
        assertNull(tokens.refresh)
    }
}
