package com.exambase.ai.auth

enum class FieldError { EMPTY, INVALID_EMAIL, WEAK_PASSWORD, MISMATCH }

/** Pure client-side validation for friendly messages. The server remains the source of truth. */
object AuthValidators {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.EMPTY
        !emailRegex.matches(value.trim()) -> FieldError.INVALID_EMAIL
        else -> null
    }

    fun password(value: String): FieldError? = when {
        value.isEmpty() -> FieldError.EMPTY
        value.length < 8 || value.none { it.isLetter() } || value.none { it.isDigit() } ->
            FieldError.WEAK_PASSWORD
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): FieldError? = when {
        confirm.isEmpty() -> FieldError.EMPTY
        password != confirm -> FieldError.MISMATCH
        else -> null
    }

    fun required(value: String): FieldError? = if (value.isBlank()) FieldError.EMPTY else null
}
