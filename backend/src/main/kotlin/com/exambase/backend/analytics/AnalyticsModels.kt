package com.exambase.backend.analytics

import kotlinx.serialization.Serializable

/**
 * Written metric definitions (Step 28).
 *
 * - **accuracy**: correct / answered among auto-gradable items (excludes unanswered & ungraded written).
 * - **timeSpent**: sum of recorded attempt durations where available (practice/quiz/contest/exam).
 * - **streak**: consecutive calendar days (UTC) with ≥1 answered item; breaks on a missed day.
 * - **points**: only server grants via [point_ledger]; clients cannot POST points.
 * - **daily goal**: answered count toward [daily_goal_target] for the current UTC day.
 */
object MetricDefinitions {
    const val ACCURACY = "accuracy = correctAnswers / answered (auto-graded only)"
    const val STREAK = "streak = consecutive UTC days with at least one answered item"
    const val POINTS = "points are server-granted only; ledger is append-only and idempotent by ref"
    const val DAILY_GOAL = "daily goal progress = answers today / daily_goal_target"
}

@Serializable
data class AnalyticsOverview(
    val totalPoints: Long,
    val accuracy: Double?,
    val answered: Int,
    val correct: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val dailyGoalTarget: Int,
    val dailyGoalProgress: Int,
    val dailyGoalMet: Boolean,
    val quizAttempts: Int,
    val contestSubmissions: Int,
    val flashcardReviews: Int,
    val examAttempts: Int,
    val metricNotes: List<String> = listOf(
        MetricDefinitions.ACCURACY,
        MetricDefinitions.STREAK,
        MetricDefinitions.POINTS,
        MetricDefinitions.DAILY_GOAL,
    ),
)

@Serializable
data class SubjectPerformanceDto(
    val subjectId: String,
    val subjectTitle: String? = null,
    val answered: Int,
    val correct: Int,
    val accuracy: Double?,
)

@Serializable
data class DailyPointDto(
    val date: String,
    val answered: Int,
    val correct: Int,
    val pointsEarned: Int,
)

@Serializable
data class TrendsDto(
    val days: List<DailyPointDto>,
)

@Serializable
data class BadgeDto(
    val code: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val pointsAward: Int = 0,
    val earnedAt: String? = null,
)

@Serializable
data class AchievementsDto(
    val totalPoints: Long,
    val badges: List<BadgeDto>,
    val earnedCount: Int,
)

@Serializable
data class LeaderboardVisibilityDto(
    val showOnPublicLeaderboards: Boolean = true,
)

@Serializable
data class UpdateLeaderboardVisibilityRequest(
    val showOnPublicLeaderboards: Boolean,
)

@Serializable
data class UpdateDailyGoalRequest(
    val target: Int,
)

/** Internal event consumed by the gamification engine (never exposed as a client grant API). */
data class ActivityEvent(
    val userId: java.util.UUID,
    val answeredDelta: Int = 0,
    val correctDelta: Int = 0,
    val subjectId: java.util.UUID? = null,
    val source: String, // practice | quiz | contest | flashcard | exam
    val refId: java.util.UUID? = null,
    val pointsHint: Int? = null,
)
