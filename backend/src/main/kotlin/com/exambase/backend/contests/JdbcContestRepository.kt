package com.exambase.backend.contests

import com.exambase.backend.Database
import com.exambase.backend.questions.AnswerKey
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcContestRepository(private val db: Database) : ContestRepository {

    override fun listSummaries(userId: UUID, statusFilter: String?, limit: Int, offset: Int): List<Triple<ContestRecord, Int, ParticipantRecord?>> =
        db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT ct.*,
                  (SELECT count(*) FROM contest_participants cp WHERE cp.contest_id = ct.id) AS pcount
                FROM contests ct
                WHERE ct.status NOT IN ('draft', 'cancelled') AND ct.visibility = 'public'
                ORDER BY ct.starts_at DESC
                LIMIT ? OFFSET ?
                """,
            ).use { ps ->
                ps.setInt(1, limit)
                ps.setInt(2, offset)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val rec = rs.toContest()
                            val count = rs.getInt("pcount")
                            val p = latestParticipant(c, rec.id, userId)
                            add(Triple(rec, count, p))
                        }
                    }
                }
            }
        }

    override fun listForLearner(userId: UUID, statusFilter: String?, limit: Int, offset: Int) =
        listSummaries(userId, statusFilter, limit, offset).map { it.first to it.second } to 0

    override fun findContest(id: UUID): ContestRecord? = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM contests WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toContest() else null }
        }
    }

    override fun participantCount(contestId: UUID): Int = db.withConnection { c ->
        c.prepareStatement("SELECT count(*) FROM contest_participants WHERE contest_id = ?").use { ps ->
            ps.setObject(1, contestId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    override fun findParticipant(contestId: UUID, userId: UUID, attempt: Int?): ParticipantRecord? =
        db.withConnection { c -> latestParticipant(c, contestId, userId, attempt) }

    override fun latestParticipant(contestId: UUID, userId: UUID): ParticipantRecord? =
        db.withConnection { c -> latestParticipant(c, contestId, userId, null) }

    private fun latestParticipant(c: Connection, contestId: UUID, userId: UUID, attempt: Int? = null): ParticipantRecord? {
        val sql = if (attempt != null) {
            "SELECT * FROM contest_participants WHERE contest_id = ? AND user_id = ? AND attempt_number = ?"
        } else {
            "SELECT * FROM contest_participants WHERE contest_id = ? AND user_id = ? ORDER BY attempt_number DESC LIMIT 1"
        }
        return c.prepareStatement(sql).use { ps ->
            ps.setObject(1, contestId)
            ps.setObject(2, userId)
            if (attempt != null) ps.setInt(3, attempt)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toParticipant() else null }
        }
    }

    override fun createContest(record: ContestRecord, questionIds: List<UUID>): ContestRecord = db.transaction { c ->
        c.prepareStatement(
            """
            INSERT INTO contests (
              id, title, title_bn, description, description_bn, status, visibility,
              starts_at, ends_at, join_opens_at, join_closes_at, capacity, duration_seconds,
              shuffle_questions, shuffle_options, show_leaderboard, show_results_at, tie_break,
              max_attempts, subject_id, created_by
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
        ).use { ps ->
            var i = 1
            ps.setObject(i++, record.id)
            ps.setString(i++, record.title)
            ps.setString(i++, record.titleBn)
            ps.setString(i++, record.description)
            ps.setString(i++, record.descriptionBn)
            ps.setString(i++, record.status)
            ps.setString(i++, record.visibility)
            ps.setTimestamp(i++, Timestamp.from(record.startsAt))
            ps.setTimestamp(i++, Timestamp.from(record.endsAt))
            ps.setTimestamp(i++, record.joinOpensAt?.let { Timestamp.from(it) })
            ps.setTimestamp(i++, record.joinClosesAt?.let { Timestamp.from(it) })
            if (record.capacity != null) ps.setInt(i++, record.capacity) else ps.setObject(i++, null)
            ps.setInt(i++, record.durationSeconds)
            ps.setBoolean(i++, record.shuffleQuestions)
            ps.setBoolean(i++, record.shuffleOptions)
            ps.setBoolean(i++, record.showLeaderboard)
            ps.setTimestamp(i++, record.showResultsAt?.let { Timestamp.from(it) })
            ps.setString(i++, record.tieBreak)
            ps.setInt(i++, record.maxAttempts)
            ps.setObject(i++, record.subjectId)
            ps.setObject(i++, record.createdBy)
            ps.executeUpdate()
        }
        questionIds.forEachIndexed { idx, qid ->
            c.prepareStatement(
                "INSERT INTO contest_questions (contest_id, question_id, position) VALUES (?,?,?)",
            ).use { ps ->
                ps.setObject(1, record.id)
                ps.setObject(2, qid)
                ps.setInt(3, idx + 1)
                ps.executeUpdate()
            }
        }
        record
    }

    override fun setStatus(contestId: UUID, status: String) = db.withConnection { c ->
        c.prepareStatement("UPDATE contests SET status = ?, updated_at = now() WHERE id = ?").use { ps ->
            ps.setString(1, status)
            ps.setObject(2, contestId)
            ps.executeUpdate()
            Unit
        }
    }

    override fun join(contestId: UUID, userId: UUID, attemptNumber: Int, now: Instant): ParticipantRecord =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO contest_participants (contest_id, user_id, status, attempt_number, joined_at)
                VALUES (?, ?, 'joined', ?, ?)
                RETURNING *
                """,
            ).use { ps ->
                ps.setObject(1, contestId)
                ps.setObject(2, userId)
                ps.setInt(3, attemptNumber)
                ps.setTimestamp(4, Timestamp.from(now))
                ps.executeQuery().use { rs -> rs.next(); rs.toParticipant() }
            }
        }

    override fun start(participantId: UUID, startedAt: Instant, deadlineAt: Instant): ParticipantRecord =
        db.withConnection { c ->
            c.prepareStatement(
                """
                UPDATE contest_participants
                SET status = 'in_progress', started_at = ?, deadline_at = ?
                WHERE id = ?
                RETURNING *
                """,
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(startedAt))
                ps.setTimestamp(2, Timestamp.from(deadlineAt))
                ps.setObject(3, participantId)
                ps.executeQuery().use { rs -> rs.next(); rs.toParticipant() }
            }
        }

    override fun leave(contestId: UUID, userId: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "DELETE FROM contest_participants WHERE contest_id = ? AND user_id = ? AND status = 'joined'",
        ).use { ps ->
            ps.setObject(1, contestId)
            ps.setObject(2, userId)
            ps.executeUpdate() > 0
        }
    }

    override fun listQuestions(contestId: UUID): List<ContestQuestionRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT cq.question_id, cq.position, cq.marks_override,
                   q.type, q.question_text, q.question_text_bn, q.marks, q.negative_marks, q.current_version, q.lesson_id,
                   q.explanation, q.explanation_bn
            FROM contest_questions cq
            JOIN questions q ON q.id = cq.question_id
            WHERE cq.contest_id = ?
            ORDER BY cq.position
            """,
        ).use { ps ->
            ps.setObject(1, contestId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val qid = rs.getObject("question_id", UUID::class.java)
                        val options = loadOptions(c, qid)
                        val correct = options.filter { it.third }.map { it.first }.toSet()
                        val key = AnswerKey(
                            questionId = qid,
                            lessonId = rs.getObject("lesson_id", UUID::class.java),
                            type = rs.getString("type"),
                            version = rs.getInt("current_version"),
                            marks = rs.getBigDecimal("marks").toDouble(),
                            negativeMarks = rs.getBigDecimal("negative_marks").toDouble(),
                            optionIds = options.map { it.first }.toSet(),
                            correctOptionIds = correct,
                            acceptedAnswers = loadAccepted(c, qid),
                            explanation = rs.getString("explanation") ?: "",
                            explanationBn = rs.getString("explanation_bn"),
                            explanationImages = emptyList(),
                        )
                        val marksOverride = rs.getBigDecimal("marks_override")?.toDouble()
                        add(
                            ContestQuestionRow(
                                questionId = qid,
                                position = rs.getInt("position"),
                                marksOverride = marksOverride,
                                key = key,
                                questionText = rs.getString("question_text"),
                                questionTextBn = rs.getString("question_text_bn"),
                                options = options.map { it.first to (it.second to null) },
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadOptions(c: Connection, questionId: UUID): List<Triple<UUID, String, Boolean>> =
        c.prepareStatement(
            "SELECT id, option_text, is_correct FROM question_options WHERE question_id = ? ORDER BY sort_order, id",
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Triple(
                                rs.getObject("id", UUID::class.java),
                                rs.getString("option_text"),
                                rs.getBoolean("is_correct"),
                            ),
                        )
                    }
                }
            }
        }

    private fun loadAccepted(c: Connection, questionId: UUID): List<String> =
        c.prepareStatement(
            "SELECT answer FROM question_answer_keys WHERE question_id = ?",
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    override fun upsertAnswer(
        participantId: UUID, questionId: UUID, selected: List<String>, text: String?, clientKey: String?, now: Instant,
    ): Instant = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO contest_answers (participant_id, question_id, selected, text_answer, client_key, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (participant_id, question_id) DO UPDATE SET
              selected = EXCLUDED.selected,
              text_answer = EXCLUDED.text_answer,
              client_key = COALESCE(EXCLUDED.client_key, contest_answers.client_key),
              updated_at = EXCLUDED.updated_at
            """,
        ).use { ps ->
            ps.setObject(1, participantId)
            ps.setObject(2, questionId)
            ps.setArray(3, c.createArrayOf("text", selected.toTypedArray()))
            ps.setString(4, text)
            ps.setString(5, clientKey)
            ps.setTimestamp(6, Timestamp.from(now))
            ps.executeUpdate()
        }
        now
    }

    override fun listAnswers(participantId: UUID): List<AnswerRow> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT question_id, selected, text_answer, client_key, result, marks_awarded FROM contest_answers WHERE participant_id = ?",
        ).use { ps ->
            ps.setObject(1, participantId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        @Suppress("UNCHECKED_CAST")
                        val selected = (rs.getArray("selected")?.array as? Array<String>)?.toList() ?: emptyList()
                        add(
                            AnswerRow(
                                questionId = rs.getObject("question_id", UUID::class.java),
                                selected = selected,
                                textAnswer = rs.getString("text_answer"),
                                clientKey = rs.getString("client_key"),
                                result = rs.getString("result"),
                                marksAwarded = rs.getBigDecimal("marks_awarded")?.toDouble(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun submit(
        participantId: UUID, status: String, submittedAt: Instant,
        score: Double, maxScore: Double, percent: Double, timeTakenMs: Long, graded: List<AnswerRow>,
    ): ParticipantRecord = db.transaction { c ->
        graded.forEach { a ->
            c.prepareStatement(
                """
                INSERT INTO contest_answers (participant_id, question_id, selected, text_answer, client_key, result, marks_awarded, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (participant_id, question_id) DO UPDATE SET
                  result = EXCLUDED.result, marks_awarded = EXCLUDED.marks_awarded, updated_at = EXCLUDED.updated_at
                """,
            ).use { ps ->
                ps.setObject(1, participantId)
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
            UPDATE contest_participants SET
              status = ?, submitted_at = ?, score = ?, max_score = ?, percent = ?, time_taken_ms = ?
            WHERE id = ?
            RETURNING *
            """,
        ).use { ps ->
            ps.setString(1, status)
            ps.setTimestamp(2, Timestamp.from(submittedAt))
            ps.setBigDecimal(3, score.toBigDecimal())
            ps.setBigDecimal(4, maxScore.toBigDecimal())
            ps.setBigDecimal(5, percent.toBigDecimal())
            ps.setLong(6, timeTakenMs)
            ps.setObject(7, participantId)
            ps.executeQuery().use { rs -> rs.next(); rs.toParticipant() }
        }
    }

    override fun expireIfNeeded(participantId: UUID, now: Instant): ParticipantRecord? = db.withConnection { c ->
        c.prepareStatement(
            """
            UPDATE contest_participants SET status = 'expired', submitted_at = COALESCE(submitted_at, ?)
            WHERE id = ? AND status = 'in_progress' AND deadline_at IS NOT NULL AND deadline_at <= ?
            RETURNING *
            """,
        ).use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.setObject(2, participantId)
            ps.setTimestamp(3, Timestamp.from(now))
            ps.executeQuery().use { rs -> if (rs.next()) rs.toParticipant() else null }
        }
    }

    override fun leaderboard(contestId: UUID, tieBreak: String, limit: Int, offset: Int): List<LeaderboardRow> =
        db.withConnection { c ->
            val order = when (tieBreak) {
                TieBreak.FASTEST_TIME -> "cp.score DESC NULLS LAST, cp.time_taken_ms ASC NULLS LAST"
                TieBreak.NONE -> "cp.score DESC NULLS LAST, cp.id"
                else -> "cp.score DESC NULLS LAST, cp.submitted_at ASC NULLS LAST"
            }
            c.prepareStatement(
                """
                SELECT cp.user_id, u.display_name, cp.score, cp.percent, cp.submitted_at, cp.time_taken_ms
                FROM contest_participants cp
                JOIN users u ON u.id = cp.user_id
                WHERE cp.contest_id = ? AND cp.status = 'submitted'
                ORDER BY $order
                LIMIT ? OFFSET ?
                """,
            ).use { ps ->
                ps.setObject(1, contestId)
                ps.setInt(2, limit)
                ps.setInt(3, offset)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                LeaderboardRow(
                                    userId = rs.getObject("user_id", UUID::class.java),
                                    displayName = rs.getString("display_name"),
                                    score = rs.getBigDecimal("score")?.toDouble() ?: 0.0,
                                    percent = rs.getBigDecimal("percent")?.toDouble() ?: 0.0,
                                    submittedAt = rs.getTimestamp("submitted_at")?.toInstant(),
                                    timeTakenMs = rs.getLong("time_taken_ms").takeIf { !rs.wasNull() },
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun leaderboardTotal(contestId: UUID): Int = db.withConnection { c ->
        c.prepareStatement(
            "SELECT count(*) FROM contest_participants WHERE contest_id = ? AND status = 'submitted'",
        ).use { ps ->
            ps.setObject(1, contestId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    override fun rankForUser(contestId: UUID, userId: UUID, tieBreak: String): Int? = db.withConnection { c ->
        val order = when (tieBreak) {
            TieBreak.FASTEST_TIME -> "score DESC NULLS LAST, time_taken_ms ASC NULLS LAST"
            TieBreak.NONE -> "score DESC NULLS LAST, id"
            else -> "score DESC NULLS LAST, submitted_at ASC NULLS LAST"
        }
        c.prepareStatement(
            """
            SELECT rank FROM (
              SELECT user_id, ROW_NUMBER() OVER (ORDER BY $order) AS rank
              FROM contest_participants
              WHERE contest_id = ? AND status = 'submitted'
            ) t WHERE user_id = ?
            """,
        ).use { ps ->
            ps.setObject(1, contestId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("rank") else null }
        }
    }

    override fun recordAbuse(contestId: UUID, userId: UUID, participantId: UUID?, reason: String) =
        db.withConnection { c ->
            c.prepareStatement(
                "INSERT INTO contest_abuse_events (contest_id, user_id, participant_id, reason) VALUES (?,?,?,?)",
            ).use { ps ->
                ps.setObject(1, contestId)
                ps.setObject(2, userId)
                ps.setObject(3, participantId)
                ps.setString(4, reason)
                ps.executeUpdate()
                Unit
            }
        }

    private fun ResultSet.toContest() = ContestRecord(
        id = getObject("id", UUID::class.java),
        title = getString("title"),
        titleBn = getString("title_bn"),
        description = getString("description") ?: "",
        descriptionBn = getString("description_bn"),
        status = getString("status"),
        visibility = getString("visibility"),
        startsAt = getTimestamp("starts_at").toInstant(),
        endsAt = getTimestamp("ends_at").toInstant(),
        joinOpensAt = getTimestamp("join_opens_at")?.toInstant(),
        joinClosesAt = getTimestamp("join_closes_at")?.toInstant(),
        capacity = getInt("capacity").takeIf { !wasNull() },
        durationSeconds = getInt("duration_seconds"),
        shuffleQuestions = getBoolean("shuffle_questions"),
        shuffleOptions = getBoolean("shuffle_options"),
        showLeaderboard = getBoolean("show_leaderboard"),
        showResultsAt = getTimestamp("show_results_at")?.toInstant(),
        tieBreak = getString("tie_break"),
        maxAttempts = getInt("max_attempts"),
        subjectId = getObject("subject_id") as? UUID,
        createdBy = getObject("created_by") as? UUID,
    )

    private fun ResultSet.toParticipant() = ParticipantRecord(
        id = getObject("id", UUID::class.java),
        contestId = getObject("contest_id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        status = getString("status"),
        attemptNumber = getInt("attempt_number"),
        joinedAt = getTimestamp("joined_at").toInstant(),
        startedAt = getTimestamp("started_at")?.toInstant(),
        deadlineAt = getTimestamp("deadline_at")?.toInstant(),
        submittedAt = getTimestamp("submitted_at")?.toInstant(),
        score = getBigDecimal("score")?.toDouble(),
        maxScore = getBigDecimal("max_score")?.toDouble(),
        percent = getBigDecimal("percent")?.toDouble(),
        timeTakenMs = getLong("time_taken_ms").takeIf { !wasNull() },
    )
}
