package com.exambase.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date
import io.ktor.server.auth.Principal
import java.util.UUID

class PasswordHasher(private val cost: Int = 12) {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        runCatching { BCrypt.verifyer().verify(password.toCharArray(), hash).verified }.getOrDefault(false)
}

object Hashing {
    private val random = SecureRandom()

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    fun newRefreshToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun newSixDigitCode(): String = "%06d".format(random.nextInt(1_000_000))

    fun codeHash(userId: UUID, code: String): String = sha256Hex("$userId:$code")
}

class JwtService(
    secret: String,
    val issuer: String,
    val accessTtl: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier = JWT.require(algorithm).withIssuer(issuer).build()

    fun issueAccessToken(userId: UUID, sessionId: UUID, guest: Boolean = false): String {
        val now = clock.instant()
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withClaim("sid", sessionId.toString())
            .withClaim("guest", guest)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(accessTtl)))
            .sign(algorithm)
    }

    /** Used by WebSocket handshake (query token). Returns null when invalid/expired. */
    fun verifyAccess(token: String): AuthPrincipal? = runCatching {
        val decoded = verifier.verify(token)
        val userId = UUID.fromString(decoded.subject)
        val sessionId = UUID.fromString(decoded.getClaim("sid").asString())
        AuthPrincipal(userId, sessionId, decoded.getClaim("guest").asBoolean() == true)
    }.getOrNull()
}

/** Authenticated caller, available via call.principal<AuthPrincipal>(). */
data class AuthPrincipal(val userId: UUID, val sessionId: UUID, val guest: Boolean = false) : Principal
