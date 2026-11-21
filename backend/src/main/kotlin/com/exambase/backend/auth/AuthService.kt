package com.exambase.backend.auth

import com.exambase.backend.ApiException
import com.exambase.backend.AuthResponse
import com.exambase.backend.DataExportDto
import com.exambase.backend.LoginRequest
import com.exambase.backend.MessageResponse
import com.exambase.backend.PasswordResetConfirmRequest
import com.exambase.backend.RegisterRequest
import com.exambase.backend.SessionDto
import com.exambase.backend.UpdateProfileRequest
import com.exambase.backend.UploadAvatarRequest
import com.exambase.backend.UserDto
import java.time.Instant
import com.exambase.backend.storage.SignedUrlService
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

/** Optional counts for the personal data export payload. */
data class ExportCounts(
    val noteCount: Int = 0,
    val quizSetCount: Int = 0,
    val flashcardSetCount: Int = 0,
    val bookmarkCount: Int = 0,
    val practiceAttemptCount: Int = 0,
)

/** Guest accounts use this reserved email domain. Real users cannot register with it. */
const val GUEST_EMAIL_SUFFIX = "@guest.exambase.local"

class AuthService(
    private val users: UserRepository,
    private val sessions: SessionRepository,
    private val codes: EmailCodeRepository,
    private val passwords: PasswordHasher,
    private val jwt: JwtService,
    private val email: EmailSender,
    private val refreshTtl: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val signedUrls: SignedUrlService? = null,
    private val exportCounts: (UUID) -> ExportCounts = { ExportCounts() },
    private val guestTtl: Duration = Duration.ofMinutes(20),
) {
    private val dummyHash by lazy { passwords.hash("not-a-real-password-1") }

    private fun unauthorized(code: String = "invalid_credentials", msg: String = "Email or password is incorrect.") =
        ApiException(HttpStatusCode.Unauthorized, code, msg)

    private val invalidCode =
        ApiException(HttpStatusCode.BadRequest, "invalid_code", "The code is invalid or has expired.")

    // ---------------- registration / login ----------------

    fun register(req: RegisterRequest): AuthResponse {
        Validation.requireValid(
            mapOf(
                "email" to Validation.email(req.email),
                "password" to Validation.password(req.password),
                "displayName" to Validation.displayName(req.displayName),
            ),
        )
        if (Validation.normalizeEmail(req.email).endsWith(GUEST_EMAIL_SUFFIX)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "This email address cannot be used.", mapOf("email" to "This email address cannot be used."))
        }
        val user = try {
            users.create(Validation.normalizeEmail(req.email), passwords.hash(req.password), req.displayName.trim())
        } catch (e: DuplicateEmailException) {
            throw ApiException(HttpStatusCode.Conflict, "email_taken", "This email is already registered.")
        }
        sendCode(user, "verify_email", "Verify your ExamBase AI email")
        return issueSession(user, req.deviceName)
    }

    /**
     * Temporary guest account so a visitor can look around the whole app for [guestTtl] (20 minutes).
     * The limit is enforced by the server: the session expires at a fixed time and refresh never extends it.
     */
    fun guestLogin(deviceName: String?): AuthResponse {
        val now = clock.instant()
        val user = users.create(
            "guest-${UUID.randomUUID()}$GUEST_EMAIL_SUFFIX",
            "!", // not a valid bcrypt hash: password login is impossible, and no slow bcrypt run per guest
            "Guest",
        )
        users.markEmailVerified(user.id)
        val token = Hashing.newRefreshToken()
        val expiresAt = now.plus(guestTtl)
        val session = sessions.create(
            user.id, Hashing.sha256Hex(token), deviceName?.trim()?.take(80)?.ifBlank { null } ?: "Guest", expiresAt,
        )
        return authResponse(user.copy(emailVerifiedAt = now), session.id, token, expiresAt)
    }

    private fun UserRecord.isGuest() = email.endsWith(GUEST_EMAIL_SUFFIX)

    fun login(req: LoginRequest): AuthResponse {
        val user = users.findByEmail(Validation.normalizeEmail(req.email))
        // Always run one bcrypt comparison so response time does not reveal whether the email exists.
        val ok = passwords.verify(req.password, user?.passwordHash ?: dummyHash)
        if (user == null || !ok) throw unauthorized()
        if (user.status != "active") throw unauthorized("account_unavailable", "This account is not available.")
        return issueSession(user, req.deviceName)
    }

    fun refresh(refreshToken: String): AuthResponse {
        val match = sessions.findByRefreshHash(Hashing.sha256Hex(refreshToken))
            ?: throw unauthorized("invalid_refresh_token", "Please sign in again.")
        val s = match.session
        val now = clock.instant()
        if (match.isPrevious) {
            // An already-rotated token was replayed: assume theft and sign the user out everywhere.
            sessions.revokeAllForUser(s.userId)
            throw unauthorized("invalid_refresh_token", "Please sign in again.")
        }
        if (s.revokedAt != null || !s.expiresAt.isAfter(now)) {
            throw unauthorized("invalid_refresh_token", "Please sign in again.")
        }
        val user = users.findById(s.userId)?.takeIf { it.status == "active" }
            ?: throw unauthorized("invalid_refresh_token", "Please sign in again.")
        val newToken = Hashing.newRefreshToken()
        val oldHash = Hashing.sha256Hex(refreshToken)
        // Guests never get a longer session by refreshing: the original 20-minute deadline stays.
        val newExpiry = if (user.isGuest()) s.expiresAt else now.plus(refreshTtl)
        val rotated = sessions.rotate(s.id, oldHash, Hashing.sha256Hex(newToken), newExpiry, now)
        if (!rotated) {
            // If another request rotated this exact token first, treat the loser as replay too.
            val latest = sessions.findByRefreshHash(oldHash)
            if (latest?.isPrevious == true) sessions.revokeAllForUser(s.userId)
            throw unauthorized("invalid_refresh_token", "Please sign in again.")
        }
        return authResponse(user, s.id, newToken, if (user.isGuest()) s.expiresAt else null)
    }

    fun logout(sessionId: UUID) = sessions.revoke(sessionId)

    // ---------------- email verification ----------------

    fun resendVerification(userId: UUID): MessageResponse {
        val user = requireUser(userId)
        if (user.emailVerifiedAt == null) sendCode(user, "verify_email", "Verify your ExamBase AI email")
        return MessageResponse("If your email is not verified yet, a new code has been sent.")
    }

    fun verifyEmail(emailRaw: String, code: String): MessageResponse {
        val user = users.findByEmail(Validation.normalizeEmail(emailRaw)) ?: throw invalidCode
        consumeCode(user, "verify_email", code)
        users.markEmailVerified(user.id)
        return MessageResponse("Email verified.")
    }

    // ---------------- password reset ----------------

    /** Always returns the same message so the endpoint cannot be used to discover registered emails. */
    fun requestPasswordReset(emailRaw: String): MessageResponse {
        users.findByEmail(Validation.normalizeEmail(emailRaw))?.let { user ->
            if (user.status == "active") sendCode(user, "reset_password", "Reset your ExamBase AI password")
        }
        return MessageResponse("If this email is registered, a reset code has been sent.")
    }

    fun confirmPasswordReset(req: PasswordResetConfirmRequest): MessageResponse {
        Validation.requireValid(mapOf("newPassword" to Validation.password(req.newPassword)))
        val user = users.findByEmail(Validation.normalizeEmail(req.email)) ?: throw invalidCode
        consumeCode(user, "reset_password", req.code)
        users.updatePassword(user.id, passwords.hash(req.newPassword))
        sessions.revokeAllForUser(user.id)
        return MessageResponse("Password updated. Please sign in.")
    }

    // ---------------- profile / sessions ----------------

    fun me(userId: UUID): UserDto = requireUser(userId).toDto()

    fun updateProfile(userId: UUID, req: UpdateProfileRequest): UserDto {
        Validation.requireValid(
            mapOf(
                "displayName" to req.displayName?.let(Validation::displayName),
                "preferredLanguage" to req.preferredLanguage?.takeIf { it !in setOf("en", "bn") }?.let { "Use en or bn" },
                "theme" to req.theme?.takeIf { it !in setOf("system", "light", "dark") }?.let { "Use system, light or dark" },
            ),
        )
        requireUser(userId)
        return users.updateProfile(userId, req.displayName?.trim(), req.preferredLanguage, req.theme).toDto()
    }

    fun listSessions(userId: UUID, currentSessionId: UUID): List<SessionDto> =
        sessions.listActive(userId, clock.instant()).map {
            SessionDto(it.id.toString(), it.deviceName, it.createdAt.toString(), it.lastUsedAt.toString(), it.id == currentSessionId)
        }

    fun revokeSession(userId: UUID, sessionId: UUID) {
        val owned = sessions.listActive(userId, clock.instant()).any { it.id == sessionId }
        if (!owned) throw ApiException(HttpStatusCode.NotFound, "not_found", "Session not found.")
        sessions.revoke(sessionId)
    }

    fun deleteAccount(userId: UUID, password: String) {
        val user = requireUser(userId)
        if (!passwords.verify(password, user.passwordHash)) throw unauthorized("invalid_credentials", "Password is incorrect.")
        users.softDelete(userId)
        sessions.revokeAllForUser(userId)
    }

    /**
     * Accepts a small base64 image (jpeg/png/webp), stores a synthetic storage key, and returns the updated user.
     * Real object-storage upload is deferred to media/admin steps; the key is still signed for clients.
     */
    fun uploadAvatar(userId: UUID, req: UploadAvatarRequest): UserDto {
        val allowed = setOf("image/jpeg", "image/png", "image/webp")
        if (req.contentType !in allowed) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "invalid_request",
                "Unsupported image type.",
                mapOf("contentType" to "Use image/jpeg, image/png or image/webp."),
            )
        }
        val bytes = try {
            Base64.getDecoder().decode(req.base64Data.replace("\\s".toRegex(), ""))
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid base64 data.", mapOf("base64Data" to "Must be valid base64."))
        }
        if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "invalid_request",
                "Image is empty or too large.",
                mapOf("base64Data" to "Maximum size is ${MAX_AVATAR_BYTES / 1024} KB."),
            )
        }
        val ext = when (req.contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val key = "avatars/$userId/${UUID.randomUUID()}.$ext"
        // Persistence of the binary is left to a future media pipeline; we only store the key.
        return users.updateAvatarKey(userId, key).toDto()
    }

    fun clearAvatar(userId: UUID): UserDto = users.updateAvatarKey(userId, null).toDto()

    fun exportData(userId: UUID, currentSessionId: UUID): DataExportDto {
        val user = requireUser(userId)
        val sessionList = listSessions(userId, currentSessionId)
        val counts = exportCounts(userId)
        return DataExportDto(
            exportedAt = clock.instant().toString(),
            user = user.toDto(),
            sessions = sessionList,
            noteCount = counts.noteCount,
            quizSetCount = counts.quizSetCount,
            flashcardSetCount = counts.flashcardSetCount,
            bookmarkCount = counts.bookmarkCount,
            practiceAttemptCount = counts.practiceAttemptCount,
        )
    }

    // ---------------- internals ----------------

    private fun requireUser(id: UUID): UserRecord =
        users.findById(id) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found.")

    private fun issueSession(user: UserRecord, deviceName: String?): AuthResponse {
        val token = Hashing.newRefreshToken()
        val session = sessions.create(
            user.id, Hashing.sha256Hex(token), deviceName?.trim()?.take(80)?.ifBlank { null }, clock.instant().plus(refreshTtl),
        )
        return authResponse(user, session.id, token)
    }

    private fun authResponse(user: UserRecord, sessionId: UUID, refreshToken: String, guestExpiresAt: Instant? = null) = AuthResponse(
        accessToken = jwt.issueAccessToken(user.id, sessionId, user.isGuest()),
        refreshToken = refreshToken,
        expiresInSeconds = jwt.accessTtl.seconds,
        user = user.toDto(),
        guestExpiresAtEpochSeconds = guestExpiresAt?.epochSecond,
    )

    private fun sendCode(user: UserRecord, purpose: String, subject: String) {
        val code = Hashing.newSixDigitCode()
        codes.save(user.id, purpose, Hashing.codeHash(user.id, code), clock.instant().plus(CODE_TTL))
        email.send(user.email, subject, "Your ExamBase AI code is $code. It expires in 15 minutes.")
    }

    private fun consumeCode(user: UserRecord, purpose: String, code: String) {
        val record = codes.findLatestActive(user.id, purpose, clock.instant()) ?: throw invalidCode
        if (record.attempts >= MAX_CODE_ATTEMPTS) {
            throw ApiException(HttpStatusCode.TooManyRequests, "too_many_attempts", "Too many wrong codes. Request a new one.")
        }
        val expected = record.codeHash.toByteArray()
        val actual = Hashing.codeHash(user.id, code.trim()).toByteArray()
        if (!MessageDigest.isEqual(expected, actual)) {
            codes.incrementAttempts(record.id)
            throw invalidCode
        }
        codes.markUsed(record.id)
    }

    private fun UserRecord.toDto(): UserDto {
        val avatarUrl = avatarKey?.let { key ->
            signedUrls?.sign(key)?.url ?: key
        }
        return UserDto(
            id = id.toString(),
            email = email,
            displayName = displayName,
            emailVerified = emailVerifiedAt != null,
            preferredLanguage = preferredLanguage,
            theme = theme,
            createdAt = createdAt.toString(),
            avatarUrl = avatarUrl,
        )
    }

    companion object {
        val CODE_TTL: Duration = Duration.ofMinutes(15)
        const val MAX_CODE_ATTEMPTS = 5
        const val MAX_AVATAR_BYTES = 1_500_000
    }
}
