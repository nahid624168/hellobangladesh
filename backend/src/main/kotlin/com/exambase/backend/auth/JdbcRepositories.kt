package com.exambase.backend.auth

import com.exambase.backend.Database
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private fun ResultSet.instant(col: String): Instant? = getTimestamp(col)?.toInstant()

private const val USER_COLUMNS =
    "id, email, password_hash, display_name, email_verified_at, status, preferred_language, theme, created_at, avatar_key"

private fun ResultSet.toUser() = UserRecord(
    id = getObject("id", UUID::class.java),
    email = getString("email"),
    passwordHash = getString("password_hash"),
    displayName = getString("display_name"),
    emailVerifiedAt = instant("email_verified_at"),
    status = getString("status"),
    preferredLanguage = getString("preferred_language"),
    theme = getString("theme"),
    createdAt = instant("created_at")!!,
    avatarKey = getString("avatar_key"),
)

class JdbcUserRepository(private val db: Database) : UserRepository {

    override fun create(email: String, passwordHash: String, displayName: String): UserRecord = try {
        db.transaction { c ->
            val user = c.prepareStatement(
                "INSERT INTO users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING $USER_COLUMNS",
            ).use { ps ->
                ps.setString(1, email)
                ps.setString(2, passwordHash)
                ps.setString(3, displayName)
                ps.executeQuery().use { rs -> rs.next(); rs.toUser() }
            }
            c.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?, 2)").use {
                it.setObject(1, user.id)
                it.executeUpdate()
            }
            user
        }
    } catch (e: SQLException) {
        if (e.sqlState == "23505") throw DuplicateEmailException() else throw e
    }

    override fun findByEmail(email: String): UserRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT $USER_COLUMNS FROM users WHERE email = ? AND status <> 'deleted'").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    override fun findById(id: UUID): UserRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT $USER_COLUMNS FROM users WHERE id = ? AND status <> 'deleted'").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    override fun markEmailVerified(id: UUID) = update("UPDATE users SET email_verified_at = now(), updated_at = now() WHERE id = ?", id)

    override fun updatePassword(id: UUID, passwordHash: String) = db.withConnection { c ->
        c.prepareStatement("UPDATE users SET password_hash = ?, updated_at = now() WHERE id = ?").use { ps ->
            ps.setString(1, passwordHash)
            ps.setObject(2, id)
            ps.executeUpdate()
            Unit
        }
    }

    override fun updateProfile(id: UUID, displayName: String?, preferredLanguage: String?, theme: String?): UserRecord =
        db.withConnection { c ->
            c.prepareStatement(
                """UPDATE users SET display_name = COALESCE(?, display_name),
                   preferred_language = COALESCE(?, preferred_language),
                   theme = COALESCE(?, theme), updated_at = now()
                   WHERE id = ? RETURNING $USER_COLUMNS""",
            ).use { ps ->
                ps.setString(1, displayName)
                ps.setString(2, preferredLanguage)
                ps.setString(3, theme)
                ps.setObject(4, id)
                ps.executeQuery().use { rs -> rs.next(); rs.toUser() }
            }
        }

    override fun updateAvatarKey(id: UUID, avatarKey: String?): UserRecord = db.withConnection { c ->
        c.prepareStatement(
            "UPDATE users SET avatar_key = ?, updated_at = now() WHERE id = ? RETURNING $USER_COLUMNS",
        ).use { ps ->
            ps.setString(1, avatarKey)
            ps.setObject(2, id)
            ps.executeQuery().use { rs -> rs.next(); rs.toUser() }
        }
    }

    /** Anonymises the row so the email can be re-registered and personal data is removed. */
    override fun softDelete(id: UUID) = update(
        """UPDATE users SET status = 'deleted', email = 'deleted+' || id::text || '@deleted.invalid',
           display_name = 'Deleted user', password_hash = '', avatar_key = NULL,
           deleted_at = now(), updated_at = now() WHERE id = ?""",
        id,
    )

    /**
     * Removes guest accounts whose 20-minute session is long over. If a hard delete is blocked by a
     * foreign key, the account is anonymised with [softDelete] instead. Returns how many were handled.
     */
    fun purgeExpiredGuests(emailSuffix: String, createdBefore: Instant): Int {
        val ids = db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT u.id FROM users u
                WHERE u.email LIKE '%' || ? AND u.status = 'active' AND u.created_at < ?
                  AND NOT EXISTS (SELECT 1 FROM sessions s WHERE s.user_id = u.id AND s.revoked_at IS NULL AND s.expires_at > now())
                LIMIT 500
                """,
            ).use { ps ->
                ps.setString(1, emailSuffix)
                ps.setTimestamp(2, Timestamp.from(createdBefore))
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } }
            }
        }
        var handled = 0
        for (id in ids) {
            try {
                db.withConnection { c -> c.prepareStatement("DELETE FROM users WHERE id = ?").use { it.setObject(1, id); it.executeUpdate() } }
            } catch (_: SQLException) {
                softDelete(id)
            }
            handled++
        }
        return handled
    }

    private fun update(sql: String, id: UUID) = db.withConnection { c ->
        c.prepareStatement(sql).use { ps -> ps.setObject(1, id); ps.executeUpdate(); Unit }
    }
}

private const val SESSION_COLUMNS = "id, user_id, device_name, created_at, last_used_at, expires_at, revoked_at"

private fun ResultSet.toSession() = SessionRecord(
    id = getObject("id", UUID::class.java),
    userId = getObject("user_id", UUID::class.java),
    deviceName = getString("device_name"),
    createdAt = instant("created_at")!!,
    lastUsedAt = instant("last_used_at")!!,
    expiresAt = instant("expires_at")!!,
    revokedAt = instant("revoked_at"),
)

class JdbcSessionRepository(private val db: Database) : SessionRepository {

    override fun create(userId: UUID, refreshHash: String, deviceName: String?, expiresAt: Instant): SessionRecord =
        db.withConnection { c ->
            c.prepareStatement(
                "INSERT INTO sessions (user_id, refresh_token_hash, device_name, expires_at) VALUES (?, ?, ?, ?) RETURNING $SESSION_COLUMNS",
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setString(2, refreshHash)
                ps.setString(3, deviceName)
                ps.setTimestamp(4, Timestamp.from(expiresAt))
                ps.executeQuery().use { rs -> rs.next(); rs.toSession() }
            }
        }

    override fun findByRefreshHash(hash: String): SessionMatch? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT $SESSION_COLUMNS, (previous_refresh_hash = ?) AS is_previous FROM sessions " +
                "WHERE refresh_token_hash = ? OR previous_refresh_hash = ?",
        ).use { ps ->
            ps.setString(1, hash); ps.setString(2, hash); ps.setString(3, hash)
            ps.executeQuery().use { rs -> if (rs.next()) SessionMatch(rs.toSession(), rs.getBoolean("is_previous")) else null }
        }
    }

    override fun rotate(id: UUID, expectedCurrentHash: String, newHash: String, newExpiresAt: Instant, now: Instant): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "UPDATE sessions SET previous_refresh_hash = refresh_token_hash, refresh_token_hash = ?, " +
                "last_used_at = ?, expires_at = ? WHERE id = ? AND refresh_token_hash = ? " +
                "AND revoked_at IS NULL AND expires_at > ?",
        ).use { ps ->
            ps.setString(1, newHash)
            ps.setTimestamp(2, Timestamp.from(now))
            ps.setTimestamp(3, Timestamp.from(newExpiresAt))
            ps.setObject(4, id)
            ps.setString(5, expectedCurrentHash)
            ps.setTimestamp(6, Timestamp.from(now))
            ps.executeUpdate() == 1
        }
    }

    override fun isActive(id: UUID, now: Instant): Boolean = db.withConnection { c ->
        c.prepareStatement("SELECT 1 FROM sessions WHERE id = ? AND revoked_at IS NULL AND expires_at > ?").use { ps ->
            ps.setObject(1, id)
            ps.setTimestamp(2, Timestamp.from(now))
            ps.executeQuery().use { it.next() }
        }
    }

    override fun listActive(userId: UUID, now: Instant): List<SessionRecord> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT $SESSION_COLUMNS FROM sessions WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ? ORDER BY last_used_at DESC",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setTimestamp(2, Timestamp.from(now))
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toSession()) } }
        }
    }

    override fun revoke(id: UUID) {
        db.withConnection { c ->
            c.prepareStatement("UPDATE sessions SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL").use {
                it.setObject(1, id); it.executeUpdate()
            }
        }
    }

    override fun revokeAllForUser(userId: UUID) {
        db.withConnection { c ->
            c.prepareStatement("UPDATE sessions SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL").use {
                it.setObject(1, userId); it.executeUpdate()
            }
        }
    }
}

class JdbcEmailCodeRepository(private val db: Database) : EmailCodeRepository {

    override fun save(userId: UUID, purpose: String, codeHash: String, expiresAt: Instant) {
        db.withConnection { c ->
            c.prepareStatement("INSERT INTO email_codes (user_id, purpose, code_hash, expires_at) VALUES (?, ?, ?, ?)").use { ps ->
                ps.setObject(1, userId)
                ps.setString(2, purpose)
                ps.setString(3, codeHash)
                ps.setTimestamp(4, Timestamp.from(expiresAt))
                ps.executeUpdate()
            }
        }
    }

    override fun findLatestActive(userId: UUID, purpose: String, now: Instant): EmailCodeRecord? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT id, code_hash, attempts FROM email_codes WHERE user_id = ? AND purpose = ? " +
                "AND used_at IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT 1",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, purpose)
            ps.setTimestamp(3, Timestamp.from(now))
            ps.executeQuery().use { rs ->
                if (rs.next()) EmailCodeRecord(rs.getObject("id", UUID::class.java), rs.getString("code_hash"), rs.getInt("attempts")) else null
            }
        }
    }

    override fun incrementAttempts(id: UUID) {
        db.withConnection { c ->
            c.prepareStatement("UPDATE email_codes SET attempts = attempts + 1 WHERE id = ?").use { it.setObject(1, id); it.executeUpdate() }
        }
    }

    override fun markUsed(id: UUID) {
        db.withConnection { c ->
            c.prepareStatement("UPDATE email_codes SET used_at = now() WHERE id = ?").use { it.setObject(1, id); it.executeUpdate() }
        }
    }
}
