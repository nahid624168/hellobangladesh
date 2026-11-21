package com.exambase.core.network

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsOverview(
    val totalPoints: Long = 0,
    val accuracy: Double? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val dailyGoalTarget: Int = 20,
    val dailyGoalProgress: Int = 0,
    val dailyGoalMet: Boolean = false,
    val quizAttempts: Int = 0,
    val contestSubmissions: Int = 0,
    val flashcardReviews: Int = 0,
    val examAttempts: Int = 0,
    val metricNotes: List<String> = emptyList(),
)

@Serializable
data class DailyPointDto(
    val date: String,
    val answered: Int = 0,
    val correct: Int = 0,
    val pointsEarned: Int = 0,
)

@Serializable
data class TrendsDto(val days: List<DailyPointDto> = emptyList())

@Serializable
data class SubjectPerformanceDto(
    val subjectId: String,
    val subjectTitle: String? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    val accuracy: Double? = null,
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
    val totalPoints: Long = 0,
    val badges: List<BadgeDto> = emptyList(),
    val earnedCount: Int = 0,
)

@Serializable
data class UpdateDailyGoalRequest(val target: Int)
