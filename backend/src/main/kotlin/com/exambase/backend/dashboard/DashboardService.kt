package com.exambase.backend.dashboard

import com.exambase.backend.Database
import com.exambase.backend.analytics.AnalyticsService
import java.util.UUID

class DashboardService(
    private val db: Database,
    private val analytics: AnalyticsService? = null,
) {
    fun load(userId: UUID, displayName: String, emailVerified: Boolean): DashboardResponse {
        val overview = runCatching { analytics?.overview(userId) }.getOrNull()
        val subjects = loadFeaturedSubjects(6)
        val continueLearning = loadContinueLessons(userId, 5)
        val recent = loadRecentNotes(userId, 5)
        val isNew = overview == null || (
            (overview.answered == 0) &&
                overview.quizAttempts == 0 &&
                overview.examAttempts == 0 &&
                continueLearning.isEmpty() &&
                recent.isEmpty()
            )

        val banners = buildList {
            if (!emailVerified) {
                add(BannerDto("verify_email", "Verify your email", "Unlock all features.", "verify_email"))
            }
            if (overview != null && !overview.dailyGoalMet && overview.dailyGoalProgress > 0) {
                add(
                    BannerDto(
                        "daily_goal",
                        "Keep going",
                        "You are ${overview.dailyGoalProgress}/${overview.dailyGoalTarget} toward today’s goal.",
                        "analytics",
                    ),
                )
            }
        }

        val recommendations = subjects.take(3).map {
            RecommendItem("subject", it.id, it.title, reason = "Popular subject")
        }

        return DashboardResponse(
            greetingName = displayName.ifBlank { "Learner" },
            emailVerified = emailVerified,
            dailyGoalTarget = overview?.dailyGoalTarget ?: 20,
            dailyGoalProgress = overview?.dailyGoalProgress ?: 0,
            dailyGoalMet = overview?.dailyGoalMet ?: false,
            currentStreakDays = overview?.currentStreakDays ?: 0,
            totalPoints = overview?.totalPoints ?: 0,
            continueLearning = continueLearning,
            recentItems = recent,
            subjectShortcuts = subjects,
            banners = banners,
            recommendations = recommendations,
            isNewUser = isNew,
        )
    }

    private fun loadFeaturedSubjects(limit: Int): List<SubjectShortcut> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, title, title_bn FROM subjects
            WHERE status = 'published'
            ORDER BY featured DESC, sort_order, title
            LIMIT ?
            """,
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SubjectShortcut(
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadContinueLessons(userId: UUID, limit: Int): List<ContinueItem> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT l.id, l.title, lp.status, lp.updated_at
            FROM lesson_progress lp
            JOIN lessons l ON l.id = lp.lesson_id AND l.status = 'published'
            WHERE lp.user_id = ? AND lp.status = 'in_progress'
            ORDER BY lp.updated_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ContinueItem(
                                type = "lesson",
                                id = rs.getObject("id", UUID::class.java).toString(),
                                title = rs.getString("title"),
                                subtitle = "In progress",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadRecentNotes(userId: UUID, limit: Int): List<RecentItem> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, title, updated_at FROM notes
            WHERE owner_id = ? AND deleted_at IS NULL
            ORDER BY updated_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecentItem(
                                type = "note",
                                id = rs.getObject("id", UUID::class.java).toString(),
                                title = rs.getString("title"),
                                at = rs.getTimestamp("updated_at")?.toInstant()?.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }
}
