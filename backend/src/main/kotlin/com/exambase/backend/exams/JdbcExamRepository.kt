package com.exambase.backend.exams

import com.exambase.backend.Database
import com.exambase.backend.questions.AnswerKey
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcExamRepository(private val db: Database) : ExamRepository {

    override fun listForLearner(userId: UUID, limit: Int, offset: Int): List<Triple<ExamRecord, Int, AttemptRecord?>> =
        db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT * FROM exams
                WHERE status NOT IN ('draft', 'archived') AND visibility = 'public'
                ORDER BY opens_at DESC
                LIMIT ? OFFSET ?
                """,
            ).use { ps ->
                ps.setInt(1, limit)
                ps.setInt(2, offset)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val e = rs.toExam()
                            add(Triple(e, attemptsUsed(c, e.id, userId), latestAttempt(c, e.id, userId)))
                        }
                    }
                }
            }
        }

    override fun findExam(id: UUID): ExamRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM exams WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toExam() else null }
        }
    }

    override fun listSections(examId: UUID): List<SectionRecord> = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM exam_sections WHERE exam_id = ? ORDER BY position").use { ps ->
            ps.setObject(1, examId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toSection()) } }
        }
    }

    override fun listQuestions(examId: UUID): List<SectionQuestionRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT esq.section_id, esq.question_id, esq.position, esq.marks_override,
                   q.type, q.question_text, q.question_text_bn, q.marks, q.negative_marks, q.current_version, q.lesson_id,
                   q.explanation, q.explanation_bn
            FROM exam_section_questions esq
            JOIN exam_sections es ON es.id = esq.section_id
            JOIN questions q ON q.id = esq.question_id
            WHERE es.exam_id = ?
            ORDER BY es.position, esq.position
            """,
        ).use { ps ->
            ps.setObject(1, examId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val qid = rs.getObject("question_id", UUID::class.java)
                        val options = loadOptions(c, qid)
                        val correct = options.filter { it.third }.map { it.first }.toSet()
                        val key = AnswerKey(
                            qid, rs.getObject("lesson_id", UUID::class.java), rs.getString("type"),
                            rs.getInt("current_version"), rs.getBigDecimal("marks").toDouble(),
                            rs.getBigDecimal("negative_marks").toDouble(), options.map { it.first }.toSet(), correct,
                            loadAccepted(c, qid), rs.getString("explanation") ?: "", rs.getString("explanation_bn"), emptyList(),
                        )
                        add(
                            SectionQuestionRow(
                                rs.getObject("section_id", UUID::class.java), qid, rs.getInt("position"),
                                rs.getBigDecimal("marks_override")?.toDouble(), key,
                                rs.getString("question_text"), rs.getString("question_text_bn"),
                                options.map { it.first to (it.second to null) },
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun attemptsUsed(examId: UUID, userId: UUID): Int = db.withConnection { c -> attemptsUsed(c, examId, userId) }
    override fun latestAttempt(examId: UUID, userId: UUID): AttemptRecord? = db.withConnection { c -> latestAttempt(c, examId, userId) }

    override fun findAttempt(id: UUID): AttemptRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM exam_attempts WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toAttempt() else null }
        }
    }

    override fun createExam(record: ExamRecord, sections: List<Pair<SectionRecord, List<UUID>>>): ExamRecord =
        db.transaction { c ->
            c.prepareStatement(
                """
                INSERT INTO exams (
                  id, title, title_bn, instructions, instructions_bn, status, visibility,
                  opens_at, closes_at, duration_seconds, max_attempts, require_email_verified,
                  subject_id, shuffle_questions, shuffle_options, results_policy, pass_percent, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
            ).use { ps ->
                var i = 1
                ps.setObject(i++, record.id)
                ps.setString(i++, record.title)
                ps.setString(i++, record.titleBn)
                ps.setString(i++, record.instructions)
                ps.setString(i++, record.instructionsBn)
                ps.setString(i++, record.status)
                ps.setString(i++, record.visibility)
                ps.setTimestamp(i++, Timestamp.from(record.opensAt))
                ps.setTimestamp(i++, Timestamp.from(record.closesAt))
                ps.setInt(i++, record.durationSeconds)
                ps.setInt(i++, record.maxAttempts)
                ps.setBoolean(i++, record.requireEmailVerified)
                ps.setObject(i++, record.subjectId)
                ps.setBoolean(i++, record.shuffleQuestions)
                ps.setBoolean(i++, record.shuffleOptions)
                ps.setString(i++, record.resultsPolicy)
                if (record.passPercent != null) ps.setInt(i++, record.passPercent) else ps.setObject(i++, null)
                ps.setObject(i++, record.createdBy)
                ps.executeUpdate()
            }
            sections.forEach { (sec, qids) ->
                c.prepareStatement(
                    "INSERT INTO exam_sections (id, exam_id, position, title, title_bn, instructions, time_limit_seconds) VALUES (?,?,?,?,?,?,?)",
                ).use { ps ->
                    ps.setObject(1, sec.id)
                    ps.setObject(2, sec.examId)
                    ps.setInt(3, sec.position)
                    ps.setString(4, sec.title)
                    ps.setString(5, sec.titleBn)
                    ps.setString(6, sec.instructions)
                    if (sec.timeLimitSeconds != null) ps.setInt(7, sec.timeLimitSeconds) else ps.setObject(7, null)
                    ps.executeUpdate()
                }
                qids.forEachIndexed { idx, qid ->
                    c.prepareStatement(
                        "INSERT INTO exam_section_questions (section_id, question_id, position) VALUES (?,?,?)",
                    ).use { ps ->
                        ps.setObject(1, sec.id)
                        ps.setObject(2, qid)
                        ps.setInt(3, idx + 1)
                        ps.executeUpdate()
                    }
                }
            }
            record
        }

    override fun startAttempt(examId: UUID, userId: UUID, attemptNumber: Int, startedAt: Instant, deadlineAt: Instant): AttemptRecord =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO exam_attempts (exam_id, user_id, attempt_number, status, started_at, deadline_at)
                VALUES (?, ?, ?, 'in_progress', ?, ?) RETURNING *
                """,
            ).use { ps ->
                ps.setObject(1, examId)
                ps.setObject(2, userId)
                ps.setInt(3, attemptNumber)
                ps.setTimestamp(4, Timestamp.from(startedAt))
                ps.setTimestamp(5, Timestamp.from(deadlineAt))
                ps.executeQuery().use { rs -> rs.next(); rs.toAttempt() }
            }
        }

    override fun upsertAnswer(attemptId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant): Instant =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO exam_answers (attempt_id, question_id, selected, text_answer, client_key, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                  selected = EXCLUDED.selected, text_answer = EXCLUDED.text_answer,
                  client_key = COALESCE(EXCLUDED.client_key, exam_answers.client_key),
                  updated_at = EXCLUDED.updated_at
                """,
            ).use { ps ->
                ps.setObject(1, attemptId)
                ps.setObject(2, questionId)
                ps.setArray(3, c.createArrayOf("text", selected.toTypedArray()))
                ps.setString(4, text)
                ps.setString(5, clientKey)
                ps.setTimestamp(6, Timestamp.from(now))
                ps.executeUpdate()
            }
            now
        }

    override fun listAnswers(attemptId: UUID): List<ExamAnswerRow> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT question_id, selected, text_answer, client_key, result, marks_awarded FROM exam_answers WHERE attempt_id = ?",
        ).use { ps ->
            ps.setObject(1, attemptId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        @Suppress("UNCHECKED_CAST")
                        val selected = (rs.getArray("selected")?.array as? Array<String>)?.toList() ?: emptyList()
                        add(
                            ExamAnswerRow(
                                rs.getObject("question_id", UUID::class.java), selected,
                                rs.getString("text_answer"), rs.getString("client_key"),
                                rs.getString("result"), rs.getBigDecimal("marks_awarded")?.toDouble(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun submitAttempt(
        attemptId: UUID, status: String, submittedAt: Instant,
        score: Double, maxScore: Double, percent: Double, passed: Boolean?, graded: List<ExamAnswerRow>,
    ): AttemptRecord = db.transaction { c ->
        graded.forEach { a ->
            c.prepareStatement(
                """
                INSERT INTO exam_answers (attempt_id, question_id, selected, text_answer, client_key, result, marks_awarded, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                  result = EXCLUDED.result, marks_awarded = EXCLUDED.marks_awarded, updated_at = EXCLUDED.updated_at
                """,
            ).use { ps ->
                ps.setObject(1, attemptId)
                ps.setObject(2, a.questionId)
                ps.setArray(3, c.createArrayOf("text", a.selected.toTypedArray()))
                ps.setString(4, a.textAnswer)
                ps.setString(5, a.clientKey)
                ps.setString(6, a.result)
                if (a.marksAwarded != null) ps.setBigDecimal(7, a.marksAwarded.toBigDecimal()) else ps.setObject(7, null)
                ps.setTimestamp(8, Timestamp.from(submittedAt))
                ps.executeUpdate()
            }
        }
        c.prepareStatement(
            """
            UPDATE exam_attempts SET status = ?, submitted_at = ?, score = ?, max_score = ?, percent = ?, passed = ?
            WHERE id = ? RETURNING *
            """,
        ).use { ps ->
            ps.setString(1, status)
            ps.setTimestamp(2, Timestamp.from(submittedAt))
            ps.setBigDecimal(3, score.toBigDecimal())
            ps.setBigDecimal(4, maxScore.toBigDecimal())
            ps.setBigDecimal(5, percent.toBigDecimal())
            if (passed != null) ps.setBoolean(6, passed) else ps.setObject(6, null)
            ps.setObject(7, attemptId)
            ps.executeQuery().use { rs -> rs.next(); rs.toAttempt() }
        }
    }

    override fun expireIfNeeded(attemptId: UUID, now: Instant): AttemptRecord? = db.withConnection { c ->
        c.prepareStatement(
            """
            UPDATE exam_attempts SET status = 'expired', submitted_at = COALESCE(submitted_at, ?)
            WHERE id = ? AND status = 'in_progress' AND deadline_at <= ?
            RETURNING *
            """,
        ).use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setObject(2, attemptId)
            ps.setTimestamp(3, Timestamp.from(now))
            ps.executeQuery().use { rs -> if (rs.next()) rs.toAttempt() else null }
        }
    }

    override fun audit(examId: UUID, attemptId: UUID?, userId: UUID?, eventType: String, detailJson: String) =
        db.withConnection { c ->
            c.prepareStatement(
                "INSERT INTO exam_audit_events (exam_id, attempt_id, user_id, event_type, detail) VALUES (?,?,?,? ,?::jsonb)",
            ).use { ps ->
                ps.setObject(1, examId)
                ps.setObject(2, attemptId)
                ps.setObject(3, userId)
                ps.setString(4, eventType)
                ps.setString(5, detailJson)
                ps.executeUpdate()
                Unit
            }
        }

    override fun listAudit(examId: UUID, limit: Int): List<Triple<Long, String, Pair<String, Instant>>> =
        db.withConnection { c ->
            c.prepareStatement(
                "SELECT id, event_type, detail::text, created_at FROM exam_audit_events WHERE exam_id = ? ORDER BY id DESC LIMIT ?",
            ).use { ps ->
                ps.setObject(1, examId)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                Triple(
                                    rs.getLong("id"),
                                    rs.getString("event_type"),
                                    rs.getString("detail") to rs.getTimestamp("created_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun attemptsUsed(c: Connection, examId: UUID, userId: UUID): Int =
        c.prepareStatement(
            "SELECT count(*) FROM exam_attempts WHERE exam_id = ? AND user_id = ? AND status <> 'void'",
        ).use { ps ->
            ps.setObject(1, examId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    private fun latestAttempt(c: Connection, examId: UUID, userId: UUID): AttemptRecord? =
        c.prepareStatement(
            "SELECT * FROM exam_attempts WHERE exam_id = ? AND user_id = ? ORDER BY attempt_number DESC LIMIT 1",
        ).use { ps ->
            ps.setObject(1, examId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toAttempt() else null }
        }

    private fun loadOptions(c: Connection, questionId: UUID): List<Triple<UUID, String, Boolean>> =
        c.prepareStatement(
            "SELECT id, option_text, is_correct FROM question_options WHERE question_id = ? ORDER BY sort_order, id",
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Triple(rs.getObject("id", UUID::class.java), rs.getString("option_text"), rs.getBoolean("is_correct")))
                    }
                }
            }
        }

    private fun loadAccepted(c: Connection, questionId: UUID): List<String> =
        c.prepareStatement("SELECT answer FROM question_answer_keys WHERE question_id = ?").use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    private fun ResultSet.toExam() = ExamRecord(
        id = getObject("id", UUID::class.java),
        title = getString("title"),
        titleBn = getString("title_bn"),
        instructions = getString("instructions") ?: "",
        instructionsBn = getString("instructions_bn"),
        status = getString("status"),
        visibility = getString("visibility"),
        opensAt = getTimestamp("opens_at").toInstant(),
        closesAt = getTimestamp("closes_at").toInstant(),
        durationSeconds = getInt("duration_seconds"),
        maxAttempts = getInt("max_attempts"),
        requireEmailVerified = getBoolean("require_email_verified"),
        subjectId = getObject("subject_id") as? UUID,
        shuffleQuestions = getBoolean("shuffle_questions"),
        shuffleOptions = getBoolean("shuffle_options"),
        resultsPolicy = getString("results_policy"),
        resultsPublishedAt = getTimestamp("results_published_at")?.toInstant(),
        passPercent = getInt("pass_percent").takeIf { !wasNull() },
        createdBy = getObject("created_by") as? UUID,
    )

    private fun ResultSet.toSection() = SectionRecord(
        id = getObject("id", UUID::class.java),
        examId = getObject("exam_id", UUID::class.java),
        position = getInt("position"),
        title = getString("title"),
        titleBn = getString("title_bn"),
        instructions = getString("instructions") ?: "",
        timeLimitSeconds = getInt("time_limit_seconds").takeIf { !wasNull() },
    )

    private fun ResultSet.toAttempt() = AttemptRecord(
        id = getObject("id", UUID::class.java),
        examId = getObject("exam_id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        attemptNumber = getInt("attempt_number"),
        status = getString("status"),
        startedAt = getTimestamp("started_at").toInstant(),
        deadlineAt = getTimestamp("deadline_at").toInstant(),
        submittedAt = getTimestamp("submitted_at")?.toInstant(),
        score = getBigDecimal("score")?.toDouble(),
        maxScore = getBigDecimal("max_score")?.toDouble(),
        percent = getBigDecimal("percent")?.toDouble(),
        passed = getBoolean("passed").takeIf { !wasNull() },
    )
}
