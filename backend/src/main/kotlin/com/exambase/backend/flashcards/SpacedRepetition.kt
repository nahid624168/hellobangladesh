package com.exambase.backend.flashcards

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Simplified SM-2 style scheduler used by ExamBase flashcards.
 *
 * Documented algorithm:
 * - New cards start at ease 2.50, interval 0, repetitions 0, due immediately.
 * - Ratings:
 *   - again: reset repetitions to 0, interval = 1 day, ease -= 0.20 (floor 1.30)
 *   - hard:  interval = max(1, previous * 1.2), ease -= 0.15, repetitions unchanged if >0 else 1
 *   - good:  first review interval=1, second=6, then interval * ease; ease unchanged; repetitions++
 *   - easy:  first interval=4, then interval * ease * 1.3; ease += 0.15; repetitions++
 * - due_at = now + interval_days (calendar days).
 *
 * This is intentionally pure and deterministic so unit tests can pin expected values.
 */
object SpacedRepetition {

    const val DEFAULT_EASE = 2.50
    const val MIN_EASE = 1.30

    data class State(
        val easeFactor: Double,
        val intervalDays: Int,
        val repetitions: Int,
        val dueAt: Instant,
        val lastReviewedAt: Instant?,
    )

    fun initial(now: Instant = Instant.now()): State =
        State(DEFAULT_EASE, 0, 0, now, null)

    fun apply(state: State, rating: String, now: Instant = Instant.now()): State {
        require(rating in ReviewRating.all) { "unknown rating: $rating" }
        val ease = state.easeFactor
        val interval = state.intervalDays
        val reps = state.repetitions

        val (newEase, newInterval, newReps) = when (rating) {
            ReviewRating.AGAIN -> {
                val e = max(MIN_EASE, ease - 0.20)
                Triple(e, 1, 0)
            }
            ReviewRating.HARD -> {
                val e = max(MIN_EASE, ease - 0.15)
                val i = if (reps == 0) 1 else max(1, (interval * 1.2).roundToInt())
                Triple(e, i, max(1, reps))
            }
            ReviewRating.GOOD -> {
                val i = when (reps) {
                    0 -> 1
                    1 -> 6
                    else -> max(1, (interval * ease).roundToInt())
                }
                Triple(ease, i, reps + 1)
            }
            ReviewRating.EASY -> {
                val e = ease + 0.15
                val i = when (reps) {
                    0 -> 4
                    else -> max(1, (interval * ease * 1.3).roundToInt())
                }
                Triple(e, i, reps + 1)
            }
            else -> error("unreachable")
        }

        return State(
            easeFactor = (newEase * 100).roundToInt() / 100.0,
            intervalDays = newInterval,
            repetitions = newReps,
            dueAt = now.plus(newInterval.toLong(), ChronoUnit.DAYS),
            lastReviewedAt = now,
        )
    }
}
