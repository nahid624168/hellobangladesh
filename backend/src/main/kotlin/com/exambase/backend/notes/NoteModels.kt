package com.exambase.backend.notes

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,
    val color: String? = null,
)

@Serializable
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: String? = null,
    val clearParent: Boolean = false,
    val color: String? = null,
    val clearColor: Boolean = false,
    val position: Int? = null,
)

@Serializable
data class CreateNoteRequest(
    val title: String = "",
    val body: String = "",
    val folderId: String? = null,
    val color: String? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val lessonId: String? = null,
    val quizSetId: String? = null,
    val flashcardSetId: String? = null,
)

@Serializable
data class UpdateNoteRequest(
    val title: String? = null,
    val body: String? = null,
    val folderId: String? = null,
    val clearFolder: Boolean = false,
    val color: String? = null,
    val clearColor: Boolean = false,
    val pinned: Boolean? = null,
    val favorite: Boolean? = null,
    val tags: List<String>? = null,
    val lessonId: String? = null,
    val clearLesson: Boolean = false,
    val quizSetId: String? = null,
    val clearQuizSet: Boolean = false,
    val flashcardSetId: String? = null,
    val clearFlashcardSet: Boolean = false,
    /** Client version for optimistic concurrency; if set and stale → 409 conflict. */
    val baseVersion: Int? = null,
)

@Serializable
data class NoteFolderDto(
    val id: String,
    val parentId: String?,
    val name: String,
    val color: String?,
    val position: Int,
    val updatedAt: String,
    val createdAt: String,
    val deleted: Boolean = false,
)

@Serializable
data class NoteAttachmentDto(
    val id: String,
    val url: String,
    val fileName: String,
    val contentType: String?,
    val sizeBytes: Long?,
)

@Serializable
data class NoteDto(
    val id: String,
    val folderId: String?,
    val title: String,
    val body: String,
    val color: String?,
    val pinned: Boolean,
    val favorite: Boolean,
    val tags: List<String>,
    val lessonId: String?,
    val quizSetId: String?,
    val flashcardSetId: String?,
    val version: Int,
    val updatedAt: String,
    val createdAt: String,
    val deleted: Boolean = false,
    val attachments: List<NoteAttachmentDto> = emptyList(),
)

@Serializable
data class NoteSummaryDto(
    val id: String,
    val folderId: String?,
    val title: String,
    val preview: String,
    val color: String?,
    val pinned: Boolean,
    val favorite: Boolean,
    val tags: List<String>,
    val version: Int,
    val updatedAt: String,
)

@Serializable
data class NoteVersionDto(
    val version: Int,
    val title: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class AddAttachmentRequest(
    val url: String,
    val fileName: String = "",
    val contentType: String? = null,
    val sizeBytes: Long? = null,
)

/** Pull changes since a watermark (ISO-8601). Includes tombstones (deleted=true). */
@Serializable
data class NotesSyncResponse(
    val notes: List<NoteDto>,
    val folders: List<NoteFolderDto>,
    val serverTime: String,
)
