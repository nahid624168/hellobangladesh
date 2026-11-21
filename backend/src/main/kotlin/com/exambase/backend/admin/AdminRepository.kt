package com.exambase.backend.admin

import java.time.Instant
import java.util.UUID

interface AdminRepository {
    fun searchUsers(query: String?, status: String?, role: String?, limit: Int, cursor: String?): Pair<List<UserRow>, String?>
    fun getUser(id: UUID): UserRow?
    fun updateUserStatus(id: UUID, status: String, updatedAt: Instant)
    fun setUserRoles(userId: UUID, roleCodes: List<String>)
    fun listRolesFor(userId: UUID): List<String>

    fun insertAudit(
        actorId: UUID?,
        action: String,
        resourceType: String,
        resourceId: String?,
        detailsJson: String,
        ip: String?,
        createdAt: Instant,
    )

    fun listAuditLogs(
        actorId: UUID?,
        resourceType: String?,
        action: String?,
        limit: Int,
        cursor: String?,
    ): Pair<List<AuditLogRow>, String?>

    fun listFeatureFlags(): List<FeatureFlagRow>
    fun getFeatureFlag(key: String): FeatureFlagRow?
    fun upsertFeatureFlag(key: String, enabled: Boolean, description: String, updatedBy: UUID?, updatedAt: Instant)

    fun listAppConfig(): List<AppConfigRow>
    fun getAppConfig(key: String): AppConfigRow?
    fun upsertAppConfig(key: String, valueJson: String, description: String, updatedBy: UUID?, updatedAt: Instant)

    fun createAbuseReport(row: AbuseReportRow)
    fun listAbuseReports(status: String?, limit: Int, cursor: String?): Pair<List<AbuseReportRow>, String?>
    fun getAbuseReport(id: UUID): AbuseReportRow?
    fun resolveAbuseReport(id: UUID, status: String, resolverId: UUID, note: String?, resolvedAt: Instant?)

    // ---- 30b content ----
    fun listSubjectsAdmin(status: String?, limit: Int, cursor: String?): Pair<List<ContentNodeRow>, String?>
    fun listChildrenAdmin(kind: String, parentId: UUID, status: String?, limit: Int): List<ContentNodeRow>
    fun getContentNode(kind: String, id: UUID): ContentNodeRow?
    fun createSubject(title: String, titleBn: String?, description: String, descriptionBn: String?, icon: String?, featured: Boolean, status: String, sortOrder: Int): ContentNodeRow
    fun updateSubject(id: UUID, title: String, titleBn: String?, description: String, descriptionBn: String?, icon: String?, featured: Boolean, status: String, sortOrder: Int): ContentNodeRow?
    fun setContentStatus(kind: String, id: UUID, status: String, updatedAt: Instant): Boolean
    fun createCategory(subjectId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int): ContentNodeRow
    fun createSubcategory(categoryId: UUID, title: String, titleBn: String?, description: String, status: String, sortOrder: Int): ContentNodeRow
    fun createLesson(subcategoryId: UUID, title: String, titleBn: String?, summary: String, body: String, status: String, sortOrder: Int, estimatedMinutes: Int?): ContentNodeRow
    fun updateLesson(id: UUID, title: String, titleBn: String?, summary: String, body: String, status: String, sortOrder: Int, estimatedMinutes: Int?): ContentNodeRow?

    // ---- questions ----
    fun listQuestionsAdmin(lessonId: UUID?, status: String?, limit: Int, cursor: String?): Pair<List<AdminQuestionRow>, String?>
    fun getQuestionAdmin(id: UUID): AdminQuestionRow?
    fun listOptionsAdmin(questionId: UUID): List<AdminQuestionOptionRow>
    fun createQuestion(
        lessonId: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        options: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow
    fun updateQuestion(
        id: UUID, type: String, text: String, textBn: String?, explanation: String, explanationBn: String?,
        difficulty: String, marks: Double, negativeMarks: Double, status: String, sortOrder: Int,
        options: List<AdminQuestionOptionInput>, tags: List<String>,
    ): AdminQuestionRow?
    fun setQuestionStatus(id: UUID, status: String, reviewNote: String?, reviewedBy: UUID?, reviewedAt: Instant?): Boolean
    fun deleteQuestionHard(id: UUID): Boolean

    // ---- contests / exams ----
    fun listContestsAdmin(status: String?, limit: Int): List<AdminContestListRow>
    fun setContestStatus(id: UUID, status: String): Boolean
    fun listExamsAdmin(status: String?, limit: Int): List<AdminExamListRow>
    fun setExamStatus(id: UUID, status: String): Boolean

    // ---- destructive tokens + import ----
    fun saveDestructiveToken(row: DestructiveTokenRow)
    fun findDestructiveToken(tokenHash: String): DestructiveTokenRow?
    fun markDestructiveTokenUsed(id: UUID, usedAt: Instant)
    fun createImportBatch(row: ImportBatchRow)
    fun finishImportBatch(id: UUID, status: String, success: Int, errors: Int, errorsJson: String, finishedAt: Instant)
}

data class AdminContestListRow(
    val id: UUID,
    val title: String,
    val status: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val participantCount: Int,
)

data class AdminExamListRow(
    val id: UUID,
    val title: String,
    val status: String,
    val opensAt: Instant,
    val closesAt: Instant,
)
