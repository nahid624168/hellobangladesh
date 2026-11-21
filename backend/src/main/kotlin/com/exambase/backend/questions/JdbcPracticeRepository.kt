package com.exambase.backend.questions

import com.exambase.backend.Database
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import java.sql.ResultSet
import java.util.UUID

private const val VISIBLE_FROM = """
    FROM questions q
    JOIN lessons l ON l.id = q.lesson_id
    JOIN subcategories sc ON sc.id = l.subcategory_id
    JOIN categories c ON c.id = sc.category_id
    JOIN subjects s ON s.id = c.subject_id
    WHERE q.status = 'published' AND l.status = 'published' AND sc.status = 'published'
      AND c.status = 'published' AND s.status = 'published'
"""

private fun ResultSet.uuid(col: String): UUID = getObject(col, UUID::class.java)

class JdbcPracticeRepository(private val db: Database) : PracticeRepository {

    override fun answerKey(id: UUID): AnswerKey? = db.withConnection { c ->
        class Head(val lessonId: UUID, val type: String, val version: Int, val marks: Double, val neg: Double, val expl: String, val explBn: String?)
        val head = c.prepareStatement(
            """
            SELECT q.lesson_id, q.type, q.current_version, q.marks, q.negative_marks, q.explanation, q.explanation_bn
            $VISIBLE_FROM AND q.id = ?
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else Head(
                    rs.uuid("lesson_id"), rs.getString("type"), rs.getInt("current_version"),
                    rs.getBigDecimal("marks").toDouble(), rs.getBigDecimal("negative_marks").toDouble(),
                    rs.getString("explanation"), rs.getString("explanation_bn"),
                )
            }
        } ?: return@withConnection null

        val allOptions = mutableSetOf<UUID>()
        val correct = mutableSetOf<UUID>()
        c.prepareStatement("SELECT id, is_correct FROM question_options WHERE question_id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    allOptions += rs.uuid("id")
                    if (rs.getBoolean("is_correct")) correct += rs.uuid("id")
                }
            }
        }
        val accepted = c.prepareStatement("SELECT answer FROM question_answer_keys WHERE question_id = ? ORDER BY answer").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString("answer")) } }
        }
        val images = c.prepareStatement(
            "SELECT id, kind, url, alt_text, alt_text_bn FROM question_images WHERE question_id = ? AND placement = 'explanation' ORDER BY sort_order, id",
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(QuestionImageDto(rs.uuid("id").toString(), rs.getString("kind"), rs.getString("url"), rs.getString("alt_text"), rs.getString("alt_text_bn")))
                }
            }
        }
        AnswerKey(id, head.lessonId, head.type, head.version, head.marks, head.neg, allOptions, correct, accepted, head.expl, head.explBn, images)
    }

    override fun recordAttempt(userId: UUID, key: AnswerKey, submission: Submission, graded: Graded): UUID = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO practice_attempts (user_id, question_id, question_version, selected_option_ids, answer_text, result, awarded_marks)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, key.questionId)
            ps.setInt(3, key.version)
            ps.setArray(4, c.createArrayOf("uuid", submission.selected.toTypedArray()))
            ps.setString(5, submission.text)
            ps.setString(6, graded.result)
            ps.setBigDecimal(7, java.math.BigDecimal.valueOf(graded.awardedMarks))
            ps.executeQuery().use { rs -> rs.next(); rs.uuid("id") }
        }
    }

    override fun history(userId: UUID, lessonId: UUID?, questionId: UUID?, limit: Int, offset: Int): Page<PracticeAttemptDto> = db.withConnection { c ->
        val extra = StringBuilder()
        val params = mutableListOf<Any>(userId)
        lessonId?.let { extra.append(" AND q.lesson_id = ?"); params += it }
        questionId?.let { extra.append(" AND q.id = ?"); params += it }
        val sql = """
            SELECT pa.id, pa.question_id, q.lesson_id, q.type, q.question_text, q.question_text_bn,
                   pa.result, pa.awarded_marks, pa.created_at
            FROM practice_attempts pa
            JOIN questions q ON q.id = pa.question_id
            JOIN lessons l ON l.id = q.lesson_id
            JOIN subcategories sc ON sc.id = l.subcategory_id
            JOIN categories c ON c.id = sc.category_id
            JOIN subjects s ON s.id = c.subject_id
            WHERE pa.user_id = ? AND q.status = 'published' AND l.status = 'published' AND sc.status = 'published'
              AND c.status = 'published' AND s.status = 'published' $extra
            ORDER BY pa.created_at DESC, pa.id
            LIMIT ? OFFSET ?
        """
        c.prepareStatement(sql).use { ps ->
            var i = 1
            params.forEach { ps.setObject(i++, it) }
            ps.setInt(i++, limit + 1)
            ps.setInt(i, offset)
            ps.executeQuery().use { rs ->
                val rows = buildList {
                    while (rs.next()) {
                        add(
                            PracticeAttemptDto(
                                attemptId = rs.uuid("id").toString(), questionId = rs.uuid("question_id").toString(),
                                lessonId = rs.uuid("lesson_id").toString(), type = rs.getString("type"),
                                text = rs.getString("question_text"), textBn = rs.getString("question_text_bn"),
                                result = rs.getString("result"), awardedMarks = rs.getBigDecimal("awarded_marks").toDouble(),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
                Paging.page(rows, limit, offset)
            }
        }
    }

    override fun summary(userId: UUID, lessonId: UUID): PracticeSummary = db.withConnection { c ->
        val total = c.prepareStatement("SELECT count(*) $VISIBLE_FROM AND q.lesson_id = ?").use { ps ->
            ps.setObject(1, lessonId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        // Latest attempt per question, limited to questions that are still visible.
        val sql = """
            SELECT count(*) AS attempted, count(*) FILTER (WHERE latest.result = 'correct') AS correct
            FROM (
              SELECT DISTINCT ON (pa.question_id) pa.question_id, pa.result
              FROM practice_attempts pa
              JOIN questions q ON q.id = pa.question_id
              JOIN lessons l ON l.id = q.lesson_id
              JOIN subcategories sc ON sc.id = l.subcategory_id
              JOIN categories c ON c.id = sc.category_id
              JOIN subjects s ON s.id = c.subject_id
              WHERE pa.user_id = ? AND q.lesson_id = ? AND q.status = 'published' AND l.status = 'published'
                AND sc.status = 'published' AND c.status = 'published' AND s.status = 'published'
              ORDER BY pa.question_id, pa.created_at DESC
            ) latest
        """
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, lessonId)
            ps.executeQuery().use { rs ->
                rs.next()
                PracticeSummary(lessonId.toString(), total, rs.getInt("attempted"), rs.getInt("correct"))
            }
        }
    }
}
