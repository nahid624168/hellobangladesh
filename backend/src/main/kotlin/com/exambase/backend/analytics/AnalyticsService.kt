package com.exambase.backend.analytics

import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Analytics + gamification. Point grants are server-only; there is no client endpoint to add points.
 */
class AnalyticsService(
    private val repo: AnalyticsRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun overview(userId: UUID): AnalyticsOverview {
        val s = repo.getOrCreateStats(userId)
        val today = LocalDate.now(clock)
        val todayAnswered = repo.answeredOn(userId, today)
        val answered = s.practiceAnswered // primary accuracy base for practice-driven MVP
        val correct = s.practiceCorrect
        val accuracy = if (answered > 0) round2(correct.toDouble() / answered * 100.0) else null
        return AnalyticsOverview(
            totalPoints = s.totalPoints,
            accuracy = accuracy,
            answered = answered,
            correct = correct,
            currentStreakDays = s.currentStreakDays,
            longestStreakDays = s.longestStreakDays,
            dailyGoalTarget = s.dailyGoalTarget,
            dailyGoalProgress = todayAnswered,
            dailyGoalMet = todayAnswered >= s.dailyGoalTarget,
            quizAttempts = s.quizAttempts,
            contestSubmissions = s.contestSubmissions,
            flashcardReviews = s.flashcardReviews,
            examAttempts = s.examAttempts,
        )
    }

    fun trends(userId: UUID, days: Int = 30): TrendsDto {
        val n = days.coerceIn(7, 90)
        val to = LocalDate.now(clock)
        val from = to.minusDays((n - 1).toLong())
        val rows = repo.dailyFor(userId, from, to).associateBy { it.date }
        val list = (0 until n).map { i ->
            val d = from.plusDays(i.toLong())
            val r = rows[d]
            DailyPointDto(d.toString(), r?.answered ?: 0, r?.correct ?: 0, r?.pointsEarned ?: 0)
        }
        return TrendsDto(list)
    }

    fun subjects(userId: UUID): List<SubjectPerformanceDto> =
        repo.subjectPerformance(userId).map {
            val acc = if (it.answered > 0) round2(it.correct.toDouble() / it.answered * 100.0) else null
            SubjectPerformanceDto(it.subjectId.toString(), null, it.answered, it.correct, acc)
        }

    fun achievements(userId: UUID): AchievementsDto {
        val s = repo.getOrCreateStats(userId)
        val earned = repo.userBadges(userId).associateBy { it.badgeCode }
        val badges = repo.listBadges().map { b ->
            BadgeDto(
                code = b.code,
                title = b.title,
                titleBn = b.titleBn,
                description = b.description,
                descriptionBn = b.descriptionBn,
                pointsAward = b.pointsAward,
                earnedAt = earned[b.code]?.earnedAt?.toString(),
            )
        }
        return AchievementsDto(s.totalPoints, badges, earned.size)
    }

    fun updateDailyGoal(userId: UUID, target: Int) {
        if (target !in 1..500) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Daily goal must be 1–500.")
        }
        val s = repo.getOrCreateStats(userId)
        repo.saveStats(s.copy(dailyGoalTarget = target))
    }

    fun leaderboardVisibility(userId: UUID) =
        LeaderboardVisibilityDto(repo.getShowLeaderboard(userId))

    fun setLeaderboardVisibility(userId: UUID, show: Boolean) {
        repo.setShowLeaderboard(userId, show)
    }

    /**
     * Apply an activity event (called from practice/quiz/contest/exam services, not from HTTP clients).
     * Idempotent when [ActivityEvent.refId] is set for point grants.
     */
    fun recordActivity(event: ActivityEvent) {
        if (event.answeredDelta < 0 || event.correctDelta < 0) return
        val today = LocalDate.now(clock)
        val now = clock.instant()
        var stats = repo.getOrCreateStats(event.userId)

        var points = 0
        if (event.answeredDelta > 0) {
            points += event.answeredDelta // 1 pt per answer
            if (event.correctDelta > 0) points += event.correctDelta * 2 // +2 per correct
        }
        event.pointsHint?.let { if (it > 0) points += it }

        if (points > 0) {
            val granted = repo.grantPoints(
                event.userId, points, "activity_${event.source}", event.source, event.refId,
            )
            if (granted) {
                stats = stats.copy(totalPoints = stats.totalPoints + points)
                repo.addDaily(event.userId, today, event.answeredDelta, event.correctDelta, points)
            } else {
                repo.addDaily(event.userId, today, event.answeredDelta, event.correctDelta, 0)
            }
        } else if (event.answeredDelta > 0 || event.correctDelta > 0) {
            repo.addDaily(event.userId, today, event.answeredDelta, event.correctDelta, 0)
        }

        stats = when (event.source) {
            "practice" -> stats.copy(
                practiceAnswered = stats.practiceAnswered + event.answeredDelta,
                practiceCorrect = stats.practiceCorrect + event.correctDelta,
            )
            "quiz" -> stats.copy(quizAttempts = stats.quizAttempts + 1)
            "contest" -> stats.copy(contestSubmissions = stats.contestSubmissions + 1)
            "flashcard" -> stats.copy(flashcardReviews = stats.flashcardReviews + event.answeredDelta.coerceAtLeast(1))
            "exam" -> stats.copy(examAttempts = stats.examAttempts + 1)
            else -> stats
        }

        stats = updateStreak(stats, today)
        repo.saveStats(stats)

        event.subjectId?.let {
            if (event.answeredDelta > 0) repo.bumpSubject(event.userId, it, event.answeredDelta, event.correctDelta, now)
        }

        evaluateBadges(event.userId, stats)
    }

    private fun updateStreak(stats: UserStatsRecord, today: LocalDate): UserStatsRecord {
        val last = stats.lastActiveDate
        val current = when {
            last == null -> 1
            last == today -> stats.currentStreakDays.coerceAtLeast(1)
            last == today.minusDays(1) -> stats.currentStreakDays + 1
            else -> 1
        }
        return stats.copy(
            currentStreakDays = current,
            longestStreakDays = maxOf(stats.longestStreakDays, current),
            lastActiveDate = today,
        )
    }

    private fun evaluateBadges(userId: UUID, stats: UserStatsRecord) {
        val now = clock.instant()
        fun tryAward(code: String) {
            if (repo.awardBadge(userId, code, now)) {
                val def = repo.listBadges().find { it.code == code } ?: return
                if (def.pointsAward > 0 && repo.grantPoints(userId, def.pointsAward, "badge", "badge", null)) {
                    // badge points without unique ref — allow once via user_badges uniqueness
                    val s = repo.getOrCreateStats(userId)
                    repo.saveStats(s.copy(totalPoints = s.totalPoints + def.pointsAward))
                }
            }
        }
        if (stats.practiceAnswered >= 1) tryAward("first_practice")
        if (stats.currentStreakDays >= 3) tryAward("streak_3")
        if (stats.currentStreakDays >= 7) tryAward("streak_7")
        if (stats.quizAttempts >= 5) tryAward("quiz_master")
        if (stats.contestSubmissions >= 1) tryAward("contest_entrant")
    }

    private fun round2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}
