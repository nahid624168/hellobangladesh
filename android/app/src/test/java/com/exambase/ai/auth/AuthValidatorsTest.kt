package com.exambase.ai.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorsTest {
    @Test fun emailEmptyIsRejected() = assertEquals(FieldError.EMPTY, AuthValidators.email("  "))
    @Test fun emailWithoutDomainIsRejected() = assertEquals(FieldError.INVALID_EMAIL, AuthValidators.email("a@b"))
    @Test fun validEmailIsAccepted() = assertNull(AuthValidators.email("student@example.com"))

    @Test fun shortPasswordIsWeak() = assertEquals(FieldError.WEAK_PASSWORD, AuthValidators.password("abc12"))
    @Test fun passwordWithoutDigitIsWeak() = assertEquals(FieldError.WEAK_PASSWORD, AuthValidators.password("abcdefgh"))
    @Test fun passwordWithoutLetterIsWeak() = assertEquals(FieldError.WEAK_PASSWORD, AuthValidators.password("12345678"))
    @Test fun strongPasswordIsAccepted() = assertNull(AuthValidators.password("study2026"))

    @Test fun mismatchedConfirmIsRejected() =
        assertEquals(FieldError.MISMATCH, AuthValidators.confirmPassword("study2026", "study2027"))
    @Test fun matchingConfirmIsAccepted() = assertNull(AuthValidators.confirmPassword("study2026", "study2026"))
}
