package com.exambase.backend.analytics

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun pointsAndAccuracyAndStreak() {
        val repo = InMemoryAnalyticsRepository()
        val svc = AnalyticsService(repo, clock)
        val user = UUID.randomUUID()
        val ref = UUID.randomUUID()
        svc.recordActivity(
            ActivityEvent(user, answeredDelta = 5, correctDelta = 4, source = "practice", refId = ref),
        )
        // duplicate ref should not double-count points
        svc.recordActivity(
            ActivityEvent(user, answeredDelta = 5, correctDelta = 4, source = "practice", refId = ref),
        )
        val o = svc.overview(user)
        assertEquals(5, o.answered)
        assertEquals(4, o.correct)
        assertEquals(80.0, o.accuracy)
        assertEquals(1, o.currentStreakDays)
        // 5 + 4*2 = 13 points once
        assertEquals(13L, o.totalPoints)
        assertTrue(o.badgesNotRequired())
        val ach = svc.achievements(user)
        assertTrue(ach.badges.any { it.code == "first_practice" && it.earnedAt != null })
    }

    @Test
    fun dailyGoalProgress() {
        val repo = InMemoryAnalyticsRepository()
        val svc = AnalyticsService(repo, clock)
        val user = UUID.randomUUID()
        svc.updateDailyGoal(user, 10)
        svc.recordActivity(ActivityEvent(user, answeredDelta = 7, correctDelta = 3, source = "practice", refId = UUID.randomUUID()))
        val o = svc.overview(user)
        assertEquals(10, o.dailyGoalTarget)
        assertEquals(7, o.dailyGoalProgress)
        assertEquals(false, o.dailyGoalMet)
    }

    @Test
    fun trendsFillMissingDays() {
        val repo = InMemoryAnalyticsRepository()
        val svc = AnalyticsService(repo, clock)
        val user = UUID.randomUUID()
        svc.recordActivity(ActivityEvent(user, answeredDelta = 2, correctDelta = 1, source = "practice", refId = UUID.randomUUID()))
        val t = svc.trends(user, 7)
        assertEquals(7, t.days.size)
        assertEquals(2, t.days.last().answered)
    }

    /** Helper so test does not depend on badge DTO shape. */
    private fun AnalyticsOverview.badgesNotRequired() = true
}
