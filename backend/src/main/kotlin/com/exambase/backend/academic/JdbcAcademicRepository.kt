package com.exambase.backend.academic

import com.exambase.backend.Database
import com.exambase.backend.storage.SignedUrlService
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/**
 * Every query that returns lessons joins the full ancestor chain and requires all four rows to be
 * published, so unpublishing a subject, category or subcategory hides everything beneath it.
 */
private const val LESSON_FROM = """
    FROM lessons l
    JOIN subcategories sc ON sc.id = l.subcategory_id
    JOIN categories c ON c.id = sc.category_id
    JOIN subjects s ON s.id = c.subject_id
"""
private const val LESSON_VISIBLE = "l.status = 'published' AND sc.status = 'published' AND c.status = 'published' AND s.status = 'published'"

private const val LESSON_SUMMARY_COLUMNS = """
    l.id, l.subcategory_id, l.title, l.title_bn, l.summary, l.summary_bn, l.estimated_minutes,
    COALESCE(p.status, 'not_started') AS progress, (b.lesson_id IS NOT NULL) AS bookmarked
"""
private const val LESSON_USER_JOINS = """
    LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.user_id = ?
    LEFT JOIN lesson_bookmarks b ON b.lesson_id = l.id AND b.user_id = ?
"""

private const val SUBJECT_SELECT = """
    SELECT s.id, s.title, s.title_bn, s.description, s.description_bn, s.icon, s.cover_url, s.is_featured,
      (SELECT count(*) FROM categories c WHERE c.subject_id = s.id AND c.status = 'published') AS category_count,
      (SELECT count(*) FROM lessons l
         JOIN subcategories sc ON sc.id = l.subcategory_id
         JOIN categories c ON c.id = sc.category_id
       WHERE c.subject_id = s.id AND l.status = 'published' AND sc.status = 'published' AND c.status = 'published') AS lesson_count
    FROM subjects s
"""

private fun ResultSet.uuid(col: String): String = getObject(col, UUID::class.java).toString()

private fun ResultSet.lessonSummary() = LessonSummary(
    id = uuid("id"),
    subcategoryId = uuid("subcategory_id"),
    title = getString("title"),
    titleBn = getString("title_bn"),
    summary = getString("summary"),
    summaryBn = getString("summary_bn"),
    estimatedMinutes = getInt("estimated_minutes").takeIf { !wasNull() },
    progress = getString("progress"),
    bookmarked = getBoolean("bookmarked"),
)

private fun ResultSet.subjectSummary() = SubjectSummary(
    id = uuid("id"),
    title = getString("title"),
    titleBn = getString("title_bn"),
    description = getString("description"),
    descriptionBn = getString("description_bn"),
    icon = getString("icon"),
    coverUrl = getString("cover_url"),
    featured = getBoolean("is_featured"),
    categoryCount = getInt("category_count"),
    lessonCount = getInt("lesson_count"),
)

private fun ResultSet.categorySummary() = CategorySummary(
    id = uuid("id"),
    subjectId = uuid("subject_id"),
    title = getString("title"),
    titleBn = getString("title_bn"),
    description = getString("description"),
    descriptionBn = getString("description_bn"),
    imageUrl = getString("image_url"),
    subcategoryCount = getInt("subcategory_count"),
)

class JdbcAcademicRepository(
    private val db: Database,
    private val signedUrls: SignedUrlService? = null,
) : AcademicRepository {

    override fun listSubjects(query: SubjectQuery, limit: Int, offset: Int): Page<SubjectSummary> = db.withConnection { c ->
        val where = StringBuilder("s.status = 'published'")
        if (query.featuredOnly) where.append(" AND s.is_featured")
        if (query.search != null) {
            where.append(" AND (s.title ILIKE ? ESCAPE '\\' OR s.title_bn ILIKE ? ESCAPE '\\' OR s.description ILIKE ? ESCAPE '\\')")
        }
        val order = when (query.sort) {
            SubjectQuery.SORT_TITLE -> "s.title ASC, s.id"
            SubjectQuery.SORT_NEWEST -> "s.created_at DESC, s.id"
            else -> "s.is_featured DESC, s.sort_order ASC, s.title ASC, s.id"
        }
        val sql = "$SUBJECT_SELECT WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        c.prepareStatement(sql).use { ps ->
            var i = 1
            if (query.search != null) {
                val like = "%" + escapeLike(query.search) + "%"
                repeat(3) { ps.setString(i++, like) }
            }
            ps.setInt(i++, limit + 1)
            ps.setInt(i, offset)
            ps.executeQuery().use { rs ->
                val rows = buildList { while (rs.next()) add(rs.subjectSummary()) }
                Paging.page(rows, limit, offset)
            }
        }
    }

    override fun getSubject(id: UUID): SubjectDetail? = db.withConnection { c ->
        val subject = findPublishedSubject(c, id) ?: return@withConnection null
        val categories = c.prepareStatement(
            """
            SELECT c.id, c.subject_id, c.title, c.title_bn, c.description, c.description_bn, c.image_url,
              (SELECT count(*) FROM subcategories sc WHERE sc.category_id = c.id AND sc.status = 'published') AS subcategory_count
            FROM categories c WHERE c.subject_id = ? AND c.status = 'published'
            ORDER BY c.sort_order, c.title, c.id
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.categorySummary()) } }
        }
        SubjectDetail(subject, categories)
    }

    private fun findPublishedSubject(c: Connection, id: UUID): SubjectSummary? =
        c.prepareStatement("$SUBJECT_SELECT WHERE s.id = ? AND s.status = 'published'").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.subjectSummary() else null }
        }

    override fun getCategory(id: UUID): CategoryDetail? = db.withConnection { c ->
        val row = c.prepareStatement(
            """
            SELECT c.id, c.subject_id, c.title, c.title_bn, c.description, c.description_bn, c.image_url,
              (SELECT count(*) FROM subcategories sc WHERE sc.category_id = c.id AND sc.status = 'published') AS subcategory_count,
              s.title AS s_title, s.title_bn AS s_title_bn
            FROM categories c JOIN subjects s ON s.id = c.subject_id
            WHERE c.id = ? AND c.status = 'published' AND s.status = 'published'
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null
                else Triple(rs.categorySummary(), rs.getString("s_title"), rs.getString("s_title_bn"))
            }
        } ?: return@withConnection null
        val (category, subjectTitle, subjectTitleBn) = row
        val subs = c.prepareStatement(
            """
            SELECT sc.id, sc.category_id, sc.title, sc.title_bn, sc.description, sc.description_bn,
              (SELECT count(*) FROM lessons l WHERE l.subcategory_id = sc.id AND l.status = 'published') AS lesson_count
            FROM subcategories sc WHERE sc.category_id = ? AND sc.status = 'published'
            ORDER BY sc.sort_order, sc.title, sc.id
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SubcategorySummary(
                                id = rs.uuid("id"),
                                categoryId = rs.uuid("category_id"),
                                title = rs.getString("title"),
                                titleBn = rs.getString("title_bn"),
                                description = rs.getString("description"),
                                descriptionBn = rs.getString("description_bn"),
                                lessonCount = rs.getInt("lesson_count"),
                            ),
                        )
                    }
                }
            }
        }
        CategoryDetail(category, listOf(Crumb("subject", category.subjectId, subjectTitle, subjectTitleBn)), subs)
    }

    override fun getSubcategory(id: UUID, userId: UUID): SubcategoryDetail? = db.withConnection { c ->
        class Head(val summary: SubcategorySummary, val crumbs: List<Crumb>)
        val head = c.prepareStatement(
            """
            SELECT sc.id, sc.category_id, sc.title, sc.title_bn, sc.description, sc.description_bn,
              (SELECT count(*) FROM lessons l WHERE l.subcategory_id = sc.id AND l.status = 'published') AS lesson_count,
              c.title AS c_title, c.title_bn AS c_title_bn, s.id AS s_id, s.title AS s_title, s.title_bn AS s_title_bn
            FROM subcategories sc
            JOIN categories c ON c.id = sc.category_id
            JOIN subjects s ON s.id = c.subject_id
            WHERE sc.id = ? AND sc.status = 'published' AND c.status = 'published' AND s.status = 'published'
            """,
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else {
                    val categoryId = rs.uuid("category_id")
                    Head(
                        SubcategorySummary(
                            id = rs.uuid("id"),
                            categoryId = categoryId,
                            title = rs.getString("title"),
                            titleBn = rs.getString("title_bn"),
                            description = rs.getString("description"),
                            descriptionBn = rs.getString("description_bn"),
                            lessonCount = rs.getInt("lesson_count"),
                        ),
                        listOf(
                            Crumb("subject", rs.uuid("s_id"), rs.getString("s_title"), rs.getString("s_title_bn")),
                            Crumb("category", categoryId, rs.getString("c_title"), rs.getString("c_title_bn")),
                        ),
                    )
                }
            }
        } ?: return@withConnection null
        val lessons = c.prepareStatement(
            """
            SELECT $LESSON_SUMMARY_COLUMNS $LESSON_FROM $LESSON_USER_JOINS
            WHERE l.subcategory_id = ? AND $LESSON_VISIBLE
            ORDER BY l.sort_order, l.title, l.id
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, userId)
            ps.setObject(3, id)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.lessonSummary()) } }
        }
        SubcategoryDetail(head.summary, head.crumbs, lessons)
    }

    override fun getLesson(id: UUID, userId: UUID): LessonDetail? = db.withConnection { c ->
        class Row(val summary: LessonSummary, val content: String, val contentBn: String?, val objectives: List<String>, val crumbs: List<Crumb>)
        val row = c.prepareStatement(
            """
            SELECT $LESSON_SUMMARY_COLUMNS, l.content, l.content_bn, l.learning_objectives,
              s.id AS s_id, s.title AS s_title, s.title_bn AS s_title_bn,
              c.id AS c_id, c.title AS c_title, c.title_bn AS c_title_bn,
              sc.title AS sc_title, sc.title_bn AS sc_title_bn
            $LESSON_FROM $LESSON_USER_JOINS
            WHERE l.id = ? AND $LESSON_VISIBLE
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, userId)
            ps.setObject(3, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else {
                    val summary = rs.lessonSummary()
                    @Suppress("UNCHECKED_CAST")
                    val objectives = (rs.getArray("learning_objectives")?.array as? Array<String>)?.toList() ?: emptyList()
                    Row(
                        summary, rs.getString("content"), rs.getString("content_bn"), objectives,
                        listOf(
                            Crumb("subject", rs.uuid("s_id"), rs.getString("s_title"), rs.getString("s_title_bn")),
                            Crumb("category", rs.uuid("c_id"), rs.getString("c_title"), rs.getString("c_title_bn")),
                            Crumb("subcategory", summary.subcategoryId, rs.getString("sc_title"), rs.getString("sc_title_bn")),
                        ),
                    )
                }
            }
        } ?: return@withConnection null

        val attachments = c.prepareStatement(
            "SELECT id, kind, title, url, size_bytes FROM lesson_attachments WHERE lesson_id = ? ORDER BY sort_order, title, id",
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val rawUrl = rs.getString("url")
                        val (url, expiresAt) = if (signedUrls != null) {
                            val s = signedUrls.sign(rawUrl)
                            s.url to s.expiresAtMillis
                        } else {
                            rawUrl to null
                        }
                        add(
                            AttachmentDto(
                                id = rs.uuid("id"),
                                kind = rs.getString("kind"),
                                title = rs.getString("title"),
                                url = url,
                                sizeBytes = rs.getLong("size_bytes").takeIf { !rs.wasNull() },
                                urlExpiresAt = expiresAt,
                            ),
                        )
                    }
                }
            }
        }

        // Neighbours inside the same subcategory, using the same ordering as the lesson list.
        val ids = c.prepareStatement(
            "SELECT l.id FROM lessons l WHERE l.subcategory_id = ? AND l.status = 'published' ORDER BY l.sort_order, l.title, l.id",
        ).use { ps ->
            ps.setObject(1, UUID.fromString(row.summary.subcategoryId))
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.uuid("id")) } }
        }
        val index = ids.indexOf(row.summary.id)

        val relatedQuestions = loadRelatedQuestions(c, id)
        val relatedQuizSets = loadRelatedQuizSets(c, id, userId)
        val relatedFlashcardSets = loadRelatedFlashcardSets(c, id, userId)
        val relatedNotes = loadRelatedNotes(c, id, userId)

        LessonDetail(
            lesson = row.summary,
            content = row.content,
            contentBn = row.contentBn,
            learningObjectives = row.objectives,
            attachments = attachments,
            breadcrumb = row.crumbs,
            previousLessonId = if (index > 0) ids[index - 1] else null,
            nextLessonId = if (index >= 0) ids.getOrNull(index + 1) else null,
            relatedQuestions = relatedQuestions,
            relatedQuizSets = relatedQuizSets,
            relatedFlashcardSets = relatedFlashcardSets,
            relatedNotes = relatedNotes,
        )
    }

    private fun loadRelatedQuestions(c: Connection, lessonId: UUID): List<RelatedQuestionSummary> =
        c.prepareStatement(
            """
            SELECT id, type, question_text, question_text_bn, difficulty, marks
            FROM questions
            WHERE lesson_id = ? AND status = 'published'
            ORDER BY sort_order, created_at, id
            LIMIT 20
            """,
        ).use { ps ->
            ps.setObject(1, lessonId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RelatedQuestionSummary(
                                id = rs.uuid("id"),
                                type = rs.getString("type"),
                                questionText = rs.getString("question_text"),
                                questionTextBn = rs.getString("question_text_bn"),
                                difficulty = rs.getString("difficulty"),
                                marks = rs.getBigDecimal("marks").toDouble(),
                            ),
                        )
                    }
                }
            }
        }

    private fun loadRelatedQuizSets(c: Connection, lessonId: UUID, userId: UUID): List<RelatedQuizSetSummary> =
        c.prepareStatement(
            """
            SELECT qs.id, qs.title, qs.time_limit_seconds, qs.owner_id,
              (SELECT count(*) FROM quiz_set_questions qsq WHERE qsq.quiz_set_id = qs.id) AS question_count
            FROM quiz_sets qs
            WHERE qs.lesson_id = ? AND qs.deleted_at IS NULL
              AND qs.owner_id = ? AND qs.status IN ('private', 'draft')
            ORDER BY qs.updated_at DESC
            LIMIT 10
            """,
        ).use { ps ->
            ps.setObject(1, lessonId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RelatedQuizSetSummary(
                                id = rs.uuid("id"),
                                title = rs.getString("title"),
                                titleBn = null,
                                questionCount = rs.getInt("question_count"),
                                timeLimitSeconds = rs.getInt("time_limit_seconds").takeIf { !rs.wasNull() },
                                isOwner = true,
                            ),
                        )
                    }
                }
            }
        }

    private fun loadRelatedFlashcardSets(c: Connection, lessonId: UUID, userId: UUID): List<RelatedFlashcardSetSummary> =
        c.prepareStatement(
            """
            SELECT fs.id, fs.title, fs.owner_id,
              (SELECT count(*) FROM flashcards fc WHERE fc.set_id = fs.id) AS card_count
            FROM flashcard_sets fs
            WHERE fs.lesson_id = ? AND fs.deleted_at IS NULL
              AND fs.owner_id = ? AND fs.status IN ('private', 'draft')
            ORDER BY fs.updated_at DESC
            LIMIT 10
            """,
        ).use { ps ->
            ps.setObject(1, lessonId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RelatedFlashcardSetSummary(
                                id = rs.uuid("id"),
                                title = rs.getString("title"),
                                titleBn = null,
                                cardCount = rs.getInt("card_count"),
                                isOwner = true,
                            ),
                        )
                    }
                }
            }
        }

    private fun loadRelatedNotes(c: Connection, lessonId: UUID, userId: UUID): List<RelatedNoteSummary> =
        c.prepareStatement(
            """
            SELECT id, title, updated_at, pinned
            FROM notes
            WHERE lesson_id = ? AND owner_id = ? AND deleted_at IS NULL
            ORDER BY pinned DESC, updated_at DESC
            LIMIT 10
            """,
        ).use { ps ->
            ps.setObject(1, lessonId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RelatedNoteSummary(
                                id = rs.uuid("id"),
                                title = rs.getString("title"),
                                updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
                                pinned = rs.getBoolean("pinned"),
                            ),
                        )
                    }
                }
            }
        }

    private fun lessonIsVisible(c: Connection, lessonId: UUID): Boolean =
        c.prepareStatement("SELECT 1 $LESSON_FROM WHERE l.id = ? AND $LESSON_VISIBLE").use { ps ->
            ps.setObject(1, lessonId)
            ps.executeQuery().use { it.next() }
        }

    override fun recordView(userId: UUID, lessonId: UUID): Boolean = db.withConnection { c ->
        if (!lessonIsVisible(c, lessonId)) return@withConnection false
        // A completed lesson stays completed when it is viewed again.
        c.prepareStatement(
            """
            INSERT INTO lesson_progress (user_id, lesson_id, status, last_viewed_at) VALUES (?, ?, 'in_progress', now())
            ON CONFLICT (user_id, lesson_id) DO UPDATE SET last_viewed_at = now()
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, lessonId)
            ps.executeUpdate()
        }
        true
    }

    override fun setBookmark(userId: UUID, lessonId: UUID, bookmarked: Boolean): Boolean = db.withConnection { c ->
        if (!lessonIsVisible(c, lessonId)) return@withConnection false
        val sql = if (bookmarked) {
            "INSERT INTO lesson_bookmarks (user_id, lesson_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        } else {
            "DELETE FROM lesson_bookmarks WHERE user_id = ? AND lesson_id = ?"
        }
        c.prepareStatement(sql).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, lessonId)
            ps.executeUpdate()
        }
        true
    }

    override fun setProgress(userId: UUID, lessonId: UUID, status: String): Boolean = db.withConnection { c ->
        if (!lessonIsVisible(c, lessonId)) return@withConnection false
        c.prepareStatement(
            """
            INSERT INTO lesson_progress (user_id, lesson_id, status, last_viewed_at, completed_at)
            VALUES (?, ?, ?, now(), CASE WHEN ? = 'completed' THEN now() END)
            ON CONFLICT (user_id, lesson_id) DO UPDATE SET
              status = EXCLUDED.status,
              last_viewed_at = now(),
              completed_at = CASE WHEN EXCLUDED.status = 'completed'
                                  THEN COALESCE(lesson_progress.completed_at, now()) ELSE NULL END
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, lessonId)
            ps.setString(3, status)
            ps.setString(4, status)
            ps.executeUpdate()
        }
        true
    }

    override fun listBookmarks(userId: UUID, limit: Int, offset: Int): Page<LessonSummary> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT $LESSON_SUMMARY_COLUMNS $LESSON_FROM $LESSON_USER_JOINS
            JOIN lesson_bookmarks bm ON bm.lesson_id = l.id AND bm.user_id = ?
            WHERE $LESSON_VISIBLE
            ORDER BY bm.created_at DESC, l.id
            LIMIT ? OFFSET ?
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, userId)
            ps.setObject(3, userId)
            ps.setInt(4, limit + 1)
            ps.setInt(5, offset)
            ps.executeQuery().use { rs -> Paging.page(buildList { while (rs.next()) add(rs.lessonSummary()) }, limit, offset) }
        }
    }

    override fun listRecent(userId: UUID, limit: Int): List<LessonSummary> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT $LESSON_SUMMARY_COLUMNS $LESSON_FROM $LESSON_USER_JOINS
            WHERE $LESSON_VISIBLE AND p.user_id IS NOT NULL
            ORDER BY p.last_viewed_at DESC, l.id
            LIMIT ?
            """,
        ).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, userId)
            ps.setInt(3, limit)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.lessonSummary()) } }
        }
    }
}
