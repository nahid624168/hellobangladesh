package com.exambase.core.network

import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteFolderRequest(
    val name: String,
    val parentId: String? = null,
    val color: String? = null,
)

@Serializable
data class UpdateNoteFolderRequest(
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
    val baseVersion: Int? = null,
)

@Serializable
data class NoteFolder(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val color: String? = null,
    val position: Int = 0,
    val updatedAt: String = "",
    val createdAt: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class NoteAttachment(
    val id: String,
    val url: String,
    val fileName: String = "",
    val contentType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class Note(
    val id: String,
    val folderId: String? = null,
    val title: String = "",
    val body: String = "",
    val color: String? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val lessonId: String? = null,
    val quizSetId: String? = null,
    val flashcardSetId: String? = null,
    val version: Int = 1,
    val updatedAt: String = "",
    val createdAt: String = "",
    val deleted: Boolean = false,
    val attachments: List<NoteAttachment> = emptyList(),
)

@Serializable
data class NoteSummary(
    val id: String,
    val folderId: String? = null,
    val title: String = "",
    val preview: String = "",
    val color: String? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val version: Int = 1,
    val updatedAt: String = "",
)

@Serializable
data class NoteVersion(
    val version: Int,
    val title: String,
    val body: String,
    val createdAt: String = "",
)

@Serializable
data class AddNoteAttachmentRequest(
    val url: String,
    val fileName: String = "",
    val contentType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class NotesSyncResponse(
    val notes: List<Note> = emptyList(),
    val folders: List<NoteFolder> = emptyList(),
    val serverTime: String = "",
)
