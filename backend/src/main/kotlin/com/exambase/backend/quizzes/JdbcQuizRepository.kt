package com.exambase.backend.quizzes

import com.exambase.backend.Database
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.academic.escapeLike
import com.exambase.backend.questions.AnswerKey
import com.exambase.backend.questions.QuestionImageDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

private fun ResultSet.uuid(col: String): UUID = getObject(col, UUID::class.java)
private fun ResultSet.instant(col: String): Instant = getTimestamp(col).toInstant()
private fun ResultSet.instantOrNull(col: String): Instant? = getTimestamp(col)?.toInstant()
private fun ResultSet.doubleOrNull(col: String): Double? = getBigDecimal(col)?.toDouble()
private fun ResultSet.intOrNull(col: String): Int? = getInt(col).let { if (wasNull()) null else it }
private fun ResultSet.boolOrNull(col: String): Boolean? = getBoolean(col).let { if (wasNull()) null else it }

private const val SET_SELECT = """
    SELECT s.id, s.title, s.description, s.lesson_id, s.status, s.time_limit_seconds, s.passing_percent, s.max_attempts,
           s.shuffle_questions, s.shuffle_options, s.show_explanations, s.created_at, s.updated_at,
           (SELECT count(*) FROM quiz_set_questions x WHERE x.quiz_set_id = s.id) AS question_count
    FROM quiz_sets s
"""

private const val ATTEMPT_SELECT = """
    SELECT id, user_id, quiz_set_id, status, started_at, deadline_at, submitted_at, score, max_score, percent, passed,
           passing_percent, show_explanations, questions_snapshot::text AS snapshot
    FROM quiz_attempts
"""

class JdbcQuizRepository(private val db: Database) : QuizRepository {
    private val json = Json

    private fun mapSet(rs: ResultSet) = QuizSetDto(
        id = rs.uuid("id").toString(),
        title = rs.getString("title"),
        description = rs.getString("description"),
        lessonId = rs.getObject("lesson_id", UUID::class.java)?.toString(),
        status = rs.getString("status"),
        settings = QuizSettingsDto(
            timeLimitSeconds = rs.intOrNull("time_limit_seconds"),
            passingPercent = rs.intOrNull("passing_percent"),
            maxAttempts = rs.intOrNull("max_attempts"),
            shuffleQuestions = rs.getBoolean("shuffle_questions"),
            shuffleOptions = rs.getBoolean("shuffle_options"),
            showExplanations = rs.getBoolean("show_explanations"),
        ),
        questionCount = rs.getInt("question_count"),
        createdAt = rs.instant("created_at").toString(),
        updatedAt = rs.instant("updated_at").toString(),
    )

    private fun mapAttempt(rs: ResultSet) = AttemptRecord(
        id = rs.uuid("id"),
        userId = rs.uuid("user_id"),
        quizSetId = rs.uuid("quiz_set_id"),
        status = rs.getString("status"),
        startedAt = rs.instant("started_at"),
        deadlineAt = rs.instantOrNull("deadline_at"),
        submittedAt = rs.instantOrNull("submitted_at"),
        score = rs.doubleOrNull("score"),
        maxScore = rs.doubleOrNull("max_score"),
        percent = rs.doubleOrNull("percent"),
        passed = rs.boolOrNull("passed"),
        passingPercent = rs.intOrNull("passing_percent"),
        showExplanations = rs.getBoolean("show_explanations"),
        questions = json.decodeFromString<List<QuizPlayQuestion>>(rs.getString("snapshot")),
    )

    private fun java.sql.PreparedStatement.setIntOrNull(i: Int, v: Int?) {
        if (v == null) setNull(i, Types.INTEGER) else setInt(i, v)
    }

    private fun java.sql.PreparedStatement.setUuidOrNull(i: Int, v: UUID?) {
        if (v == null) setNull(i, Types.OTHER) else setObject(i, v)
    }

    private fun findIn(c: Connection, ownerId: UUID, id: UUID): QuizSetDto? =
        c.prepareStatement("$SET_SELECT WHERE s.id = ? AND s.owner_id = ? AND s.deleted_at IS NULL").use { ps ->
            ps.setObject(1, id)
            ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (rs.next()) mapSet(rs) else null }
        }

    private fun findAttemptIn(c: Connection, userId: UUID, id: UUID): AttemptRecord? =
        c.prepareStatement("$ATTEMPT_SELECT WHERE id = ? AND user_id = ?").use { ps ->
            ps.setObject(1, id)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) mapAttempt(rs) else null }
        }

    override fun create(ownerId: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto = db.withConnection { c ->
        val s = draft.settings
        val id = c.prepareStatement(
            """
            INSERT INTO quiz_sets (owner_id, title, description, lesson_id, status, time_limit_seconds, passing_percent, max_attempts,
                                   shuffle_questions, shuffle_options, show_explanations, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """,
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.setString(2, draft.title)
            ps.setString(3, draft.description)
            ps.setUuidOrNull(4, draft.lessonId)
            ps.setString(5, draft.status)
            ps.setIntOrNull(6, s.timeLimitSeconds)
            ps.setIntOrNull(7, s.passingPercent)
            ps.setIntOrNull(8, s.maxAttempts)
            ps.setBoolean(9, s.shuffleQuestions)
            ps.setBoolean(10, s.shuffleOptions)
            ps.setBoolean(11, s.showExplanations)
            ps.setTimestamp(12, Timestamp.from(now))
            ps.setTimestamp(13, Timestamp.from(now))
            ps.executeQuery().use { rs -> rs.next(); rs.uuid("id") }
        }
        findIn(c, ownerId, id)!!
    }

    override fun find(ownerId: UUID, id: UUID): QuizSetDto? = db.withConnection { c -> findIn(c, ownerId, id) }

    override fun list(ownerId: UUID, filter: QuizSetFilter, limit: Int, offset: Int): Page<QuizSetListItem> = db.withConnection { c ->
        val where = StringBuilder("WHERE s.owner_id = ? AND s.deleted_at IS NULL")
        val params = mutableListOf<Any>(ownerId)
        // Archived sets are hidden unless the caller asks for them.
        if (filter.status != null) { where.append(" AND s.status = ?"); params += filter.status } else where.append(" AND s.status <> 'archived'")
        filter.lessonId?.let { where.append(" AND s.lesson_id = ?"); params += it }
        filter.search?.let { where.append(" AND s.title ILIKE ? ESCAPE '\\'"); params += "%${escapeLike(it)}%" }
        val order = when (filter.sort) {
            QuizSetFilter.SORT_TITLE -> "lower(s.title), s.id"
            QuizSetFilter.SORT_PLAYED -> "a.last_at DESC NULLS LAST, s.updated_at DESC, s.id"
            else -> "s.updated_at DESC, s.id"
        }
        val sql = """
            SELECT s.id, s.title, s.description, s.lesson_id, s.status, s.time_limit_seconds, s.passing_percent, s.max_attempts,
                   s.shuffle_questions, s.shuffle_options, s.show_explanations, s.created_at, s.updated_at,
                   (SELECT count(*) FROM quiz_set_questions x WHERE x.quiz_set_id = s.id) AS question_count,
                   a.attempt_count, a.last_at, a.best
            FROM quiz_sets s
            LEFT JOIN LATERAL (
                SELECT count(*) FILTER (WHERE status <> 'in_progress') AS attempt_count,
                       max(started_at) AS last_at,
                       max(percent) AS best
                FROM quiz_attempts WHERE quiz_set_id = s.id AND user_id = s.owner_id
            ) a ON TRUE
            $where
            ORDER BY $order
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
                        add(QuizSetListItem(mapSet(rs), rs.getInt("attempt_count"), rs.instantOrNull("last_at")?.toString(), rs.doubleOrNull("best")))
                    }
                }
                Paging.page(rows, limit, offset)
            }
        }
    }

    override fun update(ownerId: UUID, id: UUID, draft: QuizSetDraft, now: Instant): QuizSetDto? = db.withConnection { c ->
        val s = draft.settings
        val n = c.prepareStatement(
            """
            UPDATE quiz_sets SET title = ?, description = ?, lesson_id = ?, status = ?, time_limit_seconds = ?, passing_percent = ?,
                   max_attempts = ?, shuffle_questions = ?, shuffle_options = ?, show_explanations = ?, updated_at = ?
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """,
        ).use { ps ->
            ps.setString(1, draft.title)
            ps.setString(2, draft.description)
            ps.setUuidOrNull(3, draft.lessonId)
            ps.setString(4, draft.status)
            ps.setIntOrNull(5, s.timeLimitSeconds)
            ps.setIntOrNull(6, s.passingPercent)
            ps.setIntOrNull(7, s.maxAttempts)
            ps.setBoolean(8, s.shuffleQuestions)
            ps.setBoolean(9, s.shuffleOptions)
            ps.setBoolean(10, s.showExplanations)
            ps.setTimestamp(11, Timestamp.from(now))
            ps.setObject(12, id)
            ps.setObject(13, ownerId)
            ps.executeUpdate()
        }
        if (n == 0) null else findIn(c, ownerId, id)
    }

    override fun softDelete(ownerId: UUID, id: UUID, now: Instant): Boolean = db.withConnection { c ->
        c.prepareStatement("UPDATE quiz_sets SET deleted_at = ?, updated_at = ? WHERE id = ? AND owner_id = ? AND deleted_at IS NULL").use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setTimestamp(2, Timestamp.from(now))
            ps.setObject(3, id)
            ps.setObject(4, ownerId)
            ps.executeUpdate() > 0
        }
    }

    override fun duplicate(ownerId: UUID, id: UUID, newTitle: String, now: Instant): QuizSetDto? = db.transaction { c ->
        val newId = c.prepareStatement(
            """
            INSERT INTO quiz_sets (owner_id, title, description, lesson_id, status, time_limit_seconds, passing_percent, max_attempts,
                                   shuffle_questions, shuffle_options, show_explanations, created_at, updated_at)
            SELECT owner_id, ?, description, lesson_id, 'draft', time_limit_seconds, passing_percent, max_attempts,
                   shuffle_questions, shuffle_options, show_explanations, ?, ?
            FROM quiz_sets WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            RETURNING id
            """,
        ).use { ps ->
            ps.setString(1, newTitle)
            ps.setTimestamp(2, Timestamp.from(now))
            ps.setTimestamp(3, Timestamp.from(now))
            ps.setObject(4, id)
            ps.setObject(5, ownerId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.uuid("id") else null }
        } ?: return@transaction null
        c.prepareStatement("INSERT INTO quiz_set_questions (quiz_set_id, question_id, position) SELECT ?, question_id, position FROM quiz_set_questions WHERE quiz_set_id = ?").use { ps ->
            ps.setObject(1, newId)
            ps.setObject(2, id)
            ps.executeUpdate()
        }
        findIn(c, ownerId, newId)
    }

    override fun lessonVisible(lessonId: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT 1 FROM lessons l
            JOIN subcategories sc ON sc.id = l.subcategory_id
            JOIN categories c ON c.id = sc.category_id
            JOIN subjects s ON s.id = c.subject_id
            WHERE l.id = ? AND l.status = 'published' AND sc.status = 'published' AND c.status = 'published' AND s.status = 'published'
            """,
        ).use { ps ->
            ps.setObject(1, lessonId)
            ps.executeQuery().use { it.next() }
        }
    }

    override fun setQuestions(ownerId: UUID, id: UUID, questionIds: List<UUID>, now: Instant): Boolean = db.transaction { c ->
        val owned = c.prepareStatement("SELECT 1 FROM quiz_sets WHERE id = ? AND owner_id = ? AND deleted_at IS NULL FOR UPDATE").use { ps ->
            ps.setObject(1, id)
            ps.setObject(2, ownerId)
            ps.executeQuery().use { it.next() }
        }
        if (!owned) return@transaction false
        c.prepareStatement("DELETE FROM quiz_set_questions WHERE quiz_set_id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeUpdate()
        }
        c.prepareStatement("INSERT INTO quiz_set_questions (quiz_set_id, question_id, position) VALUES (?, ?, ?)").use { ps ->
            questionIds.forEachIndexed { i, qid ->
                ps.setObject(1, id)
                ps.setObject(2, qid)
                ps.setInt(3, i + 1)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        c.prepareStatement("UPDATE quiz_sets SET updated_at = ? WHERE id = ?").use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setObject(2, id)
            ps.executeUpdate()
        }
        true
    }

    override fun questionIds(quizSetId: UUID): List<UUID> = db.withConnection { c ->
        c.prepareStatement("SELECT question_id FROM quiz_set_questions WHERE quiz_set_id = ? ORDER BY position").use { ps ->
            ps.setObject(1, quizSetId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.uuid("question_id")) } }
        }
    }

    override fun inProgress(userId: UUID, quizSetId: UUID): AttemptRecord? = db.withConnection { c ->
        c.prepareStatement("$ATTEMPT_SELECT WHERE user_id = ? AND quiz_set_id = ? AND status = 'in_progress'").use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, quizSetId)
            ps.executeQuery().use { rs -> if (rs.next()) mapAttempt(rs) else null }
        }
    }

    override fun finishedAttempts(userId: UUID, quizSetId: UUID): Int = db.withConnection { c ->
        c.prepareStatement("SELECT count(*) FROM quiz_attempts WHERE user_id = ? AND quiz_set_id = ? AND status <> 'in_progress'").use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, quizSetId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    override fun startAttempt(draft: AttemptDraft): AttemptRecord = db.withConnection { c ->
        val id = c.prepareStatement(
            """
            INSERT INTO quiz_attempts (user_id, quiz_set_id, status, started_at, deadline_at, passing_percent, show_explanations, questions_snapshot)
            VALUES (?, ?, 'in_progress', ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (user_id, quiz_set_id) WHERE status = 'in_progress' DO NOTHING
            RETURNING id
            """,
        ).use { ps ->
            ps.setObject(1, draft.userId)
            ps.setObject(2, draft.quizSetId)
            ps.setTimestamp(3, Timestamp.from(draft.startedAt))
            if (draft.deadlineAt == null) ps.setNull(4, Types.TIMESTAMP) else ps.setTimestamp(4, Timestamp.from(draft.deadlineAt))
            ps.setIntOrNull(5, draft.passingPercent)
            ps.setBoolean(6, draft.showExplanations)
            ps.setString(7, json.encodeToString(draft.questions))
            ps.executeQuery().use { rs -> if (rs.next()) rs.uuid("id") else null }
        }
        if (id != null) {
            findAttemptIn(c, draft.userId, id)!!
        } else {
            // A simultaneous start won the race: hand back that attempt instead of creating a second.
            c.prepareStatement("$ATTEMPT_SELECT WHERE user_id = ? AND quiz_set_id = ? AND status = 'in_progress'").use { ps ->
                ps.setObject(1, draft.userId)
                ps.setObject(2, draft.quizSetId)
                ps.executeQuery().use { rs -> rs.next(); mapAttempt(rs) }
            }
        }
    }

    override fun findAttempt(userId: UUID, attemptId: UUID): AttemptRecord? = db.withConnection { c -> findAttemptIn(c, userId, attemptId) }

    override fun finishAttempt(attemptId: UUID, outcome: AttemptOutcome): Boolean = db.transaction { c ->
        val n = c.prepareStatement(
            """
            UPDATE quiz_attempts SET status = ?, submitted_at = ?, score = ?, max_score = ?, percent = ?, passed = ?
            WHERE id = ? AND status = 'in_progress'
            """,
        ).use { ps ->
            ps.setString(1, outcome.status)
            ps.setTimestamp(2, Timestamp.from(outcome.submittedAt))
            ps.setBigDecimal(3, BigDecimal.valueOf(outcome.score))
            ps.setBigDecimal(4, BigDecimal.valueOf(outcome.maxScore))
            ps.setBigDecimal(5, BigDecimal.valueOf(outcome.percent))
            if (outcome.passed == null) ps.setNull(6, Types.BOOLEAN) else ps.setBoolean(6, outcome.passed)
            ps.setObject(7, attemptId)
            ps.executeUpdate()
        }
        if (n == 0) return@transaction false
        c.prepareStatement(
            "INSERT INTO quiz_attempt_answers (attempt_id, position, question_id, selected_option_ids, answer_text, result, awarded_marks) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { ps ->
            outcome.answers.forEach { a ->
                ps.setObject(1, attemptId)
                ps.setInt(2, a.position)
                ps.setObject(3, a.questionId)
                ps.setArray(4, c.createArrayOf("uuid", a.selected.toTypedArray()))
                ps.setString(5, a.text)
                ps.setString(6, a.result)
                ps.setBigDecimal(7, BigDecimal.valueOf(a.awardedMarks))
                ps.addBatch()
            }
            ps.executeBatch()
        }
        true
    }

    override fun attemptAnswers(attemptId: UUID): List<AttemptAnswerRow> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT position, question_id, selected_option_ids, answer_text, result, awarded_marks FROM quiz_attempt_answers WHERE attempt_id = ? ORDER BY position",
        ).use { ps ->
            ps.setObject(1, attemptId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val selected = (rs.getArray("selected_option_ids").array as Array<*>).map { it as UUID }
                        add(
                            AttemptAnswerRow(
                                rs.getInt("position"), rs.uuid("question_id"), selected, rs.getString("answer_text"),
                                rs.getString("result"), rs.getBigDecimal("awarded_marks").toDouble(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun attempts(userId: UUID, quizSetId: UUID, limit: Int, offset: Int): Page<QuizAttemptSummaryDto> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, status, started_at, submitted_at, score, max_score, percent, passed
            FROM quiz_attempts WHERE user_id = ? AND quiz_set_id = ?
            ORDER BY started_at DESC, id LIMIT ? OFFSET ?
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, quizSetId)
            ps.setInt(3, limit + 1)
            ps.setInt(4, offset)
            ps.executeQuery().use { rs ->
                val rows = buildList {
                    while (rs.next()) {
                        add(
                            QuizAttemptSummaryDto(
                                rs.uuid("id").toString(), rs.getString("status"), rs.instant("started_at").toString(),
                                rs.instantOrNull("submitted_at")?.toString(), rs.doubleOrNull("score"), rs.doubleOrNull("max_score"),
                                rs.doubleOrNull("percent"), rs.boolOrNull("passed"),
                            ),
                        )
                    }
                }
                Paging.page(rows, limit, offset)
            }
        }
    }

    override fun answerKeys(questionIds: Collection<UUID>): Map<UUID, AnswerKey> {
        if (questionIds.isEmpty()) return emptyMap()
        return db.withConnection { c ->
            fun ids() = c.createArrayOf("uuid", questionIds.toTypedArray())
            class Head(val lessonId: UUID, val type: String, val version: Int, val marks: Double, val neg: Double, val expl: String, val explBn: String?)
            val heads = mutableMapOf<UUID, Head>()
            c.prepareStatement("SELECT id, lesson_id, type, current_version, marks, negative_marks, explanation, explanation_bn FROM questions WHERE id = ANY(?)").use { ps ->
                ps.setArray(1, ids())
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        heads[rs.uuid("id")] = Head(
                            rs.uuid("lesson_id"), rs.getString("type"), rs.getInt("current_version"),
                            rs.getBigDecimal("marks").toDouble(), rs.getBigDecimal("negative_marks").toDouble(),
                            rs.getString("explanation"), rs.getString("explanation_bn"),
                        )
                    }
                }
            }
            val options = mutableMapOf<UUID, MutableSet<UUID>>()
            val correct = mutableMapOf<UUID, MutableSet<UUID>>()
            c.prepareStatement("SELECT question_id, id, is_correct FROM question_options WHERE question_id = ANY(?)").use { ps ->
                ps.setArray(1, ids())
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val q = rs.uuid("question_id")
                        options.getOrPut(q) { mutableSetOf() } += rs.uuid("id")
                        if (rs.getBoolean("is_correct")) correct.getOrPut(q) { mutableSetOf() } += rs.uuid("id")
                    }
                }
            }
            val accepted = mutableMapOf<UUID, MutableList<String>>()
            c.prepareStatement("SELECT question_id, answer FROM question_answer_keys WHERE question_id = ANY(?) ORDER BY answer").use { ps ->
                ps.setArray(1, ids())
                ps.executeQuery().use { rs -> while (rs.next()) accepted.getOrPut(rs.uuid("question_id")) { mutableListOf() } += rs.getString("answer") }
            }
            val images = mutableMapOf<UUID, MutableList<QuestionImageDto>>()
            c.prepareStatement(
                "SELECT id, question_id, kind, url, alt_text, alt_text_bn FROM question_images WHERE question_id = ANY(?) AND placement = 'explanation' ORDER BY sort_order, id",
            ).use { ps ->
                ps.setArray(1, ids())
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        images.getOrPut(rs.uuid("question_id")) { mutableListOf() } +=
                            QuestionImageDto(rs.uuid("id").toString(), rs.getString("kind"), rs.getString("url"), rs.getString("alt_text"), rs.getString("alt_text_bn"))
                    }
                }
            }
            heads.mapValues { (id, h) ->
                AnswerKey(
                    id, h.lessonId, h.type, h.version, h.marks, h.neg, options[id].orEmpty(), correct[id].orEmpty(),
                    accepted[id].orEmpty(), h.expl, h.explBn, images[id].orEmpty(),
                )
            }
        }
    }
}
