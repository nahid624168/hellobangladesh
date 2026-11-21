package com.exambase.backend.analytics

import com.exambase.backend.Database
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class JdbcAnalyticsRepository(private val db: Database) : AnalyticsRepository {

    override fun getOrCreateStats(userId: UUID): UserStatsRecord = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM user_stats WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return@withConnection rs.toStats()
            }
        }
        c.prepareStatement(
            "INSERT INTO user_stats (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.executeUpdate()
        }
        c.prepareStatement("SELECT * FROM user_stats WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs -> rs.next(); rs.toStats() }
        }
    }

    override fun saveStats(stats: UserStatsRecord) = db.withConnection { c ->
        c.prepareStatement(
            """
            UPDATE user_stats SET
              total_points = ?, practice_answered = ?, practice_correct = ?,
              quiz_attempts = ?, contest_submissions = ?, flashcard_reviews = ?, exam_attempts = ?,
              current_streak_days = ?, longest_streak_days = ?, last_active_date = ?,
              daily_goal_target = ?, updated_at = now()
            WHERE user_id = ?
            """,
        ).use { ps ->
            var i = 1
            ps.setLong(i++, stats.totalPoints)
            ps.setInt(i++, stats.practiceAnswered)
            ps.setInt(i++, stats.practiceCorrect)
            ps.setInt(i++, stats.quizAttempts)
            ps.setInt(i++, stats.contestSubmissions)
            ps.setInt(i++, stats.flashcardReviews)
            ps.setInt(i++, stats.examAttempts)
            ps.setInt(i++, stats.currentStreakDays)
            ps.setInt(i++, stats.longestStreakDays)
            if (stats.lastActiveDate != null) ps.setDate(i++, Date.valueOf(stats.lastActiveDate)) else ps.setObject(i++, null)
            ps.setInt(i++, stats.dailyGoalTarget)
            ps.setObject(i, stats.userId)
            ps.executeUpdate()
            Unit
        }
    }

    override fun addDaily(userId: UUID, date: LocalDate, answered: Int, correct: Int, points: Int) =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO user_daily_activity (user_id, activity_date, answered, correct, points_earned)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, activity_date) DO UPDATE SET
                  answered = user_daily_activity.answered + EXCLUDED.answered,
                  correct = user_daily_activity.correct + EXCLUDED.correct,
                  points_earned = user_daily_activity.points_earned + EXCLUDED.points_earned
                """,
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setDate(2, Date.valueOf(date))
                ps.setInt(3, answered)
                ps.setInt(4, correct)
                ps.setInt(5, points)
                ps.executeUpdate()
                Unit
            }
        }

    override fun dailyFor(userId: UUID, from: LocalDate, to: LocalDate): List<DailyActivityRecord> =
        db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT activity_date, answered, correct, points_earned FROM user_daily_activity
                WHERE user_id = ? AND activity_date BETWEEN ? AND ?
                ORDER BY activity_date
                """,
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setDate(2, Date.valueOf(from))
                ps.setDate(3, Date.valueOf(to))
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                DailyActivityRecord(
                                    rs.getDate("activity_date").toLocalDate(),
                                    rs.getInt("answered"),
                                    rs.getInt("correct"),
                                    rs.getInt("points_earned"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun answeredOn(userId: UUID, date: LocalDate): Int = db.withConnection { c ->
        c.prepareStatement(
            "SELECT answered FROM user_daily_activity WHERE user_id = ? AND activity_date = ?",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setDate(2, Date.valueOf(date))
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    override fun bumpSubject(userId: UUID, subjectId: UUID, answered: Int, correct: Int, at: Instant) =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO subject_performance (user_id, subject_id, answered, correct, last_activity_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, subject_id) DO UPDATE SET
                  answered = subject_performance.answered + EXCLUDED.answered,
                  correct = subject_performance.correct + EXCLUDED.correct,
                  last_activity_at = EXCLUDED.last_activity_at
                """,
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setObject(2, subjectId)
                ps.setInt(3, answered)
                ps.setInt(4, correct)
                ps.setTimestamp(5, Timestamp.from(at))
                ps.executeUpdate()
                Unit
            }
        }

    override fun subjectPerformance(userId: UUID): List<SubjectPerfRecord> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT subject_id, answered, correct FROM subject_performance WHERE user_id = ?",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SubjectPerfRecord(
                                rs.getObject("subject_id", UUID::class.java),
                                rs.getInt("answered"),
                                rs.getInt("correct"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun grantPoints(userId: UUID, amount: Int, reason: String, refType: String?, refId: UUID?): Boolean =
        db.withConnection { c ->
            try {
                c.prepareStatement(
                    "INSERT INTO point_ledger (user_id, amount, reason, ref_type, ref_id) VALUES (?, ?, ?, ?, ?)",
                ).use { ps ->
                    ps.setObject(1, userId)
                    ps.setInt(2, amount)
                    ps.setString(3, reason)
                    ps.setString(4, refType)
                    ps.setObject(5, refId)
                    ps.executeUpdate()
                }
                true
            } catch (_: java.sql.SQLException) {
                false
            }
        }

    override fun listBadges(): List<BadgeDef> = db.withConnection { c ->
        c.prepareStatement("SELECT code, title, title_bn, description, description_bn, points_award FROM badges").use { ps ->
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            BadgeDef(
                                rs.getString("code"),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                                rs.getString("description") ?: "",
                                rs.getString("description_bn"),
                                rs.getInt("points_award"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun userBadges(userId: UUID): List<UserBadgeRow> = db.withConnection { c ->
        c.prepareStatement("SELECT badge_code, earned_at FROM user_badges WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(UserBadgeRow(rs.getString("badge_code"), rs.getTimestamp("earned_at").toInstant()))
                    }
                }
            }
        }
    }

    override fun awardBadge(userId: UUID, code: String, at: Instant): Boolean = db.withConnection { c ->
        try {
            c.prepareStatement("INSERT INTO user_badges (user_id, badge_code, earned_at) VALUES (?, ?, ?)").use { ps ->
                ps.setObject(1, userId)
                ps.setString(2, code)
                ps.setTimestamp(3, Timestamp.from(at))
                ps.executeUpdate()
            }
            true
        } catch (_: java.sql.SQLException) {
            false
        }
    }

    override fun getShowLeaderboard(userId: UUID): Boolean = db.withConnection { c ->
        // Reuse a preference column if present; default true. Stored in user_stats via optional later.
        true
    }

    override fun setShowLeaderboard(userId: UUID, show: Boolean) {
        // Preference table can be added later; no-op persist for now.
    }

    private fun ResultSet.toStats() = UserStatsRecord(
        userId = getObject("user_id", UUID::class.java),
        totalPoints = getLong("total_points"),
        practiceAnswered = getInt("practice_answered"),
        practiceCorrect = getInt("practice_correct"),
        quizAttempts = getInt("quiz_attempts"),
        contestSubmissions = getInt("contest_submissions"),
        flashcardReviews = getInt("flashcard_reviews"),
        examAttempts = getInt("exam_attempts"),
        currentStreakDays = getInt("current_streak_days"),
        longestStreakDays = getInt("longest_streak_days"),
        lastActiveDate = getDate("last_active_date")?.toLocalDate(),
        dailyGoalTarget = getInt("daily_goal_target"),
    )
}
