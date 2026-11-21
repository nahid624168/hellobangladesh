package com.exambase.core.network

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String, val deviceName: String? = null)
@Serializable data class LoginRequest(val email: String, val password: String, val deviceName: String? = null)
@Serializable data class GuestRequest(val deviceName: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class VerifyEmailRequest(val email: String, val code: String)
@Serializable data class PasswordResetRequest(val email: String)
@Serializable data class PasswordResetConfirmRequest(val email: String, val code: String, val newPassword: String)
@Serializable data class UpdateProfileRequest(val displayName: String? = null, val preferredLanguage: String? = null, val theme: String? = null)
@Serializable data class DeleteAccountRequest(val password: String)
@Serializable data class EmptyRequest(val unused: Boolean = false)
@Serializable data class UploadAvatarRequest(val contentType: String, val base64Data: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val preferredLanguage: String,
    val theme: String,
    val createdAt: String,
    val avatarUrl: String? = null,
)

/** Guest accounts use a reserved email domain (see backend GUEST_EMAIL_SUFFIX). */
val UserDto.isGuest: Boolean get() = email.endsWith("@guest.exambase.local")

@Serializable
data class SessionDto(
    val id: String,
    val deviceName: String? = null,
    val createdAt: String,
    val lastUsedAt: String,
    val current: Boolean = false,
)

@Serializable
data class DataExportDto(
    val exportedAt: String,
    val user: UserDto,
    val sessions: List<SessionDto> = emptyList(),
    val noteCount: Int = 0,
    val quizSetCount: Int = 0,
    val flashcardSetCount: Int = 0,
    val bookmarkCount: Int = 0,
    val practiceAttemptCount: Int = 0,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserDto,
    /** Only for guest sessions: when the 20-minute guest session ends (epoch seconds). */
    val guestExpiresAtEpochSeconds: Long? = null,
)

@Serializable data class MessageResponse(val message: String)

@Serializable data class ApiErrorEnvelope(val error: ApiErrorBody)
@Serializable data class ApiErrorBody(val code: String, val message: String, val fields: Map<String, String>? = null)
