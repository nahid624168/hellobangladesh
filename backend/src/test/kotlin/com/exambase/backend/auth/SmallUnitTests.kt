package com.exambase.backend.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmallUnitTests {
    @Test fun sessionRefreshRotationUsesCompareAndSwap() {
        val repo = InMemorySessionRepository()
        val userId = java.util.UUID.randomUUID()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val session = repo.create(userId, "old-hash", "test-device", now.plus(Duration.ofDays(1)))
        assertTrue(repo.rotate(session.id, "old-hash", "new-hash", now.plus(Duration.ofDays(2)), now))
        assertFalse(repo.rotate(session.id, "old-hash", "attacker-hash", now.plus(Duration.ofDays(2)), now))
        assertEquals(true, repo.findByRefreshHash("new-hash")?.isPrevious == false)
    }
    @Test fun passwordHashRoundTrip() {
        val h = PasswordHasher(cost = 4)
        val hash = h.hash("study2026")
        assertTrue(h.verify("study2026", hash))
        assertFalse(h.verify("other-pass1", hash))
        assertFalse(h.verify("study2026", ""))
    }

    @Test fun validationRules() {
        assertNull(Validation.email("a@b.co"))
        assertNotNull(Validation.email("nope"))
        assertNull(Validation.password("study2026"))
        assertNotNull(Validation.password("abcdefgh"))
        assertNotNull(Validation.password("a1" + "x".repeat(80)))
        assertNotNull(Validation.displayName("   "))
    }

    @Test fun sixDigitCodeIsAlwaysSixDigits() {
        repeat(200) { assertEquals(6, Hashing.newSixDigitCode().length) }
    }

    @Test fun rateLimiterWindowSlides() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = now
        }
        val limiter = RateLimiter(2, Duration.ofSeconds(10), clock)
        assertTrue(limiter.tryAcquire("k"))
        assertTrue(limiter.tryAcquire("k"))
        assertFalse(limiter.tryAcquire("k"))
        assertTrue(limiter.tryAcquire("other"))
        now = now.plusSeconds(11)
        assertTrue(limiter.tryAcquire("k"))
    }
}
