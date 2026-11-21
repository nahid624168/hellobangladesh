package com.exambase.backend.admin

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryAdminRepository : AdminRepository {
    private val users = ConcurrentHashMap<UUID, UserRow>()
    private val audit = mutableListOf<AuditLogRow>()
    private val flags = ConcurrentHashMap<String, FeatureFlagRow>()
    private val config = ConcurrentHashMap<String, AppConfigRow>()
    private val reports = ConcurrentHashMap<UUID, AbuseReportRow>()

    fun seedUser(row: UserRow) {
        users[row.id] = row
    }

    override fun searchUsers(query: String?, status: String?, role: String?, limit: Int, cursor: String?): Pair<List<UserRow>, String?> {
        var list = users.values.filter { it.status != "deleted" }
        if (!query.isNullOrBlank()) {
            val q = query.lowercase()
            list = list.filter { it.email.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
        }
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        if (!role.isNullOrBlank()) list = list.filter { role in it.roles }
        list = list.sortedWith(compareByDescending<UserRow> { it.createdAt }.thenByDescending { it.id })
        return list.take(limit) to null
    }

    override fun getUser(id: UUID): UserRow? = users[id]?.takeIf { it.status != "deleted" }

    override fun updateUserStatus(id: UUID, status: String, updatedAt: Instant) {
        users[id]?.let { users[id] = it.copy(status = status, updatedAt = updatedAt) }
    }

    override fun setUserRoles(userId: UUID, roleCodes: List<String>) {
        users[userId]?.let { users[userId] = it.copy(roles = roleCodes) }
    }

    override fun listRolesFor(userId: UUID): List<String> = users[userId]?.roles ?: emptyList()

    override fun insertAudit(
        actorId: UUID?,
        action: String,
        resourceType: String,
        resourceId: String?,
        detailsJson: String,
        ip: String?,
        createdAt: Instant,
    ) {
        audit += AuditLogRow(UUID.randomUUID(), actorId, action, resourceType, resourceId, detailsJson, createdAt)
    }

    override fun listAuditLogs(
        actorId: UUID?,
        resourceType: String?,
        action: String?,
        limit: Int,
        cursor: String?,
    ): Pair<List<AuditLogRow>, String?> {
        var list = audit.toList()
        if (actorId != null) list = list.filter { it.actorId == actorId }
        if (!resourceType.isNullOrBlank()) list = list.filter { it.resourceType == resourceType }
        if (!action.isNullOrBlank()) list = list.filter { it.action == action }
        list = list.sortedWith(compareByDescending<AuditLogRow> { it.createdAt }.thenByDescending { it.id })
        return list.take(limit) to null
    }

    override fun listFeatureFlags(): List<FeatureFlagRow> = flags.values.sortedBy { it.key }

    override fun getFeatureFlag(key: String): FeatureFlagRow? = flags[key]

    override fun upsertFeatureFlag(key: String, enabled: Boolean, description: String, updatedBy: UUID?, updatedAt: Instant) {
        flags[key] = FeatureFlagRow(key, enabled, description, updatedAt)
    }

    override fun listAppConfig(): List<AppConfigRow> = config.values.sortedBy { it.key }

    override fun getAppConfig(key: String): AppConfigRow? = config[key]

    override fun upsertAppConfig(key: String, valueJson: String, description: String, updatedBy: UUID?, updatedAt: Instant) {
        config[key] = AppConfigRow(key, valueJson, description, updatedAt)
    }

    override fun createAbuseReport(row: AbuseReportRow) {
        reports[row.id] = row
    }

    override fun listAbuseReports(status: String?, limit: Int, cursor: String?): Pair<List<AbuseReportRow>, String?> {
        var list = reports.values.toList()
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        list = list.sortedWith(compareByDescending<AbuseReportRow> { it.createdAt }.thenByDescending { it.id })
        return list.take(limit) to null
    }

    override fun getAbuseReport(id: UUID): AbuseReportRow? = reports[id]

    override fun resolveAbuseReport(id: UUID, status: String, resolverId: UUID, note: String?, resolvedAt: Instant?) {
        reports[id]?.let {
            reports[id] = it.copy(status = status, resolverId = resolverId, resolutionNote = note, resolvedAt = resolvedAt)
        }
    }

    private val subjects = ConcurrentHashMap<UUID, ContentNodeRow>()
    private val questions = ConcurrentHashMap<UUID, AdminQuestionRow>()
    private val options = ConcurrentHashMap<UUID, MutableList<AdminQuestionOptionRow>>()
    private val tokens = ConcurrentHashMap<String, DestructiveTokenRow>()
    private val contests = ConcurrentHashMap<UUID, AdminContestListRow>()
    private val exams = ConcurrentHashMap<UUID, AdminExamListRow>()
    private val batches = ConcurrentHashMap<UUID, ImportBatchRow>()

    fun seedContest(row: AdminContestListRow) { contests[row.id] = row }
    fun seedExam(row: AdminExamListRow) { exams[row.id] = row }
    fun seedQuestion(row: AdminQuestionRow) { questions[row.id] = row }

    override fun listSubjectsAdmin(status: String?, limit: Int, cursor: String?): Pair<List<ContentNodeRow>, String?> {
        var list = subjects.values.toList()
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        return list.take(limit) to null
    }
    override fun listChildrenAdmin(kind: String, parentId: UUID, status: String?, limit: Int) = emptyList<ContentNodeRow>()
    override fun getContentNode(kind: String, id: UUID) = subjects[id]
    override fun createSubject(title: String, titleBn: String?, description: String, descriptionBn: String?, icon: String?, featured: Boolean, status: String, sortOrder: Int): ContentNodeRow {
        val id = UUID.randomUUID()
        val row = ContentNodeRow(id, "subject", title, titleBn, status, null, sortOrder, Instant.now())
        subjects[id] = row
        return row
    }
    override fun updateSubject(id: UUID, title: String, titleBn: String?, description: String, descriptionBn: String?, icon: String?, featured: Boolean, status: String, sortOrder: Int): ContentNodeRow? {
        val row = ContentNodeRow(id, "subject", title, titleBn, status, null, sortOrder, Instant.now())
        subjects[id] = row
        return row
    }
    override fun setContentStatus(kind: String, id: UUID, status: String, updatedAt: Instant): Boolean {
        val existing = subjects[id] ?: return false
        subjects[id] = existing.copy(status = status, updatedAt = updatedAt)
        return true
    }
    override fun createCategory(subjectId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int) =
        ContentNodeRow(UUID.randomUUID(), "category", title, titleBn, status, subjectId, sortOrder, Instant.now())
    override fun createSubcategory(categoryId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int) =
        ContentNodeRow(UUID.randomUUID(), "subcategory", title, titleBn, status, categoryId, sortOrder, Instant.now())
    override fun createLesson(subcategoryId: UUID, title: String, titleBn: String?, summary: String, body: String, status: String, sortOrder: Int, estimatedMinutes: Int?) =
        ContentNodeRow(UUID.randomUUID(), "lesson", title, titleBn, status, subcategoryId, sortOrder, Instant.now())
    override fun updateLesson(id: UUID, title: String, titleBn: String?, summary: String, body: String, status: String, sortOrder: Int, estimatedMinutes: Int?) =
        ContentNodeRow(id, "lesson", title, titleBn, status, null, sortOrder, Instant.now())

    override fun listQuestionsAdmin(lessonId: UUID?, status: String?, limit: Int, cursor: String?): Pair<List<AdminQuestionRow>, String?> {
        var list = questions.values.toList()
        if (lessonId != null) list = list.filter { it.lessonId == lessonId }
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        return list.take(limit) to null
    }
    override fun getQuestionAdmin(id: UUID) = questions[id]
    override fun listOptionsAdmin(questionId: UUID) = options[questionId]?.toList() ?: emptyList()
    override fun createQuestion(
        lessonId: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        optionsIn: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow {
        val id = UUID.randomUUID()
        val row = AdminQuestionRow(id, lessonId, type, text, textBn, explanation, difficulty, marks, negativeMarks, status, sortOrder, null, Instant.now())
        questions[id] = row
        options[id] = optionsIn.mapIndexed { i, o ->
            AdminQuestionOptionRow(UUID.randomUUID(), id, o.text, o.textBn, o.isCorrect, o.sortOrder.takeIf { it != 0 } ?: i)
        }.toMutableList()
        return row
    }
    override fun updateQuestion(
        id: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        optionsIn: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow? {
        if (id !in questions) return null
        val row = AdminQuestionRow(id, questions[id]!!.lessonId, type, text, textBn, explanation, difficulty, marks, negativeMarks, status, sortOrder, null, Instant.now())
        questions[id] = row
        options[id] = optionsIn.mapIndexed { i, o ->
            AdminQuestionOptionRow(UUID.randomUUID(), id, o.text, o.textBn, o.isCorrect, i)
        }.toMutableList()
        return row
    }
    override fun setQuestionStatus(id: UUID, status: String, reviewNote: String?, reviewedBy: UUID?, reviewedAt: Instant?): Boolean {
        val q = questions[id] ?: return false
        questions[id] = q.copy(status = status, reviewNote = reviewNote)
        return true
    }
    override fun deleteQuestionHard(id: UUID): Boolean = questions.remove(id) != null

    override fun listContestsAdmin(status: String?, limit: Int): List<AdminContestListRow> {
        var list = contests.values.toList()
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        return list.take(limit)
    }
    override fun setContestStatus(id: UUID, status: String): Boolean {
        val c = contests[id] ?: return false
        contests[id] = c.copy(status = status)
        return true
    }
    override fun listExamsAdmin(status: String?, limit: Int): List<AdminExamListRow> {
        var list = exams.values.toList()
        if (!status.isNullOrBlank()) list = list.filter { it.status == status }
        return list.take(limit)
    }
    override fun setExamStatus(id: UUID, status: String): Boolean {
        val e = exams[id] ?: return false
        exams[id] = e.copy(status = status)
        return true
    }

    override fun saveDestructiveToken(row: DestructiveTokenRow) { tokens[row.tokenHash] = row }
    override fun findDestructiveToken(tokenHash: String) = tokens[tokenHash]
    override fun markDestructiveTokenUsed(id: UUID, usedAt: Instant) {
        tokens.values.find { it.id == id }?.let { tokens[it.tokenHash] = it.copy(usedAt = usedAt) }
    }
    override fun createImportBatch(row: ImportBatchRow) { batches[row.id] = row }
    override fun finishImportBatch(id: UUID, status: String, success: Int, errors: Int, errorsJson: String, finishedAt: Instant) {
        batches[id]?.let { batches[id] = it.copy(status = status, successCount = success, errorCount = errors, errorsJson = errorsJson, finishedAt = finishedAt) }
    }
}
