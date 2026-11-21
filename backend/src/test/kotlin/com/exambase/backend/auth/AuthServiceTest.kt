package com.exambase.backend.auth

import com.exambase.backend.ApiException
import com.exambase.backend.LoginRequest
import com.exambase.backend.PasswordResetConfirmRequest
import com.exambase.backend.RegisterRequest
import com.exambase.backend.UpdateProfileRequest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthServiceTest {
    private val reg = RegisterRequest("Student@Example.com ", "study2026", "Rafi")

    @Test fun registerNormalizesEmailAndSendsVerificationCode() {
        val (auth, mail) = TestFixtures.newAuth()
        val res = auth.register(reg)
        assertEquals("student@example.com", res.user.email)
        assertFalse(res.user.emailVerified)
        assertEquals(1, mail.sent.size)
    }

    @Test fun duplicateEmailIsRejected() {
        val (auth, _) = TestFixtures.newAuth()
        auth.register(reg)
        val e = assertFailsWith<ApiException> { auth.register(reg) }
        assertEquals("email_taken", e.code)
    }

    @Test fun weakPasswordIsRejectedWithFieldError() {
        val (auth, _) = TestFixtures.newAuth()
        val e = assertFailsWith<ApiException> { auth.register(reg.copy(password = "short1")) }
        assertTrue(e.fields!!.containsKey("password"))
    }

    @Test fun loginWithWrongPasswordFailsWithGenericError() {
        val (auth, _) = TestFixtures.newAuth()
        auth.register(reg)
        val wrong = assertFailsWith<ApiException> { auth.login(LoginRequest("student@example.com", "wrong-pass-1")) }
        val unknown = assertFailsWith<ApiException> { auth.login(LoginRequest("nobody@example.com", "study2026")) }
        assertEquals(wrong.code, unknown.code)
        assertEquals(wrong.message, unknown.message)
    }

    @Test fun refreshRotatesTokenAndReuseRevokesEverything() {
        val (auth, _) = TestFixtures.newAuth()
        val first = auth.register(reg)
        val second = auth.refresh(first.refreshToken)
        assertTrue(second.refreshToken != first.refreshToken)
        // Replaying the old token is treated as theft...
        assertFailsWith<ApiException> { auth.refresh(first.refreshToken) }
        // ...so even the newest token no longer works.
        assertFailsWith<ApiException> { auth.refresh(second.refreshToken) }
    }

    @Test fun logoutRevokesSession() {
        val (auth, _) = TestFixtures.newAuth()
        val res = auth.register(reg)
        val sessionId = auth.listSessions(UUID.fromString(res.user.id), UUID.randomUUID()).single().id
        auth.logout(UUID.fromString(sessionId))
        assertFailsWith<ApiException> { auth.refresh(res.refreshToken) }
    }

    @Test fun verifyEmailWithCorrectCodeWorksAndWrongCodeIsCounted() {
        val (auth, mail) = TestFixtures.newAuth()
        auth.register(reg)
        val code = mail.lastCode()
        val wrong = if (code == "000000") "111111" else "000000"
        assertFailsWith<ApiException> { auth.verifyEmail("student@example.com", wrong) }
        auth.verifyEmail("student@example.com", code)
        val login = auth.login(LoginRequest("student@example.com", "study2026"))
        assertTrue(login.user.emailVerified)
    }

    @Test fun codeIsLockedAfterTooManyWrongAttempts() {
        val (auth, mail) = TestFixtures.newAuth()
        auth.register(reg)
        val code = mail.lastCode()
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(AuthService.MAX_CODE_ATTEMPTS) { assertFailsWith<ApiException> { auth.verifyEmail("student@example.com", wrong) } }
        val e = assertFailsWith<ApiException> { auth.verifyEmail("student@example.com", code) }
        assertEquals("too_many_attempts", e.code)
    }

    @Test fun passwordResetChangesPasswordAndSignsOutOtherSessions() {
        val (auth, mail) = TestFixtures.newAuth()
        val res = auth.register(reg)
        auth.requestPasswordReset("student@example.com")
        auth.confirmPasswordReset(PasswordResetConfirmRequest("student@example.com", mail.lastCode(), "newpass2027"))
        assertFailsWith<ApiException> { auth.refresh(res.refreshToken) }
        assertFailsWith<ApiException> { auth.login(LoginRequest("student@example.com", "study2026")) }
        auth.login(LoginRequest("student@example.com", "newpass2027"))
    }

    @Test fun resetRequestForUnknownEmailLooksIdenticalAndSendsNothing() {
        val (auth, mail) = TestFixtures.newAuth()
        val known = run { auth.register(reg); auth.requestPasswordReset("student@example.com") }
        val before = mail.sent.size
        val unknown = auth.requestPasswordReset("ghost@example.com")
        assertEquals(known, unknown)
        assertEquals(before, mail.sent.size)
    }

    @Test fun profileUpdateValidatesValues() {
        val (auth, _) = TestFixtures.newAuth()
        val res = auth.register(reg)
        val id = UUID.fromString(res.user.id)
        val updated = auth.updateProfile(id, UpdateProfileRequest(displayName = "Rafi H", preferredLanguage = "bn", theme = "dark"))
        assertEquals("bn", updated.preferredLanguage)
        assertFailsWith<ApiException> { auth.updateProfile(id, UpdateProfileRequest(theme = "neon")) }
    }

    @Test fun deleteAccountRequiresPasswordAndFreesEmail() {
        val (auth, _) = TestFixtures.newAuth()
        val res = auth.register(reg)
        val id = UUID.fromString(res.user.id)
        assertFailsWith<ApiException> { auth.deleteAccount(id, "wrong-pass-1") }
        auth.deleteAccount(id, "study2026")
        assertFailsWith<ApiException> { auth.login(LoginRequest("student@example.com", "study2026")) }
        auth.register(reg) // email can be registered again
    }
}
