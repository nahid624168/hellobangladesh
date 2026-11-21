package com.exambase.backend

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String, val version: String)

/** Standard error envelope for every API error. */
@Serializable
data class ApiError(val error: ErrorBody)

@Serializable
data class ErrorBody(val code: String, val message: String, val fields: Map<String, String>? = null)

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
    val fields: Map<String, String>? = null,
) : RuntimeException(message)

@Serializable
data class MessageResponse(val message: String)

// ---- Auth DTOs
@Serializable
data class GuestRequest(val deviceName: String? = null)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String, val deviceName: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String, val deviceName: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class VerifyEmailRequest(val email: String, val code: String)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetConfirmRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val preferredLanguage: String,
    val theme: String,
    val createdAt: String,
    /** Signed or public URL for the profile photo; null when none set. */
    val avatarUrl: String? = null,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserDto,
    /** Set only for guest sessions: when the 20-minute guest session ends (epoch seconds). */
    val guestExpiresAtEpochSeconds: Long? = null,
)

// ---- Profile / sessions DTOs
@Serializable
data class UpdateProfileRequest(val displayName: String? = null, val preferredLanguage: String? = null, val theme: String? = null)

@Serializable
data class SessionDto(val id: String, val deviceName: String?, val createdAt: String, val lastUsedAt: String, val current: Boolean)

@Serializable
data class DeleteAccountRequest(val password: String)

/**
 * Avatar upload: client sends a small base64 JPEG/PNG (max ~1.5 MB decoded).
 * Server stores a key under avatars/{userId}/{uuid}.ext — real object storage arrives with admin/media steps.
 */
@Serializable
data class UploadAvatarRequest(
    val contentType: String,
    val base64Data: String,
)

@Serializable
data class DataExportDto(
    val exportedAt: String,
    val user: UserDto,
    val sessions: List<SessionDto>,
    val noteCount: Int,
    val quizSetCount: Int,
    val flashcardSetCount: Int,
    val bookmarkCount: Int,
    val practiceAttemptCount: Int,
)
