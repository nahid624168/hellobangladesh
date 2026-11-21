package com.exambase.backend.analytics

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryAnalyticsRepository : AnalyticsRepository {
    private val stats = ConcurrentHashMap<UUID, UserStatsRecord>()
    private val daily = ConcurrentHashMap<Pair<UUID, LocalDate>, DailyActivityRecord>()
    private val subjects = ConcurrentHashMap<Pair<UUID, UUID>, SubjectPerfRecord>()
    private val ledgerKeys = ConcurrentHashMap.newKeySet<String>()
    private val badges = listOf(
        BadgeDef("first_practice", "First Steps", null, "First practice", null, 10),
        BadgeDef("streak_3", "On a Roll", null, "3-day streak", null, 25),
        BadgeDef("streak_7", "Week Warrior", null, "7-day streak", null, 50),
        BadgeDef("quiz_master", "Quiz Master", null, "5 quizzes", null, 40),
        BadgeDef("contest_entrant", "Competitor", null, "Contest submit", null, 30),
    )
    private val userBadges = ConcurrentHashMap<UUID, MutableMap<String, Instant>>()
    private val leaderboardShow = ConcurrentHashMap<UUID, Boolean>()

    override fun getOrCreateStats(userId: UUID): UserStatsRecord =
        stats.getOrPut(userId) {
            UserStatsRecord(userId, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 20)
        }

    override fun saveStats(stats: UserStatsRecord) {
        this.stats[stats.userId] = stats
    }

    override fun addDaily(userId: UUID, date: LocalDate, answered: Int, correct: Int, points: Int) {
        val key = userId to date
        val cur = daily[key] ?: DailyActivityRecord(date, 0, 0, 0)
        daily[key] = cur.copy(
            answered = cur.answered + answered,
            correct = cur.correct + correct,
            pointsEarned = cur.pointsEarned + points,
        )
    }

    override fun dailyFor(userId: UUID, from: LocalDate, to: LocalDate): List<DailyActivityRecord> =
        daily.filter { it.key.first == userId && it.key.second in from..to }.values.sortedBy { it.date }

    override fun answeredOn(userId: UUID, date: LocalDate): Int =
        daily[userId to date]?.answered ?: 0

    override fun bumpSubject(userId: UUID, subjectId: UUID, answered: Int, correct: Int, at: Instant) {
        val key = userId to subjectId
        val cur = subjects[key] ?: SubjectPerfRecord(subjectId, 0, 0)
        subjects[key] = cur.copy(answered = cur.answered + answered, correct = cur.correct + correct)
    }

    override fun subjectPerformance(userId: UUID): List<SubjectPerfRecord> =
        subjects.filter { it.key.first == userId }.values.toList()

    override fun grantPoints(userId: UUID, amount: Int, reason: String, refType: String?, refId: UUID?): Boolean {
        if (refId != null) {
            val key = "$userId|$reason|$refType|$refId"
            if (!ledgerKeys.add(key)) return false
        }
        return true
    }

    override fun listBadges() = badges

    override fun userBadges(userId: UUID): List<UserBadgeRow> =
        userBadges[userId]?.map { UserBadgeRow(it.key, it.value) } ?: emptyList()

    override fun awardBadge(userId: UUID, code: String, at: Instant): Boolean {
        val map = userBadges.getOrPut(userId) { ConcurrentHashMap() }
        if (map.containsKey(code)) return false
        map[code] = at
        return true
    }

    override fun getShowLeaderboard(userId: UUID) = leaderboardShow[userId] ?: true

    override fun setShowLeaderboard(userId: UUID, show: Boolean) {
        leaderboardShow[userId] = show
    }
}
