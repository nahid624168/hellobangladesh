package com.exambase.backend.auth

import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val emailVerifiedAt: Instant?,
    val status: String,
    val preferredLanguage: String,
    val theme: String,
    val createdAt: Instant,
    /** Object-storage key for the profile photo; null when none uploaded. */
    val avatarKey: String? = null,
)

data class SessionRecord(
    val id: UUID,
    val userId: UUID,
    val deviceName: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

/** isPrevious=true means the caller presented a refresh token that was already rotated (possible theft). */
data class SessionMatch(val session: SessionRecord, val isPrevious: Boolean)

data class EmailCodeRecord(val id: UUID, val codeHash: String, val attempts: Int)

class DuplicateEmailException : RuntimeException("Email already registered")

interface UserRepository {
    fun create(email: String, passwordHash: String, displayName: String): UserRecord
    fun findByEmail(email: String): UserRecord?
    fun findById(id: UUID): UserRecord?
    fun markEmailVerified(id: UUID)
    fun updatePassword(id: UUID, passwordHash: String)
    fun updateProfile(id: UUID, displayName: String?, preferredLanguage: String?, theme: String?): UserRecord
    fun updateAvatarKey(id: UUID, avatarKey: String?): UserRecord
    fun softDelete(id: UUID)
}

interface SessionRepository {
    fun create(userId: UUID, refreshHash: String, deviceName: String?, expiresAt: Instant): SessionRecord
    fun findByRefreshHash(hash: String): SessionMatch?
    /** Compare-and-swap rotation: returns false if another request already rotated/revoked the session. */
    fun rotate(id: UUID, expectedCurrentHash: String, newHash: String, newExpiresAt: Instant, now: Instant): Boolean
    fun isActive(id: UUID, now: Instant): Boolean
    fun listActive(userId: UUID, now: Instant): List<SessionRecord>
    fun revoke(id: UUID)
    fun revokeAllForUser(userId: UUID)
}

interface EmailCodeRepository {
    fun save(userId: UUID, purpose: String, codeHash: String, expiresAt: Instant)
    fun findLatestActive(userId: UUID, purpose: String, now: Instant): EmailCodeRecord?
    fun incrementAttempts(id: UUID)
    fun markUsed(id: UUID)
}
