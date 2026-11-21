package com.exambase.backend.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpacedRepetitionTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")

    @Test fun newCardAgainResetsToOneDay() {
        val next = SpacedRepetition.apply(SpacedRepetition.initial(now), ReviewRating.AGAIN, now)
        assertEquals(0, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertTrue(next.easeFactor <= 2.50)
        assertEquals(now.plusSeconds(86400), next.dueAt)
    }

    @Test fun goodProgressionFirstTwoReviews() {
        var s = SpacedRepetition.initial(now)
        s = SpacedRepetition.apply(s, ReviewRating.GOOD, now)
        assertEquals(1, s.repetitions)
        assertEquals(1, s.intervalDays)
        s = SpacedRepetition.apply(s, ReviewRating.GOOD, now.plusSeconds(86400))
        assertEquals(2, s.repetitions)
        assertEquals(6, s.intervalDays)
    }

    @Test fun easyRaisesEase() {
        val s = SpacedRepetition.apply(SpacedRepetition.initial(now), ReviewRating.EASY, now)
        assertTrue(s.easeFactor > SpacedRepetition.DEFAULT_EASE)
        assertEquals(4, s.intervalDays)
    }

    @Test fun easeNeverBelowFloor() {
        var s = SpacedRepetition.initial(now).copy(easeFactor = 1.35)
        repeat(5) { s = SpacedRepetition.apply(s, ReviewRating.AGAIN, now) }
        assertEquals(SpacedRepetition.MIN_EASE, s.easeFactor, 0.001)
    }
}
