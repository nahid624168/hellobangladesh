package com.exambase.backend.auth

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    val rows = ConcurrentHashMap<UUID, UserRecord>()

    override fun create(email: String, passwordHash: String, displayName: String): UserRecord {
        if (rows.values.any { it.email == email }) throw DuplicateEmailException()
        val u = UserRecord(UUID.randomUUID(), email, passwordHash, displayName, null, "active", "en", "system", Instant.now())
        rows[u.id] = u
        return u
    }
    override fun findByEmail(email: String) = rows.values.firstOrNull { it.email == email && it.status != "deleted" }
    override fun findById(id: UUID) = rows[id]?.takeIf { it.status != "deleted" }
    override fun markEmailVerified(id: UUID) { rows.computeIfPresent(id) { _, u -> u.copy(emailVerifiedAt = Instant.now()) } }
    override fun updatePassword(id: UUID, passwordHash: String) { rows.computeIfPresent(id) { _, u -> u.copy(passwordHash = passwordHash) } }
    override fun updateProfile(id: UUID, displayName: String?, preferredLanguage: String?, theme: String?): UserRecord =
        rows.computeIfPresent(id) { _, u ->
            u.copy(
                displayName = displayName ?: u.displayName,
                preferredLanguage = preferredLanguage ?: u.preferredLanguage,
                theme = theme ?: u.theme,
            )
        }!!
    override fun updateAvatarKey(id: UUID, avatarKey: String?): UserRecord =
        rows.computeIfPresent(id) { _, u -> u.copy(avatarKey = avatarKey) }!!
    override fun softDelete(id: UUID) {
        rows.computeIfPresent(id) { _, u ->
            u.copy(status = "deleted", email = "deleted+$id@deleted.invalid", passwordHash = "", avatarKey = null)
        }
    }
}

class InMemorySessionRepository : SessionRepository {
    private class Row(var record: SessionRecord, var hash: String, var previousHash: String? = null)
    private val rows = ConcurrentHashMap<UUID, Row>()

    override fun create(userId: UUID, refreshHash: String, deviceName: String?, expiresAt: Instant): SessionRecord {
        val now = Instant.now()
        val r = SessionRecord(UUID.randomUUID(), userId, deviceName, now, now, expiresAt, null)
        rows[r.id] = Row(r, refreshHash)
        return r
    }
    override fun findByRefreshHash(hash: String): SessionMatch? {
        rows.values.firstOrNull { it.hash == hash }?.let { return SessionMatch(it.record, false) }
        rows.values.firstOrNull { it.previousHash == hash }?.let { return SessionMatch(it.record, true) }
        return null
    }
    @Synchronized override fun rotate(id: UUID, expectedCurrentHash: String, newHash: String, newExpiresAt: Instant, now: Instant): Boolean {
        val row = rows[id] ?: return false
        if (row.hash != expectedCurrentHash || row.record.revokedAt != null || !row.record.expiresAt.isAfter(now)) return false
        row.previousHash = row.hash
        row.hash = newHash
        row.record = row.record.copy(expiresAt = newExpiresAt, lastUsedAt = now)
        return true
    }
    override fun isActive(id: UUID, now: Instant) = rows[id]?.record?.let { it.revokedAt == null && it.expiresAt.isAfter(now) } ?: false
    override fun listActive(userId: UUID, now: Instant) =
        rows.values.map { it.record }.filter { it.userId == userId && it.revokedAt == null && it.expiresAt.isAfter(now) }
    override fun revoke(id: UUID) { rows[id]?.let { it.record = it.record.copy(revokedAt = it.record.revokedAt ?: Instant.now()) } }
    override fun revokeAllForUser(userId: UUID) { rows.values.filter { it.record.userId == userId }.forEach { revoke(it.record.id) } }
}

class InMemoryEmailCodeRepository : EmailCodeRepository {
    private data class Row(val id: UUID, val userId: UUID, val purpose: String, val hash: String, var attempts: Int, val expiresAt: Instant, var used: Boolean, val createdAt: Instant)
    private val rows = ArrayList<Row>()

    @Synchronized override fun save(userId: UUID, purpose: String, codeHash: String, expiresAt: Instant) {
        rows += Row(UUID.randomUUID(), userId, purpose, codeHash, 0, expiresAt, false, Instant.now())
    }
    @Synchronized override fun findLatestActive(userId: UUID, purpose: String, now: Instant) =
        rows.filter { it.userId == userId && it.purpose == purpose && !it.used && it.expiresAt.isAfter(now) }
            .maxByOrNull { it.createdAt }?.let { EmailCodeRecord(it.id, it.hash, it.attempts) }
    @Synchronized override fun incrementAttempts(id: UUID) { rows.firstOrNull { it.id == id }?.let { it.attempts++ } }
    @Synchronized override fun markUsed(id: UUID) { rows.firstOrNull { it.id == id }?.used = true }
}

/** Captures outgoing emails so tests can read the one-time codes. */
class CapturingEmailSender : EmailSender {
    val sent = ArrayList<Triple<String, String, String>>()
    override fun send(to: String, subject: String, body: String) { sent += Triple(to, subject, body) }
    fun lastCode(): String = Regex("(\\d{6})").find(sent.last().third)!!.value
}

object TestFixtures {
    fun newAuth(email: CapturingEmailSender = CapturingEmailSender()): Pair<AuthService, CapturingEmailSender> {
        val service = AuthService(
            users = InMemoryUserRepository(),
            sessions = InMemorySessionRepository(),
            codes = InMemoryEmailCodeRepository(),
            passwords = PasswordHasher(cost = 4),
            jwt = JwtService("test-secret-test-secret-test-secret-123", "exambase-test", java.time.Duration.ofMinutes(15)),
            email = email,
            refreshTtl = java.time.Duration.ofDays(30),
        )
        return service to email
    }
}
