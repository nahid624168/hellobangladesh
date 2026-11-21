package com.exambase.backend.admin

import com.exambase.backend.Database
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

class JdbcAdminRepository(private val db: Database) : AdminRepository {

    override fun searchUsers(query: String?, status: String?, role: String?, limit: Int, cursor: String?): Pair<List<UserRow>, String?> =
        db.withConnection { c ->
            val params = mutableListOf<Any?>()
            val where = mutableListOf<String>("u.status <> 'deleted'")
            if (!query.isNullOrBlank()) {
                where += "(u.email ILIKE ? OR u.display_name ILIKE ?)"
                val q = "%${query.trim()}%"
                params += q
                params += q
            }
            if (!status.isNullOrBlank()) {
                where += "u.status = ?"
                params += status
            }
            if (!role.isNullOrBlank()) {
                where += "EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.code = ?)"
                params += role
            }
            var cursorCreated: Instant? = null
            var cursorId: UUID? = null
            if (!cursor.isNullOrBlank()) {
                decodeCursor(cursor)?.let { (t, id) ->
                    cursorCreated = t
                    cursorId = id
                    where += "(u.created_at, u.id) < (?, ?)"
                    params += Timestamp.from(t)
                    params += id
                }
            }
            val sql = """
                SELECT u.id, u.email, u.display_name, u.status, u.email_verified_at, u.created_at, u.updated_at
                FROM users u
                WHERE ${where.joinToString(" AND ")}
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT ?
            """.trimIndent()
            params += (limit + 1)
            val rows = c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<UserRow>()
                    while (rs.next()) list += readUser(rs, c)
                    list
                }
            }
            page(rows, limit) { it.createdAt to it.id }
        }

    override fun getUser(id: UUID): UserRow? = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, email, display_name, status, email_verified_at, created_at, updated_at
            FROM users WHERE id = ? AND status <> 'deleted'
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) readUser(rs, c) else null }
        }
    }

    override fun updateUserStatus(id: UUID, status: String, updatedAt: Instant) = db.withConnection { c ->
        c.prepareStatement("UPDATE users SET status = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, status)
            ps.setTimestamp(2, Timestamp.from(updatedAt))
            ps.setObject(3, id)
            ps.executeUpdate()
        }
        if (status != "active") {
            // A suspended user must be signed out everywhere immediately, not when the access token expires.
            c.prepareStatement("UPDATE sessions SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL").use { ps ->
                ps.setObject(1, id)
                ps.executeUpdate()
            }
        }
        Unit
    }

    override fun setUserRoles(userId: UUID, roleCodes: List<String>) = db.withConnection { c ->
        c.prepareStatement("DELETE FROM user_roles WHERE user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeUpdate()
        }
        if (roleCodes.isEmpty()) return@withConnection
        val roleIds = c.prepareStatement("SELECT id, code FROM roles WHERE code = ANY(?)").use { ps ->
            ps.setArray(1, c.createArrayOf("text", roleCodes.toTypedArray()))
            ps.executeQuery().use { rs ->
                val map = mutableMapOf<String, Short>()
                while (rs.next()) map[rs.getString("code")] = rs.getShort("id")
                map
            }
        }
        c.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)").use { ps ->
            for (code in roleCodes) {
                val rid = roleIds[code] ?: continue
                ps.setObject(1, userId)
                ps.setShort(2, rid)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        Unit
    }

    override fun listRolesFor(userId: UUID): List<String> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = ? ORDER BY r.id
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<String>()
                while (rs.next()) list += rs.getString(1)
                list
            }
        }
    }

    override fun insertAudit(
        actorId: UUID?,
        action: String,
        resourceType: String,
        resourceId: String?,
        detailsJson: String,
        ip: String?,
        createdAt: Instant,
    ) = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO audit_logs (actor_id, action, resource_type, resource_id, details, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            if (actorId != null) ps.setObject(1, actorId) else ps.setObject(1, null)
            ps.setString(2, action)
            ps.setString(3, resourceType)
            ps.setString(4, resourceId)
            ps.setString(5, detailsJson)
            ps.setString(6, ip)
            ps.setTimestamp(7, Timestamp.from(createdAt))
            ps.executeUpdate()
        }
        Unit
    }

    override fun listAuditLogs(
        actorId: UUID?,
        resourceType: String?,
        action: String?,
        limit: Int,
        cursor: String?,
    ): Pair<List<AuditLogRow>, String?> = db.withConnection { c ->
        val where = mutableListOf("1=1")
        val params = mutableListOf<Any?>()
        if (actorId != null) {
            where += "actor_id = ?"
            params += actorId
        }
        if (!resourceType.isNullOrBlank()) {
            where += "resource_type = ?"
            params += resourceType
        }
        if (!action.isNullOrBlank()) {
            where += "action = ?"
            params += action
        }
        if (!cursor.isNullOrBlank()) {
            decodeCursor(cursor)?.let { (t, id) ->
                where += "(created_at, id) < (?, ?)"
                params += Timestamp.from(t)
                params += id
            }
        }
        val sql = """
            SELECT id, actor_id, action, resource_type, resource_id, details::text, created_at
            FROM audit_logs
            WHERE ${where.joinToString(" AND ")}
            ORDER BY created_at DESC, id DESC
            LIMIT ?
        """.trimIndent()
        params += (limit + 1)
        val rows = c.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AuditLogRow>()
                while (rs.next()) {
                    list += AuditLogRow(
                        id = rs.getObject("id") as UUID,
                        actorId = rs.getObject("actor_id") as? UUID,
                        action = rs.getString("action"),
                        resourceType = rs.getString("resource_type"),
                        resourceId = rs.getString("resource_id"),
                        detailsJson = rs.getString("details") ?: "{}",
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                    )
                }
                list
            }
        }
        page(rows, limit) { it.createdAt to it.id }
    }

    override fun listFeatureFlags(): List<FeatureFlagRow> = db.withConnection { c ->
        c.prepareStatement("SELECT key, enabled, description, updated_at FROM feature_flags ORDER BY key").use { ps ->
            ps.executeQuery().use { rs ->
                val list = mutableListOf<FeatureFlagRow>()
                while (rs.next()) list += readFlag(rs)
                list
            }
        }
    }

    override fun getFeatureFlag(key: String): FeatureFlagRow? = db.withConnection { c ->
        c.prepareStatement("SELECT key, enabled, description, updated_at FROM feature_flags WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) readFlag(rs) else null }
        }
    }

    override fun upsertFeatureFlag(key: String, enabled: Boolean, description: String, updatedBy: UUID?, updatedAt: Instant) =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO feature_flags (key, enabled, description, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET enabled = EXCLUDED.enabled, description = EXCLUDED.description,
                    updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, key)
                ps.setBoolean(2, enabled)
                ps.setString(3, description)
                if (updatedBy != null) ps.setObject(4, updatedBy) else ps.setObject(4, null)
                ps.setTimestamp(5, Timestamp.from(updatedAt))
                ps.executeUpdate()
            }
            Unit
        }

    override fun listAppConfig(): List<AppConfigRow> = db.withConnection { c ->
        c.prepareStatement("SELECT key, value::text, description, updated_at FROM app_config ORDER BY key").use { ps ->
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AppConfigRow>()
                while (rs.next()) list += readConfig(rs)
                list
            }
        }
    }

    override fun getAppConfig(key: String): AppConfigRow? = db.withConnection { c ->
        c.prepareStatement("SELECT key, value::text, description, updated_at FROM app_config WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) readConfig(rs) else null }
        }
    }

    override fun upsertAppConfig(key: String, valueJson: String, description: String, updatedBy: UUID?, updatedAt: Instant) =
        db.withConnection { c ->
            c.prepareStatement(
                """
                INSERT INTO app_config (key, value, description, updated_by, updated_at)
                VALUES (?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, description = EXCLUDED.description,
                    updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, key)
                ps.setString(2, valueJson)
                ps.setString(3, description)
                if (updatedBy != null) ps.setObject(4, updatedBy) else ps.setObject(4, null)
                ps.setTimestamp(5, Timestamp.from(updatedAt))
                ps.executeUpdate()
            }
            Unit
        }

    override fun createAbuseReport(row: AbuseReportRow) = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO abuse_reports (id, reporter_id, target_type, target_id, reason, details, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, row.id)
            if (row.reporterId != null) ps.setObject(2, row.reporterId) else ps.setObject(2, null)
            ps.setString(3, row.targetType)
            ps.setString(4, row.targetId)
            ps.setString(5, row.reason)
            ps.setString(6, row.details)
            ps.setString(7, row.status)
            ps.setTimestamp(8, Timestamp.from(row.createdAt))
            ps.executeUpdate()
        }
        Unit
    }

    override fun listAbuseReports(status: String?, limit: Int, cursor: String?): Pair<List<AbuseReportRow>, String?> =
        db.withConnection { c ->
            val where = mutableListOf("1=1")
            val params = mutableListOf<Any?>()
            if (!status.isNullOrBlank()) {
                where += "status = ?"
                params += status
            }
            if (!cursor.isNullOrBlank()) {
                decodeCursor(cursor)?.let { (t, id) ->
                    where += "(created_at, id) < (?, ?)"
                    params += Timestamp.from(t)
                    params += id
                }
            }
            val sql = """
                SELECT id, reporter_id, target_type, target_id, reason, details, status, resolver_id, resolution_note, created_at, resolved_at
                FROM abuse_reports
                WHERE ${where.joinToString(" AND ")}
                ORDER BY created_at DESC, id DESC
                LIMIT ?
            """.trimIndent()
            params += (limit + 1)
            val rows = c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<AbuseReportRow>()
                    while (rs.next()) list += readReport(rs)
                    list
                }
            }
            page(rows, limit) { it.createdAt to it.id }
        }

    override fun getAbuseReport(id: UUID): AbuseReportRow? = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, reporter_id, target_type, target_id, reason, details, status, resolver_id, resolution_note, created_at, resolved_at
            FROM abuse_reports WHERE id = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) readReport(rs) else null }
        }
    }

    override fun resolveAbuseReport(id: UUID, status: String, resolverId: UUID, note: String?, resolvedAt: Instant?) =
        db.withConnection { c ->
            c.prepareStatement(
                """
                UPDATE abuse_reports SET status = ?, resolver_id = ?, resolution_note = ?, resolved_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, status)
                ps.setObject(2, resolverId)
                ps.setString(3, note)
                if (resolvedAt != null) ps.setTimestamp(4, Timestamp.from(resolvedAt)) else ps.setTimestamp(4, null)
                ps.setObject(5, id)
                ps.executeUpdate()
            }
            Unit
        }


    // ===================== 30b =====================

    override fun listSubjectsAdmin(status: String?, limit: Int, cursor: String?): Pair<List<ContentNodeRow>, String?> =
        db.withConnection { c ->
            val where = mutableListOf("1=1")
            val params = mutableListOf<Any?>()
            if (!status.isNullOrBlank()) {
                where += "status = ?"
                params += status
            }
            val sql = """
                SELECT id, title, title_bn, status, sort_order, updated_at
                FROM subjects
                WHERE ${where.joinToString(" AND ")}
                ORDER BY sort_order, title
                LIMIT ?
            """.trimIndent()
            params += limit
            val rows = c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<ContentNodeRow>()
                    while (rs.next()) {
                        list += ContentNodeRow(
                            id = rs.getObject("id") as UUID,
                            kind = "subject",
                            title = rs.getString("title"),
                            titleBn = rs.getString("title_bn"),
                            status = rs.getString("status"),
                            parentId = null,
                            sortOrder = rs.getInt("sort_order"),
                            updatedAt = rs.getTimestamp("updated_at").toInstant(),
                        )
                    }
                    list
                }
            }
            rows to null
        }

    override fun listChildrenAdmin(kind: String, parentId: UUID, status: String?, limit: Int): List<ContentNodeRow> =
        db.withConnection { c ->
            val (table, parentCol, childKind) = when (kind) {
                "subject" -> Triple("categories", "subject_id", "category")
                "category" -> Triple("subcategories", "category_id", "subcategory")
                "subcategory" -> Triple("lessons", "subcategory_id", "lesson")
                else -> return@withConnection emptyList()
            }
            val where = mutableListOf("$parentCol = ?")
            val params = mutableListOf<Any?>(parentId)
            if (!status.isNullOrBlank()) {
                where += "status = ?"
                params += status
            }
            val titleBn = if (childKind == "lesson") "title_bn" else "title_bn"
            val sql = """
                SELECT id, title, $titleBn AS title_bn, status, sort_order, updated_at
                FROM $table
                WHERE ${where.joinToString(" AND ")}
                ORDER BY sort_order, title
                LIMIT ?
            """.trimIndent()
            params += limit
            c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<ContentNodeRow>()
                    while (rs.next()) {
                        list += ContentNodeRow(
                            id = rs.getObject("id") as UUID,
                            kind = childKind,
                            title = rs.getString("title"),
                            titleBn = rs.getString("title_bn"),
                            status = rs.getString("status"),
                            parentId = parentId,
                            sortOrder = rs.getInt("sort_order"),
                            updatedAt = rs.getTimestamp("updated_at").toInstant(),
                        )
                    }
                    list
                }
            }
        }

    override fun getContentNode(kind: String, id: UUID): ContentNodeRow? = db.withConnection { c ->
        val table = when (kind) {
            "subject" -> "subjects"
            "category" -> "categories"
            "subcategory" -> "subcategories"
            "lesson" -> "lessons"
            else -> return@withConnection null
        }
        c.prepareStatement("SELECT id, title, title_bn, status, sort_order, updated_at FROM $table WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                ContentNodeRow(
                    id = rs.getObject("id") as UUID,
                    kind = kind,
                    title = rs.getString("title"),
                    titleBn = rs.getString("title_bn"),
                    status = rs.getString("status"),
                    parentId = null,
                    sortOrder = rs.getInt("sort_order"),
                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                )
            }
        }
    }

    override fun createSubject(
        title: String, titleBn: String?, description: String, descriptionBn: String?,
        icon: String?, featured: Boolean, status: String, sortOrder: Int,
    ): ContentNodeRow = db.withConnection { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            """
            INSERT INTO subjects (id, title, title_bn, description, description_bn, icon, is_featured, status, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, title, title_bn, status, sort_order, updated_at
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id)
            ps.setString(2, title)
            ps.setString(3, titleBn)
            ps.setString(4, description)
            ps.setString(5, descriptionBn)
            ps.setString(6, icon)
            ps.setBoolean(7, featured)
            ps.setString(8, status)
            ps.setInt(9, sortOrder)
            ps.executeQuery().use { rs ->
                rs.next()
                ContentNodeRow(id, "subject", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), null, rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun updateSubject(
        id: UUID, title: String, titleBn: String?, description: String, descriptionBn: String?,
        icon: String?, featured: Boolean, status: String, sortOrder: Int,
    ): ContentNodeRow? = db.withConnection { c ->
        c.prepareStatement(
            """
            UPDATE subjects SET title=?, title_bn=?, description=?, description_bn=?, icon=?, is_featured=?,
                status=?, sort_order=?, updated_at=now() WHERE id=?
            RETURNING id, title, title_bn, status, sort_order, updated_at
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, title)
            ps.setString(2, titleBn)
            ps.setString(3, description)
            ps.setString(4, descriptionBn)
            ps.setString(5, icon)
            ps.setBoolean(6, featured)
            ps.setString(7, status)
            ps.setInt(8, sortOrder)
            ps.setObject(9, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                ContentNodeRow(id, "subject", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), null, rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun setContentStatus(kind: String, id: UUID, status: String, updatedAt: Instant): Boolean =
        db.withConnection { c ->
            val table = when (kind) {
                "subject" -> "subjects"
                "category" -> "categories"
                "subcategory" -> "subcategories"
                "lesson" -> "lessons"
                else -> return@withConnection false
            }
            c.prepareStatement("UPDATE $table SET status = ?, updated_at = ? WHERE id = ?").use { ps ->
                ps.setString(1, status)
                ps.setTimestamp(2, Timestamp.from(updatedAt))
                ps.setObject(3, id)
                ps.executeUpdate() > 0
            }
        }

    override fun createCategory(
        subjectId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int,
    ): ContentNodeRow = db.withConnection { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            """
            INSERT INTO categories (id, subject_id, title, title_bn, description, status, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id, title, title_bn, status, sort_order, updated_at
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, subjectId); ps.setString(3, title)
            ps.setString(4, titleBn); ps.setString(5, description); ps.setString(6, status); ps.setInt(7, sortOrder)
            ps.executeQuery().use { rs ->
                rs.next()
                ContentNodeRow(id, "category", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), subjectId, rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun createSubcategory(
        categoryId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int,
    ): ContentNodeRow = db.withConnection { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            """
            INSERT INTO subcategories (id, category_id, title, title_bn, description, status, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id, title, title_bn, status, sort_order, updated_at
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, categoryId); ps.setString(3, title)
            ps.setString(4, titleBn); ps.setString(5, description); ps.setString(6, status); ps.setInt(7, sortOrder)
            ps.executeQuery().use { rs ->
                rs.next()
                ContentNodeRow(id, "subcategory", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), categoryId, rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun createLesson(
        subcategoryId: UUID, title: String, titleBn: String?, summary: String, body: String,
        status: String, sortOrder: Int, estimatedMinutes: Int?,
    ): ContentNodeRow = db.withConnection { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            """
            INSERT INTO lessons (id, subcategory_id, title, title_bn, summary, content, status, sort_order, estimated_minutes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, title, title_bn, status, sort_order, updated_at
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, subcategoryId); ps.setString(3, title)
            ps.setString(4, titleBn); ps.setString(5, summary); ps.setString(6, body)
            ps.setString(7, status); ps.setInt(8, sortOrder)
            if (estimatedMinutes != null) ps.setInt(9, estimatedMinutes) else ps.setObject(9, null)
            ps.executeQuery().use { rs ->
                rs.next()
                ContentNodeRow(id, "lesson", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), subcategoryId, rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun updateLesson(
        id: UUID, title: String, titleBn: String?, summary: String, body: String,
        status: String, sortOrder: Int, estimatedMinutes: Int?,
    ): ContentNodeRow? = db.withConnection { c ->
        c.prepareStatement(
            """
            UPDATE lessons SET title=?, title_bn=?, summary=?, content=?, status=?, sort_order=?,
                estimated_minutes=?, updated_at=now() WHERE id=?
            RETURNING id, title, title_bn, status, sort_order, updated_at, subcategory_id
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, title); ps.setString(2, titleBn); ps.setString(3, summary); ps.setString(4, body)
            ps.setString(5, status); ps.setInt(6, sortOrder)
            if (estimatedMinutes != null) ps.setInt(7, estimatedMinutes) else ps.setObject(7, null)
            ps.setObject(8, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                ContentNodeRow(id, "lesson", rs.getString("title"), rs.getString("title_bn"),
                    rs.getString("status"), rs.getObject("subcategory_id") as UUID,
                    rs.getInt("sort_order"), rs.getTimestamp("updated_at").toInstant())
            }
        }
    }

    override fun listQuestionsAdmin(lessonId: UUID?, status: String?, limit: Int, cursor: String?): Pair<List<AdminQuestionRow>, String?> =
        db.withConnection { c ->
            val where = mutableListOf("1=1")
            val params = mutableListOf<Any?>()
            if (lessonId != null) { where += "lesson_id = ?"; params += lessonId }
            if (!status.isNullOrBlank()) { where += "status = ?"; params += status }
            val sql = """
                SELECT id, lesson_id, type, question_text, question_text_bn, explanation, difficulty,
                       marks, negative_marks, status, sort_order, review_note, updated_at
                FROM questions WHERE ${where.joinToString(" AND ")}
                ORDER BY sort_order, created_at DESC LIMIT ?
            """.trimIndent()
            params += limit
            val rows = c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, v -> setParam(ps, i + 1, v) }
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<AdminQuestionRow>()
                    while (rs.next()) list += readAdminQuestion(rs)
                    list
                }
            }
            rows to null
        }

    override fun getQuestionAdmin(id: UUID): AdminQuestionRow? = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT id, lesson_id, type, question_text, question_text_bn, explanation, difficulty,
                   marks, negative_marks, status, sort_order, review_note, updated_at
            FROM questions WHERE id = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) readAdminQuestion(rs) else null }
        }
    }

    override fun listOptionsAdmin(questionId: UUID): List<AdminQuestionOptionRow> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT id, question_id, option_text, option_text_bn, is_correct, sort_order FROM question_options WHERE question_id = ? ORDER BY sort_order",
        ).use { ps ->
            ps.setObject(1, questionId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AdminQuestionOptionRow>()
                while (rs.next()) {
                    list += AdminQuestionOptionRow(
                        id = rs.getObject("id") as UUID,
                        questionId = rs.getObject("question_id") as UUID,
                        text = rs.getString("option_text"),
                        textBn = rs.getString("option_text_bn"),
                        isCorrect = rs.getBoolean("is_correct"),
                        sortOrder = rs.getInt("sort_order"),
                    )
                }
                list
            }
        }
    }

    override fun createQuestion(
        lessonId: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        options: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow = db.withConnection { c ->
        val id = UUID.randomUUID()
        c.prepareStatement(
            """
            INSERT INTO questions (id, lesson_id, type, question_text, question_text_bn, explanation, explanation_bn,
                difficulty, marks, negative_marks, status, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, id); ps.setObject(2, lessonId); ps.setString(3, type)
            ps.setString(4, text); ps.setString(5, textBn); ps.setString(6, explanation); ps.setString(7, explanationBn)
            ps.setString(8, difficulty); ps.setBigDecimal(9, java.math.BigDecimal.valueOf(marks))
            ps.setBigDecimal(10, java.math.BigDecimal.valueOf(negativeMarks))
            ps.setString(11, status); ps.setInt(12, sortOrder)
            ps.executeUpdate()
        }
        replaceOptions(c, id, options)
        replaceTags(c, id, tags)
        getQuestionAdmin(id)!!
    }

    override fun updateQuestion(
        id: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        options: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow? = db.withConnection { c ->
        val n = c.prepareStatement(
            """
            UPDATE questions SET type=?, question_text=?, question_text_bn=?, explanation=?, explanation_bn=?,
                difficulty=?, marks=?, negative_marks=?, status=?, sort_order=?, updated_at=now()
            WHERE id=?
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, type); ps.setString(2, text); ps.setString(3, textBn)
            ps.setString(4, explanation); ps.setString(5, explanationBn); ps.setString(6, difficulty)
            ps.setBigDecimal(7, java.math.BigDecimal.valueOf(marks))
            ps.setBigDecimal(8, java.math.BigDecimal.valueOf(negativeMarks))
            ps.setString(9, status); ps.setInt(10, sortOrder); ps.setObject(11, id)
            ps.executeUpdate()
        }
        if (n == 0) return@withConnection null
        replaceOptions(c, id, options)
        replaceTags(c, id, tags)
        getQuestionAdmin(id)
    }

    override fun setQuestionStatus(id: UUID, status: String, reviewNote: String?, reviewedBy: UUID?, reviewedAt: Instant?): Boolean =
        db.withConnection { c ->
            c.prepareStatement(
                "UPDATE questions SET status=?, review_note=?, reviewed_by=?, reviewed_at=?, updated_at=now() WHERE id=?",
            ).use { ps ->
                ps.setString(1, status)
                ps.setString(2, reviewNote)
                if (reviewedBy != null) ps.setObject(3, reviewedBy) else ps.setObject(3, null)
                if (reviewedAt != null) ps.setTimestamp(4, Timestamp.from(reviewedAt)) else ps.setTimestamp(4, null)
                ps.setObject(5, id)
                ps.executeUpdate() > 0
            }
        }

    override fun deleteQuestionHard(id: UUID): Boolean = db.withConnection { c ->
        c.prepareStatement("DELETE FROM questions WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeUpdate() > 0
        }
    }

    override fun listContestsAdmin(status: String?, limit: Int): List<AdminContestListRow> = db.withConnection { c ->
        val where = if (!status.isNullOrBlank()) "WHERE status = ?" else ""
        val sql = """
            SELECT c.id, c.title, c.status, c.starts_at, c.ends_at,
                   (SELECT count(*) FROM contest_participants p WHERE p.contest_id = c.id) AS pc
            FROM contests c $where
            ORDER BY c.starts_at DESC LIMIT ?
        """.trimIndent()
        c.prepareStatement(sql).use { ps ->
            var i = 1
            if (!status.isNullOrBlank()) { ps.setString(i++, status) }
            ps.setInt(i, limit)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AdminContestListRow>()
                while (rs.next()) {
                    list += AdminContestListRow(
                        id = rs.getObject("id") as UUID,
                        title = rs.getString("title"),
                        status = rs.getString("status"),
                        startsAt = rs.getTimestamp("starts_at").toInstant(),
                        endsAt = rs.getTimestamp("ends_at").toInstant(),
                        participantCount = rs.getInt("pc"),
                    )
                }
                list
            }
        }
    }

    override fun setContestStatus(id: UUID, status: String): Boolean = db.withConnection { c ->
        c.prepareStatement("UPDATE contests SET status = ? WHERE id = ?").use { ps ->
            ps.setString(1, status); ps.setObject(2, id); ps.executeUpdate() > 0
        }
    }

    override fun listExamsAdmin(status: String?, limit: Int): List<AdminExamListRow> = db.withConnection { c ->
        val where = if (!status.isNullOrBlank()) "WHERE status = ?" else ""
        val sql = "SELECT id, title, status, opens_at, closes_at FROM exams $where ORDER BY opens_at DESC LIMIT ?"
        c.prepareStatement(sql).use { ps ->
            var i = 1
            if (!status.isNullOrBlank()) { ps.setString(i++, status) }
            ps.setInt(i, limit)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AdminExamListRow>()
                while (rs.next()) {
                    list += AdminExamListRow(
                        id = rs.getObject("id") as UUID,
                        title = rs.getString("title"),
                        status = rs.getString("status"),
                        opensAt = rs.getTimestamp("opens_at").toInstant(),
                        closesAt = rs.getTimestamp("closes_at").toInstant(),
                    )
                }
                list
            }
        }
    }

    override fun setExamStatus(id: UUID, status: String): Boolean = db.withConnection { c ->
        c.prepareStatement("UPDATE exams SET status = ? WHERE id = ?").use { ps ->
            ps.setString(1, status); ps.setObject(2, id); ps.executeUpdate() > 0
        }
    }

    override fun saveDestructiveToken(row: DestructiveTokenRow) = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO destructive_action_tokens (id, actor_id, action, resource_type, resource_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, row.id); ps.setObject(2, row.actorId); ps.setString(3, row.action)
            ps.setString(4, row.resourceType); ps.setString(5, row.resourceId)
            ps.setString(6, row.tokenHash); ps.setTimestamp(7, Timestamp.from(row.expiresAt))
            ps.executeUpdate()
        }
        Unit
    }

    override fun findDestructiveToken(tokenHash: String): DestructiveTokenRow? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT id, actor_id, action, resource_type, resource_id, token_hash, expires_at, used_at FROM destructive_action_tokens WHERE token_hash = ?",
        ).use { ps ->
            ps.setString(1, tokenHash)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                DestructiveTokenRow(
                    id = rs.getObject("id") as UUID,
                    actorId = rs.getObject("actor_id") as UUID,
                    action = rs.getString("action"),
                    resourceType = rs.getString("resource_type"),
                    resourceId = rs.getString("resource_id"),
                    tokenHash = rs.getString("token_hash"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    usedAt = rs.getTimestamp("used_at")?.toInstant(),
                )
            }
        }
    }

    override fun markDestructiveTokenUsed(id: UUID, usedAt: Instant) = db.withConnection { c ->
        c.prepareStatement("UPDATE destructive_action_tokens SET used_at = ? WHERE id = ?").use { ps ->
            ps.setTimestamp(1, Timestamp.from(usedAt)); ps.setObject(2, id); ps.executeUpdate()
        }
        Unit
    }

    override fun createImportBatch(row: ImportBatchRow) = db.withConnection { c ->
        c.prepareStatement(
            """
            INSERT INTO import_batches (id, actor_id, status, total_rows, success_count, error_count, errors, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, row.id); ps.setObject(2, row.actorId); ps.setString(3, row.status)
            ps.setInt(4, row.totalRows); ps.setInt(5, row.successCount); ps.setInt(6, row.errorCount)
            ps.setString(7, row.errorsJson); ps.setTimestamp(8, Timestamp.from(row.createdAt))
            ps.executeUpdate()
        }
        Unit
    }

    override fun finishImportBatch(id: UUID, status: String, success: Int, errors: Int, errorsJson: String, finishedAt: Instant) =
        db.withConnection { c ->
            c.prepareStatement(
                "UPDATE import_batches SET status=?, success_count=?, error_count=?, errors=?::jsonb, finished_at=? WHERE id=?",
            ).use { ps ->
                ps.setString(1, status); ps.setInt(2, success); ps.setInt(3, errors)
                ps.setString(4, errorsJson); ps.setTimestamp(5, Timestamp.from(finishedAt)); ps.setObject(6, id)
                ps.executeUpdate()
            }
            Unit
        }

    private fun replaceOptions(c: Connection, questionId: UUID, options: List<AdminQuestionOptionInput>) {
        c.prepareStatement("DELETE FROM question_options WHERE question_id = ?").use { ps ->
            ps.setObject(1, questionId); ps.executeUpdate()
        }
        if (options.isEmpty()) return
        c.prepareStatement(
            "INSERT INTO question_options (id, question_id, option_text, option_text_bn, is_correct, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { ps ->
            options.forEachIndexed { idx, o ->
                ps.setObject(1, UUID.randomUUID())
                ps.setObject(2, questionId)
                ps.setString(3, o.text)
                ps.setString(4, o.textBn)
                ps.setBoolean(5, o.isCorrect)
                ps.setInt(6, if (o.sortOrder != 0) o.sortOrder else idx)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun replaceTags(c: Connection, questionId: UUID, tags: List<String>) {
        c.prepareStatement("DELETE FROM question_tags WHERE question_id = ?").use { ps ->
            ps.setObject(1, questionId); ps.executeUpdate()
        }
        for (raw in tags) {
            val slug = raw.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').take(50)
            if (slug.isBlank()) continue
            val tagId = c.prepareStatement(
                """
                INSERT INTO tags (id, slug, name) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).use { ps ->
                // tags_slug_uq is unique index on slug; use select-or-insert
                null as UUID?
            }
            // select existing or insert
            val existing = c.prepareStatement("SELECT id FROM tags WHERE slug = ?").use { ps ->
                ps.setString(1, slug)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1) as UUID else null }
            }
            val tid = existing ?: run {
                val nid = UUID.randomUUID()
                c.prepareStatement("INSERT INTO tags (id, slug, name) VALUES (?, ?, ?)").use { ps ->
                    ps.setObject(1, nid); ps.setString(2, slug); ps.setString(3, raw.trim()); ps.executeUpdate()
                }
                nid
            }
            c.prepareStatement("INSERT INTO question_tags (question_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING").use { ps ->
                ps.setObject(1, questionId); ps.setObject(2, tid); ps.executeUpdate()
            }
        }
    }

    private fun readAdminQuestion(rs: ResultSet) = AdminQuestionRow(
        id = rs.getObject("id") as UUID,
        lessonId = rs.getObject("lesson_id") as UUID,
        type = rs.getString("type"),
        questionText = rs.getString("question_text"),
        questionTextBn = rs.getString("question_text_bn"),
        explanation = rs.getString("explanation") ?: "",
        difficulty = rs.getString("difficulty"),
        marks = rs.getBigDecimal("marks").toDouble(),
        negativeMarks = rs.getBigDecimal("negative_marks").toDouble(),
        status = rs.getString("status"),
        sortOrder = rs.getInt("sort_order"),
        reviewNote = try { rs.getString("review_note") } catch (_: Exception) { null },
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )


    private fun readUser(rs: ResultSet, c: Connection): UserRow {
        val id = rs.getObject("id") as UUID
        val roles = c.prepareStatement(
            "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.id",
        ).use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { r ->
                val list = mutableListOf<String>()
                while (r.next()) list += r.getString(1)
                list
            }
        }
        return UserRow(
            id = id,
            email = rs.getString("email"),
            displayName = rs.getString("display_name"),
            status = rs.getString("status"),
            emailVerifiedAt = rs.getTimestamp("email_verified_at")?.toInstant(),
            roles = roles,
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    private fun readFlag(rs: ResultSet) = FeatureFlagRow(
        key = rs.getString("key"),
        enabled = rs.getBoolean("enabled"),
        description = rs.getString("description") ?: "",
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun readConfig(rs: ResultSet) = AppConfigRow(
        key = rs.getString("key"),
        valueJson = rs.getString(2) ?: "null",
        description = rs.getString("description") ?: "",
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun readReport(rs: ResultSet) = AbuseReportRow(
        id = rs.getObject("id") as UUID,
        reporterId = rs.getObject("reporter_id") as? UUID,
        targetType = rs.getString("target_type"),
        targetId = rs.getString("target_id"),
        reason = rs.getString("reason"),
        details = rs.getString("details"),
        status = rs.getString("status"),
        resolverId = rs.getObject("resolver_id") as? UUID,
        resolutionNote = rs.getString("resolution_note"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
    )

    private fun <T> page(rows: List<T>, limit: Int, key: (T) -> Pair<Instant, UUID>): Pair<List<T>, String?> {
        if (rows.size <= limit) return rows to null
        val page = rows.take(limit)
        val last = page.last()
        val (t, id) = key(last)
        return page to encodeCursor(t, id)
    }

    private fun encodeCursor(t: Instant, id: UUID): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${t.toEpochMilli()}:$id".toByteArray())

    private fun decodeCursor(cursor: String): Pair<Instant, UUID>? = runCatching {
        val raw = String(Base64.getUrlDecoder().decode(cursor))
        val parts = raw.split(":", limit = 2)
        Instant.ofEpochMilli(parts[0].toLong()) to UUID.fromString(parts[1])
    }.getOrNull()

    private fun setParam(ps: java.sql.PreparedStatement, i: Int, v: Any?) {
        when (v) {
            null -> ps.setObject(i, null)
            is String -> ps.setString(i, v)
            is UUID -> ps.setObject(i, v)
            is Timestamp -> ps.setTimestamp(i, v)
            is Instant -> ps.setTimestamp(i, Timestamp.from(v))
            is Boolean -> ps.setBoolean(i, v)
            is Int -> ps.setInt(i, v)
            else -> ps.setObject(i, v)
        }
    }
}
