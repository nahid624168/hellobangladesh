package com.exambase.backend.admin

import com.exambase.backend.ApiException
import com.exambase.backend.MessageResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.Instant
import java.util.UUID

class AdminService(
    private val repo: AdminRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Used by routes to obtain the caller's roles. */
    fun loadRoles(userId: UUID): List<String> = repo.listRolesFor(userId)

    // ---------- Authorization helpers ----------

    fun requireRole(actorRoles: List<String>, minRole: String) {
        val rank = actorRoles.maxOfOrNull { Roles.rank(it) } ?: -1
        if (rank < Roles.rank(minRole)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Insufficient role.")
        }
    }

    fun isAtLeast(actorRoles: List<String>, minRole: String): Boolean {
        val rank = actorRoles.maxOfOrNull { Roles.rank(it) } ?: -1
        return rank >= Roles.rank(minRole)
    }

    // ---------- Users ----------

    fun searchUsers(
        actorId: UUID,
        actorRoles: List<String>,
        query: String?,
        status: String?,
        role: String?,
        limit: Int,
        cursor: String?,
    ): AdminUserListDto {
        requireRole(actorRoles, Roles.MODERATOR)
        val lim = limit.coerceIn(1, 50)
        val (rows, next) = repo.searchUsers(query, status, role, lim, cursor)
        return AdminUserListDto(rows.map { it.toDto() }, next)
    }

    fun getUser(actorRoles: List<String>, userId: UUID): AdminUserDto {
        requireRole(actorRoles, Roles.MODERATOR)
        val u = repo.getUser(userId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found.")
        return u.toDto()
    }

    fun updateUserStatus(
        actorId: UUID,
        actorRoles: List<String>,
        userId: UUID,
        status: String,
        reason: String?,
        ip: String?,
    ): AdminUserDto {
        requireRole(actorRoles, Roles.ADMIN)
        if (status !in setOf("active", "suspended")) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "status must be active or suspended.")
        }
        if (userId == actorId) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Cannot change your own status.")
        }
        val target = repo.getUser(userId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found.")
        // Only super_admin can touch other admins / super_admins
        val targetRank = target.roles.maxOfOrNull { Roles.rank(it) } ?: 0
        val actorRank = actorRoles.maxOfOrNull { Roles.rank(it) } ?: 0
        if (targetRank >= Roles.rank(Roles.ADMIN) && actorRank < Roles.rank(Roles.SUPER_ADMIN)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Only super_admin can moderate admins.")
        }
        val now = clock.instant()
        repo.updateUserStatus(userId, status, now)
        audit(actorId, "user.status_change", "user", userId.toString(), mapOf("status" to status, "reason" to (reason ?: "")), ip)
        return repo.getUser(userId)!!.toDto()
    }

    fun setRoles(
        actorId: UUID,
        actorRoles: List<String>,
        userId: UUID,
        roles: List<String>,
        ip: String?,
    ): AdminUserDto {
        requireRole(actorRoles, Roles.ADMIN)
        if (userId == actorId) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Cannot change your own roles.")
        }
        val allowed = Roles.ORDER.toSet()
        val cleaned = roles.map { it.trim().lowercase() }.filter { it in allowed }.distinct()
        if (cleaned.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "At least one valid role is required.")
        }
        val actorRank = actorRoles.maxOfOrNull { Roles.rank(it) } ?: 0
        val targetMax = cleaned.maxOf { Roles.rank(it) }
        // Cannot assign a role higher than or equal to your own unless super_admin
        if (targetMax >= actorRank && actorRank < Roles.rank(Roles.SUPER_ADMIN)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cannot assign a role at or above your own.")
        }
        val target = repo.getUser(userId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found.")
        val existingMax = target.roles.maxOfOrNull { Roles.rank(it) } ?: 0
        if (existingMax >= Roles.rank(Roles.ADMIN) && actorRank < Roles.rank(Roles.SUPER_ADMIN)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Only super_admin can change admin roles.")
        }
        repo.setUserRoles(userId, cleaned)
        audit(actorId, "user.roles_set", "user", userId.toString(), mapOf("roles" to cleaned), ip)
        return repo.getUser(userId)!!.toDto()
    }

    // ---------- Feature flags ----------

    fun listFlags(actorRoles: List<String>): List<FeatureFlagDto> {
        requireRole(actorRoles, Roles.ADMIN)
        return repo.listFeatureFlags().map { it.toDto() }
    }

    fun upsertFlag(
        actorId: UUID,
        actorRoles: List<String>,
        key: String,
        enabled: Boolean,
        description: String?,
        ip: String?,
    ): FeatureFlagDto {
        requireRole(actorRoles, Roles.SUPER_ADMIN)
        val k = key.trim().lowercase().replace(' ', '_')
        if (k.isBlank() || k.length > 64) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid flag key.")
        }
        val existing = repo.getFeatureFlag(k)
        val desc = description ?: existing?.description ?: ""
        val now = clock.instant()
        repo.upsertFeatureFlag(k, enabled, desc, actorId, now)
        audit(actorId, "feature_flag.upsert", "feature_flag", k, mapOf("enabled" to enabled), ip)
        return repo.getFeatureFlag(k)!!.toDto()
    }

    /** Public read for any authenticated user (used by clients to gate UI). */
    fun publicFlags(): List<FeatureFlagDto> =
        repo.listFeatureFlags().map { it.toDto() }

    // ---------- App config ----------

    fun listConfig(actorRoles: List<String>): List<AppConfigEntryDto> {
        requireRole(actorRoles, Roles.ADMIN)
        return repo.listAppConfig().map { it.toDto(json) }
    }

    fun upsertConfig(
        actorId: UUID,
        actorRoles: List<String>,
        key: String,
        value: JsonElement,
        description: String?,
        ip: String?,
    ): AppConfigEntryDto {
        requireRole(actorRoles, Roles.ADMIN)
        val k = key.trim().lowercase().replace(' ', '_')
        if (k.isBlank() || k.length > 64) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid config key.")
        }
        val existing = repo.getAppConfig(k)
        val desc = description ?: existing?.description ?: ""
        val now = clock.instant()
        val valueJson = json.encodeToString(JsonElement.serializer(), value)
        repo.upsertAppConfig(k, valueJson, desc, actorId, now)
        audit(actorId, "app_config.upsert", "app_config", k, mapOf("value" to valueJson), ip)
        return repo.getAppConfig(k)!!.toDto(json)
    }

    // ---------- Audit ----------

    fun listAudit(
        actorRoles: List<String>,
        actorIdFilter: UUID?,
        resourceType: String?,
        action: String?,
        limit: Int,
        cursor: String?,
    ): AuditLogListDto {
        requireRole(actorRoles, Roles.ADMIN)
        val lim = limit.coerceIn(1, 50)
        val (rows, next) = repo.listAuditLogs(actorIdFilter, resourceType, action, lim, cursor)
        return AuditLogListDto(
            items = rows.map { r ->
                AuditLogDto(
                    id = r.id.toString(),
                    actorId = r.actorId?.toString(),
                    action = r.action,
                    resourceType = r.resourceType,
                    resourceId = r.resourceId,
                    details = runCatching { json.parseToJsonElement(r.detailsJson) }.getOrNull(),
                    createdAt = r.createdAt.toString(),
                )
            },
            nextCursor = next,
        )
    }

    // ---------- Abuse reports ----------

    fun createReport(
        reporterId: UUID?,
        targetType: String,
        targetId: String,
        reason: String,
        details: String?,
    ): AbuseReportDto {
        val allowed = setOf("user", "question", "note", "quiz_set", "flashcard_set", "comment", "other")
        if (targetType !in allowed) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid targetType.")
        }
        if (reason.isBlank() || reason.length > 200) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "reason is required (max 200).")
        }
        val id = UUID.randomUUID()
        val now = clock.instant()
        val row = AbuseReportRow(
            id = id,
            reporterId = reporterId,
            targetType = targetType,
            targetId = targetId.trim(),
            reason = reason.trim(),
            details = details?.trim()?.take(2000),
            status = "open",
            resolverId = null,
            resolutionNote = null,
            createdAt = now,
            resolvedAt = null,
        )
        repo.createAbuseReport(row)
        return row.toDto()
    }

    fun listReports(actorRoles: List<String>, status: String?, limit: Int, cursor: String?): AbuseReportListDto {
        requireRole(actorRoles, Roles.MODERATOR)
        val lim = limit.coerceIn(1, 50)
        val (rows, next) = repo.listAbuseReports(status, lim, cursor)
        return AbuseReportListDto(rows.map { it.toDto() }, next)
    }

    fun resolveReport(
        actorId: UUID,
        actorRoles: List<String>,
        reportId: UUID,
        status: String,
        note: String?,
        ip: String?,
    ): AbuseReportDto {
        requireRole(actorRoles, Roles.MODERATOR)
        if (status !in setOf("resolved", "dismissed", "reviewing")) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "status must be resolved, dismissed or reviewing.")
        }
        val existing = repo.getAbuseReport(reportId)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Report not found.")
        val resolvedAt = if (status in setOf("resolved", "dismissed")) clock.instant() else null
        repo.resolveAbuseReport(reportId, status, actorId, note?.take(1000), resolvedAt)
        audit(actorId, "abuse_report.resolve", "abuse_report", reportId.toString(), mapOf("status" to status), ip)
        return repo.getAbuseReport(reportId)!!.toDto()
    }


    // ---------- 30b Content ----------

    private val contentStatuses = setOf("draft", "published")
    private val questionStatuses = setOf("draft", "review", "published", "archived")
    private val questionTypes = setOf("single_choice", "multiple_select", "true_false", "short_answer", "written", "image_based")
    private val contestStatuses = setOf("draft", "scheduled", "live", "ended", "cancelled")
    private val examStatuses = setOf("draft", "scheduled", "open", "closed", "archived")

    fun listSubjects(actorRoles: List<String>, status: String?, limit: Int): AdminContentListDto {
        requireRole(actorRoles, Roles.EDITOR)
        val (rows, next) = repo.listSubjectsAdmin(status, limit.coerceIn(1, 50), null)
        return AdminContentListDto(rows.map { it.toDto() }, next)
    }

    fun listChildren(actorRoles: List<String>, kind: String, parentId: UUID, status: String?): AdminContentListDto {
        requireRole(actorRoles, Roles.EDITOR)
        val rows = repo.listChildrenAdmin(kind, parentId, status, 50)
        return AdminContentListDto(rows.map { it.toDto() })
    }

    fun createSubject(actorId: UUID, actorRoles: List<String>, body: UpsertSubjectRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        if (body.title.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "title is required.")
        val row = repo.createSubject(body.title.trim(), body.titleBn, body.description, body.descriptionBn, body.icon, body.isFeatured, body.status, body.sortOrder)
        audit(actorId, "content.create", "subject", row.id.toString(), mapOf("title" to row.title), ip)
        return row.toDto()
    }

    fun updateSubject(actorId: UUID, actorRoles: List<String>, id: UUID, body: UpsertSubjectRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        val row = repo.updateSubject(id, body.title.trim(), body.titleBn, body.description, body.descriptionBn, body.icon, body.isFeatured, body.status, body.sortOrder)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Subject not found.")
        audit(actorId, "content.update", "subject", id.toString(), mapOf("status" to body.status), ip)
        return row.toDto()
    }

    fun setContentStatus(actorId: UUID, actorRoles: List<String>, kind: String, id: UUID, status: String, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(status)
        if (!repo.setContentStatus(kind, id, status, clock.instant())) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Content not found.")
        }
        audit(actorId, "content.status", kind, id.toString(), mapOf("status" to status), ip)
        return repo.getContentNode(kind, id)!!.toDto()
    }

    fun createCategory(actorId: UUID, actorRoles: List<String>, body: UpsertCategoryRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        val subjectId = UUID.fromString(body.subjectId)
        val row = repo.createCategory(subjectId, body.title.trim(), body.titleBn, body.description, body.status, body.sortOrder)
        audit(actorId, "content.create", "category", row.id.toString(), mapOf("title" to row.title), ip)
        return row.toDto()
    }

    fun createSubcategory(actorId: UUID, actorRoles: List<String>, body: UpsertSubcategoryRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        val categoryId = UUID.fromString(body.categoryId)
        val row = repo.createSubcategory(categoryId, body.title.trim(), body.titleBn, body.description, body.status, body.sortOrder)
        audit(actorId, "content.create", "subcategory", row.id.toString(), mapOf("title" to row.title), ip)
        return row.toDto()
    }

    fun createLesson(actorId: UUID, actorRoles: List<String>, body: UpsertLessonRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        val subId = UUID.fromString(body.subcategoryId)
        val row = repo.createLesson(subId, body.title.trim(), body.titleBn, body.summary, body.bodyMarkdown, body.status, body.sortOrder, body.estimatedMinutes)
        audit(actorId, "content.create", "lesson", row.id.toString(), mapOf("title" to row.title), ip)
        return row.toDto()
    }

    fun updateLesson(actorId: UUID, actorRoles: List<String>, id: UUID, body: UpsertLessonRequest, ip: String?): AdminContentNodeDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateContentStatus(body.status)
        val row = repo.updateLesson(id, body.title.trim(), body.titleBn, body.summary, body.bodyMarkdown, body.status, body.sortOrder, body.estimatedMinutes)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Lesson not found.")
        audit(actorId, "content.update", "lesson", id.toString(), mapOf("status" to body.status), ip)
        return row.toDto()
    }

    // ---------- Questions ----------

    fun listQuestions(actorRoles: List<String>, lessonId: String?, status: String?, limit: Int): AdminQuestionListDto {
        requireRole(actorRoles, Roles.EDITOR)
        val lid = lessonId?.let { UUID.fromString(it) }
        val (rows, next) = repo.listQuestionsAdmin(lid, status, limit.coerceIn(1, 50), null)
        return AdminQuestionListDto(rows.map { q ->
            val opts = repo.listOptionsAdmin(q.id)
            q.toDto(opts)
        }, next)
    }

    fun getQuestion(actorRoles: List<String>, id: UUID): AdminQuestionDto {
        requireRole(actorRoles, Roles.EDITOR)
        val q = repo.getQuestionAdmin(id) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
        return q.toDto(repo.listOptionsAdmin(id))
    }

    fun createQuestion(actorId: UUID, actorRoles: List<String>, body: AdminQuestionUpsertRequest, ip: String?): AdminQuestionDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateQuestionBody(body)
        val row = repo.createQuestion(
            UUID.fromString(body.lessonId), body.type, body.questionText.trim(), body.questionTextBn,
            body.explanation, body.explanationBn, body.difficulty, body.marks, body.negativeMarks,
            body.status, body.sortOrder, body.options, body.tags,
        )
        audit(actorId, "question.create", "question", row.id.toString(), mapOf("type" to body.type), ip)
        return row.toDto(repo.listOptionsAdmin(row.id))
    }

    fun updateQuestion(actorId: UUID, actorRoles: List<String>, id: UUID, body: AdminQuestionUpsertRequest, ip: String?): AdminQuestionDto {
        requireRole(actorRoles, Roles.EDITOR)
        validateQuestionBody(body)
        val row = repo.updateQuestion(
            id, body.type, body.questionText.trim(), body.questionTextBn, body.explanation, body.explanationBn,
            body.difficulty, body.marks, body.negativeMarks, body.status, body.sortOrder, body.options, body.tags,
        ) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
        audit(actorId, "question.update", "question", id.toString(), mapOf("status" to body.status), ip)
        return row.toDto(repo.listOptionsAdmin(id))
    }

    fun setQuestionStatus(actorId: UUID, actorRoles: List<String>, id: UUID, status: String, reviewNote: String?, ip: String?): AdminQuestionDto {
        // Editors can move to review; moderators/admins can publish/archive
        when (status) {
            "draft", "review" -> requireRole(actorRoles, Roles.EDITOR)
            "published", "archived" -> requireRole(actorRoles, Roles.MODERATOR)
            else -> throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid question status.")
        }
        val reviewedAt = if (status in setOf("published", "archived", "review")) clock.instant() else null
        if (!repo.setQuestionStatus(id, status, reviewNote, actorId, reviewedAt)) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Question not found.")
        }
        audit(actorId, "question.status", "question", id.toString(), mapOf("status" to status), ip)
        return repo.getQuestionAdmin(id)!!.toDto(repo.listOptionsAdmin(id))
    }

    fun bulkImport(actorId: UUID, actorRoles: List<String>, body: BulkImportRequest, ip: String?): BulkImportResultDto {
        requireRole(actorRoles, Roles.EDITOR)
        if (body.questions.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "questions array is empty.")
        if (body.questions.size > 200) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "At most 200 questions per batch.")
        val batchId = UUID.randomUUID()
        val now = clock.instant()
        repo.createImportBatch(ImportBatchRow(batchId, actorId, "processing", body.questions.size, 0, 0, "[]", now, null))
        val errors = mutableListOf<String>()
        var success = 0
        body.questions.forEachIndexed { idx, item ->
            try {
                val req = AdminQuestionUpsertRequest(
                    lessonId = item.lessonId, type = item.type, questionText = item.questionText,
                    questionTextBn = item.questionTextBn, explanation = item.explanation,
                    difficulty = item.difficulty, marks = item.marks, negativeMarks = item.negativeMarks,
                    status = item.status, options = item.options, tags = item.tags,
                )
                validateQuestionBody(req)
                repo.createQuestion(
                    UUID.fromString(item.lessonId), item.type, item.questionText.trim(), item.questionTextBn,
                    item.explanation, null, item.difficulty, item.marks, item.negativeMarks,
                    item.status, 0, item.options, item.tags,
                )
                success++
            } catch (e: Exception) {
                errors += "row $idx: ${e.message ?: "error"}"
            }
        }
        val status = when {
            success == body.questions.size -> "completed"
            success == 0 -> "failed"
            else -> "partial"
        }
        val errorsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), errors.take(50))
        repo.finishImportBatch(batchId, status, success, errors.size, errorsJson, clock.instant())
        audit(actorId, "question.bulk_import", "import_batch", batchId.toString(), mapOf("success" to success, "errors" to errors.size), ip)
        return BulkImportResultDto(batchId.toString(), status, body.questions.size, success, errors.size, errors.take(20))
    }

    // ---------- Contests / Exams ----------

    fun listContests(actorRoles: List<String>, status: String?): AdminContestListDto {
        requireRole(actorRoles, Roles.EDITOR)
        val rows = repo.listContestsAdmin(status, 50)
        return AdminContestListDto(rows.map {
            AdminContestDto(it.id.toString(), it.title, it.status, it.startsAt.toString(), it.endsAt.toString(), it.participantCount)
        })
    }

    fun setContestStatus(actorId: UUID, actorRoles: List<String>, id: UUID, status: String, ip: String?): AdminContestDto {
        requireRole(actorRoles, Roles.ADMIN)
        if (status !in contestStatuses) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid contest status.")
        if (!repo.setContestStatus(id, status)) throw ApiException(HttpStatusCode.NotFound, "not_found", "Contest not found.")
        audit(actorId, "contest.status", "contest", id.toString(), mapOf("status" to status), ip)
        val row = repo.listContestsAdmin(null, 200).first { it.id == id }
        return AdminContestDto(row.id.toString(), row.title, row.status, row.startsAt.toString(), row.endsAt.toString(), row.participantCount)
    }

    fun listExams(actorRoles: List<String>, status: String?): AdminExamListDto {
        requireRole(actorRoles, Roles.EDITOR)
        val rows = repo.listExamsAdmin(status, 50)
        return AdminExamListDto(rows.map {
            AdminExamDto(it.id.toString(), it.title, it.status, it.opensAt.toString(), it.closesAt.toString())
        })
    }

    fun setExamStatus(actorId: UUID, actorRoles: List<String>, id: UUID, status: String, ip: String?): AdminExamDto {
        requireRole(actorRoles, Roles.ADMIN)
        if (status !in examStatuses) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid exam status.")
        if (!repo.setExamStatus(id, status)) throw ApiException(HttpStatusCode.NotFound, "not_found", "Exam not found.")
        audit(actorId, "exam.status", "exam", id.toString(), mapOf("status" to status), ip)
        val row = repo.listExamsAdmin(null, 200).first { it.id == id }
        return AdminExamDto(row.id.toString(), row.title, row.status, row.opensAt.toString(), row.closesAt.toString())
    }

    // ---------- Destructive confirmation ----------

    fun requestDestructiveToken(actorId: UUID, actorRoles: List<String>, action: String, resourceType: String, resourceId: String): DestructiveTokenDto {
        requireRole(actorRoles, Roles.ADMIN)
        val allowedActions = setOf("archive_question", "delete_question", "unpublish_subject", "cancel_contest", "archive_exam")
        if (action !in allowedActions) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Unknown destructive action.")
        }
        val raw = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteArray(32).also { java.security.SecureRandom().nextBytes(it) },
        )
        val hash = com.exambase.backend.auth.Hashing.sha256Hex(raw)
        val expires = clock.instant().plusSeconds(300) // 5 minutes
        val id = UUID.randomUUID()
        repo.saveDestructiveToken(DestructiveTokenRow(id, actorId, action, resourceType, resourceId, hash, expires, null))
        return DestructiveTokenDto(raw, expires.toString(), action, resourceType, resourceId)
    }

    fun confirmDestructive(actorId: UUID, actorRoles: List<String>, token: String, ip: String?): MessageResponse {
        requireRole(actorRoles, Roles.ADMIN)
        val hash = com.exambase.backend.auth.Hashing.sha256Hex(token)
        val row = repo.findDestructiveToken(hash)
            ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_token", "Token is invalid or expired.")
        if (row.actorId != actorId) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Token belongs to another admin.")
        if (row.usedAt != null) throw ApiException(HttpStatusCode.BadRequest, "invalid_token", "Token already used.")
        if (row.expiresAt.isBefore(clock.instant())) throw ApiException(HttpStatusCode.BadRequest, "invalid_token", "Token expired.")
        val resourceId = try { UUID.fromString(row.resourceId) } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Bad resource id.")
        }
        when (row.action) {
            "archive_question" -> repo.setQuestionStatus(resourceId, "archived", null, actorId, clock.instant())
            "delete_question" -> repo.deleteQuestionHard(resourceId)
            "unpublish_subject" -> repo.setContentStatus("subject", resourceId, "draft", clock.instant())
            "cancel_contest" -> repo.setContestStatus(resourceId, "cancelled")
            "archive_exam" -> repo.setExamStatus(resourceId, "archived")
            else -> throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Unknown action.")
        }
        repo.markDestructiveTokenUsed(row.id, clock.instant())
        audit(actorId, "destructive.${row.action}", row.resourceType, row.resourceId, mapOf("confirmed" to true), ip)
        return MessageResponse("Action ${row.action} completed.")
    }

    private fun validateContentStatus(status: String) {
        if (status !in contentStatuses) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "status must be draft or published.")
    }

    private fun validateQuestionBody(body: AdminQuestionUpsertRequest) {
        if (body.type !in questionTypes) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid question type.")
        if (body.questionText.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "questionText is required.")
        if (body.status !in questionStatuses) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid question status.")
        if (body.difficulty !in setOf("easy", "medium", "hard")) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid difficulty.")
        if (body.marks <= 0) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "marks must be > 0.")
        if (body.type in setOf("single_choice", "multiple_select", "true_false", "image_based") && body.options.size < 2) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "At least 2 options required for this type.")
        }
    }

    private fun ContentNodeRow.toDto() = AdminContentNodeDto(
        id = id.toString(), kind = kind, title = title, titleBn = titleBn, status = status,
        parentId = parentId?.toString(), sortOrder = sortOrder, updatedAt = updatedAt.toString(),
    )

    private fun AdminQuestionRow.toDto(opts: List<AdminQuestionOptionRow>) = AdminQuestionDto(
        id = id.toString(), lessonId = lessonId.toString(), type = type, questionText = questionText,
        questionTextBn = questionTextBn, explanation = explanation, difficulty = difficulty,
        marks = marks, negativeMarks = negativeMarks, status = status, sortOrder = sortOrder,
        options = opts.map { AdminQuestionOptionDto(it.id.toString(), it.text, it.textBn, it.isCorrect, it.sortOrder) },
        reviewNote = reviewNote, updatedAt = updatedAt.toString(),
    )


    // ---------- helpers ----------

    private fun audit(actorId: UUID?, action: String, resourceType: String, resourceId: String?, details: Map<String, Any?>, ip: String?) {
        val detailsJson = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(details.mapValues { (_, v) ->
                when (v) {
                    null -> JsonPrimitive(null as String?)
                    is Boolean -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    is String -> JsonPrimitive(v)
                    is List<*> -> JsonPrimitive(v.joinToString(","))
                    else -> JsonPrimitive(v.toString())
                }
            }),
        )
        repo.insertAudit(actorId, action, resourceType, resourceId, detailsJson, ip, clock.instant())
    }

    private fun UserRow.toDto() = AdminUserDto(
        id = id.toString(),
        email = email,
        displayName = displayName,
        status = status,
        emailVerified = emailVerifiedAt != null,
        roles = roles,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun FeatureFlagRow.toDto() = FeatureFlagDto(
        key = key,
        enabled = enabled,
        description = description,
        updatedAt = updatedAt.toString(),
    )

    private fun AppConfigRow.toDto(json: Json) = AppConfigEntryDto(
        key = key,
        value = runCatching { json.parseToJsonElement(valueJson) }.getOrDefault(JsonPrimitive(null as String?)),
        description = description,
        updatedAt = updatedAt.toString(),
    )

    private fun AbuseReportRow.toDto() = AbuseReportDto(
        id = id.toString(),
        reporterId = reporterId?.toString(),
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        details = details,
        status = status,
        resolverId = resolverId?.toString(),
        resolutionNote = resolutionNote,
        createdAt = createdAt.toString(),
        resolvedAt = resolvedAt?.toString(),
    )
}
