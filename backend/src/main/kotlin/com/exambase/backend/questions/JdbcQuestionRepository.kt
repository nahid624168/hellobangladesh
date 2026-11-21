package com.exambase.backend.questions

import com.exambase.backend.Database
import com.exambase.backend.academic.Crumb
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.academic.escapeLike
import java.sql.ResultSet
import java.util.UUID

/** A question is visible only when it and the whole lesson chain above it are published. */
private const val Q_FROM = """
    FROM questions q
    JOIN lessons l ON l.id = q.lesson_id
    JOIN subcategories sc ON sc.id = l.subcategory_id
    JOIN categories c ON c.id = sc.category_id
    JOIN subjects s ON s.id = c.subject_id
"""
private const val Q_VISIBLE =
    "q.status = 'published' AND l.status = 'published' AND sc.status = 'published' AND c.status = 'published' AND s.status = 'published'"

/**
 * Deliberately lists columns by name: is_correct, explanation, explanation_bn and the answer-key table are never
 * selected by this class, so they cannot reach a response even by accident.
 */
private const val Q_SUMMARY_COLUMNS = """
    q.id, q.lesson_id, q.type, q.question_text, q.question_text_bn, q.difficulty, q.marks, q.negative_marks,
    COALESCE((SELECT array_agg(t.slug ORDER BY t.slug) FROM question_tags qt JOIN tags t ON t.id = qt.tag_id
              WHERE qt.question_id = q.id), '{}') AS tags,
    EXISTS (SELECT 1 FROM question_images qi WHERE qi.question_id = q.id AND qi.placement = 'question') AS has_image
"""

private fun ResultSet.uuid(col: String): String = getObject(col, UUID::class.java).toString()

private fun ResultSet.questionSummary(): QuestionSummary {
    @Suppress("UNCHECKED_CAST")
    val tags = (getArray("tags")?.array as? Array<String>)?.toList() ?: emptyList()
    return QuestionSummary(
        id = uuid("id"),
        lessonId = uuid("lesson_id"),
        type = getString("type"),
        text = getString("question_text"),
        textBn = getString("question_text_bn"),
        difficulty = getString("difficulty"),
        marks = getBigDecimal("marks").toDouble(),
        negativeMarks = getBigDecimal("negative_marks").toDouble(),
        tags = tags,
        hasImage = getBoolean("has_image"),
    )
}

class JdbcQuestionRepository(private val db: Database) : QuestionRepository {

    override fun listQuestions(filter: QuestionFilter, limit: Int, offset: Int): Page<QuestionSummary> = db.withConnection { c ->
        val where = StringBuilder(Q_VISIBLE)
        val params = mutableListOf<Any>()
        fun cond(sql: String, vararg values: Any) {
            where.append(" AND ").append(sql)
            params.addAll(values)
        }
        filter.subjectId?.let { cond("s.id = ?", it) }
        filter.categoryId?.let { cond("c.id = ?", it) }
        filter.subcategoryId?.let { cond("sc.id = ?", it) }
        filter.lessonId?.let { cond("l.id = ?", it) }
        filter.type?.let { cond("q.type = ?", it) }
        filter.difficulty?.let { cond("q.difficulty = ?", it) }
        filter.tag?.let {
            cond("EXISTS (SELECT 1 FROM question_tags qt JOIN tags t ON t.id = qt.tag_id WHERE qt.question_id = q.id AND t.slug = ?)", it)
        }
        filter.search?.let {
            val like = "%" + escapeLike(it) + "%"
            cond("(q.question_text ILIKE ? ESCAPE '\\' OR q.question_text_bn ILIKE ? ESCAPE '\\')", like, like)
        }
        val order = when (filter.sort) {
            QuestionFilter.SORT_NEWEST -> "q.created_at DESC, q.id"
            QuestionFilter.SORT_DIFFICULTY ->
                "CASE q.difficulty WHEN 'easy' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END, q.sort_order, q.id"
            else -> "q.sort_order ASC, q.created_at ASC, q.id"
        }
        val sql = "SELECT $Q_SUMMARY_COLUMNS $Q_FROM WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        c.prepareStatement(sql).use { ps ->
            var i = 1
            params.forEach { ps.setObject(i++, it) }
            ps.setInt(i++, limit + 1)
            ps.setInt(i, offset)
            ps.executeQuery().use { rs ->
                Paging.page(buildList { while (rs.next()) add(rs.questionSummary()) }, limit, offset)
            }
        }
    }

    override fun getQuestion(id: UUID): QuestionDetail? = db.withConnection { c ->
        class Head(val summary: QuestionSummary, val version: Int, val crumbs: List<Crumb>)
        val head = c.prepareStatement(
            """
            SELECT $Q_SUMMARY_COLUMNS, q.current_version,
              s.id AS s_id, s.title AS s_title, s.title_bn AS s_title_bn,
              c.id AS c_id, c.title AS c_title, c.title_bn AS c_title_bn,
              sc.id AS sc_id, sc.title AS sc_title, sc.title_bn AS sc_title_bn,
              l.title AS l_title, l.title_bn AS l_title_bn
            $Q_FROM
            WHERE q.id = ? AND $Q_VISIBLE
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else {
                    val summary = rs.questionSummary()
                    Head(
                        summary, rs.getInt("current_version"),
                        listOf(
                            Crumb("subject", rs.uuid("s_id"), rs.getString("s_title"), rs.getString("s_title_bn")),
                            Crumb("category", rs.uuid("c_id"), rs.getString("c_title"), rs.getString("c_title_bn")),
                            Crumb("subcategory", rs.uuid("sc_id"), rs.getString("sc_title"), rs.getString("sc_title_bn")),
                            Crumb("lesson", summary.lessonId, rs.getString("l_title"), rs.getString("l_title_bn")),
                        ),
                    )
                }
            }
        } ?: return@withConnection null

        // is_correct is intentionally absent from this SELECT.
        val options = c.prepareStatement(
            "SELECT id, option_text, option_text_bn FROM question_options WHERE question_id = ? ORDER BY sort_order, id",
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(OptionDto(rs.uuid("id"), rs.getString("option_text"), rs.getString("option_text_bn"))) }
            }
        }
        val images = c.prepareStatement(
            """
            SELECT id, kind, url, alt_text, alt_text_bn FROM question_images
            WHERE question_id = ? AND placement = 'question' ORDER BY sort_order, id
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(QuestionImageDto(rs.uuid("id"), rs.getString("kind"), rs.getString("url"), rs.getString("alt_text"), rs.getString("alt_text_bn")))
                    }
                }
            }
        }
        QuestionDetail(head.summary, options, images, head.crumbs, head.version)
    }

    override fun listTags(): List<TagDto> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT t.slug, t.name, t.name_bn, count(DISTINCT q.id) AS question_count
            FROM tags t
            JOIN question_tags qt ON qt.tag_id = t.id
            JOIN questions q ON q.id = qt.question_id
            JOIN lessons l ON l.id = q.lesson_id
            JOIN subcategories sc ON sc.id = l.subcategory_id
            JOIN categories c ON c.id = sc.category_id
            JOIN subjects s ON s.id = c.subject_id
            WHERE $Q_VISIBLE
            GROUP BY t.id
            ORDER BY t.slug
            LIMIT 500
            """,
        ).use { ps ->
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(TagDto(rs.getString("slug"), rs.getString("name"), rs.getString("name_bn"), rs.getInt("question_count"))) }
            }
        }
    }

    override fun setBookmark(userId: UUID, questionId: UUID, bookmarked: Boolean): Boolean = db.withConnection { c ->
        // Only bookmark visible questions
        val visible = c.prepareStatement(
            "SELECT 1 $Q_FROM WHERE q.id = ? AND $Q_VISIBLE"
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { it.next() }
        }
        if (!visible) return@withConnection false
        if (bookmarked) {
            c.prepareStatement(
                """
                INSERT INTO question_bookmarks (user_id, question_id) VALUES (?, ?)
                ON CONFLICT (user_id, question_id) DO NOTHING
                """
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setObject(2, questionId)
                ps.executeUpdate()
            }
        } else {
            c.prepareStatement(
                "DELETE FROM question_bookmarks WHERE user_id = ? AND question_id = ?"
            ).use { ps ->
                ps.setObject(1, userId)
                ps.setObject(2, questionId)
                ps.executeUpdate()
            }
        }
        true
    }

    override fun isBookmarked(userId: UUID, questionId: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "SELECT 1 FROM question_bookmarks WHERE user_id = ? AND question_id = ?"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, questionId)
            ps.executeQuery().use { it.next() }
        }
    }

    override fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<QuestionBookmarkItem> = db.withConnection { c ->
        val total = c.prepareStatement(
            "SELECT count(*) FROM question_bookmarks WHERE user_id = ?"
        ).use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val items = c.prepareStatement(
            """
            SELECT $Q_SUMMARY_COLUMNS, qb.created_at AS bookmarked_at
            $Q_FROM
            JOIN question_bookmarks qb ON qb.question_id = q.id
            WHERE qb.user_id = ? AND $Q_VISIBLE
            ORDER BY qb.created_at DESC
            LIMIT ? OFFSET ?
            """
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setInt(2, limit)
            ps.setInt(3, offset)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            QuestionBookmarkItem(
                                question = rs.questionSummary(),
                                bookmarkedAt = rs.getTimestamp("bookmarked_at").toInstant().toString(),
                            )
                        )
                    }
                }
            }
        }
        Page(items, Paging.encode(offset, limit, total))
    }

    override fun createReport(userId: UUID, questionId: UUID, reason: String, details: String): QuestionReportDto? = db.withConnection { c ->
        val visible = c.prepareStatement(
            "SELECT 1 $Q_FROM WHERE q.id = ? AND $Q_VISIBLE"
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { it.next() }
        }
        if (!visible) return@withConnection null
        c.prepareStatement(
            """
            INSERT INTO question_reports (user_id, question_id, reason, details)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, question_id) WHERE status = 'open' DO NOTHING
            RETURNING id, question_id, reason, details, status, created_at
            """
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, questionId)
            ps.setString(3, reason)
            ps.setString(4, details)
            ps.executeQuery().use { rs ->
                if (!rs.next()) {
                    // Already has an open report — fetch it
                    c.prepareStatement(
                        """
                        SELECT id, question_id, reason, details, status, created_at
                        FROM question_reports
                        WHERE user_id = ? AND question_id = ? AND status = 'open'
                        """
                    ).use { ps2 ->
                        ps2.setObject(1, userId)
                        ps2.setObject(2, questionId)
                        ps2.executeQuery().use { rs2 ->
                            if (!rs2.next()) return@withConnection null
                            QuestionReportDto(
                                id = rs2.getObject("id", UUID::class.java).toString(),
                                questionId = rs2.getObject("question_id", UUID::class.java).toString(),
                                reason = rs2.getString("reason"),
                                details = rs2.getString("details"),
                                status = rs2.getString("status"),
                                createdAt = rs2.getTimestamp("created_at").toInstant().toString(),
                            )
                        }
                    }
                } else {
                    QuestionReportDto(
                        id = rs.getObject("id", UUID::class.java).toString(),
                        questionId = rs.getObject("question_id", UUID::class.java).toString(),
                        reason = rs.getString("reason"),
                        details = rs.getString("details"),
                        status = rs.getString("status"),
                        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                    )
                }
            }
        }
    }
}
