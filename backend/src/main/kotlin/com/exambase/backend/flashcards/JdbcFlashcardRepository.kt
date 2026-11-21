package com.exambase.backend.flashcards

import com.exambase.backend.Database
import com.exambase.backend.academic.Page
import com.exambase.backend.academic.Paging
import com.exambase.backend.academic.escapeLike
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcFlashcardRepository(private val db: Database) : FlashcardRepository {

    override fun createSet(ownerId: UUID, title: String, description: String?, lessonId: UUID?, status: String): FlashcardSetDto =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO flashcard_sets (owner_id, title, description, lesson_id, status)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, title, description, lesson_id, status, created_at, updated_at
                """,
            ).use { ps ->
                ps.setObject(1, ownerId)
                ps.setString(2, title)
                ps.setString(3, description)
                ps.setObject(4, lessonId)
                ps.setString(5, status)
                ps.executeQuery().use { rs ->
                    rs.next()
                    setDto(rs, 0)
                }
            }
        }

    override fun listSets(ownerId: UUID, q: String?, status: String?, sort: String, limit: Int, offset: Int): Page<FlashcardSetListItem> =
        db.withConnection { c ->
            val where = StringBuilder("s.owner_id = ? AND s.deleted_at IS NULL")
            val params = mutableListOf<Any>(ownerId)
            if (status != null) {
                where.append(" AND s.status = ?")
                params += status
            } else {
                where.append(" AND s.status <> 'archived'")
            }
            if (!q.isNullOrBlank()) {
                where.append(" AND (s.title ILIKE ? OR COALESCE(s.description,'') ILIKE ?)")
                val like = "%" + escapeLike(q.trim()) + "%"
                params += like
                params += like
            }
            val order = when (sort) {
                "title" -> "s.title ASC"
                "cards" -> "card_count DESC, s.updated_at DESC"
                else -> "s.updated_at DESC"
            }
            val total = c.prepareStatement("SELECT count(*) FROM flashcard_sets s WHERE $where").use { ps ->
                params.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
            val sql = """
                SELECT s.id, s.title, s.description, s.lesson_id, s.status, s.created_at, s.updated_at,
                       (SELECT count(*) FROM flashcards f WHERE f.set_id = s.id) AS card_count,
                       (SELECT count(*) FROM flashcard_srs r
                          JOIN flashcards f ON f.id = r.card_id
                          WHERE f.set_id = s.id AND r.user_id = s.owner_id AND r.due_at <= now()) AS due_count,
                       (SELECT max(r.last_reviewed_at) FROM flashcard_srs r
                          JOIN flashcards f ON f.id = r.card_id
                          WHERE f.set_id = s.id AND r.user_id = s.owner_id) AS last_reviewed_at
                FROM flashcard_sets s
                WHERE $where
                ORDER BY $order
                LIMIT ? OFFSET ?
            """.trimIndent()
            val items = c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.setInt(params.size + 1, limit)
                ps.setInt(params.size + 2, offset)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                FlashcardSetListItem(
                                    set = setDto(rs, rs.getInt("card_count")),
                                    dueCount = rs.getInt("due_count"),
                                    lastReviewedAt = rs.getTimestamp("last_reviewed_at")?.toInstant()?.toString(),
                                ),
                            )
                        }
                    }
                }
            }
            Page(items, Paging.encode(offset, limit, total))
        }

    override fun getSet(ownerId: UUID, setId: UUID): FlashcardSetDetail? = db.withConnection { c ->
        val set = c.prepareStatement(
            """
            SELECT s.id, s.title, s.description, s.lesson_id, s.status, s.created_at, s.updated_at,
                   (SELECT count(*) FROM flashcards f WHERE f.set_id = s.id) AS card_count
            FROM flashcard_sets s
            WHERE s.id = ? AND s.owner_id = ? AND s.deleted_at IS NULL
            """,
        ).use { ps ->
            ps.setObject(1, setId)
            ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (!rs.next()) return@withConnection null else setDto(rs, rs.getInt("card_count")) }
        }
        val cards = c.prepareStatement(
            """
            SELECT id, set_id, position, front_text, front_text_bn, back_text, back_text_bn,
                   front_image_url, back_image_url, difficulty, tags
            FROM flashcards WHERE set_id = ? ORDER BY position
            """,
        ).use { ps ->
            ps.setObject(1, setId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(cardDto(rs)) } }
        }
        FlashcardSetDetail(set, cards)
    }

    override fun updateSet(
        ownerId: UUID, setId: UUID, title: String?, description: String?,
        lessonId: UUID?, clearLesson: Boolean, status: String?,
    ): FlashcardSetDto? = db.withConnection { c ->
        val cur = c.prepareStatement(
            "SELECT title, description, lesson_id, status FROM flashcard_sets WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
        ).use { ps ->
            ps.setObject(1, setId); ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (!rs.next()) return@withConnection null; rs }
        }
        val newTitle = title ?: cur.getString("title")
        val newDesc = description ?: cur.getString("description")
        val newLesson = when {
            clearLesson -> null
            lessonId != null -> lessonId
            else -> cur.getObject("lesson_id", UUID::class.java)
        }
        val newStatus = status ?: cur.getString("status")
        c.prepareStatement(
            """
            UPDATE flashcard_sets SET title = ?, description = ?, lesson_id = ?, status = ?, updated_at = now()
            WHERE id = ? AND owner_id = ?
            RETURNING id, title, description, lesson_id, status, created_at, updated_at
            """,
        ).use { ps ->
            ps.setString(1, newTitle)
            ps.setString(2, newDesc)
            ps.setObject(3, newLesson)
            ps.setString(4, newStatus)
            ps.setObject(5, setId)
            ps.setObject(6, ownerId)
            ps.executeQuery().use { rs ->
                rs.next()
                val count = c.prepareStatement("SELECT count(*) FROM flashcards WHERE set_id = ?").use { cps ->
                    cps.setObject(1, setId)
                    cps.executeQuery().use { r -> r.next(); r.getInt(1) }
                }
                setDto(rs, count)
            }
        }
    }

    override fun deleteSet(ownerId: UUID, setId: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement(
            "UPDATE flashcard_sets SET deleted_at = now(), updated_at = now() WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
        ).use { ps ->
            ps.setObject(1, setId)
            ps.setObject(2, ownerId)
            ps.executeUpdate() > 0
        }
    }

    override fun duplicateSet(ownerId: UUID, setId: UUID): FlashcardSetDto? = db.withConnection { c ->
        val detail = getSet(ownerId, setId) ?: return@withConnection null
        val copy = createSet(ownerId, detail.set.title + " (copy)", detail.set.description, detail.set.lessonId?.let(UUID::fromString), FlashcardSetStatus.DRAFT)
        detail.cards.forEach { card ->
            addCard(
                ownerId, UUID.fromString(copy.id),
                CreateFlashcardRequest(
                    frontText = card.frontText, frontTextBn = card.frontTextBn,
                    backText = card.backText, backTextBn = card.backTextBn,
                    frontImageUrl = card.frontImageUrl, backImageUrl = card.backImageUrl,
                    difficulty = card.difficulty, tags = card.tags,
                ),
            )
        }
        getSet(ownerId, UUID.fromString(copy.id))!!.set
    }

    override fun addCard(ownerId: UUID, setId: UUID, req: CreateFlashcardRequest): FlashcardDto? = db.withConnection { c ->
        if (!ownsSet(c, ownerId, setId)) return@withConnection null
        val pos = c.prepareStatement("SELECT COALESCE(max(position), 0) + 1 FROM flashcards WHERE set_id = ?").use { ps ->
            ps.setObject(1, setId)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        c.prepareStatement(
            """
            INSERT INTO flashcards (set_id, position, front_text, front_text_bn, back_text, back_text_bn,
                                    front_image_url, back_image_url, difficulty, tags)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, set_id, position, front_text, front_text_bn, back_text, back_text_bn,
                      front_image_url, back_image_url, difficulty, tags
            """,
        ).use { ps ->
            ps.setObject(1, setId)
            ps.setInt(2, pos)
            ps.setString(3, req.frontText.trim())
            ps.setString(4, req.frontTextBn)
            ps.setString(5, req.backText.trim())
            ps.setString(6, req.backTextBn)
            ps.setString(7, req.frontImageUrl)
            ps.setString(8, req.backImageUrl)
            ps.setString(9, req.difficulty ?: "medium")
            ps.setArray(10, c.createArrayOf("text", req.tags.toTypedArray()))
            ps.executeQuery().use { rs -> rs.next(); cardDto(rs) }
        }.also {
            c.prepareStatement("UPDATE flashcard_sets SET updated_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, setId); ps.executeUpdate()
            }
        }
    }

    override fun updateCard(ownerId: UUID, cardId: UUID, req: UpdateFlashcardRequest): FlashcardDto? = db.withConnection { c ->
        val setId = cardSetId(c, ownerId, cardId) ?: return@withConnection null
        c.prepareStatement(
            """
            UPDATE flashcards SET
              front_text = COALESCE(?, front_text),
              front_text_bn = COALESCE(?, front_text_bn),
              back_text = COALESCE(?, back_text),
              back_text_bn = COALESCE(?, back_text_bn),
              front_image_url = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ? ELSE front_image_url END,
              back_image_url = CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ? ELSE back_image_url END,
              difficulty = COALESCE(?, difficulty),
              tags = COALESCE(?, tags),
              updated_at = now()
            WHERE id = ?
            RETURNING id, set_id, position, front_text, front_text_bn, back_text, back_text_bn,
                      front_image_url, back_image_url, difficulty, tags
            """,
        ).use { ps ->
            var i = 1
            ps.setString(i++, req.frontText)
            ps.setString(i++, req.frontTextBn)
            ps.setString(i++, req.backText)
            ps.setString(i++, req.backTextBn)
            ps.setBoolean(i++, req.clearFrontImage)
            ps.setString(i++, req.frontImageUrl)
            ps.setString(i++, req.frontImageUrl)
            ps.setBoolean(i++, req.clearBackImage)
            ps.setString(i++, req.backImageUrl)
            ps.setString(i++, req.backImageUrl)
            ps.setString(i++, req.difficulty)
            if (req.tags != null) ps.setArray(i++, c.createArrayOf("text", req.tags.toTypedArray()))
            else { ps.setObject(i++, null); }
            ps.setObject(i, cardId)
            ps.executeQuery().use { rs -> if (!rs.next()) null else cardDto(rs) }
        }.also {
            c.prepareStatement("UPDATE flashcard_sets SET updated_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, setId); ps.executeUpdate()
            }
        }
    }

    override fun deleteCard(ownerId: UUID, cardId: UUID): Boolean = db.withConnection { c ->
        val setId = cardSetId(c, ownerId, cardId) ?: return@withConnection false
        val ok = c.prepareStatement("DELETE FROM flashcards WHERE id = ?").use { ps ->
            ps.setObject(1, cardId); ps.executeUpdate() > 0
        }
        if (ok) {
            // compact positions
            c.prepareStatement(
                """
                WITH ordered AS (
                  SELECT id, row_number() OVER (ORDER BY position) AS new_pos FROM flashcards WHERE set_id = ?
                )
                UPDATE flashcards f SET position = ordered.new_pos FROM ordered WHERE f.id = ordered.id
                """,
            ).use { ps -> ps.setObject(1, setId); ps.executeUpdate() }
            c.prepareStatement("UPDATE flashcard_sets SET updated_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, setId); ps.executeUpdate()
            }
        }
        ok
    }

    override fun reorderCards(ownerId: UUID, setId: UUID, cardIds: List<UUID>): Boolean = db.withConnection { c ->
        if (!ownsSet(c, ownerId, setId)) return@withConnection false
        cardIds.forEachIndexed { index, id ->
            c.prepareStatement("UPDATE flashcards SET position = ? WHERE id = ? AND set_id = ?").use { ps ->
                ps.setInt(1, index + 1)
                ps.setObject(2, id)
                ps.setObject(3, setId)
                ps.executeUpdate()
            }
        }
        true
    }

    override fun dueCards(ownerId: UUID, setId: UUID, limit: Int, now: Instant): List<StudyCardDto> = db.withConnection { c ->
        if (!ownsSet(c, ownerId, setId)) return@withConnection emptyList()
        // Cards with no SRS row are due immediately (new cards).
        c.prepareStatement(
            """
            SELECT f.id, f.set_id, f.position, f.front_text, f.front_text_bn, f.back_text, f.back_text_bn,
                   f.front_image_url, f.back_image_url, f.difficulty, f.tags,
                   r.ease_factor, r.interval_days, r.repetitions, r.due_at, r.last_reviewed_at
            FROM flashcards f
            LEFT JOIN flashcard_srs r ON r.card_id = f.id AND r.user_id = ?
            WHERE f.set_id = ? AND (r.due_at IS NULL OR r.due_at <= ?)
            ORDER BY COALESCE(r.due_at, to_timestamp(0)) ASC, f.position ASC
            LIMIT ?
            """,
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.setObject(2, setId)
            ps.setTimestamp(3, Timestamp.from(now))
            ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val card = cardDto(rs)
                        val ease = rs.getObject("ease_factor")
                        val srs = if (ease == null) null else SrsStateDto(
                            easeFactor = rs.getDouble("ease_factor"),
                            intervalDays = rs.getInt("interval_days"),
                            repetitions = rs.getInt("repetitions"),
                            dueAt = rs.getTimestamp("due_at").toInstant().toString(),
                            lastReviewedAt = rs.getTimestamp("last_reviewed_at")?.toInstant()?.toString(),
                        )
                        add(StudyCardDto(card, srs))
                    }
                }
            }
        }
    }

    override fun review(ownerId: UUID, cardId: UUID, rating: String, now: Instant): ReviewResultDto? = db.withConnection { c ->
        val setId = cardSetId(c, ownerId, cardId) ?: return@withConnection null
        val current = c.prepareStatement(
            "SELECT ease_factor, interval_days, repetitions, due_at, last_reviewed_at FROM flashcard_srs WHERE user_id = ? AND card_id = ?",
        ).use { ps ->
            ps.setObject(1, ownerId); ps.setObject(2, cardId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) SpacedRepetition.initial(now)
                else SpacedRepetition.State(
                    easeFactor = rs.getDouble("ease_factor"),
                    intervalDays = rs.getInt("interval_days"),
                    repetitions = rs.getInt("repetitions"),
                    dueAt = rs.getTimestamp("due_at").toInstant(),
                    lastReviewedAt = rs.getTimestamp("last_reviewed_at")?.toInstant(),
                )
            }
        }
        val next = SpacedRepetition.apply(current, rating, now)
        c.prepareStatement(
            """
            INSERT INTO flashcard_srs (user_id, card_id, ease_factor, interval_days, repetitions, due_at, last_reviewed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, card_id) DO UPDATE SET
              ease_factor = EXCLUDED.ease_factor,
              interval_days = EXCLUDED.interval_days,
              repetitions = EXCLUDED.repetitions,
              due_at = EXCLUDED.due_at,
              last_reviewed_at = EXCLUDED.last_reviewed_at
            """,
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.setObject(2, cardId)
            ps.setDouble(3, next.easeFactor)
            ps.setInt(4, next.intervalDays)
            ps.setInt(5, next.repetitions)
            ps.setTimestamp(6, Timestamp.from(next.dueAt))
            ps.setTimestamp(7, Timestamp.from(next.lastReviewedAt!!))
            ps.executeUpdate()
        }
        c.prepareStatement(
            """
            INSERT INTO flashcard_reviews (user_id, card_id, set_id, rating, ease_before, ease_after, interval_before, interval_after, reviewed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        ).use { ps ->
            ps.setObject(1, ownerId)
            ps.setObject(2, cardId)
            ps.setObject(3, setId)
            ps.setString(4, rating)
            ps.setDouble(5, current.easeFactor)
            ps.setDouble(6, next.easeFactor)
            ps.setInt(7, current.intervalDays)
            ps.setInt(8, next.intervalDays)
            ps.setTimestamp(9, Timestamp.from(now))
            ps.executeUpdate()
        }
        ReviewResultDto(
            cardId = cardId.toString(),
            rating = rating,
            srs = SrsStateDto(
                easeFactor = next.easeFactor,
                intervalDays = next.intervalDays,
                repetitions = next.repetitions,
                dueAt = next.dueAt.toString(),
                lastReviewedAt = next.lastReviewedAt.toString(),
            ),
        )
    }

    override fun analytics(ownerId: UUID, setId: UUID, now: Instant): SetAnalyticsDto? = db.withConnection { c ->
        if (!ownsSet(c, ownerId, setId)) return@withConnection null
        val cardCount = c.prepareStatement("SELECT count(*) FROM flashcards WHERE set_id = ?").use { ps ->
            ps.setObject(1, setId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val dueCount = c.prepareStatement(
            """
            SELECT count(*) FROM flashcards f
            LEFT JOIN flashcard_srs r ON r.card_id = f.id AND r.user_id = ?
            WHERE f.set_id = ? AND (r.due_at IS NULL OR r.due_at <= ?)
            """,
        ).use { ps ->
            ps.setObject(1, ownerId); ps.setObject(2, setId); ps.setTimestamp(3, Timestamp.from(now))
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val reviewed = c.prepareStatement(
            "SELECT count(*), avg(r.ease_factor) FROM flashcard_srs r JOIN flashcards f ON f.id = r.card_id WHERE f.set_id = ? AND r.user_id = ? AND r.last_reviewed_at IS NOT NULL",
        ).use { ps ->
            ps.setObject(1, setId); ps.setObject(2, ownerId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1) to rs.getObject(2)?.let { rs.getDouble(2) }
            }
        }
        SetAnalyticsDto(setId.toString(), cardCount, dueCount, reviewed.first, reviewed.second)
    }

    private fun ownsSet(c: Connection, ownerId: UUID, setId: UUID): Boolean =
        c.prepareStatement("SELECT 1 FROM flashcard_sets WHERE id = ? AND owner_id = ? AND deleted_at IS NULL").use { ps ->
            ps.setObject(1, setId); ps.setObject(2, ownerId)
            ps.executeQuery().use { it.next() }
        }

    private fun cardSetId(c: Connection, ownerId: UUID, cardId: UUID): UUID? =
        c.prepareStatement(
            """
            SELECT f.set_id FROM flashcards f
            JOIN flashcard_sets s ON s.id = f.set_id
            WHERE f.id = ? AND s.owner_id = ? AND s.deleted_at IS NULL
            """,
        ).use { ps ->
            ps.setObject(1, cardId); ps.setObject(2, ownerId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else null }
        }

    private fun setDto(rs: ResultSet, cardCount: Int) = FlashcardSetDto(
        id = rs.getObject("id", UUID::class.java).toString(),
        title = rs.getString("title"),
        description = rs.getString("description"),
        lessonId = rs.getObject("lesson_id", UUID::class.java)?.toString(),
        status = rs.getString("status"),
        cardCount = cardCount,
        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
    )

    private fun cardDto(rs: ResultSet): FlashcardDto {
        @Suppress("UNCHECKED_CAST")
        val tags = (rs.getArray("tags")?.array as? Array<String>)?.toList() ?: emptyList()
        return FlashcardDto(
            id = rs.getObject("id", UUID::class.java).toString(),
            setId = rs.getObject("set_id", UUID::class.java).toString(),
            position = rs.getInt("position"),
            frontText = rs.getString("front_text"),
            frontTextBn = rs.getString("front_text_bn"),
            backText = rs.getString("back_text"),
            backTextBn = rs.getString("back_text_bn"),
            frontImageUrl = rs.getString("front_image_url"),
            backImageUrl = rs.getString("back_image_url"),
            difficulty = rs.getString("difficulty"),
            tags = tags,
        )
    }
}
