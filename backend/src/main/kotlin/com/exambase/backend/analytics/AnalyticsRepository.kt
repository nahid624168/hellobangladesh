package com.exambase.backend.analytics

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UserStatsRecord(
    val userId: UUID,
    val totalPoints: Long,
    val practiceAnswered: Int,
    val practiceCorrect: Int,
    val quizAttempts: Int,
    val contestSubmissions: Int,
    val flashcardReviews: Int,
    val examAttempts: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastActiveDate: LocalDate?,
    val dailyGoalTarget: Int,
)

data class DailyActivityRecord(
    val date: LocalDate,
    val answered: Int,
    val correct: Int,
    val pointsEarned: Int,
)

data class SubjectPerfRecord(
    val subjectId: UUID,
    val answered: Int,
    val correct: Int,
)

data class BadgeDef(val code: String, val title: String, val titleBn: String?, val description: String, val descriptionBn: String?, val pointsAward: Int)
data class UserBadgeRow(val badgeCode: String, val earnedAt: Instant)

interface AnalyticsRepository {
    fun getOrCreateStats(userId: UUID): UserStatsRecord
    fun saveStats(stats: UserStatsRecord)
    fun addDaily(userId: UUID, date: LocalDate, answered: Int, correct: Int, points: Int)
    fun dailyFor(userId: UUID, from: LocalDate, to: LocalDate): List<DailyActivityRecord>
    fun answeredOn(userId: UUID, date: LocalDate): Int
    fun bumpSubject(userId: UUID, subjectId: UUID, answered: Int, correct: Int, at: Instant)
    fun subjectPerformance(userId: UUID): List<SubjectPerfRecord>
    /** Returns false if this (user,reason,ref) was already granted. */
    fun grantPoints(userId: UUID, amount: Int, reason: String, refType: String?, refId: UUID?): Boolean
    fun listBadges(): List<BadgeDef>
    fun userBadges(userId: UUID): List<UserBadgeRow>
    fun awardBadge(userId: UUID, code: String, at: Instant): Boolean
    fun getShowLeaderboard(userId: UUID): Boolean
    fun setShowLeaderboard(userId: UUID, show: Boolean)
}
