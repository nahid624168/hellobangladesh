package com.exambase.backend.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.UUID

/** Role codes matching V1 roles table. Higher id = more power. */
object Roles {
    const val GUEST = "guest"
    const val USER = "user"
    const val PREMIUM = "premium"
    const val MODERATOR = "moderator"
    const val EDITOR = "editor"
    const val ADMIN = "admin"
    const val SUPER_ADMIN = "super_admin"

    val ORDER = listOf(GUEST, USER, PREMIUM, MODERATOR, EDITOR, ADMIN, SUPER_ADMIN)
    fun rank(code: String): Int = ORDER.indexOf(code).coerceAtLeast(0)
}

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val status: String,
    val emailVerified: Boolean,
    val roles: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AdminUserListDto(
    val items: List<AdminUserDto>,
    val nextCursor: String? = null,
)

@Serializable
data class UpdateUserStatusRequest(
    val status: String, // active | suspended
    val reason: String? = null,
)

@Serializable
data class SetRolesRequest(
    val roles: List<String>,
)

@Serializable
data class FeatureFlagDto(
    val key: String,
    val enabled: Boolean,
    val description: String,
    val updatedAt: String? = null,
)

@Serializable
data class UpsertFeatureFlagRequest(
    val enabled: Boolean,
    val description: String? = null,
)

@Serializable
data class AppConfigEntryDto(
    val key: String,
    val value: JsonElement,
    val description: String,
    val updatedAt: String? = null,
)

@Serializable
data class UpsertAppConfigRequest(
    val value: JsonElement,
    val description: String? = null,
)

@Serializable
data class AuditLogDto(
    val id: String,
    val actorId: String? = null,
    val action: String,
    val resourceType: String,
    val resourceId: String? = null,
    val details: JsonElement? = null,
    val createdAt: String,
)

@Serializable
data class AuditLogListDto(
    val items: List<AuditLogDto>,
    val nextCursor: String? = null,
)

@Serializable
data class AbuseReportDto(
    val id: String,
    val reporterId: String? = null,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: String,
    val resolverId: String? = null,
    val resolutionNote: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
)

@Serializable
data class AbuseReportListDto(
    val items: List<AbuseReportDto>,
    val nextCursor: String? = null,
)

@Serializable
data class ResolveAbuseReportRequest(
    val status: String, // resolved | dismissed | reviewing
    val resolutionNote: String? = null,
)

@Serializable
data class CreateAbuseReportRequest(
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String? = null,
)

// Internal domain rows
data class UserRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: String,
    val emailVerifiedAt: Instant?,
    val roles: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AuditLogRow(
    val id: UUID,
    val actorId: UUID?,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val detailsJson: String,
    val createdAt: Instant,
)

data class FeatureFlagRow(
    val key: String,
    val enabled: Boolean,
    val description: String,
    val updatedAt: Instant,
)

data class AppConfigRow(
    val key: String,
    val valueJson: String,
    val description: String,
    val updatedAt: Instant,
)

data class AbuseReportRow(
    val id: UUID,
    val reporterId: UUID?,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String?,
    val status: String,
    val resolverId: UUID?,
    val resolutionNote: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
)

// ---------- Step 30b: content / questions / contests / exams / destructive tokens ----------

@Serializable
data class AdminContentNodeDto(
    val id: String,
    val kind: String, // subject | category | subcategory | lesson
    val title: String,
    val titleBn: String? = null,
    val status: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: String? = null,
)

@Serializable
data class AdminContentListDto(
    val items: List<AdminContentNodeDto>,
    val nextCursor: String? = null,
)

@Serializable
data class UpsertSubjectRequest(
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val descriptionBn: String? = null,
    val icon: String? = null,
    val isFeatured: Boolean = false,
    val status: String = "draft",
    val sortOrder: Int = 0,
)

@Serializable
data class UpsertCategoryRequest(
    val subjectId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val status: String = "draft",
    val sortOrder: Int = 0,
)

@Serializable
data class UpsertSubcategoryRequest(
    val categoryId: String,
    val title: String,
    val titleBn: String? = null,
    val description: String = "",
    val status: String = "draft",
    val sortOrder: Int = 0,
)

@Serializable
data class UpsertLessonRequest(
    val subcategoryId: String,
    val title: String,
    val titleBn: String? = null,
    val summary: String = "",
    val bodyMarkdown: String = "",
    val status: String = "draft",
    val sortOrder: Int = 0,
    val estimatedMinutes: Int? = null,
)

@Serializable
data class SetContentStatusRequest(
    val status: String,
)

@Serializable
data class AdminQuestionOptionInput(
    val id: String? = null,
    val text: String,
    val textBn: String? = null,
    val isCorrect: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class AdminQuestionUpsertRequest(
    val lessonId: String,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val explanation: String = "",
    val explanationBn: String? = null,
    val difficulty: String = "medium",
    val marks: Double = 1.0,
    val negativeMarks: Double = 0.0,
    val status: String = "draft",
    val sortOrder: Int = 0,
    val options: List<AdminQuestionOptionInput> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class AdminQuestionDto(
    val id: String,
    val lessonId: String,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val explanation: String = "",
    val difficulty: String,
    val marks: Double,
    val negativeMarks: Double,
    val status: String,
    val sortOrder: Int = 0,
    val options: List<AdminQuestionOptionDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val reviewNote: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AdminQuestionOptionDto(
    val id: String,
    val text: String,
    val textBn: String? = null,
    val isCorrect: Boolean,
    val sortOrder: Int = 0,
)

@Serializable
data class AdminQuestionListDto(
    val items: List<AdminQuestionDto>,
    val nextCursor: String? = null,
)

@Serializable
data class SetQuestionStatusRequest(
    val status: String, // draft | review | published | archived
    val reviewNote: String? = null,
)

@Serializable
data class BulkImportQuestionItem(
    val lessonId: String,
    val type: String,
    val questionText: String,
    val questionTextBn: String? = null,
    val explanation: String = "",
    val difficulty: String = "medium",
    val marks: Double = 1.0,
    val negativeMarks: Double = 0.0,
    val status: String = "draft",
    val options: List<AdminQuestionOptionInput> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class BulkImportRequest(
    val questions: List<BulkImportQuestionItem>,
)

@Serializable
data class BulkImportResultDto(
    val batchId: String,
    val status: String,
    val totalRows: Int,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList(),
)

@Serializable
data class AdminContestDto(
    val id: String,
    val title: String,
    val status: String,
    val startsAt: String,
    val endsAt: String,
    val participantCount: Int = 0,
)

@Serializable
data class AdminContestListDto(val items: List<AdminContestDto>)

@Serializable
data class SetContestStatusRequest(val status: String)

@Serializable
data class AdminExamDto(
    val id: String,
    val title: String,
    val status: String,
    val opensAt: String,
    val closesAt: String,
)

@Serializable
data class AdminExamListDto(val items: List<AdminExamDto>)

@Serializable
data class SetExamStatusRequest(val status: String)

@Serializable
data class RequestDestructiveTokenRequest(
    val action: String,
    val resourceType: String,
    val resourceId: String,
)

@Serializable
data class DestructiveTokenDto(
    val token: String,
    val expiresAt: String,
    val action: String,
    val resourceType: String,
    val resourceId: String,
)

@Serializable
data class ConfirmDestructiveRequest(
    val token: String,
)

// Internal rows for 30b
data class ContentNodeRow(
    val id: UUID,
    val kind: String,
    val title: String,
    val titleBn: String?,
    val status: String,
    val parentId: UUID?,
    val sortOrder: Int,
    val updatedAt: Instant,
)

data class AdminQuestionRow(
    val id: UUID,
    val lessonId: UUID,
    val type: String,
    val questionText: String,
    val questionTextBn: String?,
    val explanation: String,
    val difficulty: String,
    val marks: Double,
    val negativeMarks: Double,
    val status: String,
    val sortOrder: Int,
    val reviewNote: String?,
    val updatedAt: Instant,
)

data class AdminQuestionOptionRow(
    val id: UUID,
    val questionId: UUID,
    val text: String,
    val textBn: String?,
    val isCorrect: Boolean,
    val sortOrder: Int,
)

data class DestructiveTokenRow(
    val id: UUID,
    val actorId: UUID,
    val action: String,
    val resourceType: String,
    val resourceId: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

data class ImportBatchRow(
    val id: UUID,
    val actorId: UUID,
    val status: String,
    val totalRows: Int,
    val successCount: Int,
    val errorCount: Int,
    val errorsJson: String,
    val createdAt: Instant,
    val finishedAt: Instant?,
)
