package com.exambase.backend.search

import com.exambase.backend.Database
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcSearchRepository(private val db: Database) : SearchRepository {

    override fun searchSubjects(q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, title, title_bn, left(description, 120) AS snippet
            FROM subjects
            WHERE status = 'published'
              AND (title ILIKE ? OR title_bn ILIKE ? OR description ILIKE ?)
            ORDER BY title
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setString(1, like); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchHit(
                                SearchType.SUBJECT,
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                                rs.getString("snippet"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun searchLessons(q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT l.id, l.title, l.title_bn, left(l.summary, 120) AS snippet
            FROM lessons l
            WHERE l.status = 'published'
              AND (l.title ILIKE ? OR l.title_bn ILIKE ? OR l.summary ILIKE ?)
            ORDER BY l.title
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setString(1, like); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchHit(
                                SearchType.LESSON,
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                                rs.getString("snippet"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun searchQuestions(q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, left(question_text, 100) AS title, question_text_bn
            FROM questions
            WHERE status = 'published'
              AND (question_text ILIKE ? OR question_text_bn ILIKE ?)
            ORDER BY created_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setString(1, like); ps.setString(2, like); ps.setInt(3, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val title = rs.getString("title") ?: ""
                        add(
                            SearchHit(
                                SearchType.QUESTION,
                                rs.getObject("id", UUID::class.java).toString(),
                                title,
                                rs.getString("question_text_bn"),
                                title,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun searchQuizzes(userId: UUID, q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        // Quiz sets are owner-private only (no public catalog yet).
        c.prepareStatement(
            """
            SELECT id, title, title_bn
            FROM quiz_sets
            WHERE owner_id = ? AND deleted_at IS NULL AND status <> 'archived'
              AND (title ILIKE ? OR title_bn ILIKE ?)
            ORDER BY updated_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setObject(1, userId); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchHit(
                                SearchType.QUIZ,
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                                privateOwned = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun searchFlashcards(userId: UUID, q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, title, title_bn
            FROM flashcard_sets
            WHERE owner_id = ? AND deleted_at IS NULL AND status <> 'archived'
              AND (title ILIKE ? OR title_bn ILIKE ?)
            ORDER BY updated_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setObject(1, userId); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchHit(
                                SearchType.FLASHCARD,
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                rs.getString("title_bn"),
                                privateOwned = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun searchNotes(userId: UUID, q: String, limit: Int): List<SearchHit> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, title, left(body, 120) AS snippet
            FROM notes
            WHERE owner_id = ? AND deleted_at IS NULL
              AND (title ILIKE ? OR body ILIKE ?)
            ORDER BY updated_at DESC
            LIMIT ?
            """,
        ).use { ps ->
            val like = "%${escape(q)}%"
            ps.setObject(1, userId); ps.setString(2, like); ps.setString(3, like); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchHit(
                                SearchType.NOTE,
                                rs.getObject("id", UUID::class.java).toString(),
                                rs.getString("title"),
                                snippet = rs.getString("snippet"),
                                privateOwned = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun saveRecent(userId: UUID, query: String) = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO search_recent (user_id, query_text, searched_at)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, query_text) DO UPDATE SET searched_at = EXCLUDED.searched_at
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, query.take(120))
            ps.setTimestamp(3, Timestamp.from(Instant.now()))
            ps.executeUpdate()
            Unit
        }
    }

    override fun listRecent(userId: UUID, limit: Int): List<String> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT query_text FROM search_recent WHERE user_id = ? ORDER BY searched_at DESC LIMIT ?",
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

    private fun escape(q: String) = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
