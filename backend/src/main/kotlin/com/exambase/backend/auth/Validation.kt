package com.exambase.backend.auth

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode

object Validation {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalizeEmail(raw: String) = raw.trim().lowercase()

    fun email(raw: String): String? {
        val e = normalizeEmail(raw)
        return when {
            e.isEmpty() -> "Email is required"
            e.length > 254 || !emailRegex.matches(e) -> "Enter a valid email address"
            else -> null
        }
    }

    /** bcrypt only uses the first 72 bytes, so longer passwords are rejected instead of silently truncated. */
    fun password(p: String): String? = when {
        p.length < 8 || p.none { it.isLetter() } || p.none { it.isDigit() } ->
            "Use at least 8 characters with a letter and a number"
        p.toByteArray().size > 72 -> "Password is too long (max 72 bytes)"
        else -> null
    }

    fun displayName(raw: String): String? {
        val n = raw.trim()
        return when {
            n.isEmpty() -> "Name is required"
            n.length > 60 -> "Name must be 60 characters or fewer"
            else -> null
        }
    }

    fun requireValid(fields: Map<String, String?>) {
        val errors = fields.filterValues { it != null }.mapValues { it.value!! }
        if (errors.isNotEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "Please check the highlighted fields.", errors)
        }
    }
}
