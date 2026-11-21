package com.exambase.backend.auth

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple in-memory sliding-window limiter. Fine for a single instance only;
 * move to Redis before running more than one backend replica.
 */
class RateLimiter(
    private val maxRequests: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val hits = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val calls = AtomicLong()

    fun tryAcquire(key: String): Boolean {
        val now = clock.millis()
        if (calls.incrementAndGet() % PURGE_EVERY == 0L) purge(now)
        val queue = hits.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() >= window.toMillis()) queue.removeFirst()
            if (queue.size >= maxRequests) return false
            queue.addLast(now)
            return true
        }
    }

    /** Drops keys with no recent hits so the map cannot grow without limit. */
    private fun purge(now: Long) {
        hits.entries.removeIf { (_, queue) ->
            synchronized(queue) { queue.isEmpty() || now - queue.last() >= window.toMillis() }
        }
    }

    private companion object {
        const val PURGE_EVERY = 512L
    }
}
